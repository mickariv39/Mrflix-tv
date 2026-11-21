package com.mrflix.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Facteur de forme de l'appareil.
 *
 * MrFlix tourne d'un seul tenant sur trois familles d'écrans : le téléviseur
 * (navigation à la télécommande, recul de trois mètres), le téléphone (au
 * doigt, portrait, ~360 dp de large) et la tablette (au doigt mais large,
 * proche d'une TV posée à un mètre). Le reste de l'interface s'adapte à partir
 * de cette seule valeur, lue via [LocalForme].
 */
enum class Forme {
    TV, TELEPHONE, TABLETTE;

    /** Vrai dès qu'on est au doigt : téléphone ou tablette. */
    val tactile: Boolean get() = this != TV

    /** Largeur confortable : TV et tablette. Le téléphone, lui, est étroit. */
    val large: Boolean get() = this != TELEPHONE
}

/**
 * Forme courante. `staticCompositionLocalOf` : la valeur ne change qu'à la
 * rotation ou au dépliage, événements assez rares pour ne pas vouloir
 * recomposer tout l'arbre à chaque lecture.
 *
 * Défaut TÉLÉPHONE et non TV : c'est le cas le plus contraint, donc le repli
 * le moins risqué si un composable est rendu hors de tout fournisseur (aperçus).
 */
val LocalForme = staticCompositionLocalOf { Forme.TELEPHONE }

/**
 * Déduit la forme de l'appareil.
 *
 * La « TV-ité » ne change jamais en cours de route : on interroge le système
 * une fois. Le seuil tablette suit la recommandation Android (sw600dp), pris
 * sur `smallestScreenWidthDp` — la plus petite des deux dimensions — pour qu'une
 * rotation ne fasse pas passer un téléphone pour une tablette.
 */
@Composable
fun rememberForme(): Forme {
    val contexte = LocalContext.current
    val config = LocalConfiguration.current
    val estTv = remember { estTelevision(contexte) }
    return when {
        estTv -> Forme.TV
        config.smallestScreenWidthDp >= 600 -> Forme.TABLETTE
        else -> Forme.TELEPHONE
    }
}

/** Hors composition (ex. `onCreate`) : l'appareil est-il un téléviseur ? */
fun estTelevision(contexte: Context): Boolean {
    val pm = contexte.packageManager
    if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("android.hardware.type.television") ||
        pm.hasSystemFeature("android.software.leanback")
    ) return true
    val uiMode = contexte.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
