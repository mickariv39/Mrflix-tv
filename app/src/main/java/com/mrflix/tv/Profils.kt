package com.mrflix.tv

/**
 * Profils utilisateur. Transposition de profiles.js.
 *
 * Chacun garde ses catégories cochées ; les identifiants du fournisseur et la
 * clé TMDB restent communs à la maison, comme sur Windows.
 *
 * La palette est reprise telle quelle : chaque ligne parcourt le cercle
 * chromatique, des teintes vives aux plus sourdes, avec des neutres pour finir.
 */
object Profils {

    val COULEURS = listOf(
        0xFFE50914, 0xFFC2185B, 0xFFAD1457, 0xFF8E24AA, 0xFF6A1B9A, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5,
        0xFF0072C9, 0xFF0097A7, 0xFF00897B, 0xFF00A86B, 0xFF2E7D32, 0xFF558B2F, 0xFF9E9D24, 0xFFF9A825,
        0xFFF4A300, 0xFFFB8C00, 0xFFD84315, 0xFFA1442F, 0xFF6D4C41, 0xFF4E342E, 0xFF546E7A, 0xFF37474F,
        0xFFB0413E, 0xFF7CB342, 0xFF26A69A, 0xFF5C6BC0, 0xFFEC407A, 0xFF78909C, 0xFF8D6E63, 0xFF212121
    )

    fun creer(nom: String, couleur: Long, image: String? = null): Profil = Profil(
        id = "prof_" + System.currentTimeMillis() + "_" +
                (0..9999).random().toString().padStart(4, '0'),
        nom = nom.ifBlank { "Profil" }.take(20),
        couleur = couleur,
        image = image
    )

    /** Initiale affichée sur la pastille. */
    fun initiale(profil: Profil?): String =
        (profil?.nom?.trim()?.firstOrNull() ?: '?').uppercase()
}

/** Un profil. `couleur` est une valeur ARGB, comme dans la palette ci-dessus. */
data class Profil(
    val id: String,
    val nom: String,
    val couleur: Long,
    /** Affiche TMDB servant de photo de profil. Null = pastille colorée. */
    val image: String? = null
)