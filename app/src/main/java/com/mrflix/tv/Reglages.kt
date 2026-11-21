package com.mrflix.tv

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Réglages de l'utilisateur, conservés d'une session à l'autre.
 *
 * Même principe que settings.js côté Windows : on stocke ce qui est MASQUÉ,
 * pas ce qui est visible. Une catégorie inconnue est donc affichée par défaut
 * — les nouveautés du fournisseur apparaissent sans rien cocher.
 *
 * Les catégories masquées sont propres à CHAQUE PROFIL. Les identifiants du
 * fournisseur et la clé TMDB restent communs, comme sur Windows.
 */
object Reglages {

    private const val FICHIER = "mrflix"
    private const val CLE_MASQUEES = "categories_masquees"   // ancien format, à plat
    private const val CLE_PROFILS = "profils"
    private const val CLE_ACTIF = "profil_actif"

    private lateinit var prefs: SharedPreferences

    /**
     * Copies en mémoire, indispensables et non décoratives.
     *
     * `estVisible()` est appelée UNE FOIS PAR ENTRÉE du catalogue, soit près
     * de cent mille fois par lecture. Sans ces copies, chaque appel relisait
     * les préférences ET réanalysait le JSON de la liste des profils : le
     * refiltrage après les options prenait plusieurs minutes sur le boîtier,
     * au point de passer pour une panne.
     *
     * Toute écriture qui touche aux profils ou aux catégories doit appeler
     * `invalide()`, sinon l'écran continuerait d'afficher l'ancien état.
     */
    @Volatile private var profilsCache: List<Profil>? = null
    @Volatile private var profilCache: Profil? = null
    @Volatile private var masqueesCache: Set<String>? = null

    private fun invalide() {
        profilsCache = null
        profilCache = null
        masqueesCache = null
    }

    fun init(contexte: Context) {
        prefs = contexte.applicationContext.getSharedPreferences(FICHIER, Context.MODE_PRIVATE)
        invalide()
        migre()
    }

    /* --------------------------------------------------------- Identifiants */

    /**
     * Les accès du fournisseur, saisis par appairage.
     *
     * Communs à tous les profils, comme sur Windows : un seul abonnement, mais
     * plusieurs personnes qui regardent.
     *
     * Ils ne sont plus compilés dans l'APK. C'est ce qui permet de distribuer
     * l'application sans distribuer l'abonnement — une clé `BuildConfig` est
     * extractible en quelques minutes par qui possède le fichier.
     */
    private const val CLE_HOTE = "xtream_hote"
    private const val CLE_UTILISATEUR = "xtream_utilisateur"
    private const val CLE_MOTDEPASSE = "xtream_motdepasse"
    private const val CLE_TMDB = "cle_tmdb"

    val hote: String get() = prefs.getString(CLE_HOTE, "") ?: ""
    val utilisateur: String get() = prefs.getString(CLE_UTILISATEUR, "") ?: ""
    val motDePasse: String get() = prefs.getString(CLE_MOTDEPASSE, "") ?: ""
    val cleTmdb: String get() = prefs.getString(CLE_TMDB, "") ?: ""

    /** Sans les trois, aucune requête n'est possible : l'appairage s'impose. */
    val estAppaire: Boolean
        get() = hote.isNotBlank() && utilisateur.isNotBlank() && motDePasse.isNotBlank()

    /**
     * `commit()` et non `apply()` : le catalogue est chargé dans la foulée, et
     * une écriture différée le ferait partir avec les anciennes valeurs.
     */
    fun enregistreAcces(
        hote: String,
        utilisateur: String,
        motDePasse: String,
        cleTmdb: String
    ) {
        val edition = prefs.edit()
            .putString(CLE_HOTE, hote)
            .putString(CLE_UTILISATEUR, utilisateur)
            .putString(CLE_MOTDEPASSE, motDePasse)
        // Une clé laissée vide garde celle déjà en place : le formulaire le dit,
        // et ça évite de la ressaisir à chaque réappairage.
        if (cleTmdb.isNotBlank()) edition.putString(CLE_TMDB, cleTmdb)
        edition.commit()
    }

    /** Repart à l'écran d'appairage sans toucher aux profils ni aux positions. */
    fun oublieAcces() {
        prefs.edit()
            .remove(CLE_HOTE)
            .remove(CLE_UTILISATEUR)
            .remove(CLE_MOTDEPASSE)
            .commit()
    }

    /* -------------------------------------------------------------- Profils */

    /**
     * Reprise de l'ancien format : les catégories déjà cochées deviennent
     * celles du premier profil, sans rien perdre.
     */
    private fun migre() {
        if (prefs.contains(CLE_PROFILS)) return

        val premier = Profils.creer("Moi", Profils.COULEURS[0])
        ecritProfils(listOf(premier))
        prefs.edit().putString(CLE_ACTIF, premier.id).apply()

        val ancien = prefs.getStringSet(CLE_MASQUEES, null)
        if (ancien != null) {
            prefs.edit()
                .putStringSet(cleMasquees(premier.id), ancien)
                .remove(CLE_MASQUEES)
                .apply()
        }
        invalide()
    }

    private fun cleMasquees(idProfil: String) = "$CLE_MASQUEES/$idProfil"

    private fun ecritProfils(liste: List<Profil>) {
        val tableau = JSONArray()
        liste.forEach { p ->
            tableau.put(JSONObject().apply {
                put("id", p.id)
                put("nom", p.nom)
                put("couleur", p.couleur)
                put("image", p.image ?: "")
            })
        }
        prefs.edit().putString(CLE_PROFILS, tableau.toString()).apply()
        invalide()
    }

    private fun litProfils(): List<Profil> = try {
        val tableau = JSONArray(prefs.getString(CLE_PROFILS, "[]") ?: "[]")
        (0 until tableau.length()).mapNotNull { i ->
            val o = tableau.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { return@mapNotNull null }
            Profil(
                id = id,
                nom = o.optString("nom").ifBlank { "Profil" },
                couleur = o.optLong("couleur", Profils.COULEURS[0]),
                image = o.optString("image").takeIf { it.isNotBlank() }
            )
        }
    } catch (e: Exception) {
        // Un fichier illisible ne doit jamais empêcher l'ouverture.
        emptyList()
    }

    val profils: List<Profil>
        get() = profilsCache ?: litProfils().also { profilsCache = it }

    var profilActif: String
        get() = prefs.getString(CLE_ACTIF, "") ?: ""
        set(id) {
            prefs.edit().putString(CLE_ACTIF, id).apply()
            // Changer de profil change les catégories masquées : la copie en
            // mémoire appartenait au profil précédent.
            invalide()
        }

    fun profil(): Profil? {
        profilCache?.let { return it }
        val trouve = profils.find { it.id == profilActif } ?: profils.firstOrNull()
        profilCache = trouve
        return trouve
    }

    fun ajouterProfil(nom: String, couleur: Long, image: String?): Profil {
        val p = Profils.creer(nom, couleur, image)
        // Un nouveau profil part à neuf : rien n'est repris du précédent.
        ecritProfils(profils + p)
        return p
    }

    fun renommerProfil(id: String, nom: String, couleur: Long, image: String?) {
        ecritProfils(profils.map {
            if (it.id == id) it.copy(
                nom = nom.ifBlank { it.nom }.take(20),
                couleur = couleur,
                image = image
            ) else it
        })
    }

    /** Le dernier profil ne peut pas être supprimé : il n'y aurait plus rien. */
    fun supprimerProfil(id: String): Boolean {
        val restants = profils.filter { it.id != id }
        if (restants.isEmpty()) return false
        ecritProfils(restants)
        prefs.edit().remove(cleMasquees(id)).apply()
        invalide()
        if (profilActif == id) profilActif = restants[0].id
        return true
    }

    /* ----------------------------------------------------------- Catégories */

    /**
     * Clés des catégories retirées du catalogue, pour le profil actif.
     *
     * Toujours un `HashSet` : `contains()` est appelé une fois par entrée du
     * catalogue, la recherche doit être immédiate. La copie en mémoire est
     * mise à jour AVANT l'écriture sur disque, pour que la lecture suivante
     * voie le nouvel état sans attendre `apply()`.
     */
    var masquees: Set<String>
        get() {
            masqueesCache?.let { return it }
            val id = profil()?.id
            val lues: Set<String> =
                if (id == null) emptySet()
                else HashSet(prefs.getStringSet(cleMasquees(id), emptySet()) ?: emptySet())
            masqueesCache = lues
            return lues
        }
        set(valeur) {
            val id = profil()?.id ?: return
            val fige = HashSet(valeur)
            masqueesCache = fige
            prefs.edit().putStringSet(cleMasquees(id), fige).apply()
        }

    /* --------------------------------------------------------- Reprise vidéo */

    /**
     * Position de lecture par titre, propre à chaque profil.
     *
     * Transposition de savePosition() et position() dans settings.js. Comme
     * sous Windows, un titre vu à plus de 95 % est effacé : il est terminé,
     * proposer de le reprendre n'aurait pas de sens.
     */
    private const val FIN = 0.95
    private const val MINIMUM = 30          // en dessous, ça ne vaut pas la peine

    private fun clePositions(idProfil: String) = "positions/$idProfil"

    private fun positions(): JSONObject {
        val id = profil()?.id ?: return JSONObject()
        return try {
            JSONObject(prefs.getString(clePositions(id), "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** Secondes déjà vues pour cette URL, 0 si rien à reprendre. */
    fun position(url: String): Int = positions().optInt(url, 0)

    private fun cleOrigines() = "positions_origine/" + (profil()?.id ?: "")

    /**
     * `origine` identifie le TITRE, pas le fichier : « s:1234 » pour une série,
     * « f:5678 » pour un film. Sans lui, impossible de rattacher un épisode à
     * sa série — l'URL ne contient que l'identifiant de l'épisode.
     */
    fun enregistrePosition(url: String, secondes: Int, duree: Int, origine: String?) {
        val id = profil()?.id ?: return
        if (duree <= 0 || secondes < MINIMUM) return

        val toutes = positions()
        val ordre = try {
            JSONObject(prefs.getString(cleOrdre(), "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }

        val termine = secondes.toDouble() / duree > FIN
        if (termine) {
            toutes.remove(url)
            ordre.remove(url)
        } else {
            toutes.put(url, secondes)
            // L'instant du dernier visionnage : c'est lui qui ordonne la
            // rangée « Reprendre », pas l'ordre d'insertion.
            ordre.put(url, System.currentTimeMillis())
        }

        // Un titre franchi au-delà de 95 % rejoint les « déjà vu » : c'est
        // l'inverse de « Reprendre », il quitte l'un et entre dans l'autre.
        // Rangé par clé de TITRE (« f:… » / « s:… »), pas par URL : un épisode
        // terminé marque la série entière.
        var vusJson: String? = null
        if (termine && !origine.isNullOrBlank()) {
            val vus = lisJson(cleVus())
            vus.put(origine, System.currentTimeMillis())
            if (vus.length() > VUS_MAX) {
                vus.keys().asSequence()
                    .sortedBy { vus.optLong(it, 0) }
                    .take(vus.length() - VUS_MAX)
                    .toList()
                    .forEach { vus.remove(it) }
            }
            vusJson = vus.toString()
        }

        // Borné : sans ça le fichier grossirait indéfiniment au fil des mois.
        if (toutes.length() > 300) {
            val cles = toutes.keys().asSequence().toList()
            cles.take(toutes.length() - 300).forEach { toutes.remove(it) }
        }
        val origines = try {
            JSONObject(prefs.getString(cleOrigines(), "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        if (toutes.has(url)) {
            if (!origine.isNullOrBlank()) origines.put(url, origine)
        } else origines.remove(url)

        val editeur = prefs.edit()
            .putString(clePositions(id), toutes.toString())
            .putString(cleOrdre(), ordre.toString())
            .putString(cleOrigines(), origines.toString())
        if (vusJson != null) editeur.putString(cleVus(), vusJson)
        editeur.apply()
    }

    /* ----------------------------------------------------------- Déjà vu */

    /** Au-delà, le fichier grossirait sans fin ; on oublie les plus anciens. */
    private const val VUS_MAX = 400

    private fun cleVus() = "vus/" + (profil()?.id ?: "")

    private fun lisJson(cle: String): JSONObject = try {
        JSONObject(prefs.getString(cle, "{}") ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    /**
     * Clés de titre (« f:123 », « s:456 ») vus jusqu'au bout au moins une
     * fois, du plus récent au plus ancien.
     */
    fun vus(): List<String> {
        val o = lisJson(cleVus())
        return o.keys().asSequence()
            .sortedByDescending { o.optLong(it, 0) }
            .toList()
    }

    fun estVu(cleFavori: String): Boolean = lisJson(cleVus()).has(cleFavori)

    /** Renvoie l'état APRÈS bascule, pour rafraîchir le bouton sans relire. */
    fun basculeVu(film: Film): Boolean {
        val cle = cleFavori(film)
        val o = lisJson(cleVus())
        val ajoute = !o.has(cle)
        if (ajoute) o.put(cle, System.currentTimeMillis()) else o.remove(cle)
        prefs.edit().putString(cleVus(), o.toString()).apply()
        return ajoute
    }

    /**
     * Titres en cours de lecture, du plus récent au plus ancien.
     *
     * Transposition de continueWatching() dans settings.js. On renvoie les URL
     * et l'avancement ; c'est à l'appelant de retrouver les titres dans le
     * catalogue, comme le fait findByUrl() sous Windows.
     */
    fun enCours(): List<Reprise> {
        val toutes = positions()
        val ordre = try {
            JSONObject(prefs.getString(cleOrdre(), "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val origines = try {
            JSONObject(prefs.getString(cleOrigines(), "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        return toutes.keys().asSequence()
            .mapNotNull { url ->
                val secondes = toutes.optInt(url, 0)
                if (secondes <= 0) null
                else Reprise(
                    url = url,
                    secondes = secondes,
                    vuA = ordre.optLong(url, 0),
                    origine = origines.optString(url)
                )
            }
            .sortedByDescending { it.vuA }
            .take(20)
            .toList()
    }

    private fun cleOrdre() = "positions_ordre/" + (profil()?.id ?: "")

    fun oubliePosition(url: String) {
        val id = profil()?.id ?: return
        val toutes = positions()
        toutes.remove(url)
        prefs.edit().putString(clePositions(id), toutes.toString()).apply()
    }

    /* ----------------------------------------------------------- Pistes */

    /**
     * Langue préférée pour l'audio et pour les sous-titres.
     *
     * On retient la LANGUE, jamais le numéro de piste : l'ordre change d'un
     * épisode à l'autre, comme le note player.js. Un choix fait sur un épisode
     * s'applique donc correctement au suivant.
     *
     * `type` reprend les constantes de Media3 : 1 pour l'audio, 3 pour le
     * texte. Les nommer ici éviterait d'y faire dépendre ce fichier.
     */
    private fun cleLangue(type: Int) = "langue_piste_$type/" + (profil()?.id ?: "")

    /** null pour les sous-titres signifie « aucun ». */
    fun languePreferee(type: Int): String? =
        prefs.getString(cleLangue(type), if (type == 1) "fr" else null)
            ?.takeIf { it.isNotBlank() }

    fun enregistreLangue(type: Int, langue: String) {
        prefs.edit().putString(cleLangue(type), langue).apply()
    }

    /* --------------------------------------------------------------- Ma liste */

    /**
     * Favoris, propres à chaque profil. Transposition de myList dans
     * settings.js. On retient l'identifiant du titre, pas l'objet : le
     * catalogue est rechargé à chaque session et les objets changent.
     */
    private fun cleListe(idProfil: String) = "ma_liste/$idProfil"

    fun cleFavori(film: Film) = (if (film.estSerie) "s:" else "f:") + film.id

    val maListe: Set<String>
        get() {
            val id = profil()?.id ?: return emptySet()
            return prefs.getStringSet(cleListe(id), emptySet()) ?: emptySet()
        }

    fun estFavori(film: Film) = maListe.contains(cleFavori(film))

    /** Renvoie l'état APRÈS bascule, pour rafraîchir le bouton sans relire. */
    fun basculeFavori(film: Film): Boolean {
        val id = profil()?.id ?: return false
        val cle = cleFavori(film)
        val liste = maListe.toMutableSet()
        val ajoute = liste.add(cle)
        if (!ajoute) liste.remove(cle)
        prefs.edit().putStringSet(cleListe(id), liste).apply()
        return ajoute
    }

    fun estVisible(cle: String) = !masquees.contains(cle)

    /* -------------------------------------------------------------- Affiches */

    /**
     * Interroger TMDB pour les affiches des cartes.
     *
     * Coupé, l'application n'utilise que les jaquettes du fournisseur : plus
     * laides, mais aucune requête pendant le défilement. Sert autant à mesurer
     * d'où viennent les à-coups qu'à s'en passer si on préfère la fluidité.
     */
    var affichesTmdb: Boolean
        get() = prefs.getBoolean("affiches_tmdb", true)
        set(valeur) = prefs.edit().putBoolean("affiches_tmdb", valeur).apply()

    fun basculeCategorie(cle: String) {
        val actuelles = masquees.toMutableSet()
        if (!actuelles.remove(cle)) actuelles.add(cle)
        masquees = actuelles
    }

    fun toutCocher() {
        masquees = emptySet()
    }

    fun toutDecocher(cles: List<String>) {
        masquees = cles.toSet()
    }

    /** Ne garde que ce que Catalogue.devineLangue reconnaît comme français. */
    fun garderFrancaisSeulement(categories: List<CategorieBrute>) {
        masquees = categories.filter { it.langue != "fr" }.map { it.cle }.toSet()
    }
}

/** Un titre commencé : son URL, l'avancement en secondes, et quand. */
data class Reprise(
    val url: String,
    val secondes: Int,
    val vuA: Long,
    /** « s:1234 » ou « f:5678 ». Vide pour les positions d'avant cette version. */
    val origine: String
)

/** Une catégorie telle que le serveur l'annonce, avant filtrage. */
data class CategorieBrute(
    val cle: String,
    val nom: String,
    val type: String,      // "films", "series" ou "tv"
    val langue: String,
    val nombre: Int
)