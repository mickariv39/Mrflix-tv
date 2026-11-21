package com.mrflix.tv

import android.util.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Un film, ou une série vue de l'extérieur. */
data class Film(
    val id: String,
    val nom: String,
    val extension: String,
    val affiche: String?,
    val categorie: String,
    /** Action, Comédie… lu dans le nom de catégorie, sinon la catégorie elle-même. */
    val genre: String = "",
    /** Netflix, Prime, Disney+… déduite du nom brut de la catégorie. */
    val plateforme: String = "autre",
    val estSerie: Boolean = false,
    val ajoute: Long = 0
) {
    val url: String
        get() = if (estSerie) ""
        else "${Config.HOTE}/movie/${Config.UTILISATEUR}/${Config.MOTDEPASSE}/$id.$extension"
}

data class Rangee(val titre: String, val films: List<Film>)

data class Chaine(
    val id: String,
    val nom: String,
    val logo: String?,
    val categorie: String,
    /** Sport, Info, Jeunesse… déduit du nom brut de la catégorie. */
    val bouquet: String = "autre",
    val archive: Boolean,
    val joursArchive: Int
) {
    val url: String
        get() = "${Config.HOTE}/live/${Config.UTILISATEUR}/${Config.MOTDEPASSE}/$id.ts"
}

data class RangeeChaines(val titre: String, val chaines: List<Chaine>)

/** Un épisode, chargé seulement à l'ouverture d'une série. */
data class Episode(
    val id: String,
    val titre: String,
    val saison: Int,
    val numero: Int,
    val extension: String,
    val vignette: String?,
    /** Résumé propre à l'épisode, quand le fournisseur le donne. */
    val resume: String = ""
) {
    val url: String
        get() = "${Config.HOTE}/series/${Config.UTILISATEUR}/${Config.MOTDEPASSE}/$id.$extension"
}

data class Programme(
    val titre: String,
    val resume: String,
    val debut: Long,
    val fin: Long,
    val debutTexte: String
) {
    val dureeMinutes: Int get() = ((fin - debut) / 60).toInt().coerceAtLeast(1)
}

/** Les titres d'une plateforme, tous genres confondus. */
data class GroupePlateforme(val id: String, val titres: List<Film>)

/** Les chaînes d'un bouquet thématique. */
data class GroupeBouquet(val id: String, val rangees: List<RangeeChaines>)

data class CatalogueComplet(
    val films: List<Rangee>,
    val series: List<Rangee>,
    val chaines: List<RangeeChaines>,
    val categories: List<CategorieBrute> = emptyList(),
    val plateformesFilms: List<GroupePlateforme> = emptyList(),
    val plateformesSeries: List<GroupePlateforme> = emptyList(),
    val bouquets: List<GroupeBouquet> = emptyList()
) {
    /** Tout ce qui a une date d'ajout, plafonné large pour le « Tout voir ». */
    val recentsTous: List<Film> by lazy {
        (films + series).flatMap { it.films }
            .filter { it.ajoute > 0 }
            .sortedByDescending { it.ajoute }
            .take(Config.RECENTS_TOUT_MAX)
    }

    /** Ce que montre la rangée de l'accueil, une simple tranche de recentsTous. */
    val recents: List<Film> by lazy { recentsTous.take(Config.RECENTS_MAX) }

    val chainesArchivees: List<Chaine> by lazy {
        chaines.flatMap { it.chaines }.filter { it.archive }
    }
}

/**
 * Accès au serveur Xtream.
 *
 * Le catalogue peut compter des dizaines de milliers d'entrées. Deux règles
 * en découlent, apprises en saturant les 256 Mo du Chromecast :
 *   - on ne construit JAMAIS d'objet JSON complet en mémoire : lecture en flux ;
 *   - on limite le nombre d'éléments retenus AU MOMENT de la lecture, pas après.
 */
object Xtream {

    /**
     * Plafond global, uniquement pour éviter le manque de mémoire si toutes
     * les catégories sont cochées d'un coup. Un Film dépouillé pèse environ
     * 400 octets : 30 000 titres tiennent dans 12 Mo.
     */
    private const val TITRES_MAX = 30000

    /**
     * Deux verrous SÉPARÉS, et non un seul.
     *
     * `verrouReseau` sérialise les allers-retours serveur (la plupart des
     * abonnements ne tolèrent qu'une connexion). `verrouDisque` sérialise la
     * ré-analyse locale.
     *
     * Les confondre créait le bug qu'on répare ici : au premier lancement,
     * l'analyse réseau tient le verrou pendant 10 à 30 s d'entrées-sorties
     * bloquantes ; si l'utilisateur changeait un filtre dans les options
     * pendant ce temps, la ré-analyse depuis le disque — pourtant instantanée —
     * restait coincée derrière, l'écran ne bougeait pas, et il fallait quitter
     * l'application. Un `HttpURLConnection` bloqué n'est pas interrompu par
     * l'annulation de la coroutine : le verrou ne se libérait qu'au bout du
     * délai réseau.
     */
    private val verrouReseau = Mutex()
    private val verrouDisque = Mutex()

    private fun cleCategorie(type: String, nomBrut: String) = "$type||$nomBrut"

    suspend fun tout(reseau: Boolean): CatalogueComplet =
        if (reseau) verrouReseau.withLock { toutSequentiel() }
        else verrouDisque.withLock { toutParallele() }

    /** Depuis le serveur : une requête après l'autre, la plupart des
     * abonnements ne tolèrent qu'une connexion à la fois. */
    private suspend fun toutSequentiel(): CatalogueComplet = withContext(Dispatchers.IO) {
        val lf = elements("get_vod_streams", categories("get_vod_categories", true), false, true)
        val ls = elements("get_series", categories("get_series_categories", true), true, true)
        val lc = chaines(categories("get_live_categories", true), true)
        assemble(lf, ls, lc)
    }

    /**
     * Depuis le disque : les trois fichiers sont lus et analysés EN PARALLÈLE.
     * C'est le chemin pris juste après le choix du profil ; sur le boîtier
     * quadricœur, ça divise d'autant l'attente avant l'affichage du catalogue.
     */
    private suspend fun toutParallele(): CatalogueComplet = coroutineScope {
        val lf = async(Dispatchers.IO) {
            elements("get_vod_streams", categories("get_vod_categories", false), false, false)
        }
        val ls = async(Dispatchers.IO) {
            elements("get_series", categories("get_series_categories", false), true, false)
        }
        val lc = async(Dispatchers.IO) {
            chaines(categories("get_live_categories", false), false)
        }
        assemble(lf.await(), ls.await(), lc.await())
    }

    private fun assemble(lf: Contenu, ls: Contenu, lc: ContenuChaines) = CatalogueComplet(
        films = lf.rangees,
        series = ls.rangees,
        plateformesFilms = lf.plateformes,
        plateformesSeries = ls.plateformes,
        chaines = lc.rangees,
        bouquets = lc.bouquets,
        categories = (lf.inventaire + ls.inventaire + lc.inventaire)
            .sortedWith(compareBy({ it.type }, { -it.nombre }))
    )

    /* -------------------------------------------------------- Lecture en flux */

    /** Parcourt un tableau JSON objet par objet, sans jamais tout charger. */
    private fun <T> parcours(
        action: String,
        reseau: Boolean,
        surObjet: (Map<String, String>) -> T?
    ): List<T> {
        val fichier = prepare(action, reseau)
        val resultats = mutableListOf<T>()

        // Flux TAMPONNÉ : sans ça, InputStreamReader fait une lecture système
        // par poignée d'octets sur un fichier de plusieurs Mo — l'analyse
        // était deux fois plus lente qu'elle ne devrait.
        JsonReader(
            BufferedReader(InputStreamReader(fichier.inputStream(), Charsets.UTF_8), 1 shl 16)
        ).use { lecteur ->
            lecteur.isLenient = true
            if (lecteur.peek() != android.util.JsonToken.BEGIN_ARRAY) return emptyList()
            lecteur.beginArray()
            // Réutilisée d'un objet à l'autre : les appelants recopient ce dont
            // ils ont besoin et ne gardent jamais la map. Sur des dizaines de
            // milliers de titres, autant d'allocations de HashMap en moins.
            val champs = HashMap<String, String>()
            while (lecteur.hasNext()) {
                champs.clear()
                lecteur.beginObject()
                while (lecteur.hasNext()) {
                    val cle = lecteur.nextName()
                    when (lecteur.peek()) {
                        android.util.JsonToken.NULL -> lecteur.nextNull()

                        // Les serveurs Xtream ne typent pas tous pareil : un champ
                        // qui est une chaîne chez l'un est un tableau chez l'autre.
                        // On saute ce qu'on ne sait pas lire, sinon un seul champ
                        // inattendu fait échouer le catalogue entier.
                        android.util.JsonToken.BEGIN_ARRAY,
                        android.util.JsonToken.BEGIN_OBJECT -> lecteur.skipValue()

                        else -> {
                            // Tout en texte : évite les conversions et les
                            // surprises de typage d'un serveur à l'autre.
                            champs[cle] = lecteur.nextString()
                        }
                    }
                }
                lecteur.endObject()
                surObjet(champs)?.let { resultats.add(it) }
            }
            lecteur.endArray()
        }
        return resultats
    }

    private fun categories(action: String, reseau: Boolean): Map<String, String> {
        val resultat = HashMap<String, String>()
        parcours(action, reseau) { o ->
            val id = o["category_id"] ?: return@parcours null
            resultat[id] = o["category_name"] ?: ""
            null
        }
        return resultat
    }

    /** Ce qu'une passe de lecture produit : les rangées par genre ET par plateforme. */
    private class Contenu(
        val rangees: List<Rangee>,
        val plateformes: List<GroupePlateforme>,
        val inventaire: List<CategorieBrute>
    )

    private fun elements(
        action: String,
        categories: Map<String, String>,
        estSerie: Boolean,
        reseau: Boolean
    ): Contenu {
        val parGenre = LinkedHashMap<String, MutableList<Film>>()
        val parPlateforme = LinkedHashMap<String, MutableList<Film>>()
        // Instances partagées, pour ne pas recréer la même chaîne par titre.
        val genresPropres = HashMap<String, String>()
        val genresVrais = HashMap<String, String>()
        // Plateforme.detecte() balaie une quinzaine d'expressions régulières ;
        // le résultat ne dépend que de la catégorie, on le calcule une fois.
        val plates = HashMap<String, String>()
        val extensions = HashMap<String, String>()
        // Clé de catégorie et verdict de visibilité : constants pour toute une
        // catégorie, inutile de refaire la concaténation et le contains() par
        // titre.
        val clesParCat = HashMap<String, String>()
        val visibleParCat = HashMap<String, Boolean>()
        var gardes = 0
        val compte = HashMap<String, Int>()
        val brutParCle = HashMap<String, String>()
        val type = if (estSerie) "series" else "films"

        parcours(action, reseau) { o ->
            val categorieBrute = categories[o["category_id"]] ?: return@parcours null
            val cle = clesParCat.getOrPut(categorieBrute) { cleCategorie(type, categorieBrute) }

            compte[cle] = (compte[cle] ?: 0) + 1
            brutParCle[cle] = categorieBrute
            if (!visibleParCat.getOrPut(categorieBrute) { Reglages.estVisible(cle) }) {
                return@parcours null
            }

            // Le nom de genre nettoyé est le MÊME pour tous les films d'une
            // catégorie : on réutilise l'instance au lieu d'en créer une par
            // titre. Sur des dizaines de milliers d'entrées, ça compte.
            val genre = genresPropres.getOrPut(categorieBrute) {
                Catalogue.nettoieGroupe(categorieBrute)
            }
            val plateforme = plates.getOrPut(categorieBrute) {
                Plateformes.detecte(categorieBrute)
            }

            // Un mot de genre dans le nom de catégorie prime. À défaut, on
            // garde le nom de la catégorie : à l'intérieur d'une plateforme,
            // la subdivision du fournisseur vaut mieux qu'un seul grand tas.
            val genreVrai = genresVrais.getOrPut(categorieBrute) {
                Genres.depuisCategorie(categorieBrute) ?: genre
            }

            val listeGenre = parGenre.getOrPut(genre) { mutableListOf() }
            val listePlateforme = parPlateforme.getOrPut(plateforme) { mutableListOf() }

            val placeGenre = listeGenre.size < Config.FILMS_PAR_CATEGORIE &&
                    !(parGenre.size > Config.CATEGORIES_MAX && listeGenre.isEmpty())
            val placePlateforme = listePlateforme.size < Config.TITRES_PAR_PLATEFORME

            // Rien à garder nulle part : on n'alloue même pas l'objet.
            if (!placeGenre && !placePlateforme) return@parcours null
            if (gardes >= TITRES_MAX) return@parcours null
            gardes += 1

            // Un SEUL objet, rangé dans les deux listes : le partager évite
            // de doubler la mémoire du catalogue.
            val film = Film(
                id = o[if (estSerie) "series_id" else "stream_id"] ?: return@parcours null,
                nom = Catalogue.nettoieNom(o["name"] ?: ""),
                // Trois valeurs possibles pour des milliers de films :
                // autant partager les instances.
                extension = extensions.getOrPut(
                    (o["container_extension"] ?: "").ifBlank { "mp4" }
                ) { (o["container_extension"] ?: "").ifBlank { "mp4" } },
                affiche = (o[if (estSerie) "cover" else "stream_icon"])
                    ?.takeIf { it.startsWith("http") },
                categorie = genre,
                genre = genreVrai,
                plateforme = plateforme,
                estSerie = estSerie,
                ajoute = o["added"]?.toLongOrNull()
                    ?: o["last_modified"]?.toLongOrNull() ?: 0
            )

            if (placeGenre) listeGenre.add(film)
            if (placePlateforme) listePlateforme.add(film)
            null
        }

        val inventaire = compte.mapNotNull { (cle, nombre) ->
            val brut = brutParCle[cle] ?: return@mapNotNull null
            CategorieBrute(
                cle = cle,
                nom = Catalogue.nettoieGroupe(brut),
                type = type,
                langue = Catalogue.devineLangue(brut),
                nombre = nombre
            )
        }

        return Contenu(
            rangees = parGenre.entries
                .take(Config.CATEGORIES_MAX)
                .map { Rangee(it.key, it.value) }
                .filter { it.films.isNotEmpty() },
            plateformes = Plateformes.trier(
                parPlateforme.entries
                    .filter { it.value.isNotEmpty() }
                    .map { it.key to GroupePlateforme(it.key, it.value) }
            ) { g -> g.titres.size }.map { it.second },
            inventaire = inventaire
        )
    }

    /** Ce qu'une passe sur les chaînes produit : par catégorie ET par bouquet. */
    private class ContenuChaines(
        val rangees: List<RangeeChaines>,
        val bouquets: List<GroupeBouquet>,
        val inventaire: List<CategorieBrute>
    )

    private fun chaines(categories: Map<String, String>, reseau: Boolean): ContenuChaines {
        val parGenre = LinkedHashMap<String, MutableList<Chaine>>()
        // Par bouquet, puis par catégorie à l'intérieur : les rangées d'un
        // bouquet gardent ainsi le nom de catégorie du fournisseur.
        val parBouquet = LinkedHashMap<String, LinkedHashMap<String, MutableList<Chaine>>>()
        val genresPropres = HashMap<String, String>()
        val bouquetsMemo = HashMap<String, String>()
        val clesParCat = HashMap<String, String>()
        val visibleParCat = HashMap<String, Boolean>()
        val compte = HashMap<String, Int>()
        val brutParCle = HashMap<String, String>()

        parcours("get_live_streams", reseau) { o ->
            val categorieBrute = categories[o["category_id"]] ?: return@parcours null
            val cle = clesParCat.getOrPut(categorieBrute) { cleCategorie("tv", categorieBrute) }

            compte[cle] = (compte[cle] ?: 0) + 1
            brutParCle[cle] = categorieBrute
            if (!visibleParCat.getOrPut(categorieBrute) { Reglages.estVisible(cle) }) {
                return@parcours null
            }

            val genre = genresPropres.getOrPut(categorieBrute) {
                Catalogue.nettoieGroupe(categorieBrute)
            }
            val bouquet = bouquetsMemo.getOrPut(categorieBrute) {
                Plateformes.detecteBouquet(categorieBrute)
            }

            val listeGenre = parGenre.getOrPut(genre) { mutableListOf() }
            val rangeesBouquet = parBouquet.getOrPut(bouquet) { LinkedHashMap() }
            val listeBouquet = rangeesBouquet.getOrPut(genre) { mutableListOf() }

            val placeGenre = listeGenre.size < Config.CHAINES_PAR_CATEGORIE &&
                    !(parGenre.size > Config.CATEGORIES_MAX && listeGenre.isEmpty())
            val totalBouquet = rangeesBouquet.values.sumOf { it.size }
            val placeBouquet = totalBouquet < Config.TITRES_PAR_PLATEFORME

            if (!placeGenre && !placeBouquet) return@parcours null

            // Un seul objet, rangé dans les deux classements.
            val chaine = Chaine(
                id = o["stream_id"] ?: return@parcours null,
                nom = Catalogue.nettoieNom(o["name"] ?: ""),
                logo = o["stream_icon"]?.takeIf { it.startsWith("http") },
                categorie = genre,
                bouquet = bouquet,
                archive = o["tv_archive"] == "1",
                joursArchive = o["tv_archive_duration"]?.toIntOrNull() ?: 0
            )

            if (placeGenre) listeGenre.add(chaine)
            if (placeBouquet) listeBouquet.add(chaine)
            null
        }

        val inventaire = compte.mapNotNull { (cle, nombre) ->
            val brut = brutParCle[cle] ?: return@mapNotNull null
            CategorieBrute(
                cle = cle,
                nom = Catalogue.nettoieGroupe(brut),
                type = "tv",
                langue = Catalogue.devineLangue(brut),
                nombre = nombre
            )
        }

        return ContenuChaines(
            rangees = parGenre.entries
                .take(Config.CATEGORIES_MAX)
                .map { RangeeChaines(it.key, it.value) }
                .filter { it.chaines.isNotEmpty() },
            bouquets = Plateformes.trier(
                parBouquet.entries
                    .map { (id, parCategorie) ->
                        id to GroupeBouquet(
                            id,
                            parCategorie.entries
                                .map { RangeeChaines(it.key, it.value) }
                                .filter { it.chaines.isNotEmpty() }
                        )
                    }
                    .filter { it.second.rangees.isNotEmpty() }
            ) { g -> g.rangees.sumOf { it.chaines.size } }.map { it.second },
            inventaire = inventaire
        )
    }

    /* --------------------------------------------------------------- Épisodes */

    /**
     * Épisodes d'une série. Transposition de loadEpisodes() dans xtream.js.
     *
     * Chargés seulement à l'ouverture de la fiche : un appel par série sur
     * un catalogue entier prendrait plusieurs minutes. La réponse ne concerne
     * qu'une série, le JSON complet est donc acceptable ici — contrairement
     * au catalogue, qui reste lu en flux.
     */
    suspend fun episodes(serieId: String): List<Episode> = withContext(Dispatchers.IO) {
        val racine = JSONObject(texte("get_series_info&series_id=$serieId"))
        val parSaison = racine.optJSONObject("episodes") ?: return@withContext emptyList()

        // Affiche de la série, en dernier recours quand l'épisode n'a pas d'image.
        val couverture = racine.optJSONObject("info")
            ?.optString("cover")?.takeIf { it.startsWith("http") }

        val sortie = mutableListOf<Episode>()
        for (cleSaison in parSaison.keys()) {
            // Certains serveurs renvoient un objet là où d'autres mettent un
            // tableau : on saute ce qu'on ne sait pas lire, comme le lecteur
            // en flux du catalogue.
            val liste = parSaison.optJSONArray(cleSaison) ?: continue

            for (i in 0 until liste.length()) {
                val o = liste.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue

                val info = o.optJSONObject("info")
                val vignette = info?.optString("movie_image")?.takeIf { it.startsWith("http") }
                // Selon les serveurs : « plot », parfois « overview » ou
                // « description ».
                val resume = listOf("plot", "overview", "description")
                    .firstNotNullOfOrNull { cle ->
                        info?.optString(cle)?.trim()?.takeIf { it.isNotEmpty() }
                    } ?: ""

                sortie.add(
                    Episode(
                        id = id,
                        titre = o.optString("title").ifBlank { "Épisode" },
                        saison = o.optString("season").toIntOrNull()
                            ?: cleSaison.toIntOrNull() ?: 1,
                        numero = o.optString("episode_num").toIntOrNull() ?: 0,
                        extension = o.optString("container_extension").ifBlank { "mp4" },
                        vignette = vignette ?: couverture,
                        resume = resume
                    )
                )
            }
        }
        sortie.sortedWith(compareBy({ it.saison }, { it.numero }))
    }

    /* ----------------------------------------------------------------- Replay */

    suspend fun replay(chaine: Chaine): List<Programme> = withContext(Dispatchers.IO) {
        // L'EPG d'une chaîne est petit : ici le JSON complet ne pose pas problème.
        val brut = texte("get_simple_data_table&stream_id=${chaine.id}")
        val liste = JSONObject(brut).optJSONArray("epg_listings") ?: return@withContext emptyList()

        val maintenant = System.currentTimeMillis() / 1000
        val programmes = mutableListOf<Programme>()
        for (i in 0 until liste.length()) {
            val o = liste.getJSONObject(i)
            val debut = o.optString("start_timestamp").toLongOrNull() ?: continue
            val fin = o.optString("stop_timestamp").toLongOrNull() ?: continue
            if (fin >= maintenant) continue
            programmes.add(
                Programme(
                    titre = decode(o.optString("title")).ifBlank { "Programme" },
                    resume = decode(o.optString("description")),
                    debut = debut,
                    fin = fin,
                    debutTexte = o.optString("start")
                )
            )
        }
        programmes.sortedByDescending { it.debut }.take(Config.REPLAY_MAX)
    }

    /** Même format que sur Windows : /timeshift/utilisateur/motdepasse/durée/date/id.ts */
    fun urlReplay(chaine: Chaine, p: Programme): String {
        val date = p.debutTexte.trim().replace(" ", ":").take(16)
            .replace(Regex(":(\\d{2}):(\\d{2})$"), ":$1-$2")
        return "${Config.HOTE}/timeshift/${Config.UTILISATEUR}/${Config.MOTDEPASSE}/" +
                "${p.dureeMinutes}/$date/${chaine.id}.ts"
    }

    private fun decode(texte: String): String = try {
        String(android.util.Base64.decode(texte, android.util.Base64.DEFAULT))
    } catch (e: Exception) {
        texte
    }

    /* ------------------------------------------------------------------ Réseau */

    private fun adresse(action: String) =
        "${Config.HOTE}/player_api.php" +
                "?username=${Config.UTILISATEUR}" +
                "&password=${Config.MOTDEPASSE}" +
                "&action=$action"

    /**
     * Garantit la présence du fichier de cache pour cette action et le renvoie.
     * En réseau, la réponse est copiée sur disque par blocs : elle ne passe
     * jamais entièrement par la mémoire.
     */
    private fun prepare(action: String, reseau: Boolean): File {
        val cible = Cache.fichier(action)
        if (!reseau) {
            if (!cible.exists() || cible.length() == 0L) {
                throw Exception("Rien en cache pour $action")
            }
            return cible
        }

        val lien = URL(adresse(action)).openConnection() as HttpURLConnection
        lien.setRequestProperty("User-Agent", Config.AGENT)
        lien.connectTimeout = 15000
        lien.readTimeout = 30000
        try {
            if (lien.responseCode !in 200..299) {
                throw Exception("Le serveur a répondu ${lien.responseCode} sur $action")
            }
            val provisoire = File(cible.parentFile, cible.name + ".part")
            try {
                lien.inputStream.use { entree ->
                    provisoire.outputStream().use { sortie -> entree.copyTo(sortie, 32 * 1024) }
                }
                if (cible.exists()) cible.delete()
                if (!provisoire.renameTo(cible)) {
                    throw Exception("Renommage du cache impossible pour $action")
                }
            } catch (e: Exception) {
                // Un .part laissé derrière serait pris pour un cache valide
                // à la lecture suivante.
                provisoire.delete()
                throw e
            }
        } finally {
            lien.disconnect()
        }
        return cible
    }

    /** Pour les réponses courtes seulement (EPG). */
    private fun texte(action: String): String {
        val lien = URL(adresse(action)).openConnection() as HttpURLConnection
        lien.setRequestProperty("User-Agent", Config.AGENT)
        lien.connectTimeout = 15000
        lien.readTimeout = 20000
        try {
            if (lien.responseCode !in 200..299) {
                throw Exception("Le serveur a répondu ${lien.responseCode}")
            }
            return lien.inputStream.bufferedReader().use { it.readText() }
        } finally {
            lien.disconnect()
        }
    }
}