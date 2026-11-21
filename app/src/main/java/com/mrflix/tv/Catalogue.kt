package com.mrflix.tv

/**
 * Reprise fidèle de m3u.js : mêmes listes de mots, même ordre de priorité.
 *
 * Si tu ajoutes une langue ou un mot ici, pense à le faire aussi côté
 * Windows — les deux versions doivent classer pareil.
 */
object Catalogue {

    private val LANGUES = linkedMapOf(
        "fr" to Langue("Français", listOf("fr", "fra", "france", "french", "vf", "vostfr", "québec", "quebec", "belgique", "suisse")),
        "en" to Langue("Anglais", listOf("en", "eng", "uk", "us", "usa", "english", "american", "british")),
        "ar" to Langue("Arabe", listOf("ar", "ara", "arabic", "arab", "maroc", "morocco", "algerie", "algérie", "tunisie", "egypt", "msr", "ksa", "mbc")),
        "es" to Langue("Espagnol", listOf("es", "esp", "spain", "spanish", "latino", "mexico")),
        "pt" to Langue("Portugais", listOf("pt", "por", "brazil", "brasil", "br", "portugal")),
        "de" to Langue("Allemand", listOf("de", "ger", "deu", "german", "deutsch")),
        "it" to Langue("Italien", listOf("it", "ita", "italy", "italian", "italia")),
        "nl" to Langue("Néerlandais", listOf("nl", "nld", "dutch", "netherlands", "belgium")),
        "tr" to Langue("Turc", listOf("tr", "tur", "turkey", "turkish")),
        "ru" to Langue("Russe", listOf("ru", "rus", "russia", "russian")),
        "pl" to Langue("Polonais", listOf("pl", "pol", "poland", "polish")),
        "in" to Langue("Inde / Asie", listOf("in", "ind", "india", "hindi", "tamil", "urdu", "pak", "asia", "china", "japan", "korea"))
    )

    data class Langue(val libelle: String, val mots: List<String>)

    private val SEPARATEURS = Regex("[^a-zà-ÿ]+")
    private val PREFIXE_LANGUE = Regex("^\\s*(FR|EN|US|UK|AR|BE|CA|DE|ES|IT|PT|NL|TR|RU|PL)\\s*[|:\\-]\\s*", RegexOption.IGNORE_CASE)
    private val MENTION_QUALITE = Regex("[\\[(]\\s*(HD|FHD|SD|4K|UHD|VF|VOSTFR|MULTI)\\s*[\\])]", RegexOption.IGNORE_CASE)
    private val PONCTUATION_BORD = Regex("^[\\s\\-|.:]+|[\\s\\-|.:]+$")
    private val PREFIXE_COURT = Regex("^\\s*[A-Za-z]{2,3}\\s*[|:\\-]\\s*")

    /** Devine la langue d'un groupe. Le préfixe isolé prime, comme sur Windows. */
    fun devineLangue(groupe: String?): String {
        val texte = (groupe ?: "").lowercase()
        val mots = texte.split(SEPARATEURS).filter { it.isNotEmpty() }

        // Un préfixe isolé ("FR | Action") est le signal le plus fiable.
        for ((code, langue) in LANGUES) {
            if (mots.isNotEmpty() && langue.mots.contains(mots[0])) return code
        }
        for ((code, langue) in LANGUES) {
            if (langue.mots.any { it.length > 3 && texte.contains(it) }) return code
        }
        return "xx"
    }

    /** Les codes disponibles, dans l'ordre d'affichage de l'écran Options. */
    val codes: List<String> get() = LANGUES.keys.toList()

    fun libelleLangue(code: String): String = LANGUES[code]?.libelle ?: "Autre / inconnu"

    /** Enlève le préfixe de langue et les mentions de qualité. */
    fun nettoieNom(brut: String): String {
        val propre = brut
            .replace(PREFIXE_LANGUE, "")
            .replace(MENTION_QUALITE, "")
            .replace(PONCTUATION_BORD, "")
            .trim()
        return propre.ifBlank { brut.trim() }
    }

    /** Enlève le préfixe du nom de genre et met la première lettre en majuscule. */
    fun nettoieGroupe(brut: String?): String {
        var groupe = (brut ?: "").replace(PREFIXE_COURT, "")
        groupe = groupe.replace(PONCTUATION_BORD, "")
        if (groupe.isBlank()) groupe = (brut ?: "").trim().ifBlank { "Divers" }
        return groupe.replaceFirstChar { it.uppercase() }
    }
}
