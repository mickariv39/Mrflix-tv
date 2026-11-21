package com.mrflix.tv

/**
 * Genre lisible directement dans le nom de catégorie du fournisseur.
 *
 * Transposition de la première moitié de genres.js : la liste MOTS_GENRES et
 * depuisCategorie(). L'ordre compte, le premier motif qui correspond gagne.
 *
 * La seconde moitié de genres.js — le classement par TMDB pour les titres dont
 * la catégorie ne dit rien — n'est pas reprise : elle demanderait une requête
 * par titre, et les rangées se réorganiseraient au fur et à mesure des
 * réponses. Ici, un titre sans mot de genre garde le nom de sa catégorie.
 *
 * Si tu ajoutes un genre ici, ajoute-le aussi dans genres.js.
 */
object Genres {

    private val MOTS = listOf(
        r("\\baction\\b") to "Action",
        r("\\baventure") to "Aventure",
        r("\\banimation|anim[ée]s?\\b|\\bmanga|\\banime\\b") to "Animation",
        r("\\bcom[ée]die|\\bhumour|\\bcomedy") to "Comédie",
        r("\\bpolicier|\\bpolar\\b|\\bcrime\\b|\\benqu[êe]te") to "Policier",
        r("\\bdocumentaire|\\bdocu\\b|\\bdoc\\b") to "Documentaire",
        r("\\bdrame\\b|\\bdrama\\b") to "Drame",
        r("\\bfamille|\\bfamily\\b|\\bjeunesse|\\benfant") to "Famille",
        r("\\bfantastique|\\bfantasy\\b") to "Fantastique",
        r("\\bhistoire\\b|\\bhistorique") to "Histoire",
        r("\\bhorreur|\\bhorror\\b|\\b[ée]pouvante") to "Horreur",
        r("\\bmusique|\\bmusical|\\bconcert") to "Musique",
        r("\\bromance|\\bromantique|\\bsentimental") to "Romance",
        r("\\bscience[- ]?fiction|\\bsci[- ]?fi\\b|\\bsf\\b") to "Science-fiction",
        r("\\bthriller|\\bsuspense") to "Thriller",
        r("\\bguerre\\b|\\bwar\\b") to "Guerre",
        r("\\bwestern") to "Western",
        r("\\bsport") to "Sport",
        r("\\bt[ée]l[ée][- ]?r[ée]alit[ée]|\\breality\\b") to "Téléréalité"
    )

    private fun r(motif: String) = Regex(motif, RegexOption.IGNORE_CASE)

    /** Genre trouvé dans le nom de catégorie, ou null s'il n'y en a pas. */
    fun depuisCategorie(groupe: String?): String? {
        val texte = groupe ?: ""
        for ((motif, nom) in MOTS) {
            if (motif.containsMatchIn(texte)) return nom
        }
        return null
    }

    /* ------------------------------------------------------------ Via TMDB */

    /**
     * Table des genres TMDB. Ces identifiants sont fixes et documentés :
     * les demander au serveur coûterait une requête pour un résultat qui ne
     * change jamais. Films et séries ont des listes distinctes.
     */
    private val TMDB_FILMS = mapOf(
        28 to "Action", 12 to "Aventure", 16 to "Animation", 35 to "Comédie",
        80 to "Policier", 99 to "Documentaire", 18 to "Drame", 10751 to "Famille",
        14 to "Fantastique", 36 to "Histoire", 27 to "Horreur", 10402 to "Musique",
        9648 to "Mystère", 10749 to "Romance", 878 to "Science-fiction",
        10770 to "Téléfilm", 53 to "Thriller", 10752 to "Guerre", 37 to "Western"
    )

    private val TMDB_SERIES = mapOf(
        10759 to "Action & Aventure", 16 to "Animation", 35 to "Comédie",
        80 to "Policier", 99 to "Documentaire", 18 to "Drame", 10751 to "Famille",
        10762 to "Jeunesse", 9648 to "Mystère", 10763 to "Info",
        10764 to "Téléréalité", 10765 to "SF & Fantastique", 10766 to "Feuilleton",
        10767 to "Talk-show", 10768 to "Guerre & Politique", 37 to "Western"
    )

    /** Nom du genre correspondant à un identifiant TMDB, ou null. */
    fun depuisTmdb(identifiant: Int, estSerie: Boolean): String? =
        if (estSerie) TMDB_SERIES[identifiant] else TMDB_FILMS[identifiant]
}