package com.mrflix.tv

/**
 * Détection de la plateforme d'origine à partir du nom de catégorie.
 *
 * Transposition fidèle de platforms.js. Les fournisseurs IPTV nomment presque
 * toujours leurs catégories ainsi : « FR | NETFLIX », « |FR| AMAZON PRIME »,
 * « SERIES DISNEY+ ».
 *
 * Si tu ajoutes une plateforme ici, ajoute-la aussi côté Windows : les deux
 * versions doivent classer pareil.
 */
object Plateformes {

    data class Plateforme(
        val id: String,
        val libelle: String,
        val motif: Regex?,
        val c1: Long,
        val c2: Long
    )

    /** L'ordre compte : le premier motif qui correspond gagne. */
    private val LISTE = listOf(
        Plateforme("netflix", "Netflix", r("netflix|\\bnflx\\b"), 0xFFE50914, 0xFF7B0710),
        Plateforme("prime", "Prime Video", r("amazon|prime\\s*-?\\s*video|\\bprime\\b"), 0xFF00A8E1, 0xFF0B4D6B),
        Plateforme("disney", "Disney+", r("disney|\\bd\\s*\\+"), 0xFF113CCF, 0xFF0A1A5C),
        Plateforme("appletv", "Apple TV+", r("apple\\s*tv|\\bapple\\b"), 0xFF3A3A3C, 0xFF000000),
        Plateforme("max", "HBO Max", r("\\bhbo\\b|\\bmax\\b(?!imum)"), 0xFF7B2FF7, 0xFF2A0A5E),
        Plateforme("canal", "Canal+", r("canal\\s*\\+?|canalplus|\\bmycanal\\b"), 0xFF4A4A4A, 0xFF000000),
        Plateforme("paramount", "Paramount+", r("paramount"), 0xFF0064FF, 0xFF002A6B),
        Plateforme("ocs", "OCS", r("\\bocs\\b"), 0xFFFF6A00, 0xFF7A3200),
        Plateforme("crunchy", "Crunchyroll", r("crunchyroll|\\badn\\b|\\bwakanim\\b"), 0xFFF47521, 0xFF7A3A10),
        Plateforme("peacock", "Peacock", r("peacock"), 0xFF00B4D8, 0xFF054A5C),
        Plateforme("hulu", "Hulu", r("\\bhulu\\b"), 0xFF1CE783, 0xFF0A5C34),
        Plateforme("starz", "Starz", r("\\bstarz\\b|\\bstarzplay\\b"), 0xFF00A3A3, 0xFF004A4A),
        Plateforme("sky", "Sky", r("\\bsky\\b(?!\\s*news)"), 0xFF0072C9, 0xFF013A66),
        Plateforme("tf1", "TF1+ / M6+", r("\\btf1\\b|\\bmyt?f1\\b|\\bm6\\b|\\b6play\\b|salto"), 0xFFE2001A, 0xFF6B0010),
        Plateforme("autre", "Autre", null, 0xFF4A4A4A, 0xFF1A1A1A)
    )

    private fun r(motif: String) = Regex(motif, RegexOption.IGNORE_CASE)

    /** Plateforme déduite du nom brut de la catégorie du fournisseur. */
    fun detecte(groupe: String?): String {
        val texte = groupe ?: ""
        for (p in LISTE) {
            if (p.motif != null && p.motif.containsMatchIn(texte)) return p.id
        }
        return "autre"
    }

    fun info(id: String): Plateforme = LISTE.find { it.id == id } ?: LISTE.last()

    /* ------------------------------------------------------------- Bouquets */

    /**
     * Bouquets thématiques pour les chaînes en direct.
     * L'ordre compte : les motifs les plus spécifiques passent avant les plus
     * larges, sinon « International » avalerait presque tout.
     */
    private val BOUQUETS = listOf(
        Plateforme("adulte", "Adulte", r("\\badult|xxx|\\+\\s?18|18\\s?\\+"), 0xFF7A1F3D, 0xFF2A0A14),
        Plateforme("sport", "Sport", r("\\bsport|bein|dazn|eurosport|\\bfoot|rugby|tennis|\\bf1\\b|ligue|premier\\s*league"), 0xFF00C853, 0xFF00461D),
        Plateforme("info", "Info", r("\\binfo|news|actualit|bfm|cnews|\\blci\\b|france\\s*24|euronews|i24"), 0xFF0288D1, 0xFF01344F),
        Plateforme("jeunesse", "Jeunesse", r("jeunesse|enfant|\\bkids\\b|cartoon|gulli|nickelodeon|boomerang|piwi|tiji|\\bmanga|anime"), 0xFFFFB300, 0xFF7A5200),
        Plateforme("cinema", "Cinéma & séries", r("cin[ée]|\\bfilm|s[ée]ries?\\b|\\bocs\\b|action|thriller|comedie|com[ée]die"), 0xFFC62828, 0xFF4A0D0D),
        Plateforme("docu", "Découverte", r("docu|d[ée]couverte|discovery|nat\\s*geo|national\\s*geo|histoire|plan[èe]te|science|voyage|animaux"), 0xFF00897B, 0xFF00352F),
        Plateforme("musique", "Musique", r("musique|\\bmusic\\b|\\bmtv\\b|clips?|\\bradio|\\bnrj\\b|trace\\b"), 0xFF8E24AA, 0xFF3A0A45),
        Plateforme("divertissement", "Divertissement", r("divertissement|entertainment|t[ée]l[ée]\\s*r[ée]alit|people|talk"), 0xFFF4511E, 0xFF5C1D08),
        Plateforme("tnt", "TNT & généralistes", r("\\btnt\\b|g[ée]n[ée]ralis|national|\\bfrance\\b|\\bhertzien"), 0xFF3949AB, 0xFF141A52),
        Plateforme("international", "International", r("international|[ée]tranger|\\b(uk|usa|us|en|de|es|it|pt|nl|pl|ru|tr|ar|be|ch|ca)\\b|maroc|alg[ée]rie|tunisie|afrique|arab|turk|belg|suisse|canada|portugal|espagn|italie|allemagne"), 0xFF5D4037, 0xFF231512),
        Plateforme("autre", "Autre", null, 0xFF4A4A4A, 0xFF1A1A1A)
    )

    fun detecteBouquet(groupe: String?): String {
        val texte = groupe ?: ""
        for (b in BOUQUETS) {
            if (b.motif != null && b.motif.containsMatchIn(texte)) return b.id
        }
        return "autre"
    }

    fun infoBouquet(id: String): Plateforme = BOUQUETS.find { it.id == id } ?: BOUQUETS.last()

    /** Ordre d'affichage : les plus fournies d'abord, « Autre » toujours en dernier. */
    fun <T> trier(entrees: List<Pair<String, T>>, nombre: (T) -> Int): List<Pair<String, T>> =
        entrees.sortedWith(
            compareBy(
                { if (it.first == "autre") 1 else 0 },
                { -nombre(it.second) }
            )
        )
}