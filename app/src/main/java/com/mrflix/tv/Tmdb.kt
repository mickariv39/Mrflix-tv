package com.mrflix.tv

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** Ce que TMDB nous apprend sur un titre. */
data class Fiche(
    val affiche: String?,
    val titreCorrige: String?,
    val fond: String?,
    /** Identifiant TMDB, nécessaire pour la fiche détaillée. 0 si inconnu. */
    val id: Int = 0,
    /** Résumé court, déjà présent dans la réponse de recherche. */
    val resume: String = "",
    /** Genre TMDB principal. Vide si TMDB n'en donne pas. */
    val genre: String = ""
)

/** Fiche détaillée : ce qu'on affiche avant de lancer la lecture. */
data class Detail(
    val resume: String,
    val note: Double,
    val duree: Int,
    val saisons: Int,
    val genres: List<String>,
    val realisation: String,
    val acteurs: List<Acteur>
)

data class Acteur(val nom: String, val role: String, val photo: String?)

/**
 * Affiches TMDB. Transposition de tmdb.js.
 *
 * Trois principes repris tels quels côté Windows :
 *   - le titre du fournisseur est nettoyé avant d'être cherché ;
 *   - le résultat est mis en cache sur disque, y compris les échecs ;
 *   - trois requêtes simultanées au maximum.
 */
object Tmdb {

    private const val IMAGES = "https://image.tmdb.org/t/p/"
    // w154 : encore un tiers de moins que w185, et toujours suffisant pour
    // des vignettes de 150x225 dp regardées à quatre mètres. Le cache existant
    // est réécrit à la lecture, quelle que soit l'ancienne taille.
    private const val TAILLE_AFFICHE = "w154"
    private const val TAILLE_FOND = "w1280"

    // Six au lieu de trois : TMDB tolère bien plus, et avec trois permis
    // les cartes visibles attendaient leur tour pendant le défilement.
    private val enParallele = Semaphore(6)
    /**
     * Tout ce qui a déjà été cherché, SANS plafond utile.
     *
     * Il y avait ici une limite de Config.FICHES_EN_MEMOIRE (2 000) entrées.
     * Avec 36 000 titres sur le disque, chargeCache() en évinçait 34 000 à
     * l'instant même où il les lisait : enCache() les voyait alors comme
     * « jamais cherchés » et le préchargement les redemandait au réseau à
     * chaque session. Le travail n'était pas perdu sur le disque, il était
     * perdu en mémoire.
     *
     * Ce qui rend la levée du plafond possible : le résumé n'est plus gardé.
     * C'était le seul champ volumineux — un paragraphe contre quelques URL.
     * Sans lui, une fiche pèse environ 400 octets, soit 15 Mo pour 36 000
     * titres, du même ordre que le catalogue lui-même. Le résumé complet vient
     * de detail(), qui est appelé à l'ouverture d'une fiche.
     *
     * Le plafond restant n'est qu'un garde-fou contre un catalogue aberrant.
     */
    private const val PLAFOND_MEMOIRE = 80_000

    /**
     * Ordre d'INSERTION, surtout pas d'accès.
     *
     * Le troisième paramètre de LinkedHashMap valait `true` — ordre d'accès,
     * pour une éviction LRU. Sur une telle table, LIRE MODIFIE LA STRUCTURE :
     * chaque `cache[c]` déplace l'entrée en fin de liste et invalide tout
     * itérateur en cours ailleurs. Une lecture non protégée, présente ou
     * ajoutée plus tard, suffisait à faire tomber une ConcurrentModification
     * dans apprendReparations() — au premier démarrage, quand les cartes
     * cherchent leurs affiches pendant que le cache finit de se charger.
     *
     * Le bénéfice était nul : le plafond est à 80 000 pour environ 36 000
     * fiches, l'éviction ne se déclenche jamais. Elle reste comme garde-fou
     * contre un catalogue aberrant, mais en ordre d'insertion — et les
     * lectures redeviennent de vraies lectures.
     */
    private val cache = object : LinkedHashMap<String, Fiche?>(4096, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Fiche?>) =
            size > PLAFOND_MEMOIRE
    }
    private var fichier: File? = null
    private var modifie = false

    /** Nouvelles fiches accumulées depuis la dernière écriture sur disque. */
    private const val SAUVEGARDE_TOUS_LES = 40
    private var depuisSauvegarde = 0

    fun init(contexte: Context) {
        fichier = File(contexte.filesDir, "tmdb.json")
        // Gardé pour le socle embarqué dans l'APK. C'est l'application, pas
        // l'activité, sinon on retient une activité détruite.
        ressources = contexte.applicationContext.assets
    }

    private var ressources: android.content.res.AssetManager? = null

    /**
     * Lecture du cache disque. À appeler depuis une coroutine, AVANT le
     * catalogue : avec des dizaines de milliers de fiches, analyser ce fichier
     * sur le fil principal gèlerait l'ouverture de l'application.
     */
    suspend fun prepare() = withContext(Dispatchers.IO) {
        if (charge) return@withContext
        chargeCache()
        charge = true
        // La table de réparation s'apprend sur le cache complet ; elle ne sert
        // qu'aux recherches réseau et à la maintenance, donc on la construit
        // après avoir rendu le cache utilisable.
        apprendReparations()
    }

    private var charge = false

    /** Ramène une URL d'affiche à la taille configurée, quelle que soit l'ancienne. */
    private fun tailleCourante(url: String) =
        url.replace(Regex("/t/p/w\\d+/"), "/t/p/$TAILLE_AFFICHE/")

    /**
     * Écarte les URL d'affiche inexploitables, sans fermer la porte aux
     * services autres que TMDB.
     *
     * Le socle produit par le PC contient des URL en « w154null » : quand TMDB
     * connaît le titre mais n'a aucune affiche, `poster_path` vaut null et la
     * concaténation le colle tel quel. Ces URL ne sont PAS nulles, donc le
     * repli `fiche?.affiche ?: film.affiche` de CarteFilm ne se déclenchait
     * jamais : la jaquette du fournisseur, souvent présente, n'était même pas
     * essayée.
     *
     * Deux rejets seulement, et surtout PAS un motif propre à TMDB : les
     * affiches de secours viennent d'OMDb ou de TVmaze, sur d'autres serveurs
     * et sous d'autres formes. Un filtre trop précis les rejetterait toutes.
     *
     * Appliqué à la LECTURE : les mauvaises URL déjà sur le disque disparaissent
     * d'elles-mêmes à la prochaine sauvegarde, sans repasser sur le fichier.
     */
    private fun urlUtilisable(brut: String?): String? {
        val url = brut?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!url.startsWith("http")) return null
        if (url.endsWith("null")) return null
        return url
    }

    val actif: Boolean get() = Config.CLE_TMDB.length > 10

    /* ------------------------------------------------------------ Nettoyage */

    // Deux formes distinctes de la même liste : sur Windows, une seule
    // expression avec le drapeau g retenait sa position entre deux .test().
    private const val TECHNIQUE =
        "vf|vff|vfq|vos+t*(?:fr)?|vosta|vo|multi|hd|fhd|sd|4k|uhd|hevc|hvec|x26[45]|" +
                "web|webrip|bluray|bdrip|dvdrip|remux|imax|truefrench|french|coreen|cor[ée]en|turc|turque|" +
                "espagnol|italien|anglais|japonais|chinois|indien|arabe|nollywood|bollywood"

    private val MOTS_TECHNIQUES = Regex("\\b($TECHNIQUE)\\b", RegexOption.IGNORE_CASE)
    private val TECHNIQUE_SEUL = Regex("^\\s*($TECHNIQUE|\\d{3,4}p)\\s*$", RegexOption.IGNORE_CASE)

    private val DECORATIONS = Regex("[\\u2070-\\u209f\\u1d00-\\u1dbf\\u2190-\\u2bff\\ufe0f]")
    private val ANNEE = Regex("\\b(19\\d{2}|20\\d{2})\\b")
    private val PARENTHESES = Regex("[\\[(（][^\\])）]*[\\])）]")
    private val SYMBOLES_TETE = Regex("^[^\\p{L}\\p{N}]+")
    private val CODE_TETE = Regex("^(?:[A-Z0-9]{2,4} ?){1,3}[^\\p{L}\\p{N}\\s]+\\s*")
    private val RESOLUTION = Regex("\\b\\d{3,4}p\\b", RegexOption.IGNORE_CASE)
    private val CODEC = Regex("\\bh\\.?26[45]\\b", RegexOption.IGNORE_CASE)
    private val EPISODE = Regex("\\bS\\d{1,2}\\s*E\\d{1,3}\\b", RegexOption.IGNORE_CASE)
    private val ALTERNATIF = Regex("[(（]\\s*([\\p{L}\\p{N}][^)）]{2,40})\\s*[)）]")

    data class TitrePropre(val titre: String, val annee: String, val alternatif: String)

    /**
     * Résultats de nettoieTitre() mémoïsés.
     *
     * nettoieTitre() enchaîne une quinzaine d'expressions régulières et il est
     * appelé PENDANT la composition (enCache() dans CarteFilm), sur le fil
     * principal, à chaque carte qui entre à l'écran pendant un défilement.
     * Le résultat ne dépend que du nom brut : on le calcule une fois.
     *
     * LRU plafonnée : borne l'empreinte sur un catalogue aberrant.
     *
     * Ordre d'INSERTION et non d'accès, pour la même raison que `cache` : avec
     * `accessOrder = true`, la lecture ligne suivante serait une modification
     * structurelle, et elle est appelée depuis le fil principal à chaque carte
     * qui défile pendant que d'autres threads écrivent ici.
     */
    private const val TITRES_PROPRES_MAX = 50_000
    private val titresPropres = object : LinkedHashMap<String, TitrePropre>(4096, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TitrePropre>) =
            size > TITRES_PROPRES_MAX
    }

    fun nettoieTitre(brut: String): TitrePropre {
        synchronized(titresPropres) { titresPropres[brut] }?.let { return it }
        val calcule = nettoieTitreCalcul(brut)
        synchronized(titresPropres) { titresPropres[brut] = calcule }
        return calcule
    }

    /** Transposition de cleanTitle(). Même ordre, mêmes garde-fous. */
    private fun nettoieTitreCalcul(brut: String): TitrePropre {
        // 1. Décorations Unicode : « 4K » en exposant, flèches, émojis.
        var nom = DECORATIONS.replace(brut, " ").replace(Regex("\\s{2,}"), " ")

        val annee = ANNEE.find(nom)?.groupValues?.get(1) ?: ""

        // 2. Titre original entre parenthèses, souvent le nom connu des bases.
        var alternatif = ALTERNATIF.find(nom)?.groupValues?.get(1) ?: ""
        if (alternatif.isNotEmpty() && (
                    Regex("^\\s*\\d{4}\\s*$").matches(alternatif) ||
                            !Regex("\\p{L}{2}").containsMatchIn(alternatif) ||
                            TECHNIQUE_SEUL.matches(alternatif.trim())
                    )
        ) alternatif = ""

        // 3. Segments de tête, en cascade : « |FR| |IT| », « FR DV| », « |FR|31| ».
        //    Au moins deux caractères, sinon « L'amour est dans le pré »
        //    perdrait son « L' », pris pour un code pays.
        for (i in 0 until 4) {
            val avant = nom
            nom = SYMBOLES_TETE.replace(nom, "")
            nom = CODE_TETE.replace(nom, "")
            if (nom == avant) break
        }

        nom = PARENTHESES.replace(nom, " ")
        nom = ANNEE.replace(nom, " ")
        nom = RESOLUTION.replace(nom, " ")
        nom = CODEC.replace(nom, " ")
        nom = MOTS_TECHNIQUES.replace(nom, " ")
        nom = EPISODE.replace(nom, " ")
        nom = Regex("[._]+").replace(nom, " ")
        nom = Regex("\\s{2,}").replace(nom, " ")
        nom = nom.trim().replace(Regex("^[\\s\\-|:.]+|[\\s\\-|:.…]+$"), "")

        return TitrePropre(nom, annee, alternatif)
    }

    /**
     * Transposition de variantes() dans tmdb.js. Sept formes au maximum,
     * essayées dans l'ordre : beaucoup de titres ne se trouvent que sous
     * l'une d'elles.
     */
    private fun variantes(p: TitrePropre): List<String> {
        val sortie = mutableListOf(p.titre)
        if (p.alternatif.isNotEmpty() && !p.alternatif.equals(p.titre, ignoreCase = true)) {
            sortie.add(p.alternatif)
        }

        fun ajoute(v: String) {
            val t = v.replace(Regex("\\s{2,}"), " ").trim()
            if (t.length >= 3 && sortie.none { it.equals(t, ignoreCase = true) }) sortie.add(t)
        }

        // Ponctuation décorative : « Ni vu… Ni connu… »
        ajoute(p.titre.replace("…", " "))

        // Sans accents ni apostrophes : « L'Été meurtrier » -> « L Ete meurtrier »
        ajoute(
            Normalizer.normalize(p.titre, Normalizer.Form.NFD)
                .replace(Regex("[\\u0300-\\u036f]"), "")
                .replace(Regex("['\u2019`]"), " ")
        )

        // Autour d'un tiret ou d'un deux-points : « Koh-Lanta : Les Chasseurs
        // de feu » ne se trouve que sous « Koh-Lanta ».
        val moities = p.titre.split(Regex("\\s+[-\u2013\u2014:]\\s+"))
        if (moities.size > 1) {
            ajoute(moities[0])
            ajoute(moities.drop(1).joinToString(" - "))
        }

        // Sans les numéros isolés : « Sauvez Willy 1 » -> « Sauvez Willy »
        ajoute(p.titre.replace(Regex("\\b\\d{1,2}\\b"), " "))

        // Partie latine seule, pour les titres bilingues arabe/latin.
        ajoute(p.titre.replace(Regex("[^\\p{IsLatin}\\p{N}\\s'\u2019:!?,.-]"), " "))

        ajoute(p.titre.replace(Regex("^(saga|collection|coffret)\\s+", RegexOption.IGNORE_CASE), ""))

        // Accents rendus à un titre amputé par le fournisseur : « astrix le
        // gaulois » redevient « astérix le gaulois ». Placé en dernier, il ne
        // coûte une requête que lorsque tout le reste a échoué.
        repare(p.titre)?.let { ajoute(it) }

        return sortie.take(8)
    }

    /* -------------------------------------------------------------- Requête */

    /** Panne réseau, à distinguer d'un titre réellement introuvable. */
    private class ErreurReseau : Exception()

    /** Résultat déjà connu. `fiche` à null signifie « cherché, introuvable ». */
    class Connu(val fiche: Fiche?)

    private fun cle(p: TitrePropre, estSerie: Boolean) =
        (if (estSerie) "tv:" else "film:") + p.titre.lowercase() + ":" + p.annee

    /**
     * Consultation immédiate du cache, sans réseau ni coroutine.
     *
     * Permet d'afficher une affiche déjà connue dès la première image, au lieu
     * de dessiner une carte vide puis de la redessiner. Même principe que la
     * passe `cachedOnly` de genres.js sous Windows.
     *
     * Renvoie null si le titre n'a jamais été cherché.
     */
    fun enCache(nomBrut: String, estSerie: Boolean): Connu? {
        if (!actif) return null
        val propre = nettoieTitre(nomBrut)
        if (propre.titre.length < 2) return null
        val c = cle(propre, estSerie)
        return synchronized(cache) { if (cache.containsKey(c)) Connu(cache[c]) else null }
    }

    /**
     * Portée de fond pour les recherches partagées : le travail se termine
     * même si la carte qui l'a demandé disparaît, et le résultat entre dans le
     * cache pour la fois suivante.
     */
    private val porteeFond = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Recherches en cours, par clé de cache : une seule requête réseau par titre. */
    private val enVol = ConcurrentHashMap<String, Deferred<Fiche?>>()

    suspend fun fiche(nomBrut: String, estSerie: Boolean): Fiche? {
        if (!actif) return null

        val propre = nettoieTitre(nomBrut)
        if (propre.titre.length < 2) return null

        val cle = cle(propre, estSerie)
        synchronized(cache) { if (cache.containsKey(cle)) return cache[cle] }

        // Plusieurs cartes peuvent réclamer le même titre en même temps
        // (rangées différentes, carte visible + préchargement) : elles
        // partagent une seule recherche au lieu de refaire chacune la cascade
        // de variantes.
        val tache = enVol.getOrPut(cle) {
            porteeFond.async { chercheEtRetiens(cle, propre, estSerie) }
        }
        return try {
            tache.await()
        } finally {
            enVol.remove(cle, tache)
        }
    }

    private suspend fun chercheEtRetiens(
        cle: String,
        propre: TitrePropre,
        estSerie: Boolean
    ): Fiche? {
        var resultat: Fiche? = null
        var erreurReseau = false

        enParallele.withPermit {
            // Même cascade que lookup() sous Windows : chaque variante du titre
            // est essayée avant d'abandonner.
            for (essai in variantes(propre)) {
                try {
                    resultat = cherche(TitrePropre(essai, propre.annee, ""), estSerie)
                } catch (e: ErreurReseau) {
                    erreurReseau = true
                }
                if (resultat != null) break
            }

            // Dernier recours : chercher dans l'autre catégorie. Les fournisseurs
            // rangent souvent un feuilleton en film, et inversement.
            if (resultat == null && !erreurReseau) {
                for (essai in variantes(propre)) {
                    try {
                        resultat = cherche(TitrePropre(essai, propre.annee, ""), !estSerie)
                    } catch (e: ErreurReseau) {
                        erreurReseau = true
                    }
                    if (resultat != null) break
                }
            }
        }

        // Un titre vraiment introuvable est mis en cache : sans ça il serait
        // recherché à chaque affichage. Mais un échec RÉSEAU ne l'est pas,
        // sinon un simple délai dépassé condamnerait le titre pour de bon.
        if (resultat == null && erreurReseau) return null

        val aSauver = synchronized(cache) {
            // Le résumé est retiré AVANT la mise en cache : c'est lui qui
            // pesait, et il est refourni par detail() quand une fiche s'ouvre.
            // L'appelant, lui, reçoit le résultat complet.
            cache[cle] = resultat?.copy(resume = "")
            modifie = true
            depuisSauvegarde += 1
            depuisSauvegarde >= SAUVEGARDE_TOUS_LES
        }
        // onStop ne suffit pas : le Chromecast tue l'application sans prévenir
        // et toute la session serait perdue.
        if (aSauver) withContext(Dispatchers.IO) { sauvegarde() }
        return resultat
    }

    private suspend fun cherche(t: TitrePropre, estSerie: Boolean): Fiche? =
        withContext(Dispatchers.IO) {
            val type = if (estSerie) "tv" else "movie"
            val adresse = StringBuilder("https://api.themoviedb.org/3/search/$type")
                .append("?api_key=${Config.CLE_TMDB}")
                .append("&language=${Config.LANGUE_TMDB}")
                .append("&query=${URLEncoder.encode(t.titre, "UTF-8")}")
            if (t.annee.isNotEmpty()) {
                adresse.append(if (estSerie) "&first_air_date_year=" else "&year=").append(t.annee)
            }

            try {
                val lien = URL(adresse.toString()).openConnection() as HttpURLConnection
                // Court exprès : une requête bloquée retient un permis et
                // laisse des cartes vides à l'écran.
                lien.connectTimeout = 6000
                lien.readTimeout = 6000
                val corps = try {
                    // Un code non-200 vient du serveur, pas du titre :
                    // c'est une panne, pas un « introuvable ».
                    if (lien.responseCode != 200) throw ErreurReseau()
                    lien.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    lien.disconnect()
                }

                val premiers = JSONObject(corps).optJSONArray("results") ?: return@withContext null
                if (premiers.length() == 0) return@withContext null
                val o = premiers.getJSONObject(0)

                val affiche = o.optString("poster_path").takeIf { it.length > 1 }
                val fond = o.optString("backdrop_path").takeIf { it.length > 1 }
                val titre = (if (estSerie) o.optString("name") else o.optString("title"))
                    .takeIf { it.isNotBlank() }

                Fiche(
                    // Une seule règle pour les trois sources — réseau, cache
                    // disque, socle du PC : ce qui n'est pas une image entre
                    // dans le cache comme absent, jamais comme URL bancale.
                    affiche = urlUtilisable(affiche?.let { IMAGES + TAILLE_AFFICHE + it }),
                    titreCorrige = titre,
                    fond = urlUtilisable(fond?.let { IMAGES + TAILLE_FOND + it }),
                    id = o.optInt("id", 0),
                    resume = o.optString("overview"),
                    // genre_ids est déjà dans la réponse : le premier suffit,
                    // c'est le principal. Aucune requête de plus.
                    genre = o.optJSONArray("genre_ids")
                        ?.takeIf { it.length() > 0 }
                        ?.let { Genres.depuisTmdb(it.optInt(0, -1), estSerie) }
                        ?: ""
                )
            } catch (e: ErreurReseau) {
                // Doit remonter : c'est une panne, pas un titre introuvable.
                // Un catch(Exception) trop large l'aurait avalée — même piège
                // que les coroutines annulées.
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Coupure, DNS, délai dépassé : panne, pas absence de résultat.
                throw ErreurReseau()
            }
        }

    /* -------------------------------------------------------- Préchargement */

    /** Un titre à chercher, tel que le catalogue le nomme. */
    data class ATrouver(val nom: String, val estSerie: Boolean)

    /**
     * Cherche à l'avance les affiches de tout le catalogue.
     *
     * Sans ça, chaque carte lance sa recherche au moment où elle apparaît :
     * le défilement paie le réseau. En préchargeant, tout est déjà en cache
     * quand l'utilisateur arrive sur la rangée.
     *
     * Ne bloque pas l'interface : à lancer dans une coroutine d'arrière-plan.
     * `surAvancement` est appelé de temps en temps, pas à chaque titre.
     */
    suspend fun precharge(
        titres: List<ATrouver>,
        surAvancement: (fait: Int, total: Int) -> Unit
    ) = coroutineScope {
        if (!actif) return@coroutineScope

        // Les titres déjà connus, même comme introuvables, sont écartés :
        // enCache() ne touche ni au réseau ni au disque.
        val manquants = titres
            .distinctBy { (if (it.estSerie) "tv:" else "film:") + it.nom }
            .filter { enCache(it.nom, it.estSerie) == null }
        if (manquants.isEmpty()) return@coroutineScope

        val file = ConcurrentLinkedQueue(manquants)
        val fait = AtomicInteger(0)
        val total = manquants.size
        surAvancement(0, total)

        // Quatre ouvriers, pas six : on laisse des permis libres pour les
        // cartes réellement à l'écran, qui doivent rester prioritaires.
        List(4) {
            launch(Dispatchers.IO) {
                while (true) {
                    val t = file.poll() ?: break
                    try {
                        fiche(t.nom, t.estSerie)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Un titre qui échoue ne doit pas arrêter les autres.
                    }
                    val n = fait.incrementAndGet()
                    // Un rapport tous les dix : sinon on redessine l'écran
                    // des centaines de fois pour rien.
                    if (n % 10 == 0 || n == total) surAvancement(n, total)
                }
            }
        }.joinAll()

        withContext(Dispatchers.IO) { sauvegarde() }
    }

    /* --------------------------------------------------------------- Détail */

    /** Cache mémoire seulement : une fiche détaillée n'est ouverte qu'à la demande. */
    private val details = HashMap<String, Detail?>()

    /**
     * Fiche détaillée d'un titre. Transposition de details() dans tmdb.js.
     *
     * Une seule requête grâce à `append_to_response=credits` : résumé, note,
     * durée, genres, réalisation et distribution arrivent ensemble.
     */
    suspend fun detail(nomBrut: String, estSerie: Boolean): Detail? {
        if (!actif) return null

        val base = fiche(nomBrut, estSerie) ?: return null
        if (base.id == 0) return null

        val cle = (if (estSerie) "tv:" else "film:") + base.id
        synchronized(details) { if (details.containsKey(cle)) return details[cle] }

        val resultat = enParallele.withPermit {
            withContext(Dispatchers.IO) {
                val type = if (estSerie) "tv" else "movie"
                val adresse = "https://api.themoviedb.org/3/$type/${base.id}" +
                        "?api_key=${Config.CLE_TMDB}" +
                        "&language=${Config.LANGUE_TMDB}" +
                        "&append_to_response=credits"
                try {
                    val lien = URL(adresse).openConnection() as HttpURLConnection
                    lien.connectTimeout = 6000
                    lien.readTimeout = 6000
                    val corps = try {
                        if (lien.responseCode != 200) return@withContext null
                        lien.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        lien.disconnect()
                    }

                    val d = JSONObject(corps)
                    val credits = d.optJSONObject("credits")

                    val equipe = credits?.optJSONArray("crew")
                    var realisation = ""
                    if (equipe != null) {
                        for (i in 0 until equipe.length()) {
                            val p = equipe.optJSONObject(i) ?: continue
                            if (p.optString("job") == "Director") {
                                realisation = p.optString("name"); break
                            }
                        }
                    }
                    // Une série n'a pas de réalisateur unique : on prend les créateurs.
                    if (realisation.isBlank()) {
                        val crees = d.optJSONArray("created_by")
                        if (crees != null) {
                            realisation = (0 until crees.length())
                                .mapNotNull { crees.optJSONObject(it)?.optString("name") }
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                        }
                    }

                    val distribution = credits?.optJSONArray("cast")
                    val acteurs = mutableListOf<Acteur>()
                    if (distribution != null) {
                        for (i in 0 until minOf(distribution.length(), 14)) {
                            val p = distribution.optJSONObject(i) ?: continue
                            val photo = p.optString("profile_path").takeIf { it.length > 1 }
                            acteurs.add(
                                Acteur(
                                    nom = p.optString("name"),
                                    role = p.optString("character"),
                                    photo = photo?.let { IMAGES + "w185" + it }
                                )
                            )
                        }
                    }

                    val genres = d.optJSONArray("genres")
                    val listeGenres = if (genres == null) emptyList() else
                        (0 until genres.length()).mapNotNull { genres.optJSONObject(it)?.optString("name") }

                    Detail(
                        resume = d.optString("overview").ifBlank { base.resume },
                        note = d.optDouble("vote_average", 0.0),
                        duree = d.optInt("runtime", 0).takeIf { it > 0 }
                            ?: d.optJSONArray("episode_run_time")?.optInt(0, 0) ?: 0,
                        saisons = d.optInt("number_of_seasons", 0),
                        genres = listeGenres,
                        realisation = realisation,
                        acteurs = acteurs
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }

        synchronized(details) { details[cle] = resultat }
        return resultat
    }

    /* ------------------------------------------------------ Détail d'épisodes */

    /** Ce que TMDB sait d'un épisode : son vrai titre, son résumé, son image. */
    data class EpisodeInfo(val nom: String, val resume: String, val image: String?)

    private val saisonsCache = HashMap<String, Map<Int, EpisodeInfo>>()

    /**
     * Détail d'une saison entière : une seule requête `/tv/{id}/season/{n}`,
     * indexée par numéro d'épisode. Sert à compléter la liste d'épisodes de la
     * fiche quand le fournisseur ne donne ni synopsis ni image par épisode.
     */
    suspend fun saison(nomSerie: String, numeroSaison: Int): Map<Int, EpisodeInfo> {
        if (!actif || numeroSaison <= 0) return emptyMap()
        val base = fiche(nomSerie, estSerie = true) ?: return emptyMap()
        if (base.id == 0) return emptyMap()

        val cle = "tv:${base.id}:s$numeroSaison"
        synchronized(saisonsCache) { saisonsCache[cle]?.let { return it } }

        val resultat = enParallele.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val adresse = "https://api.themoviedb.org/3/tv/${base.id}" +
                            "/season/$numeroSaison?api_key=${Config.CLE_TMDB}" +
                            "&language=${Config.LANGUE_TMDB}"
                    val lien = URL(adresse).openConnection() as HttpURLConnection
                    lien.connectTimeout = 6000
                    lien.readTimeout = 6000
                    val corps = try {
                        if (lien.responseCode != 200) return@withContext emptyMap<Int, EpisodeInfo>()
                        lien.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        lien.disconnect()
                    }

                    val eps = JSONObject(corps).optJSONArray("episodes")
                        ?: return@withContext emptyMap<Int, EpisodeInfo>()
                    val map = HashMap<Int, EpisodeInfo>()
                    for (i in 0 until eps.length()) {
                        val e = eps.optJSONObject(i) ?: continue
                        val num = e.optInt("episode_number", -1)
                        if (num < 0) continue
                        // optString rend la chaîne « null » quand la valeur JSON
                        // est nulle : d'où le filtre sur le « / » initial.
                        val still = e.optString("still_path")
                            .takeIf { it.length > 1 && it.startsWith("/") }
                        map[num] = EpisodeInfo(
                            nom = e.optString("name"),
                            resume = e.optString("overview"),
                            image = still?.let { IMAGES + "w300" + it }
                        )
                    }
                    map
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emptyMap<Int, EpisodeInfo>()
                }
            }
        }

        if (resultat.isNotEmpty()) synchronized(saisonsCache) { saisonsCache[cle] = resultat }
        return resultat
    }

    /* -------------------------------------------------------------- Avatars */

    /** Une affiche proposée comme image de profil. */
    data class Avatar(val libelle: String, val image: String)

    private val avatars = HashMap<String, List<Avatar>>()

    /**
     * Affiches proposées pour une photo de profil. Transposition de avatars()
     * dans tmdb.js : la recherche si l'utilisateur tape quelque chose, les
     * tendances de la semaine sinon.
     */
    suspend fun avatars(recherche: String): List<Avatar> {
        if (!actif) return emptyList()

        val q = recherche.trim()
        val cle = "avatars|" + q.lowercase().ifBlank { "__tendances" }
        synchronized(avatars) { avatars[cle]?.let { return it } }

        val adresse = if (q.isNotEmpty())
            "https://api.themoviedb.org/3/search/multi?api_key=${Config.CLE_TMDB}" +
                    "&language=${Config.LANGUE_TMDB}&query=${URLEncoder.encode(q, "UTF-8")}"
        else
            "https://api.themoviedb.org/3/trending/all/week?api_key=${Config.CLE_TMDB}" +
                    "&language=${Config.LANGUE_TMDB}"

        val sortie = enParallele.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val lien = URL(adresse).openConnection() as HttpURLConnection
                    lien.connectTimeout = 6000
                    lien.readTimeout = 6000
                    val corps = try {
                        if (lien.responseCode != 200) return@withContext emptyList()
                        lien.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        lien.disconnect()
                    }

                    val resultats = JSONObject(corps).optJSONArray("results")
                        ?: return@withContext emptyList()
                    val liste = mutableListOf<Avatar>()
                    for (i in 0 until resultats.length()) {
                        val r = resultats.optJSONObject(i) ?: continue
                        val libelle = r.optString("title").ifBlank { r.optString("name") }
                        // Une affiche pour un film ou une série, un portrait
                        // pour une personne.
                        val chemin = r.optString("poster_path").takeIf { it.length > 1 }
                            ?: r.optString("profile_path").takeIf { it.length > 1 }
                        if (libelle.isBlank() || chemin == null) continue
                        liste.add(Avatar(libelle, IMAGES + "w185" + chemin))
                        if (liste.size >= 30) break
                    }
                    liste
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Hors ligne : on ne met rien en cache, pour réessayer plus tard.
                    emptyList()
                }
            }
        }

        if (sortie.isNotEmpty()) synchronized(avatars) { avatars[cle] = sortie }
        return sortie
    }

    /* ---------------------------------------------------------- Popularité */

    /** Un titre populaire côté TMDB, avant tout rapprochement avec le catalogue. */
    data class Populaire(val id: Int, val titre: String)

    /** En mémoire seulement : la popularité change d'une session à l'autre. */
    private val popularite = HashMap<String, List<Populaire>>()

    /** Pages TMDB tirées par appel : chacune ~20 titres, déjà triés par popularité. */
    private const val PAGES_POPULAIRES = 4

    /**
     * Les titres populaires en France selon TMDB, films ou séries.
     *
     * `region=FR` n'existe que pour /movie/popular ; TMDB l'ignore simplement
     * pour /tv/popular, `language=fr-FR` reste alors le seul biais possible.
     * Plusieurs pages plutôt qu'une seule : sur une plateforme qui ne pèse que
     * quelques centaines de titres, se limiter au vingt premiers globalement
     * populaires ne croisait presque jamais son catalogue. Le rapprochement
     * avec le catalogue se fait ensuite dans correspondances().
     */
    suspend fun populaires(pourSeries: Boolean): List<Populaire> {
        if (!actif) return emptyList()
        val cle = if (pourSeries) "tv" else "movie"
        synchronized(popularite) { popularite[cle]?.let { return it } }

        val sortie = enParallele.withPermit {
            withContext(Dispatchers.IO) {
                val liste = mutableListOf<Populaire>()
                for (page in 1..PAGES_POPULAIRES) {
                    try {
                        val adresse = "https://api.themoviedb.org/3/$cle/popular" +
                                "?api_key=${Config.CLE_TMDB}&language=${Config.LANGUE_TMDB}" +
                                "&region=FR&page=$page"
                        val lien = URL(adresse).openConnection() as HttpURLConnection
                        lien.connectTimeout = 6000
                        lien.readTimeout = 6000
                        val corps = try {
                            if (lien.responseCode != 200) break
                            lien.inputStream.bufferedReader().use { it.readText() }
                        } finally {
                            lien.disconnect()
                        }
                        val resultats = JSONObject(corps).optJSONArray("results") ?: break
                        if (resultats.length() == 0) break
                        for (i in 0 until resultats.length()) {
                            val r = resultats.optJSONObject(i) ?: continue
                            val id = r.optInt("id", 0)
                            if (id == 0) continue
                            val titre = (if (pourSeries) r.optString("name") else r.optString("title"))
                            liste.add(Populaire(id, titre))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        break
                    }
                }
                liste
            }
        }

        if (sortie.isNotEmpty()) synchronized(popularite) { popularite[cle] = sortie }
        return sortie
    }

    /**
     * Rapproche des titres populaires TMDB avec ce qui existe VRAIMENT dans le
     * catalogue.
     *
     * Deux passes, dans l'ordre :
     *   1. Par identifiant TMDB — le plus sûr, mais seulement pour les titres
     *      déjà cherchés au moins une fois (enCache, sans réseau).
     *   2. Par titre nettoyé — TMDB nous donne déjà le titre canonique de
     *      chaque titre populaire ; le comparer au nôtre une fois passé par
     *      nettoieTitre() retrouve tout le reste SANS requête réseau. Utile
     *      sur les grosses plateformes/catégories où la plupart des titres
     *      n'ont encore jamais été cherchés individuellement.
     * Hors du fil principal : les deux passes appellent nettoieTitre().
     */
    fun correspondances(populaires: List<Populaire>, candidats: List<Film>, max: Int): List<Film> {
        if (populaires.isEmpty() || candidats.isEmpty()) return emptyList()

        val parId = HashMap<Int, Film>()
        val parTitre = HashMap<String, Film>()
        for (f in candidats) {
            val connu = enCache(f.nom, f.estSerie)
            val id = connu?.fiche?.id ?: 0
            if (id != 0) parId.putIfAbsent(id, f)
            val nettoye = nettoieTitre(f.nom).titre.lowercase()
            if (nettoye.length >= 2) parTitre.putIfAbsent(nettoye, f)
        }
        if (parId.isEmpty() && parTitre.isEmpty()) return emptyList()

        val vus = HashSet<String>()
        val sortie = ArrayList<Film>(max)
        for (p in populaires) {
            val film = parId[p.id] ?: parTitre[p.titre.trim().lowercase()] ?: continue
            if (vus.add(film.id + "|" + film.nom)) sortie.add(film)
            if (sortie.size >= max) break
        }
        return sortie
    }

    /* ------------------------------------------------- Préchargement au repos */

    /**
     * Cherche les affiches manquantes tant que personne ne touche à rien.
     *
     * Appelé depuis une coroutine annulable : une touche pressée annule le
     * travail en cours.
     *
     * La reprise ne repose plus sur un index mémorisé. Ce qui reste à faire
     * est recalculé au début de chaque passe, en écartant tout ce qui est déjà
     * connu — y compris les titres cherchés en vain. C'est plus robuste qu'un
     * curseur : quatre ouvriers écrivaient l'ancien `reprise` en même temps,
     * chacun avec son propre indice, si bien qu'il pouvait reculer. Et le
     * total affiché diminue maintenant d'une passe à l'autre, ce qui rend
     * l'avancement réellement lisible.
     */
    suspend fun prechargeAuRepos(
        titres: List<ATrouver>,
        surAvancement: (analyse: Boolean, fait: Int, total: Int) -> Unit
    ) = coroutineScope {
        if (!actif || titres.isEmpty()) return@coroutineScope

        // La comparaison passe par nettoieTitre() sur tout le catalogue : hors
        // du fil principal, et avec son propre compteur. Sans lui, une phase
        // muette de plusieurs secondes passe pour un blocage.
        val manquants = withContext(Dispatchers.Default) {
            val sortie = ArrayList<ATrouver>()
            titres.forEachIndexed { i, t ->
                if (i % 500 == 0) surAvancement(true, i, titres.size)
                if (enCache(t.nom, t.estSerie) == null) sortie.add(t)
            }
            sortie
        }
        if (manquants.isEmpty()) {
            surAvancement(false, 0, 0)
            return@coroutineScope
        }

        val total = manquants.size
        val curseur = AtomicInteger(0)
        surAvancement(false, 0, total)

        // Quatre ouvriers : personne n'attend rien pendant une pause, autant
        // avancer vite. La moindre touche annule tout de suite ce bloc, donc
        // le risque de gêner l'utilisateur est nul.
        List(4) {
            launch(Dispatchers.IO) {
                while (true) {
                    val i = curseur.getAndIncrement()
                    if (i >= total) break
                    val t = manquants[i]
                    try {
                        fiche(t.nom, t.estSerie)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Un titre qui échoue n'arrête pas les autres.
                    }
                    if (i % 25 == 0) surAvancement(false, i, total)
                }
            }
        }.joinAll()

        surAvancement(false, total, total)
        withContext(Dispatchers.IO) { sauvegarde() }
    }

    /* -------------------------------------------------- Échange avec le PC */

    /**
     * Dossier lisible par `adb pull` et `adb push` sans `run-as`, contrairement
     * à `filesDir`. Sur l'appareil :
     * `/sdcard/Android/data/com.mrflix.tv/files/`
     */
    fun dossierEchange(contexte: Context): File? = contexte.getExternalFilesDir(null)

    /**
     * Écrit la liste des titres qui restent à chercher, AVEC leurs clés et
     * leurs variantes déjà calculées.
     *
     * Le point important est là : le script du PC ne recalcule rien. Il ne
     * connaît ni nettoieTitre() ni variantes(), il se contente d'interroger
     * TMDB avec ce qu'on lui donne et de réécrire les mêmes clés. C'est ce qui
     * garantit qu'aucune règle ne peut diverger entre les deux côtés — une
     * divergence produirait un fichier accepté sans erreur et totalement
     * inutile.
     */
    suspend fun exporteTitres(
        destination: File,
        titres: List<ATrouver>,
        surAvancement: (fait: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        val vues = HashSet<String>()
        var ecrits = 0

        // Écriture au fil de l'eau, jamais d'arbre JSON complet en mémoire :
        // 28 000 entrées avec leurs variantes font plusieurs mégaoctets, et
        // les construire d'un bloc avant d'écrire saturait le tas. Même règle
        // que pour le catalogue dans Xtream.kt.
        destination.bufferedWriter().use { sortie ->
            sortie.write("{\"version\":1,\"titres\":[")

            titres.forEachIndexed { i, t ->
                if (i % 500 == 0) surAvancement(i, titres.size)

                val propre = nettoieTitre(t.nom)
                if (propre.titre.length >= 2) {
                    val c = cle(propre, t.estSerie)
                    // Déjà connu, même comme introuvable : rien à demander.
                    val connu = synchronized(cache) { cache.containsKey(c) }
                    if (vues.add(c) && !connu) {
                        if (ecrits > 0) sortie.write(",")
                        sortie.write(
                            JSONObject().apply {
                                put("c", c)
                                put("s", t.estSerie)
                                put("a", propre.annee)
                                put("v", JSONArray(variantes(propre)))
                            }.toString()
                        )
                        ecrits += 1
                    }
                }
            }

            sortie.write("]}")
        }
        surAvancement(titres.size, titres.size)
        ecrits
    }

    /* ------------------------------------------- Réparation des titres nus */

    /**
     * Table des mots amputés, apprise sur le cache lui-même.
     *
     * Une partie des noms arrive du fournisseur privée de ses lettres
     * accentuées : « trésor » devient « trsor », « ténèbres » devient
     * « tnbres ». Mais le même mot figure correctement écrit dans les
     * milliers de fiches déjà trouvées. On en déduit la correspondance sans
     * dictionnaire externe et sans rien inventer.
     *
     * Deux garde-fous, tous deux nécessaires — mesurés sur le socle réel :
     *
     * Un mot déjà vu tel quel dans un titre correct n'est JAMAIS une
     * amputation. Sans cette règle, « île » amputé donne « le », et l'article
     * défini était remplacé partout : « le smoking » devenait « île smoking ».
     *
     * Une seule occurrence ne prouve rien. Il en faut au moins deux pour
     * qu'une correspondance entre dans la table.
     */
    /**
     * Remplacée d'un bloc, jamais modifiée sur place : `repare()` tourne sur
     * plusieurs threads de préchargement pendant qu'`apprendReparations()`
     * peut être relancé (import d'affiches). Une map immuable publiée via
     * `@Volatile` évite la ConcurrentModificationException.
     */
    @Volatile private var reparations: Map<String, String> = emptyMap()

    private val LETTRES_NUES = Regex("[^a-z0-9]")

    private fun apprendReparations() {
        // ArrayList(cache.keys) plutôt que d'itérer les entrées : la copie est
        // faite par la table elle-même, sous verrou, et l'itérateur ne survit
        // pas à l'appel. Une analyse de plusieurs centaines de millisecondes
        // sur la table vivante bloquerait tous les enCache() des cartes.
        val cles = synchronized(cache) { ArrayList(cache.keys) }
        val valeurs = synchronized(cache) { cles.filter { cache[it] != null } }

        val formes = HashMap<String, HashMap<String, Int>>()
        val telsQuels = HashMap<String, Int>()
        for (c in valeurs) {
            val titre = c.substringAfter(':').substringBeforeLast(':')
            for (mot in titre.split(' ')) {
                if (mot.isEmpty()) continue
                val nu = LETTRES_NUES.replace(mot, "")
                if (nu.length < 3) continue
                if (mot.all { it.code < 128 }) {
                    telsQuels[nu] = (telsQuels[nu] ?: 0) + 1
                } else {
                    val compte = formes.getOrPut(nu) { HashMap() }
                    compte[mot] = (compte[mot] ?: 0) + 1
                }
            }
        }

        val nouvelles = HashMap<String, String>()
        for ((nu, candidats) in formes) {
            if (telsQuels.containsKey(nu)) continue
            val total = candidats.values.sum()
            if (total < 2) continue
            val meilleur = candidats.maxByOrNull { it.value } ?: continue
            nouvelles[nu] = meilleur.key
        }
        reparations = nouvelles
    }

    /** Le titre avec ses accents rendus, ou null si rien n'a changé. */
    fun repare(titre: String): String? {
        if (reparations.isEmpty()) return null
        // Un titre qui porte déjà des accents n'a pas été amputé.
        if (titre.any { it.code > 127 }) return null
        var change = false
        val mots = titre.split(' ').map { mot ->
            val nu = LETTRES_NUES.replace(mot.lowercase(), "")
            val remplacement = reparations[nu]
            if (remplacement != null && remplacement != mot.lowercase()) {
                change = true
                remplacement
            } else mot
        }
        return if (change) mots.joinToString(" ") else null
    }

    /* ------------------------------------------ Titres amputés à la source */

    /**
     * Certains noms arrivent du fournisseur privés de leurs lettres
     * accentuées ET de leurs apostrophes : « Le drôle de Noël de Scrooge »
     * devient « le drle de nol de scrooge », « L'île » devient « lle ». Ce
     * n'est pas nous qui abîmons ces titres, ils entrent déjà cassés.
     *
     * TMDB ne les reconnaît évidemment pas, et ils s'accumulent comme
     * introuvables. Mais le même film est souvent DÉJÀ dans le cache sous sa
     * forme correcte, venu d'une autre catégorie : il suffit de les
     * rapprocher, sans la moindre requête réseau.
     *
     * La signature reproduit exactement la mutilation — tout ce qui n'est ni
     * lettre ASCII, ni chiffre, ni espace est supprimé. Les deux formes
     * deviennent alors identiques.
     */
    private val SIGNATURE_NUE = Regex("[^a-z0-9 ]")

    private fun signature(cle: String): String {
        val type = cle.substringBefore(':')
        val titre = cle.substringAfter(':').substringBeforeLast(':')
        val nu = SIGNATURE_NUE.replace(titre.lowercase(), "")
            .replace(Regex("\\s+"), " ").trim()
        return "$type|$nu"
    }

    private fun anneeDe(cle: String) = cle.substringAfterLast(':')

    /**
     * Rattache les introuvables à leur jumeau correctement orthographié.
     *
     * Trois garde-fous, mesurés sur le socle réel : sans eux, « wonder
     * woman:1984 » se rattachait à « Wonder Woman », alors que 1984 fait
     * partie du titre et désigne un autre film.
     */
    suspend fun rattacheIntrouvables(): Int = withContext(Dispatchers.Default) {
        val connues = HashMap<String, Pair<String, Fiche>>()
        val ambigues = HashSet<String>()
        val manquantes = ArrayList<String>()

        synchronized(cache) {
            for ((c, f) in cache) {
                if (f == null) {
                    manquantes.add(c)
                    continue
                }
                val titre = c.substringAfter(':').substringBeforeLast(':')
                // Preuve qu'une lettre a bien été perdue. Sans caractère
                // non-ASCII dans la forme correcte, les deux clés seraient
                // identiques et il n'y aurait rien à rapprocher.
                if (titre.all { it.code < 128 }) continue
                val s = signature(c)
                // Une signature courte rapprocherait n'importe quoi.
                if (s.length < 10) continue
                val deja = connues[s]
                if (deja == null) connues[s] = c to f
                else if (deja.second != f) ambigues.add(s)
            }
        }

        val ajouts = HashMap<String, Fiche>()
        for (c in manquantes) {
            val s = signature(c)
            if (s.length < 10 || ambigues.contains(s)) continue
            val (cleConnue, fiche) = connues[s] ?: continue
            val a = anneeDe(c)
            val b = anneeDe(cleConnue)
            // Deux années renseignées qui divergent : ce ne sont pas les
            // mêmes films, quelle que soit la ressemblance du titre.
            if (a.isNotEmpty() && b.isNotEmpty() && a != b) continue
            ajouts[c] = fiche
        }

        if (ajouts.isNotEmpty()) {
            synchronized(cache) { ajouts.forEach { (c, f) -> cache[c] = f } }
            withContext(Dispatchers.IO) { sauvegarde() }
        }
        ajouts.size
    }

    /**
     * Relance une recherche TMDB pour les introuvables dont le titre a pu
     * être réparé.
     *
     * Seuls ceux-là : relancer les autres redonnerait exactement le même
     * échec, avec le même titre amputé. Sur le socle mesuré, environ quatre
     * cents titres sur dix-neuf cents sont concernés.
     *
     * TMDB n'impose pas de quota journalier, seulement un débit — d'où les
     * permis déjà en place, réutilisés tels quels.
     */
    suspend fun reessayeReparables(surAvancement: (Int, Int) -> Unit): Int {
        if (!actif) return 0

        val aRefaire = synchronized(cache) {
            cache.entries.filter { it.value == null }.map { it.key }
        }.mapNotNull { c ->
            val estSerie = c.startsWith("tv:")
            val titre = c.substringAfter(':').substringBeforeLast(':')
            val annee = c.substringAfterLast(':')
            val repare = repare(titre) ?: return@mapNotNull null
            Triple(c, TitrePropre(repare, annee, ""), estSerie)
        }

        var trouves = 0
        var faits = 0
        // Sans ce compteur, « TMDB ne connaît pas ce titre » et « la requête a
        // échoué » donnaient le même silence, et il était impossible de dire
        // si le réseau tenait.
        var pannes = 0

        for ((c, propre, estSerie) in aRefaire) {
            var echec = false
            val trouve: Fiche? = enParallele.withPermit {
                var r: Fiche? = null
                // Le fournisseur range souvent un feuilleton en film, et
                // inversement : on essaie les deux catégories.
                for (type in listOf(estSerie, !estSerie)) {
                    r = try {
                        cherche(propre, type)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Une panne réseau ne doit pas condamner le titre :
                        // on n'écrit rien plutôt qu'un verdict non obtenu.
                        echec = true
                        null
                    }
                    if (r != null) break
                }
                r
            }
            if (trouve != null) {
                synchronized(cache) { cache[c] = trouve }
                trouves += 1
            } else if (echec) pannes += 1

            faits += 1
            if (faits % 10 == 0) surAvancement(faits, aRefaire.size)
        }

        if (trouves > 0) withContext(Dispatchers.IO) { sauvegarde() }
        surAvancement(aRefaire.size, aRefaire.size)
        dernieresPannes = pannes
        return trouves
    }

    /** Requêtes qui ont échoué lors de la dernière réparation. */
    var dernieresPannes: Int = 0
        private set

    /**
     * Efface les verdicts « introuvable » pour que tout soit recherché à neuf.
     *
     * Le socle produit par le PC a été construit avec un nettoyage de titre
     * qui SUPPRIME les accents au lieu de les replier : « Le drôle de Noël »
     * partait à TMDB sous la forme « le drle de nol ». Près de dix-sept cents
     * films parfaitement connus ont ainsi été déclarés introuvables.
     *
     * Le nettoyage de ce fichier, lui, conserve les accents. Il suffit donc
     * d'oublier ces verdicts : l'application refera les recherches elle-même,
     * avec les bons titres, au fil de l'affichage.
     *
     * Les affiches DÉJÀ trouvées ne sont pas touchées — seules les entrées
     * nulles disparaissent.
     */
    suspend fun oublieIntrouvables(): Int = withContext(Dispatchers.IO) {
        val efface = synchronized(cache) {
            val cles = cache.entries.filter { it.value == null }.map { it.key }
            cles.forEach { cache.remove(it) }
            cles.size
        }
        if (efface > 0) sauvegarde()
        efface
    }

    /**
     * Exporte les titres que TMDB n'a PAS trouvés, pour qu'un autre service
     * les cherche depuis le PC.
     *
     * `exporteTitres()` les ignore volontairement : ils sont « connus », même
     * comme introuvables, et le but était de ne pas les redemander. Ici c'est
     * l'inverse — ce sont exactement eux qui nous intéressent.
     *
     * `catalogue` restreint l'export à ce qui est RÉELLEMENT AFFICHÉ. Le
     * cache couvre tout le catalogue d'origine, langues masquées comprises :
     * sans ce filtre, l'export partait avec dix-huit cents titres arabes et
     * turcs qui n'apparaîtront jamais à l'écran, et le quota quotidien d'OMDb
     * — mille requêtes — s'y consumait pour un rendement de trois pour cent.
     *
     * La clé porte déjà le titre nettoyé et l'année : rien à recalculer, et
     * le fichier revenu se fusionnera sur les mêmes clés.
     */
    suspend fun exporteIntrouvables(
        destination: File,
        catalogue: CatalogueComplet?
    ): Int = withContext(Dispatchers.IO) {
        val visibles: Set<String>? = catalogue?.let { c ->
            val titres = (c.films + c.series).flatMap { it.films } +
                    (c.plateformesFilms + c.plateformesSeries).flatMap { it.titres }
            titres.mapNotNull { f ->
                val propre = nettoieTitre(f.nom)
                if (propre.titre.length < 2) null else cle(propre, f.estSerie)
            }.toHashSet()
        }

        val cles = synchronized(cache) {
            cache.entries
                .filter { it.value == null && (visibles == null || visibles.contains(it.key)) }
                .map { it.key }
        }

        var ecrits = 0
        destination.bufferedWriter().use { sortie ->
            sortie.write("{\"version\":1,\"titres\":[")
            cles.forEach { c ->
                val estSerie = c.startsWith("tv:")
                val reste = c.substringAfter(':')
                val annee = reste.substringAfterLast(':')
                val titre = reste.substringBeforeLast(':')
                if (titre.length < 2) return@forEach
                if (ecrits > 0) sortie.write(",")
                sortie.write(
                    JSONObject().apply {
                        put("c", c)
                        put("s", estSerie)
                        put("q", titre)
                        put("a", annee)
                    }.toString()
                )
                ecrits += 1
            }
            sortie.write("]}")
        }
        ecrits
    }

    /**
     * Relit un `tmdb.json` produit par le PC et le fusionne dans le cache.
     *
     * Même format que le fichier écrit par sauvegarde(), donc les deux sens
     * restent compatibles. Renvoie -1 si le fichier est absent.
     */
    suspend fun importeAffiches(source: File): Int = withContext(Dispatchers.IO) {
        if (!source.exists()) return@withContext -1

        var lues = 0
        try {
            // En flux, pour la même raison que chargeCache : un fichier de
            // quinze mégaoctets ne tient pas en arbre JSON sur le boîtier, et
            // l'échec se traduisait par « aucun tmdb.json lisible » alors que
            // le fichier était parfaitement valide.
            val recues = HashMap<String, Fiche?>()
            litEnFlux(source) { c, fiche ->
                recues[c] = fiche
                lues += 1
            }
            synchronized(cache) { cache.putAll(recues) }
        } catch (e: Exception) {
            return@withContext -1
        }

        modifie = true
        sauvegarde()
        apprendReparations()
        lues
    }

    /* ---------------------------------------------------------------- Cache */

    /**
     * Socle d'affiches embarqué dans l'APK, recopié au premier lancement.
     *
     * Sans lui, un `adb shell pm clear` — la commande utilisée quand les 8 Go
     * du boîtier saturent — efface des dizaines de milliers de fiches, et il
     * faut tout rechercher. Avec lui, l'application se réinstalle déjà pleine.
     *
     * La copie se fait par blocs : le fichier pèse plusieurs mégaoctets et le
     * lire d'un bloc pour l'écrire ensuite doublerait inutilement l'empreinte.
     * Le fichier absent n'est pas une erreur — l'application marche sans.
     */
    private fun sortDesRessources(destination: File) {
        val assets = ressources ?: return
        try {
            assets.open("tmdb.json").use { entree ->
                // Fichier provisoire puis renommage : une coupure de courant
                // pendant la copie ne doit pas laisser un socle tronqué que
                // chargeCache() prendrait pour un cache valide.
                val provisoire = File(destination.parentFile, destination.name + ".init")
                provisoire.outputStream().use { sortie -> entree.copyTo(sortie, 32 * 1024) }
                provisoire.renameTo(destination)
            }
        } catch (e: Exception) {
            // Pas de socle embarqué, ou copie impossible : on part de zéro,
            // le préchargement de fond fera le travail.
        }
    }

    /**
     * Lit un fichier de cache au fil de l'eau et appelle `recoit` par entrée.
     *
     * Surtout PAS `JSONObject(readText())` : cette forme charge le fichier
     * entier en texte, puis en construit l'arbre complet en mémoire. À sept
     * mégaoctets ça passait ; à quinze, le tas du boîtier est dépassé,
     * l'exception est avalée et le cache repart à VIDE — le fichier grossit,
     * devient illisible, et tout le travail accumulé disparaît en silence.
     *
     * En flux, la mémoire ne dépend plus de la taille du fichier. Même
     * principe que la lecture du catalogue dans Xtream.kt.
     */
    private fun litEnFlux(f: File, recoit: (String, Fiche?) -> Unit) {
        // UTF-8 explicite, jamais FileReader : celui-ci décode avec le jeu de
        // caractères PAR DÉFAUT de la plateforme. Le fichier est écrit en
        // UTF-8, et le relire autrement transforme les deux octets de « é » en
        // deux caractères parasites — « Comédie » s'affichait « ComÃ©die ».
        // `readText()` de Kotlin impose UTF-8, d'où l'absence du problème
        // avant le passage en flux.
        JsonReader(
            BufferedReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8), 1 shl 16)
        ).use { lecteur ->
            lecteur.beginObject()
            while (lecteur.hasNext()) {
                val cle = lecteur.nextName()
                if (lecteur.peek() == JsonToken.NULL) {
                    lecteur.nextNull()
                    recoit(cle, null)
                    continue
                }
                var a: String? = null
                var t: String? = null
                var fond: String? = null
                var id = 0
                var genre = ""
                lecteur.beginObject()
                while (lecteur.hasNext()) {
                    when (lecteur.nextName()) {
                        "a" -> a = lecteur.nextString()
                        "t" -> t = lecteur.nextString()
                        "f" -> fond = lecteur.nextString()
                        "i" -> id = lecteur.nextInt()
                        "g" -> genre = lecteur.nextString()
                        // « r » : le résumé des anciens fichiers, abandonné.
                        else -> lecteur.skipValue()
                    }
                }
                lecteur.endObject()
                recoit(
                    cle,
                    Fiche(
                        affiche = urlUtilisable(a)?.let(::tailleCourante),
                        titreCorrige = t?.takeIf { it.isNotBlank() },
                        fond = urlUtilisable(fond),
                        id = id,
                        resume = "",
                        genre = genre
                    )
                )
            }
            lecteur.endObject()
        }
    }

    private fun chargeCache() {
        val f = fichier ?: return
        if (!f.exists()) sortDesRessources(f)
        if (!f.exists()) return
        try {
            // Accumulé à part puis fusionné d'un bloc : enCache() peut être
            // appelé depuis les cartes pendant que cette lecture tourne
            // encore (l'intro ne l'attend plus).
            val recu = HashMap<String, Fiche?>(1 shl 15)
            litEnFlux(f) { cle, fiche -> recu[cle] = fiche }
            synchronized(cache) { cache.putAll(recu) }
        } catch (e: Exception) {
            synchronized(cache) { cache.clear() }
        }
    }

    /**
     * Écrit le cache sur disque.
     *
     * On repart du fichier existant et on pose les nouvelles fiches PAR-DESSUS.
     * Écraser le fichier avec le seul contenu de la mémoire effaçait tout ce
     * que le plafond LRU avait évincé : le cache se vidait au lieu de grossir,
     * et les mêmes titres repartaient en recherche à chaque lancement.
     */
    @Synchronized
    fun sauvegarde() {
        if (!modifie) return
        val f = fichier ?: return
        try {
            val provisoire = File(f.parentFile, f.name + ".part")
            // Copie de travail : la mémoire est parcourue pendant l'écriture,
            // et une recherche en cours pourrait la modifier au même instant.
            val enMemoire = synchronized(cache) { HashMap(cache) }
            val ecrites = HashSet<String>(enMemoire.size * 2)

            provisoire.bufferedWriter(Charsets.UTF_8, 1 shl 16).use { sortie ->
                sortie.write("{")
                var premier = true

                fun ecrit(cle: String, fiche: Fiche?) {
                    if (!premier) sortie.write(",")
                    premier = false
                    sortie.write(JSONObject.quote(cle))
                    sortie.write(":")
                    if (fiche == null) sortie.write("null")
                    else sortie.write(
                        JSONObject().apply {
                            put("a", fiche.affiche ?: "")
                            put("t", fiche.titreCorrige ?: "")
                            put("f", fiche.fond ?: "")
                            put("i", fiche.id)
                            put("g", fiche.genre)
                        }.toString()
                    )
                }

                // Ce que le disque contient déjà, relu en flux et non d'un
                // bloc. Relire quinze mégaoctets avec JSONObject(readText())
                // dépassait le tas : l'échec était avalé, l'écriture repartait
                // d'un objet vide, et tout le cache disparaissait remplacé par
                // les quelques fiches présentes en mémoire.
                if (f.exists()) {
                    try {
                        litEnFlux(f) { cle, ancienne ->
                            if (ecrites.add(cle)) {
                                // La mémoire fait foi quand elle connaît la clé.
                                ecrit(cle, if (enMemoire.containsKey(cle)) enMemoire[cle] else ancienne)
                            }
                        }
                    } catch (e: Exception) {
                        // Fichier illisible ou tronqué : ce qu'on a pu relire
                        // est conservé, le reste vient de la mémoire.
                    }
                }

                enMemoire.forEach { (cle, fiche) ->
                    if (ecrites.add(cle)) ecrit(cle, fiche)
                }

                sortie.write("}")
            }

            // Écriture en deux temps : une coupure de courant pendant
            // l'écriture ne doit pas laisser un fichier tronqué illisible.
            if (f.exists()) f.delete()
            provisoire.renameTo(f)

            modifie = false
            depuisSauvegarde = 0
        } catch (e: Exception) {
            // Un cache qui échoue ne doit jamais empêcher l'affichage.
        }
    }

    fun vide() {
        synchronized(cache) { cache.clear() }
        fichier?.delete()
        File(fichier?.parentFile, "tmdb.json.part").delete()
        modifie = false
        depuisSauvegarde = 0
    }

    val taille: Int get() = synchronized(cache) { cache.size }

    /**
     * Ce que « affiches connues » ne dit pas.
     *
     * Une carte grise a deux causes possibles, et le nombre total ne permet
     * pas de les séparer : soit TMDB n'a jamais trouvé le titre, soit il l'a
     * trouvé mais sans affiche. Ces deux compteurs le disent, et évitent de
     * corriger à l'aveugle.
     */
    val introuvables: Int get() = synchronized(cache) { cache.count { it.value == null } }

    val sansImage: Int
        get() = synchronized(cache) { cache.count { it.value != null && it.value?.affiche == null } }
}