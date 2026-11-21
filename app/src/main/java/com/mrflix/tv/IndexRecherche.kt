package com.mrflix.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Index de recherche préparé une seule fois par catalogue.
 *
 * Sans lui, chaque lettre tapée relançait la normalisation des 54 723 noms du
 * catalogue — retrait des accents et passage en minuscules — soit deux
 * opérations coûteuses par titre, pour un résultat qui ne change jamais.
 *
 * Ici les noms sont normalisés au chargement et rangés dans des tableaux
 * parallèles. Une frappe ne fait plus que des comparaisons de chaînes, ce qui
 * se compte en millisecondes.
 */
object IndexRecherche {

    private val ACCENTS = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun normalise(texte: String): String =
        ACCENTS.replace(Normalizer.normalize(texte, Normalizer.Form.NFD), "").lowercase()

    /** Le catalogue indexé. Comparé par identité : deux chargements donnent
    deux objets distincts, même si leur contenu se ressemble. */
    private var source: CatalogueComplet? = null

    // Préfixés « table » : sans ça, le tableau et la fonction de recherche
    // porteraient le même nom et Kotlin ne saurait plus lequel on désigne.
    private var nomsTitres: Array<String> = emptyArray()
    private var tableTitres: Array<Film> = emptyArray()
    private var nomsChaines: Array<String> = emptyArray()
    private var tableChaines: Array<Chaine> = emptyArray()

    val nombreTitres: Int get() = tableTitres.size

    /** À appeler dès qu'un catalogue est disponible, hors du fil principal. */
    suspend fun prepare(catalogue: CatalogueComplet) = withContext(Dispatchers.Default) {
        if (source === catalogue) return@withContext

        // Les listes par genre et par plateforme ne contiennent pas la même
        // chose : on prend les deux, puis on dédoublonne.
        val tousTitres = (
                (catalogue.films + catalogue.series).flatMap { it.films } +
                        (catalogue.plateformesFilms + catalogue.plateformesSeries).flatMap { it.titres }
                ).distinctBy { it.id + "|" + it.nom }

        val toutesChaines = (
                catalogue.chaines.flatMap { it.chaines } +
                        catalogue.bouquets.flatMap { g -> g.rangees.flatMap { it.chaines } }
                ).distinctBy { it.id }

        tableTitres = tousTitres.toTypedArray()
        nomsTitres = Array(tableTitres.size) { normalise(tableTitres[it].nom) }
        tableChaines = toutesChaines.toTypedArray()
        nomsChaines = Array(tableChaines.size) { normalise(tableChaines[it].nom) }

        source = catalogue
    }

    /**
     * Titres dont le nom contient la recherche, d'un seul type.
     *
     * On s'arrête dès qu'on en a assez au lieu de parcourir tout le catalogue :
     * pour un mot courant, la différence est de deux ordres de grandeur.
     */
    suspend fun titres(recherche: String, maximum: Int, estSerie: Boolean): List<Film> =
        withContext(Dispatchers.Default) {
            val cible = normalise(recherche)
            val sortie = ArrayList<Film>(maximum)
            for (i in nomsTitres.indices) {
                if (tableTitres[i].estSerie != estSerie) continue
                if (nomsTitres[i].contains(cible)) {
                    sortie.add(tableTitres[i])
                    if (sortie.size >= maximum) break
                }
            }
            sortie
        }

    suspend fun chaines(recherche: String, maximum: Int): List<Chaine> =
        withContext(Dispatchers.Default) {
            val cible = normalise(recherche)
            val sortie = ArrayList<Chaine>(maximum)
            for (i in nomsChaines.indices) {
                if (nomsChaines[i].contains(cible)) {
                    sortie.add(tableChaines[i])
                    if (sortie.size >= maximum) break
                }
            }
            sortie
        }

    /* ------------------------------------------- Épisodes éparpillés en VOD */

    /**
     * Un même film ou épisode, décliné en plusieurs qualités par le
     * fournisseur : « Rocky », « Rocky 4K », « Rocky 1080p ».
     *
     * `versions` est trié de la meilleure qualité à la moins bonne, donc
     * `versions.first()` est le choix par défaut.
     */
    data class Opus(
        val numero: Int,
        val nom: String,
        val versions: List<Film>
    ) {
        val qualites: List<String> get() = versions.map { qualiteLisible(it.nom) }
    }

    /**
     * Un paquet d'épisodes rangés en films par le fournisseur.
     *
     * Nommé GroupeEpisodes et non Collection : `Collection` est déjà le type
     * de base de kotlin.collections, et le redéclarer dans ce paquet le
     * masquerait partout ailleurs.
     *
     * `titre` reste le titre NU, celui qui sert à mesurer la pertinence :
     * préfixer « Saga » ferait perdre la correspondance avec la recherche et
     * reléguerait le groupe en fin de liste. Le préfixe n'existe qu'à
     * l'affichage, dans `libelle`.
     *
     * `episodes` reste disponible et donne UNE entrée par opus, dans sa
     * meilleure qualité. Tout ce qui n'a pas besoin de choisir — la file du
     * lecteur, le comptage, l'affiche — continue de s'en servir sans rien
     * savoir des versions.
     */
    data class GroupeEpisodes(
        val titre: String,
        val opus: List<Opus>,
        val estSaga: Boolean = false
    ) {
        val libelle: String get() = if (estSaga) "Saga $titre" else titre
        val episodes: List<Film> get() = opus.map { it.versions.first() }
    }

    /** Au-dessous, ce n'est pas un feuilleton, juste des titres qui se ressemblent. */
    private const val MINIMUM_GROUPE = 3

    /**
     * Ce qui sépare une saga d'un feuilleton, faute de marqueur explicite.
     *
     * Une saga compte quelques titres numérotés bas — Rocky va jusqu'à neuf
     * en comptant les Creed, Star Wars jusqu'à douze. Un anime rangé en VOD
     * en compte des dizaines, numérotés jusqu'à plusieurs centaines. Ces deux
     * seuils suffisent à les distinguer quand le fournisseur n'écrit ni
     * « épisode » ni « S01E05 ».
     *
     * Douze et non huit : à huit, la saga Rocky passait juste au-dessus et
     * était traitée comme un feuilleton, ce qui désactivait le regroupement
     * des versions et faisait disparaître le choix de qualité.
     */
    private const val SAGA_TITRES_MAX = 12
    private const val SAGA_NUMERO_MAX = 12

    /**
     * Combien de titres on examine avant de regrouper.
     *
     * Bien plus que ce qui sera affiché : une série de 200 épisodes remplirait
     * sinon le quota à elle seule et masquerait tout le reste des résultats.
     * C'est précisément le défaut qu'on corrige ici.
     */
    private const val EXAMINES_MAX = 1200

    /**
     * Frontières de mot insensibles au tiret bas.
     *
     * Attention à ne pas confondre avec le piège JavaScript : là-bas, `\b`
     * s'appuie sur `\w`, purement ASCII, et ne correspond jamais devant
     * « épisode ». C'est le défaut de catalog.js. En Java, `\b` utilise
     * Character.isLetterOrDigit et gère très bien les accents — ce n'était
     * donc PAS un problème ici.
     *
     * Ce qui l'est : `\b` considère le tiret bas comme une lettre. Sur
     * « Naruto_ep_12 », fréquent chez les fournisseurs, `\bep` ne correspond
     * pas et l'épisode n'est jamais reconnu. Ces deux lookarounds ne
     * retiennent que lettres et chiffres, donc le tiret bas redevient un
     * séparateur.
     */
    private const val AVANT = "(?<![\\p{L}\\p{N}])"
    private const val APRES = "(?![\\p{L}\\p{N}])"

    // Compilées une fois : appliquées à plus d'un millier de noms par frappe,
    // les reconstruire à chaque appel se verrait à l'écran.
    private val PARENTHESES = Regex("[\\[(][^\\])]*[\\])]")

    /**
     * S01E05, S1 E5, et le format 1x05 que beaucoup de fournisseurs
     * emploient. Ce dernier manquait, et laissait le « 1x05 » dans le titre
     * de base : chaque épisode formait son propre paquet.
     */
    private val SAISON_EPISODE = Regex(
        AVANT + "(?:s\\s*\\d{1,2}\\s*[-. ]?\\s*e\\s*\\d{1,3}|\\d{1,2}\\s*x\\s*\\d{1,3})" + APRES,
        RegexOption.IGNORE_CASE
    )

    /**
     * « Saison 2 » ou « S2 » sans numéro d'épisode accolé.
     *
     * Sans ce retrait, une série se scindait en autant de paquets que de
     * saisons : « Kaamelott Saison 1 » et « Kaamelott Saison 2 » donnaient
     * deux titres de base différents.
     */
    private val SAISON_SEULE = Regex(
        AVANT + "(?:saison|season|s)\\s*\\.?\\s*\\d{1,2}" + APRES,
        RegexOption.IGNORE_CASE
    )

    private val MOT_EPISODE = Regex(
        AVANT + "(?:episode|épisode|ep|chapitre|chap|part|partie)\\s*\\.?\\s*\\d{1,4}" + APRES,
        RegexOption.IGNORE_CASE
    )
    private val ANNEE = Regex(AVANT + "(?:19|20)\\d{2}" + APRES)

    /**
     * Le premier numéro isolé d'un nom : celui d'un opus.
     *
     * NUMERO_FINAL ne retire un numéro qu'en FIN de nom, donc « Rocky 2 La
     * Revanche » gardait le sien et son titre de base n'avait plus rien de
     * commun avec « Rocky ». La saga se retrouvait trouée : les opus sans
     * sous-titre formaient le groupe, les autres tombaient dans les films
     * seuls, deux sections plus bas.
     *
     * Un séparateur n'est PAS exigé : beaucoup de fournisseurs écrivent
     * « Rocky 2 La Revanche » sans ponctuation.
     *
     * Deux chiffres au plus, et jamais collé à une lettre — « Le 5e Élément »
     * et « Chambre 1408 » restent intacts.
     */
    private val PREMIER_NUMERO = Regex(AVANT + "\\d{1,2}" + APRES)

    /**
     * Tout ce qui suit le numéro d'opus est retiré, sous-titre compris :
     * c'est le seul moyen de ramener « Rocky 2 La Revanche » et « Rocky 4 »
     * au même titre de base.
     *
     * Le garde-fou des trois caractères protège les titres qui COMMENCENT par
     * un nombre — « 13 Reasons Why » ne doit pas se réduire à rien.
     */
    private fun coupeAuNumero(nom: String): String {
        val trouve = PREMIER_NUMERO.find(nom) ?: return nom
        val avant = nom.substring(0, trouve.range.first).trim()
        return if (avant.length >= 3) avant else nom
    }
    private val NUMERO_FINAL = Regex("[\\s\\-_.:#|]*\\d{1,4}\\s*$")
    private val SEPARATEURS = Regex("[\\s\\-_.:#|]+")

    private val RESOLUTION = Regex(AVANT + "\\d{3,4}p" + APRES, RegexOption.IGNORE_CASE)
    private val TECHNIQUE_LARGE = Regex(
        AVANT + "(?:vf|vostfr|vosta|vo|multi|hd|fhd|sd|4k|uhd|hevc|x265|french|truefrench)" + APRES,
        RegexOption.IGNORE_CASE
    )
    private val ESPACES = Regex("\\s+")

    /* ------------------------------------------- Formes réelles du catalogue */

    /**
     * Le préfixe de source, tel que le fournisseur l'écrit vraiment :
     * « |FR| Rocky », et non « FR | Rocky ».
     *
     * PREFIXE_LANGUE dans Catalogue.kt attend le code en TÊTE de chaîne, donc
     * il ne correspond jamais à cette forme. Seule la barre initiale est
     * retirée par la ponctuation de bord, et le « FR » reste collé au titre.
     *
     * Le retrait est fait ici et non dans nettoieNom : le socle d'affiches a
     * été construit à partir des noms actuels, et changer le nettoyage
     * général déplacerait les clés du cache TMDB — trente-quatre mille fiches
     * deviendraient introuvables d'un coup.
     */
    private val PREFIXE_SOURCE = Regex(
        "^\\s*(?:[|\\[(]\\s*[\\p{L}]{2,4}\\s*[|\\])]|[\\p{L}]{2,4}\\s*\\|)\\s*"
    )

    /**
     * Chiffres romains d'une saga : « Rocky II », « Rocky V ».
     *
     * Sans conversion, ces titres n'ont aucun numéro et forment chacun leur
     * propre paquet — la moitié de la saga Rocky partait ainsi dans les films
     * seuls.
     *
     * Le « I » seul est volontairement absent de la liste : trop de titres
     * contiennent un « I » isolé. Et un romain en TÊTE de nom n'est pas
     * converti — « V for Vendetta » n'est pas le cinquième d'une série. La
     * casse n'est pas ignorée non plus : ces numéros s'écrivent en capitales,
     * et accepter « vi » ou « x » ferait des ravages.
     */
    private val ROMAIN = Regex(AVANT + "(?:XII|XI|IX|VIII|VII|VI|IV|III|II|X|V)" + APRES)
    private val VALEUR_ROMAINE = mapOf(
        "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7,
        "VIII" to 8, "IX" to 9, "X" to 10, "XI" to 11, "XII" to 12
    )

    private fun enChiffres(nom: String): String =
        ROMAIN.replace(nom) { m ->
            if (m.range.first == 0) m.value
            else VALEUR_ROMAINE[m.value]?.toString() ?: m.value
        }

    /** Le nom tel qu'on veut le traiter : sans préfixe, romains convertis. */
    private fun prepare(nom: String): String = enChiffres(nom.replace(PREFIXE_SOURCE, ""))

    /* ------------------------------------------------------------ Qualités */

    /**
     * Qualités reconnues, de la meilleure à la moins bonne.
     *
     * L'ordre de la liste EST le classement : `versions.first()` sera donc la
     * meilleure. Les lookarounds évitent que « hd » soit trouvé à l'intérieur
     * de « fhd », ce qui ferait passer un fichier 1080p pour du 720p.
     */
    private val QUALITES = listOf(
        "4K" to Regex(AVANT + "(?:4k|uhd|2160p)" + APRES, RegexOption.IGNORE_CASE),
        "1080p" to Regex(AVANT + "(?:1080p|fhd|full\\s?hd)" + APRES, RegexOption.IGNORE_CASE),
        "720p" to Regex(AVANT + "(?:720p|hd)" + APRES, RegexOption.IGNORE_CASE),
        "SD" to Regex(AVANT + "(?:sd|480p|360p)" + APRES, RegexOption.IGNORE_CASE)
    )

    /** Rang de qualité : plus petit, meilleur. Inconnu passe en dernier. */
    private fun rangQualite(nom: String): Int {
        QUALITES.forEachIndexed { i, (_, motif) -> if (motif.containsMatchIn(nom)) return i }
        return QUALITES.size
    }

    /** « 4K », « 1080p »… ou « Standard » quand le fournisseur ne dit rien. */
    fun qualiteLisible(nom: String): String {
        QUALITES.forEach { (libelle, motif) -> if (motif.containsMatchIn(nom)) return libelle }
        return "Standard"
    }

    /** Le nom débarrassé de la qualité et de l'année, pour l'afficher sans bruit. */
    private fun nomSansQualite(nom: String): String {
        var propre = prepare(nom).replace(PARENTHESES, " ").replace(RESOLUTION, " ")
        QUALITES.forEach { (_, motif) -> propre = propre.replace(motif, " ") }
        propre = propre.replace(TECHNIQUE_LARGE, " ").replace(ANNEE, " ")
            .replace(ESPACES, " ").trim()
        // Un tiret laissé par l'année qu'on vient de retirer : sans ça,
        // « |FR| Rocky - 1976 4K » s'affichait « Rocky - ».
        return propre.trim { it in " -_.:#|" }
    }

    /**
     * Ce qui identifie un opus à l'intérieur d'un paquet.
     *
     * Le numéro suffit quand il existe. Sinon on se rabat sur le nom privé de
     * sa qualité : c'est ce qui rapproche « Rocky » de « Rocky 4K » sans
     * fusionner par erreur deux épisodes distincts dont le numéro n'a pas pu
     * être lu — eux gardent des noms différents.
     *
     * `saga` change tout pour le cas sans numéro. Dans une saga, l'original
     * ne porte presque jamais de numéro : « Rocky », « Rocky 1976 » et
     * « Rocky 1 4K » désignent le même film. Sans cette règle ils formaient
     * trois lignes, dont aucune n'avait de version à proposer — donc plus
     * aucune pastille de qualité. Elle ne s'applique QU'aux sagas : dans un
     * feuilleton, deux épisodes dont le numéro échappe à l'analyse doivent
     * rester distincts.
     */
    private fun cleOpus(nom: String, saga: Boolean): String {
        val n = numeroEpisode(nom)
        return when {
            n > 0 -> "n:$n"
            saga -> "n:1"
            else -> "t:" + normalise(nomSansQualite(nom))
        }
    }

    /**
     * Les motifs qui livrent le numéro d'épisode, du plus explicite au plus
     * incertain. Le format 1x05 vient en tête : quand il est présent, il ne
     * laisse aucun doute.
     */
    private val NUMEROS = listOf(
        Regex(AVANT + "s\\s*\\d{1,2}\\s*[-. ]?\\s*e\\s*(\\d{1,3})" + APRES, RegexOption.IGNORE_CASE),
        Regex(AVANT + "\\d{1,2}\\s*x\\s*(\\d{1,3})" + APRES, RegexOption.IGNORE_CASE),
        Regex(
            AVANT + "(?:episode|épisode|ep|chapitre|chap)\\s*\\.?\\s*(\\d{1,4})" + APRES,
            RegexOption.IGNORE_CASE
        ),
        Regex("(?:^|[\\s\\-_.:#|])(\\d{1,4})\\s*$"),
        // En dernier recours, le premier numéro isolé : « Rocky 2 La
        // Revanche » n'a pas de chiffre en fin de chaîne, et sans ce motif
        // tous les opus sous-titrés se rangeaient à zéro, donc avant le
        // premier film de la saga.
        Regex(AVANT + "(\\d{1,2})" + APRES)
    )

    /**
     * Titre débarrassé de tout ce qui distingue un épisode d'un autre.
     *
     * « Dragon Ball Z 042 VF », « Dragon Ball Z ep 43 » et
     * « Dragon Ball Z S01E05 1080p » donnent tous « Dragon Ball Z ».
     *
     * Repris de baseTitle() dans catalog.js, avec plusieurs corrections : la
     * version Windows ne retire ici ni la résolution ni « french », alors
     * qu'elle le fait dans episodeNumber(), et elle ignore le format 1x05
     * ainsi que la saison seule. Elle souffre en outre du `\b` ASCII de
     * JavaScript, qui lui fait manquer « épisode » — défaut à corriger
     * là-bas, pas ici.
     */
    fun titreBase(nom: String): String = coupeAuNumero(
        prepare(nom)
            .replace(PARENTHESES, " ")
            .replace(SAISON_EPISODE, " ")
            // Après SAISON_EPISODE : sinon « S01 » de « S01E05 » partirait seul
            // et laisserait un « E05 » orphelin dans le titre de base.
            .replace(SAISON_SEULE, " ")
            .replace(MOT_EPISODE, " ")
            .replace(RESOLUTION, " ")
            .replace(TECHNIQUE_LARGE, " ")
            .replace(ANNEE, " ")
            .replace(SEPARATEURS, " ")
            .trim()
    )
        // Après la coupe : elle peut laisser une ponctuation en bout de ligne.
        .replace(NUMERO_FINAL, "")
        .replace(SEPARATEURS, " ")
        .trim()

    /**
     * Numéro d'épisode déduit du nom, pour ordonner un groupe.
     *
     * Les mentions de qualité partent d'abord : sans ça « DBZ 042 VF » n'a
     * plus de chiffre en fin de chaîne et le dernier motif échoue.
     */
    fun numeroEpisode(nom: String): Int {
        val propre = prepare(nom)
            .replace(PARENTHESES, " ")
            .replace(RESOLUTION, " ")
            .replace(TECHNIQUE_LARGE, " ")
            // L'année part aussi : « Blade Runner 2049 » se rangeait sinon en
            // 2049e position de son groupe, tout à la fin.
            .replace(ANNEE, " ")
            .replace(ESPACES, " ")
            .trim()
        for (motif in NUMEROS) {
            val m = motif.find(propre) ?: continue
            val n = m.groupValues[1].toIntOrNull() ?: continue
            return n
        }
        return 0
    }

    /**
     * Pertinence : le titre qui commence par la recherche passe devant.
     * score() dans catalog.js.
     *
     * Le motif de début de mot est fourni par l'appelant, compilé une seule
     * fois pour toute la recherche : le construire ici coûterait une
     * compilation d'expression régulière par titre comparé, soit plus d'un
     * millier par frappe.
     */
    private fun score(titre: String, cible: String, debutDeMot: Regex): Int {
        val t = normalise(titre)
        return when {
            t == cible -> 0
            t.startsWith(cible) -> 1
            debutDeMot.containsMatchIn(t) -> 2
            else -> 3
        }
    }

    /**
     * Films correspondant à la recherche, séparés en groupes et titres seuls.
     *
     * Transposition de clusterLoose() dans catalog.js. Beaucoup de
     * fournisseurs rangent les animes et les feuilletons en VOD, une ligne par
     * épisode : sans ce regroupement, « Dragon Ball Z » renvoie deux cents
     * lignes identiques et le reste des résultats devient invisible.
     */
    suspend fun filmsGroupes(
        recherche: String,
        maximum: Int
    ): Pair<List<GroupeEpisodes>, List<Film>> = withContext(Dispatchers.Default) {
        val cible = normalise(recherche)
        // Un début de mot, pas seulement après une espace : la version
        // précédente cherchait « espace + recherche » et manquait donc les
        // titres où le mot suit un tiret ou un deux-points.
        val debutDeMot = Regex(AVANT + Regex.escape(cible))

        val trouves = ArrayList<Film>(64)
        for (i in nomsTitres.indices) {
            if (tableTitres[i].estSerie) continue
            if (nomsTitres[i].contains(cible)) {
                trouves.add(tableTitres[i])
                if (trouves.size >= EXAMINES_MAX) break
            }
        }

        // LinkedHashMap : à nombre d'épisodes égal, l'ordre du catalogue est
        // conservé, donc deux recherches identiques donnent le même écran.
        val paquets = LinkedHashMap<String, Pair<String, ArrayList<Film>>>()
        val seuls = ArrayList<Film>()

        for (f in trouves) {
            val base = titreBase(f.nom)
            // Un titre réduit à deux caractères ne veut plus rien dire :
            // regrouper là-dessus rassemblerait n'importe quoi. À la
            // différence de clusterLoose(), qui les abandonne purement et
            // simplement, on les garde comme titres seuls — sinon ils
            // disparaissent des résultats sans que rien ne le signale.
            if (base.length < 3) {
                seuls.add(f)
                continue
            }
            paquets.getOrPut(normalise(base)) { base to ArrayList() }.second.add(f)
        }

        val groupes = ArrayList<GroupeEpisodes>()
        for ((base, items) in paquets.values) {
            // Un marqueur explicite tranche seul : personne n'écrit « Rocky
            // épisode 4 ». À défaut, on se fie au nombre de numéros distincts
            // — et non au nombre de fichiers, que les qualités gonflent — et
            // à leur hauteur.
            val explicite = items.any {
                SAISON_EPISODE.containsMatchIn(it.nom) || MOT_EPISODE.containsMatchIn(it.nom)
            }
            val distincts = items.map { numeroEpisode(it.nom) }.filter { it > 0 }.distinct()
            val sagaProbable = !explicite &&
                    distincts.size <= SAGA_TITRES_MAX &&
                    (distincts.maxOrNull() ?: 0) <= SAGA_NUMERO_MAX

            // Les versions d'un même opus se rejoignent AVANT tout le reste :
            // sans ça, « Rocky » et « Rocky 4K » occupaient deux lignes du
            // groupe pour un seul film, et surtout UN film décliné en trois
            // qualités atteignait à lui seul le minimum et formait un faux
            // groupe. Le seuil porte sur les opus, jamais sur les fichiers.
            val parOpus = LinkedHashMap<String, ArrayList<Film>>()
            for (f in items) parOpus.getOrPut(cleOpus(f.nom, sagaProbable)) { ArrayList() }.add(f)

            val opus = parOpus.values.map { versions ->
                val triees = versions.sortedWith(
                    compareBy({ rangQualite(it.nom) }, { it.nom })
                )
                Opus(
                    numero = triees.map { numeroEpisode(it.nom) }.maxOrNull() ?: 0,
                    nom = nomSansQualite(triees.first().nom),
                    versions = triees
                )
            }.sortedWith(compareBy({ it.numero }, { it.nom }))

            if (opus.size >= MINIMUM_GROUPE) {
                groupes.add(
                    GroupeEpisodes(
                        titre = base,
                        opus = opus,
                        estSaga = sagaProbable && opus.size <= SAGA_TITRES_MAX
                    )
                )
            } else {
                seuls.addAll(items)
            }
        }

        groupes.sortWith(compareBy({ score(it.titre, cible, debutDeMot) }, { it.titre }))
        seuls.sortWith(compareBy({ score(it.nom, cible, debutDeMot) }, { it.nom }))

        groupes.take(maximum) to seuls.take(maximum)
    }
}