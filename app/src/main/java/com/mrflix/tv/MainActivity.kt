package com.mrflix.tv

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.painterResource
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import androidx.compose.foundation.Image as ImageComposable
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.request.crossfade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path.Companion.toOkioPath
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Palette de MrFlix Web (public/css/style.css, :root) : noir légèrement
// violacé, surfaces « verre » translucides, coins bien arrondis.
private val FOND = Color(0xFF09070C)        // --bg
private val SURFACE = Color(0xFF1C1922)     // --glass-strong, rendu opaque
private val CARTE = Color(0x17FFFFFF)       // --glass (9 % de blanc)
private val LIGNE = Color(0x24FFFFFF)       // --glass-edge (14 % de blanc)
private val ACCENT = Color(0xFFE50914)      // --accent
private val DISCRET = Color(0xFFA19CAB)     // --text-dim
private val TEXTE = Color(0xFFE4E1EA)       // --text-body
private val RAYON = 12.dp                   // --radius-small
private val RAYON_GRAND = 28.dp             // --radius-large

/** Pilule : boutons, onglets, badges (border-radius: 999px). */
private val PILULE = RoundedCornerShape(50)

/**
 * Le grossissement au focus suivait le ressort par défaut de
 * animateFloatAsState : rebond visible, en plus du défilement natif de la
 * rangée qui bouge en même temps. Un tween linéaire et court supprime ce
 * rebond, pour un mouvement qui se sent d'un seul tenant.
 */
private val RESSORT_FOCUS: AnimationSpec<Float> = tween(durationMillis = 140, easing = LinearEasing)

/** Hauteur de .topbar : logo 28 dp plus 12 dp de marge en haut et en bas. */
private val HAUTEUR_BARRE = 74.dp

/** La barre du haut, version téléphone : logo, titre, recherche, réglages (10 + 42 + 10). */
private val HAUTEUR_BARRE_TEL = 62.dp

/** Ce que la barre de navigation du bas (téléphone) réserve en bas de l'écran :
 *  la barre material (~80 dp) plus la zone système (geste ou trois boutons). */
private val HAUTEUR_NAV_BAS = 68.dp + 14.dp + 22.dp

/* ------------------------------------------------------- Dimensions adaptatives
 *
 * Les valeurs TV viennent de styles.css (recul de trois mètres, télécommande).
 * Sur téléphone, tout se resserre : marges plus fines, rangées plus proches,
 * affiches deux fois plus petites pour en voir trois par ligne au lieu de deux.
 * La taille de DÉCODAGE des affiches, elle, reste réglée dans Image().
 */

/** Marge latérale des rangées et des écrans. */
@Composable
private fun margeEcran(): Dp = if (LocalForme.current == Forme.TELEPHONE) 16.dp else 52.dp

/** Espace vertical entre deux rangées. */
@Composable
private fun espaceRangees(): Dp = if (LocalForme.current == Forme.TELEPHONE) 22.dp else 34.dp

/** Largeur d'une affiche. La hauteur suit le ratio 2:3 de styles.css. */
@Composable
private fun carteL(): Dp = when (LocalForme.current) {
    Forme.TELEPHONE -> 112.dp
    Forme.TABLETTE -> 132.dp
    Forme.TV -> 150.dp
}

@Composable
private fun carteH(): Dp = carteL() * 1.5f

/**
 * Hauteur réservée en tête d'un contenu qui défile derrière la barre flottante.
 * Sur téléphone : barre plus courte, mais la barre d'état système passe
 * par-dessus — on lui ajoute donc sa hauteur.
 */
@Composable
private fun insetHaut(extra: Dp = 0.dp): Dp {
    val tel = LocalForme.current == Forme.TELEPHONE
    val barreEtat = if (tel)
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    return (if (tel) HAUTEUR_BARRE_TEL else HAUTEUR_BARRE) + barreEtat + extra
}

/** Marge basse d'un contenu : sur téléphone, la barre du bas mange l'écran. */
@Composable
private fun insetBas(base: Dp): Dp =
    base + (if (LocalForme.current == Forme.TELEPHONE)
        HAUTEUR_NAV_BAS + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    else 0.dp)

/* ------------------------------------------------------ Défilement « pivot »
 *
 * Le désordre à la télécommande venait de trois mécanismes qui se battaient :
 * le défilement automatique de Compose, nos corrections maison
 * (ajusterDefilementRangee) et les liens bas/haut forcés vers la PREMIÈRE
 * carte de la rangée voisine — carte souvent détruite quand cette rangée
 * avait déjà été défilée, d'où le repli de Compose sur « une affiche au
 * hasard à droite ».
 *
 * Tout est remplacé par une seule règle, celle des interfaces TV (Netflix,
 * Google TV) : la carte qui a le focus vient TOUJOURS au même endroit.
 *  - dans une rangée : collée à la marge de gauche ;
 *  - dans la liste des rangées : son haut sur une ligne fixe sous la barre.
 * Comme chaque rangée garde sa carte active à gauche, la touche bas tombe
 * naturellement sur la carte de gauche de la rangée du dessous, celle qu'on
 * y avait laissée : plus de saut, plus de rattrapage.
 *
 * C'est ce que Compose appelle un BringIntoViewSpec : il ne décide que de la
 * distance à faire défiler quand un élément prend le focus.
 */

/** Pivot horizontal : la carte focus se cale sur la marge de gauche. */
@OptIn(ExperimentalFoundationApi::class)
private class PivotHorizontal(private val margePx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - margePx
}

/**
 * Pivot vertical : le haut de la carte focus se cale sur `lignePx`.
 *
 * Les petits éléments (boutons du bandeau, < `petitPx`) ne déclenchent un
 * défilement que s'ils sont sous l'écran : le bandeau vedette remonte
 * lui-même tout en haut quand un de ses boutons prend le focus (voir Heros).
 */
@OptIn(ExperimentalFoundationApi::class)
private class PivotVertical(
    private val lignePx: Float,
    private val petitPx: Float
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (size < petitPx) {
            val bas = offset + size
            return if (bas > containerSize) bas - containerSize else 0f
        }
        return offset - lignePx
    }
}

/**
 * Hauteur de l'entête d'une rangée (titre + espace) : la ligne de pivot
 * vertical est posée juste en dessous, pour que le titre reste visible.
 */
private val ENTETE_RANGEE = 44.dp

/**
 * Liste verticale de rangées. `haut` = ce qui précède la première rangée
 * dans le défilement (barre flottante, marge) : la carte focus se calera à
 * `haut + ENTETE_RANGEE` du haut de la liste.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColonneRangees(haut: Dp, contenu: @Composable () -> Unit) {
    val densite = LocalDensity.current
    val spec = remember(haut, densite) {
        with(densite) { PivotVertical((haut + ENTETE_RANGEE).toPx(), 80.dp.toPx()) }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = contenu)
}

/** À poser autour de chaque LazyRow de rangée : pivot horizontal. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DefilementRangee(contenu: @Composable () -> Unit) {
    val densite = LocalDensity.current
    val marge = margeEcran()
    val spec = remember(marge, densite) { with(densite) { PivotHorizontal(marge.toPx()) } }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = contenu)
}

/** Année trouvée dans le nom brut (« Dune (2021) », « Dune - 2021 »), sinon null. */
private val ANNEE = Regex("""(?:^|[\s(\[\-–.])((?:19|20)\d{2})(?=$|[\s)\]\-–.])""")
private fun anneeDe(nom: String): String? = ANNEE.findAll(nom).lastOrNull()?.groupValues?.get(1)

/**
 * Ouverture d'une série depuis n'importe quelle carte.
 *
 * Les cartes sont enfouies sous plusieurs niveaux de rangées : passer un
 * rappel de main en main aurait demandé un paramètre supplémentaire à six
 * composables intermédiaires qui n'en ont que faire.
 */
private val LocalOuvrirFiche = staticCompositionLocalOf<(Film) -> Unit> { {} }

/** Interrupteur des affiches TMDB, pour qu'il agisse sans recharger. */
private val LocalOuvrirGroupe =
    staticCompositionLocalOf<(IndexRecherche.GroupeEpisodes) -> Unit> { {} }
private val LocalAffichesTmdb = staticCompositionLocalOf { true }

/** Ce qu'on précharge : le début de chaque rangée, et pas au-delà. */
private const val AFFICHES_PAR_RANGEE = 12
private const val AFFICHES_PRECHARGEES = 500

private val SECTIONS = listOf(
    "accueil" to "Accueil",
    "series" to "Séries",
    "films" to "Films",
    "chaines" to "Chaînes TV",
    "liste" to "Ma liste",
    "replay" to "Replay"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        Reglages.init(this)
        Cache.init(this)
        CatalogueBinaire.init(this)
        Tmdb.init(this)

        // Téléphone / tablette : on rend la barre d'état au système et on
        // dessine sous elle (les composables réservent l'encoche via
        // statusBarsPadding). Le thème plein écran, hérité de la TV, la
        // masquait — logo et titres se retrouvaient collés au bord.
        if (!estTelevision(this)) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        // Plafond mémoire des images : 25 % du tas.
        // Le Chromecast a 2 Go partagés avec le système.
        SingletonImageLoader.setSafe { contexte ->
            ImageLoader.Builder(contexte)
                // ── Si ces trois lignes empêchent la compilation, supprime-les :
                // le nom de ces réglages a changé entre versions de Coil.
                // Elles limitent le nombre de téléchargements et de décodages
                // simultanés. Moins en parallèle donne un défilement PLUS
                // régulier : chacun aboutit plus vite et le processeur du
                // Chromecast n'est plus saturé.
                .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(4))
                .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(2))
                // ── fin du bloc supprimable
                .memoryCache {
                    MemoryCache.Builder()
                        // 25 % au lieu de 15 % : les affiches pèsent désormais
                        // trois fois moins, on en garde donc bien plus.
                        .maxSizePercent(contexte, 0.25)
                        .build()
                }
                // Cache disque : sans lui les affiches sont retéléchargées
                // à chaque lancement. 48 Mo, le Chromecast n'a que 8 Go.
                .diskCache {
                    DiskCache.Builder()
                        .directory(contexte.cacheDir.resolve("affiches").toOkioPath())
                        // 48 Mo ne tenaient pas avec 36 000 titres : les
                        // affiches sortaient du cache aussitôt entrées et
                        // étaient retéléchargées sans arrêt.
                        .maxSizeBytes(200L * 1024 * 1024)
                        .build()
                }
                .build()
        }
        setContent {
            // Toute l'interface s'adapte à partir de cette valeur : TV à la
            // télécommande, téléphone au doigt, tablette entre les deux.
            CompositionLocalProvider(LocalForme provides rememberForme()) {
                Application()
            }
        }
    }

    // Un seul fil de fond, réutilisé : à chaque arrêt on ne relance plus un
    // Thread neuf, et deux sauvegardes ne se chevauchent jamais.
    private val fondSauvegarde = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun onStop() {
        super.onStop()
        // Hors du fil principal : sauvegarde() relit et réécrit tout le
        // fichier, ce qui gèlerait l'image au moment de quitter.
        fondSauvegarde.execute { Tmdb.sauvegarde() }
    }

    override fun onDestroy() {
        super.onDestroy()
        fondSauvegarde.shutdown()
    }
}

@Composable
private fun Application() {
    var catalogue by remember { mutableStateOf<CatalogueComplet?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var horsLigne by remember { mutableStateOf(false) }
    var versionReglages by remember { mutableIntStateOf(0) }
    // Rafraîchissement depuis le serveur : seulement si le cache disque a plus
    // de 6 h, ou s'il est absent. En dessous, on se contente du disque —
    // l'écran s'affiche alors sans la moindre requête réseau. Le bouton
    // « Recharger du serveur » des options force toujours la mise à jour.
    // Changer les catégories, lui, ne demande jamais de requête : seul le
    // filtrage appliqué à la lecture du JSON brut change.
    var rafraichirReseau by remember { mutableStateOf(Cache.ageMinutes > 6 * 60) }
    // Sans témoin, un rechargement depuis le serveur est invisible : l'ancien
    // catalogue reste affiché et rien ne bouge pendant plusieurs minutes.
    var chargementReseau by remember { mutableStateOf(false) }
    // Même raison pour la relecture du cache disque au retour des options :
    // l'ancien catalogue reste à l'écran pendant qu'on le refiltre, ce qui
    // ressemble trait pour trait à un bouton sans effet.
    var refiltrage by remember { mutableStateOf(false) }
    // Incrémenté à chaque touche : sert de signal d'activité. Le préchargement
    // de fond redémarre sa temporisation dès qu'il change.
    // Un flux et non un état Compose : un état lu comme clé d'effet faisait
    // RECOMPOSER toute l'application à chaque appui sur la télécommande.
    val activite = remember { kotlinx.coroutines.flow.MutableStateFlow(0) }
    // (analyse en cours, fait, total) : la phase de comparaison au cache et
    // la recherche réseau ont chacune leur compteur.
    var fond by remember { mutableStateOf<Triple<Boolean, Int, Int>?>(null) }
    // Tant qu'aucun profil n'est choisi, l'écran de choix occupe toute la place
    // et le catalogue n'est pas chargé : il dépend des catégories du profil.
    var profil by remember { mutableStateOf<Profil?>(null) }
    var demarrage by remember { mutableStateOf(true) }
    // Lu une seule fois : la valeur ne change qu'à l'appairage, et c'est
    // l'écran lui-même qui bascule l'état.
    var appaire by remember { mutableStateOf(Reglages.estAppaire) }

    var section by remember { mutableStateOf("accueil") }
    var ecran by remember { mutableStateOf("principal") }
    var chaineChoisie by remember { mutableStateOf<Chaine?>(null) }
    var titreChoisi by remember { mutableStateOf<Film?>(null) }
    var groupeChoisi by remember {
        mutableStateOf<IndexRecherche.GroupeEpisodes?>(null)
    }
    var precharge by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Bumpé au retour au premier plan (sortie du lecteur) : « Reprendre » se
    // recalcule alors, mais PAS à chaque recomposition ni à chaque tic de
    // préchargement.
    var reveil by remember { mutableIntStateOf(0) }
    val contexteCycle = LocalContext.current
    DisposableEffect(contexteCycle) {
        val proprio = contexteCycle as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) reveil++
        }
        proprio?.lifecycle?.addObserver(obs)
        onDispose { proprio?.lifecycle?.removeObserver(obs) }
    }

    // Écran de démarrage : sa durée est celle de l'intro, POINT. Le cache des
    // affiches se charge en arrière-plan pendant ce temps ; s'il n'a pas fini,
    // ce n'est pas grave — il ne sert qu'une fois un profil choisi et le
    // catalogue affiché, et enCache() renvoie simplement « inconnu » en
    // attendant. Avant, la sélection de profil attendait aussi la fin de
    // l'analyse du fichier (plusieurs secondes).
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) { Tmdb.prepare() }
    }
    // Clé sur `demarrage` et non sur `Unit` : l'appairage rebascule l'état à
    // `true` pour rejouer l'intro, et un effet keyé sur `Unit` ne se
    // relancerait jamais — l'application resterait figée sur le logo.
    LaunchedEffect(demarrage) {
        if (!demarrage) return@LaunchedEffect
        delay(INTRO_MS.toLong())
        demarrage = false
    }

    LaunchedEffect(versionReglages, profil) {
        val idProfil = profil?.id ?: return@LaunchedEffect
        erreur = null
        // On garde l'ancien catalogue à l'écran pendant le rechargement :
        // au retour des options, seul le filtrage a changé.
        var relu = false
        refiltrage = true
        try {
            // 1. Cache binaire : le catalogue déjà analysé et filtré. Quasi
            //    instantané tant que les fichiers source et les catégories
            //    cochées n'ont pas bougé.
            val bin = withContext(Dispatchers.IO) { CatalogueBinaire.lire(idProfil) }
            if (bin != null) {
                catalogue = bin
                relu = true
            } else {
                // 2. Sinon, l'analyse JSON complète. Le binaire a été rejeté :
                //    les catégories cochées ou les fichiers source ont bougé,
                //    l'ancien catalogue en mémoire ne correspond plus à ce qui
                //    doit s'afficher. On le LÂCHE avant la ré-analyse — la
                //    garder pendant qu'on construit la nouvelle, sur les 2 Go
                //    partagés du boîtier, pouvait faire échouer l'analyse en
                //    silence : l'écran restait alors sur l'ancien filtre
                //    jusqu'au redémarrage (seul un redémarrage libérait la
                //    mémoire et laissait l'analyse aboutir).
                catalogue = null
                catalogue = Xtream.tout(reseau = false)
                relu = true
                catalogue?.let { c -> launch(Dispatchers.IO) { CatalogueBinaire.ecrire(idProfil, c) } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Analyse impossible (rien en cache, ou mémoire insuffisante) :
            // on tentera le réseau juste après. Si l'ancien catalogue a déjà
            // été lâché, l'écran montre le chargement plutôt qu'un vieux filtre.
        } finally {
            refiltrage = false
        }
        // Sans cache lisible, le nouveau filtrage ne s'appliquerait qu'au
        // prochain démarrage : on va chercher le catalogue plutôt que de
        // laisser le bouton sans effet visible.
        if (!rafraichirReseau && relu) return@LaunchedEffect

        chargementReseau = true
        try {
            catalogue = Xtream.tout(reseau = true)
            horsLigne = false
            // Le serveur a réécrit les fichiers source : le binaire est périmé.
            catalogue?.let { c -> launch(Dispatchers.IO) { CatalogueBinaire.ecrire(idProfil, c) } }
        } catch (e: CancellationException) {
            // Le chargement a été relancé : ce n'est pas un échec, et
            // l'exception doit remonter pour annuler proprement.
            throw e
        } catch (e: Exception) {
            if (catalogue == null) erreur = e.message ?: e.javaClass.simpleName
            else horsLigne = true
        }
        rafraichirReseau = false
        chargementReseau = false
    }

    // Index de recherche préparé dès maintenant, en arrière-plan : la première
    // recherche est ainsi instantanée au lieu d'attendre la normalisation des
    // dizaines de milliers de noms.
    LaunchedEffect(catalogue) {
        catalogue?.let { IndexRecherche.prepare(it) }
    }

    // Relu à chaque retour des options : sans cet état, les cartes déjà
    // composées ne se redessinaient pas et l'interrupteur semblait sans effet.
    val affichesTmdb = remember(versionReglages) { Reglages.affichesTmdb }

    // « Français uniquement » est actif quand toutes les catégories non
    // françaises sont masquées et qu'au moins une française reste visible.
    val francaisSeul = remember(versionReglages, catalogue) {
        val toutes = catalogue?.categories.orEmpty()
        val autres = toutes.filter { it.langue != "fr" }.map { it.cle }.toSet()
        val masquees = Reglages.masquees
        toutes.isNotEmpty() && masquees.containsAll(autres) &&
            toutes.any { it.langue == "fr" && !masquees.contains(it.cle) }
    }

    // Préchargement de fond : après dix secondes sans rien toucher, on
    // cherche les affiches manquantes de TOUT le catalogue, y compris ce qui
    // n'est pas à l'écran. La moindre touche annule ce bloc — `activite` est
    // une clé de l'effet — et le travail reprendra où il s'était arrêté.
    LaunchedEffect(catalogue, ecran) {
        val c = catalogue ?: return@LaunchedEffect
        if (ecran != "principal") return@LaunchedEffect
        // collectLatest : chaque appui annule le travail en cours et relance
        // l'attente, sans rien recomposer.
        activite.collectLatest {
            if (fond != null) fond = null

            // Dix secondes : assez pour ne pas démarrer entre deux appuis, assez
            // court pour que le travail commence dès qu'on pose la télécommande.
            delay(10_000)

            // Des dizaines de milliers d'objets : construits hors du fil principal.
            val titres = withContext(Dispatchers.Default) {
                (c.films + c.series).flatMap { r -> r.films }
                    .map { Tmdb.ATrouver(it.nom, it.estSerie) }
            }
            // Le total renvoyé est ce qui RESTE à chercher, pas la taille du
            // catalogue : il doit diminuer d'une pause à l'autre. S'il ne bouge
            // pas entre deux passes, c'est que rien n'est retenu.
            Tmdb.prechargeAuRepos(titres) { analyse, fait, total ->
                fond = when {
                    analyse -> Triple(true, fait, total)
                    total <= 0 || fait >= total -> null
                    else -> Triple(false, fait, total)
                }
            }
            fond = null
        }
    }

    val ouvrirFiche: (Film) -> Unit = { f -> titreChoisi = f; ecran = "fiche" }
    val ouvrirGroupe: (IndexRecherche.GroupeEpisodes) -> Unit = { g ->
        groupeChoisi = g; ecran = "groupe"
    }

    // Signature sonore : à l'instant où le mot « MRFLIX » achève de s'écrire
    // (voir EcranDemarrage). Keyée sur `demarrage` comme l'intro elle-même,
    // pour tomber juste même quand l'appairage la rejoue — et pour ne pas
    // sonner dans le vide pendant l'écran d'appairage d'un boîtier neuf.
    val contexteSon = LocalContext.current
    LaunchedEffect(demarrage) {
        if (!demarrage) return@LaunchedEffect
        delay(1900)
        try {
            MediaPlayer.create(contexteSon, R.raw.mrflix_signature)?.apply {
                // Libéré à la fin : sinon le lecteur reste en mémoire et
                // garde la sortie audio occupée.
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            // Un son qui échoue ne doit jamais empêcher l'application de servir.
        }
    }

    // Préchargement des affiches dès que le catalogue est là. En arrière-plan :
    // l'écran reste utilisable pendant ce temps, les cartes se remplissent au
    // fur et à mesure. Une fois terminé, le défilement ne touche plus au réseau.
    LaunchedEffect(catalogue) {
        val c = catalogue ?: return@LaunchedEffect
        // Avec un catalogue complet, précharger TOUT serait des dizaines de
        // milliers de requêtes. On ne précharge que le début de chaque rangée,
        // c'est-à-dire ce qui s'affiche avant le premier défilement ; le reste
        // se cherche à la demande, avec le délai anti-saccades.
        // Hors du fil principal : precharge() trie d'abord ce qui manque en
        // nettoyant chaque titre (une quinzaine d'expressions régulières).
        withContext(Dispatchers.Default) {
            val titres = (c.films + c.series)
                .flatMap { r -> r.films.take(AFFICHES_PAR_RANGEE) }
                .take(AFFICHES_PRECHARGEES)
                .map { Tmdb.ATrouver(it.nom, it.estSerie) }
            Tmdb.precharge(titres) { fait, total ->
                precharge = if (fait >= total) null else fait to total
            }
        }
        precharge = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FOND)
            // Toute touche interrompt le travail de fond. onPreviewKeyEvent
            // voit l'événement AVANT qu'il ne soit consommé par une carte,
            // donc rien ne peut lui échapper.
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) activite.value += 1
                false   // on ne consomme pas : la navigation continue normalement
            }
    ) {
        CompositionLocalProvider(
            LocalOuvrirFiche provides ouvrirFiche,
            LocalOuvrirGroupe provides ouvrirGroupe,
            LocalAffichesTmdb provides affichesTmdb
        ) {
            when {
                // Avant tout le reste : sans accès au fournisseur, il n'y a ni
                // catalogue à charger ni profil qui vaille.
                !appaire -> EcranAppairage {
                    appaire = true
                    demarrage = true
                }

                demarrage -> EcranDemarrage()

                profil == null -> EcranProfils { choisi ->
                    Reglages.profilActif = choisi.id
                    profil = choisi
                }

                ecran == "groupe" && groupeChoisi != null -> {
                    // Retour à la recherche, pas à l'accueil : on vient de là.
                    val revenir = { ecran = "recherche" }
                    BackHandler(onBack = revenir)
                    EcranGroupe(groupeChoisi!!, surRetour = revenir)
                }

                ecran == "fiche" && titreChoisi != null -> {
                    // Chaque écran ajouté a besoin de son propre BackHandler,
                    // sinon le bouton retour quitte l'application.
                    // `reveil` est bumpé au retour : la fiche a pu changer
                    // « Ma liste » ou « Déjà vu », il faut recalculer ces listes.
                    val revenir = { reveil++; ecran = "principal" }
                    BackHandler(onBack = revenir)
                    FicheDetail(titre = titreChoisi!!, surRetour = revenir)
                }

                ecran == "changer-profil" -> {
                    val revenir = { ecran = "principal" }
                    BackHandler(onBack = revenir)
                    EcranProfils { choisi ->
                        Reglages.profilActif = choisi.id
                        profil = choisi
                        catalogue = null
                        ecran = "principal"
                    }
                }

                ecran == "options" -> {
                    val revenir = {
                        versionReglages++
                        ecran = "principal"
                    }
                    BackHandler(onBack = revenir)
                    Options(
                        catalogue,
                        surRetour = revenir,
                        surChangerProfil = { ecran = "changer-profil" },
                        surRecharger = {
                            rafraichirReseau = true
                            versionReglages++
                            ecran = "principal"
                        },
                        surReappairer = {
                            // Le catalogue en mémoire vient de l'ancien
                            // compte : on le lâche, et le prochain chargement
                            // repartira du réseau avec les nouveaux accès.
                            catalogue = null
                            profil = null
                            rafraichirReseau = true
                            ecran = "principal"
                            appaire = false
                        }
                    )
                }

                ecran == "recherche" -> {
                    val revenir = { ecran = "principal" }
                    BackHandler(onBack = revenir)
                    Recherche(catalogue, surRetour = revenir)
                }

                ecran == "programmes" && chaineChoisie != null -> {
                    val revenir = { ecran = "principal" }
                    BackHandler(onBack = revenir)
                    Programmes(chaine = chaineChoisie!!, surRetour = revenir)
                }

                else -> Principal(
                    catalogue = catalogue,
                    erreur = erreur,
                    horsLigne = horsLigne,
                    chargementReseau = chargementReseau,
                    refiltrage = refiltrage,
                    travailFond = fond,
                    precharge = precharge,
                    reveil = reveil,
                    section = section,
                    surSection = { section = it },
                    surOptions = { ecran = "options" },
                    surRecherche = { ecran = "recherche" },
                    surProgrammes = { c -> chaineChoisie = c; ecran = "programmes" },
                    francais = francaisSeul,
                    surFrancais = {
                        // La même règle que le bouton « Français seulement »
                        // des options : on ne garde que les catégories
                        // reconnues comme françaises, ou on recoche tout.
                        val toutes = catalogue?.categories.orEmpty()
                        if (toutes.isNotEmpty()) {
                            if (francaisSeul) Reglages.toutCocher()
                            else Reglages.garderFrancaisSeulement(toutes)
                            versionReglages++
                        }
                    }
                )
            }
        }
    }
}

/* -------------------------------------------------------------- Principal */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Principal(
    catalogue: CatalogueComplet?,
    erreur: String?,
    horsLigne: Boolean,
    chargementReseau: Boolean,
    refiltrage: Boolean,
    travailFond: Triple<Boolean, Int, Int>?,
    precharge: Pair<Int, Int>?,
    reveil: Int,
    section: String,
    surSection: (String) -> Unit,
    surOptions: () -> Unit,
    surRecherche: () -> Unit,
    surProgrammes: (Chaine) -> Unit,
    francais: Boolean = false,
    surFrancais: () -> Unit = {}
) {
    // Le contenu commence DERRIÈRE la barre : la recherche géométrique du
    // voisin vers le bas ne le trouve donc pas. On désigne explicitement la
    // cible de la touche bas au lieu de laisser Compose deviner.
    val contenu = remember { FocusRequester() }

    // La partie coûteuse de « Reprendre » : calculée une fois par catalogue.
    val parCleFavori = remember(catalogue) {
        catalogue?.let(::indexParCleFavori) ?: emptyMap()
    }
    // Le rapprochement avec les positions en cours : refait seulement quand le
    // catalogue change ou au retour du lecteur (`reveil`), jamais à chaque
    // recomposition. Une liste stable évite aussi de recomposer Accueil /
    // ParPlateforme à chaque tic du préchargement.
    val reprisesToutes = remember(parCleFavori, reveil) { titresEnCours(parCleFavori, null) }
    val reprisesSeries = remember(parCleFavori, reveil) { titresEnCours(parCleFavori, serie = true) }
    val reprisesFilms = remember(parCleFavori, reveil) { titresEnCours(parCleFavori, serie = false) }

    // « Déjà vu » : même logique, recalculé seulement au changement de
    // catalogue ou au retour du lecteur. Ce qui est encore dans « Reprendre »
    // n'y figure pas — un titre est dans l'un OU dans l'autre.
    val vusFilms = remember(catalogue, reveil, reprisesFilms) {
        catalogue?.let {
            val enCours = reprisesFilms.mapTo(HashSet()) { e -> e.film.id }
            titresVus(it.plateformesFilms).filterNot { f -> f.id in enCours }
        } ?: emptyList()
    }
    val vusSeries = remember(catalogue, reveil, reprisesSeries) {
        catalogue?.let {
            val enCours = reprisesSeries.mapTo(HashSet()) { e -> e.film.id }
            titresVus(it.plateformesSeries).filterNot { f -> f.id in enCours }
        } ?: emptyList()
    }

    val forme = LocalForme.current
    var plusOuvert by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        // Barre du haut : flotte au-dessus du contenu, le dégradé l'estompe.
        // Sur téléphone elle est réduite au logo + recherche + réglages ;
        // les sections passent dans la barre du bas.
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .zIndex(1f)
                .statusBarsPadding()
                .focusProperties { down = contenu }
        ) {
            if (forme == Forme.TELEPHONE) BarreHautTel(section, surRecherche, surOptions)
            else BarreNavigation(section, surSection, surOptions, surRecherche)
            BandeauxEtat(horsLigne, chargementReseau, refiltrage, travailFond, precharge)
        }

        // Le contenu occupe TOUT l'écran et passe derrière les barres, dans
        // chaque section : c'est ce qui donne au dégradé quelque chose à
        // estomper. Chaque écran réserve lui-même la hauteur des barres en
        // tête et en pied de son défilement (voir insetHaut / insetBas),
        // sauf l'accueil dont le bandeau doit justement apparaître derrière.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contenu)
                .focusGroup(),
            contentAlignment = Alignment.Center
        ) {
            ContenuSection(
                catalogue, erreur, section,
                reprisesToutes, reprisesSeries, reprisesFilms,
                vusSeries, vusFilms, surProgrammes,
                francais, surFrancais
            )
        }

        // Barre du bas : téléphone seulement. Tablette et TV gardent les
        // onglets du haut, largement assez larges pour eux.
        if (forme == Forme.TELEPHONE) {
            OngletsTel(
                section = section,
                surSection = surSection,
                surPlus = { plusOuvert = true },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f)
            )
        }
    }

    if (plusOuvert) {
        FeuillePlus(
            section = section,
            surChoix = { s -> surSection(s); plusOuvert = false },
            surFermer = { plusOuvert = false }
        )
    }
}

/** Les bandeaux d'état sous la barre du haut (hors ligne, chargement, etc.). */
@Composable
private fun BandeauxEtat(
    horsLigne: Boolean,
    chargementReseau: Boolean,
    refiltrage: Boolean,
    travailFond: Triple<Boolean, Int, Int>?,
    precharge: Pair<Int, Int>?
) {
    if (horsLigne) {
        MessageBarre("Serveur injoignable — catalogue enregistré", Color(0xFFE0A030))
    }
    if (chargementReseau) {
        MessageBarre("Mise à jour depuis le serveur…", ACCENT)
    }
    if (refiltrage) {
        MessageBarre("Application des catégories…", ACCENT)
    }
    // Sur téléphone, les compteurs d'affiches recouvraient les pastilles :
    // on n'y garde que les messages qui comptent (hors ligne, chargement).
    if (LocalForme.current == Forme.TELEPHONE) return
    travailFond?.let { (analyse, fait, total) ->
        if (analyse) MessageBarre("Analyse : $fait / $total titres", DISCRET)
        else MessageBarre("Affiches en fond : ${total - fait} restantes", DISCRET)
    }
    precharge?.let { (fait, total) ->
        MessageBarre("Affiches : $fait / $total", DISCRET)
    }
}

/** Le corps d'une section : ce que la barre de navigation sélectionne. */
@Composable
private fun ContenuSection(
    catalogue: CatalogueComplet?,
    erreur: String?,
    section: String,
    reprisesToutes: List<EnCours>,
    reprisesSeries: List<EnCours>,
    reprisesFilms: List<EnCours>,
    vusSeries: List<Film>,
    vusFilms: List<Film>,
    surProgrammes: (Chaine) -> Unit,
    francais: Boolean = false,
    surFrancais: () -> Unit = {}
) {
    val tel = LocalForme.current == Forme.TELEPHONE
    when {
        erreur != null -> Text(
            text = "Échec : $erreur",
            color = Color(0xFFFF6B6B),
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = margeEcran())
        )

        catalogue == null -> CircularProgressIndicator(color = ACCENT)

        else -> when (section) {
            "accueil" -> Accueil(catalogue, reprisesToutes)
            // Téléphone : les écrans de MrFlix Web, en grille.
            "series" -> if (tel) CatalogueTel(catalogue.plateformesSeries, francais, surFrancais)
            else ParPlateforme(
                catalogue.plateformesSeries, "Séries", reprisesSeries, vusSeries
            )
            "films" -> if (tel) CatalogueTel(catalogue.plateformesFilms, francais, surFrancais)
            else ParPlateforme(
                catalogue.plateformesFilms, "Films", reprisesFilms, vusFilms
            )
            "chaines" -> if (tel) ChainesTel(catalogue.bouquets, francais, surFrancais, surProgrammes)
            else ParBouquet(catalogue.bouquets)
            "liste" -> MaListe(catalogue)
            "replay" -> ListeReplay(catalogue.bouquets, surProgrammes)
        }
    }
}

/**
 * Barre de navigation du bas, façon téléphone. Cinq entrées : les quatre
 * sections principales et « Plus » (Ma liste, Replay), qui ouvre une feuille.
 */
@Composable
private fun BarreBasse(
    section: String,
    surSection: (String) -> Unit,
    surPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val couleurs = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White,
        selectedTextColor = Color.White,
        unselectedIconColor = DISCRET,
        unselectedTextColor = DISCRET,
        indicatorColor = Color(0x33E50914)
    )
    // cle, glyphe, nom — plus "plus" en dernier, qui ouvre la feuille.
    val onglets = listOf(
        Triple("accueil", "🏠", "Accueil"),
        Triple("films", "🎬", "Films"),
        Triple("series", "📺", "Séries"),
        Triple("chaines", "📡", "Chaînes"),
        Triple("plus", "☰", "Plus")
    )
    NavigationBar(
        modifier = modifier,
        containerColor = Color(0xF20B0B0B),
        tonalElevation = 0.dp
    ) {
        for ((cle, glyphe, nom) in onglets) {
            val choisi = when (cle) {
                "plus" -> section == "liste" || section == "replay"
                else -> section == cle
            }
            NavigationBarItem(
                selected = choisi,
                onClick = { if (cle == "plus") surPlus() else surSection(cle) },
                icon = { Text(glyphe, fontSize = 18.sp) },
                label = { Text(nom, fontSize = 11.sp) },
                alwaysShowLabel = true,
                colors = couleurs
            )
        }
    }
}

/** La feuille « Plus » : Ma liste et Replay, hors de la barre du bas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuillePlus(
    section: String,
    surChoix: (String) -> Unit,
    surFermer: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = surFermer,
        containerColor = Color(0xF21C1922),
        shape = RoundedCornerShape(topStart = RAYON_GRAND, topEnd = RAYON_GRAND),
        dragHandle = null
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text("Plus", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            listOf("liste" to "Ma liste", "replay" to "Replay").forEach { (cle, nom) ->
                LigneFeuilleTel(nom, section == cle) { surChoix(cle) }
            }
        }
    }
}

/** Ligne d'information sous la barre du haut. */
@Composable
private fun MessageBarre(texte: String, couleur: Color) {
    Text(
        text = texte,
        color = couleur,
        fontSize = 13.sp,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(start = margeEcran(), end = margeEcran(), top = 2.dp, bottom = 3.dp)
    )
}

@Composable
private fun BarreNavigation(
    section: String,
    surSection: (String) -> Unit,
    surOptions: () -> Unit,
    surRecherche: () -> Unit,
    compacte: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // .topbar de MrFlix Web : le fond sombre s'estompe vers le bas,
            // ce qui laisse deviner ce qui défile derrière.
            .background(
                Brush.verticalGradient(
                    0f to Color(0xF209070C),
                    0.65f to Color(0xB309070C),
                    1f to Color.Transparent
                )
            )
            .padding(
                horizontal = if (compacte) 16.dp else 40.dp,
                vertical = if (compacte) 8.dp else 14.dp
            )
    ) {
        // Le M de src/assets/logo.svg, converti en ressource vectorielle.
        ImageComposable(
            painter = painterResource(R.drawable.logo_mrflix),
            contentDescription = "MrFlix",
            modifier = Modifier.height(if (compacte) 24.dp else 30.dp)
        )

        if (compacte) {
            Spacer(Modifier.width(10.dp))
            Text(
                "MRFLIX",
                color = ACCENT,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.4.sp
            )
            // Téléphone : les sections sont dans la barre du bas.
            Spacer(Modifier.weight(1f))
            BoutonIcone(Icone.RECHERCHE, "Rechercher", surRecherche)
            Spacer(Modifier.width(10.dp))
            BoutonIcone(Icone.PLUS, "Options", surOptions)
        } else {
            // Filet vertical entre le logo et les onglets, comme .section-title.
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(1.dp).height(26.dp).background(LIGNE))
            Spacer(Modifier.width(18.dp))
            // .chips : une rangée de pilules. L'onglet actif est blanc plein.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SECTIONS.forEach { (cle, libelle) ->
                    LienNav(libelle, actif = section == cle) { surSection(cle) }
                }
            }
            Spacer(Modifier.weight(1f))
            BoutonIcone(Icone.RECHERCHE, "Rechercher", surRecherche)
            Spacer(Modifier.width(10.dp))
            BoutonIcone(Icone.PLUS, "Options", surOptions)
        }
    }
}

/** Les icônes de la barre du haut, dessinées au trait comme sur MrFlix Web. */
private enum class Icone { RECHERCHE, PLUS }

@Composable
private fun BoutonIcone(icone: Icone, description: String, surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }
    val echelle by animateFloatAsState(
        if (enFocus) 1.1f else 1f, animationSpec = RESSORT_FOCUS, label = "echelle"
    )
    val couleur = if (enFocus) Color(0xFF111111) else Color.White
    // .glass.round : un rond de verre de 40 px, l'icône dessinée au trait.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .size(42.dp)
            .clip(CircleShape)
            .background(if (enFocus) Color.White else Color(0x17FFFFFF))
            .border(1.dp, if (enFocus) Color.White else LIGNE, CircleShape)
    ) {
        Canvas(Modifier.size(20.dp)) {
            val trait = 2.dp.toPx()
            when (icone) {
                Icone.RECHERCHE -> {
                    val r = size.minDimension * 0.34f
                    val c = Offset(size.width * 0.43f, size.height * 0.43f)
                    drawCircle(
                        couleur, radius = r, center = c,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = trait)
                    )
                    val d = r * 0.72f
                    drawLine(
                        couleur,
                        start = Offset(c.x + d, c.y + d),
                        end = Offset(size.width * 0.9f, size.height * 0.9f),
                        strokeWidth = trait,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                Icone.PLUS -> {
                    // Les trois points « ••• » de MrFlix Web.
                    val y = size.height / 2
                    listOf(0.2f, 0.5f, 0.8f).forEach { fx ->
                        drawCircle(couleur, radius = trait * 0.95f, center = Offset(size.width * fx, y))
                    }
                }
            }
        }
    }
}

@Composable
private fun LienNav(titre: String, actif: Boolean, surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }
    val echelle by animateFloatAsState(
        if (enFocus) 1.08f else 1f, animationSpec = RESSORT_FOCUS, label = "echelle"
    )
    // .chip de MrFlix Web : pilule de verre ; l'onglet actif est blanc plein.
    // Le focus (la télécommande, faute de curseur) grossit et cercle de blanc.
    val plein = actif || enFocus
    Text(
        text = titre,
        color = if (plein) Color(0xFF111111) else Color.White,
        fontSize = 15.sp,
        fontWeight = if (actif) FontWeight.Bold else FontWeight.SemiBold,
        modifier = Modifier
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(PILULE)
            .background(
                when {
                    enFocus -> Color.White
                    actif -> Color(0xE6FFFFFF)
                    else -> Color(0x1AFFFFFF)
                }
            )
            .border(
                width = if (enFocus) 2.dp else 1.dp,
                color = if (enFocus) ACCENT else if (actif) Color.White else LIGNE,
                shape = PILULE
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/* ---------------------------------------------------------------- Accueil */

@Composable
private fun Accueil(catalogue: CatalogueComplet, reprises: List<EnCours>) {
    val vedette = catalogue.recents.firstOrNull()

    // « Tout voir » de la rangée Ajouté récemment : une grille plein écran,
    // sur le même principe que les tuiles de plateforme.
    var voirTousRecents by remember { mutableStateOf(false) }
    BackHandler(enabled = voirTousRecents) { voirTousRecents = false }
    if (voirTousRecents) {
        EcranTousLesTitres("Ajouté récemment", catalogue.recentsTous) { voirTousRecents = false }
        return
    }

    // Popularité TMDB rapprochée du catalogue : ne se relance pas à chaque
    // recomposition, seulement quand le catalogue lui-même change.
    var populairesFrance by remember(catalogue) { mutableStateOf<List<Film>>(emptyList()) }
    LaunchedEffect(catalogue) {
        val popFilms = Tmdb.populaires(pourSeries = false)
        val popSeries = Tmdb.populaires(pourSeries = true)
        // correspondances() appelle enCache() sur chaque titre du catalogue :
        // hors du fil principal, sous peine de geler l'appli sur un gros
        // catalogue (mesuré : plus de vingt secondes sur le Chromecast).
        populairesFrance = withContext(Dispatchers.Default) {
            val films = catalogue.films.flatMap { it.films }
            val series = catalogue.series.flatMap { it.films }
            val trouvesFilms = Tmdb.correspondances(popFilms, films, Config.POPULAIRES_MAX)
            val trouvesSeries = Tmdb.correspondances(popSeries, series, Config.POPULAIRES_MAX)
            entrelace(trouvesFilms, trouvesSeries).take(Config.POPULAIRES_MAX)
        }
    }

    val seriesRows = remember(catalogue) { catalogue.series.take(Config.RANGEES_ACCUEIL) }
    val filmsRows = remember(catalogue) { catalogue.films.take(Config.RANGEES_ACCUEIL) }
    val chainesRows = remember(catalogue) { catalogue.chaines.take(Config.RANGEES_ACCUEIL) }

    val etatListe = rememberLazyListState()
    val haut = insetHaut(8.dp)
    val ficheVedette = vedette?.let { rememberFiche(it) }

    Box(Modifier.fillMaxSize()) {
        // .ambient de MrFlix Web : les couleurs de l'affiche vedette, floues,
        // derrière tout. Le flou est « gratuit » : l'image est décodée en
        // 48x27 pixels puis étirée sur l'écran, ce qui la lisse d'elle-même.
        // Aucun effet de flou à calculer, rien à redessiner au défilement.
        val ambiance = ficheVedette?.value?.fond ?: ficheVedette?.value?.affiche ?: vedette?.affiche
        if (ambiance != null) {
            Image(
                url = ambiance,
                description = "",
                largeurPx = 48, hauteurPx = 27,
                echelleContenu = ContentScale.Crop,
                // alpha passé à l'image et non Modifier.alpha() : ce dernier
                // dessine d'abord dans un calque plein écran hors champ, à
                // chaque image — mesuré trop lourd pour la puce du Chromecast.
                alpha = 0.45f,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            )
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.8f).background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color(0x6609070C), 1f to FOND)
                )
            )
        }

        ColonneRangees(haut = haut) {
            LazyColumn(
                state = etatListe,
                contentPadding = PaddingValues(top = haut, bottom = insetBas(60.dp)),
                verticalArrangement = Arrangement.spacedBy(espaceRangees())
            ) {
                if (vedette != null) {
                    item(key = "heros", contentType = "heros") {
                        Heros(vedette, ficheVedette?.value, etatListe)
                    }
                }

                // « Reprendre » AVANT les nouveautés : c'est ce qu'on vient
                // chercher en premier en allumant la télé.
                if (reprises.isNotEmpty()) {
                    item(key = "reprise", contentType = "rangee-films") {
                        RangeeReprise("Reprendre la lecture", reprises)
                    }
                }

                if (catalogue.recents.isNotEmpty()) {
                    item(key = "recents", contentType = "rangee-films") {
                        RangeeFilms(
                            Rangee("Ajouté récemment", catalogue.recents),
                            surVoirTout = { voirTousRecents = true }
                        )
                    }
                }

                if (populairesFrance.isNotEmpty()) {
                    item(key = "populaire-france", contentType = "rangee-films") {
                        RangeeFilms(Rangee("Populaire en France", populairesFrance))
                    }
                }

                // Comme sur Windows : tout se mélange, le type est dans le titre.
                items(seriesRows, key = { "s-${it.titre}" }, contentType = { "rangee-films" }) { r ->
                    RangeeFilms(Rangee("Séries · ${r.titre}", r.films))
                }
                items(filmsRows, key = { "f-${it.titre}" }, contentType = { "rangee-films" }) { r ->
                    RangeeFilms(Rangee("Films · ${r.titre}", r.films))
                }
                items(chainesRows, key = { "tv-${it.titre}" }, contentType = { "rangee-chaines" }) { r ->
                    RangeeChainesVue(Rangee2("TV · ${r.titre}", r.chaines))
                }
            }
        }
    }
}

/**
 * La fiche TMDB d'un titre : lue tout de suite dans le cache, puis cherchée
 * sur le réseau si besoin. Partagée par le bandeau vedette et le fond ambiant.
 */
@Composable
private fun rememberFiche(film: Film): State<Fiche?> {
    val connu = remember(film.id) { Tmdb.enCache(film.nom, film.estSerie) }
    val fiche = remember(film.id) { mutableStateOf(connu?.fiche) }
    LaunchedEffect(film.id) {
        if (Tmdb.actif && connu == null) {
            try {
                fiche.value = Tmdb.fiche(film.nom, film.estSerie)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fiche.value = null
            }
        }
    }
    return fiche
}

@Composable
private fun Heros(film: Film, fiche: Fiche?, etatListe: LazyListState) {
    val contexte = LocalContext.current
    val ouvrirFiche = LocalOuvrirFiche.current
    val portee = rememberCoroutineScope()
    val tel = LocalForme.current == Forme.TELEPHONE

    val titre = if (Config.CORRIGER_TITRES) (fiche?.titreCorrige ?: film.nom) else film.nom
    val affiche = fiche?.affiche ?: film.affiche
    val annee = remember(film.nom) { anneeDe(film.nom) }
    val meta = listOfNotNull(
        if (film.estSerie) "Série" else "Film",
        annee,
        fiche?.genre?.ifBlank { null } ?: film.categorie
    ).joinToString("  ·  ")

    // Quand un bouton du bandeau prend le focus, on remonte TOUT en haut
    // pour montrer le bandeau en entier (le pivot vertical ignore exprès les
    // petits éléments — voir PivotVertical).
    val remonter = Modifier.onFocusChanged {
        if (it.hasFocus) portee.launch { etatListe.animateScrollToItem(0) }
    }

    // .hero-card de MrFlix Web : une carte de verre aux grands coins,
    // l'affiche à gauche, le texte et les boutons à droite.
    val hauteur = if (tel) 200.dp else 300.dp
    val forme = RoundedCornerShape(RAYON_GRAND)
    Row(
        modifier = Modifier
            .padding(horizontal = margeEcran())
            .widthIn(max = 780.dp)
            .fillMaxWidth()
            .height(hauteur)
            .clip(forme)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, LIGNE, forme)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(2f / 3f)
                .background(CARTE)
        ) {
            if (affiche != null) {
                Image(
                    url = affiche,
                    description = titre,
                    largeurPx = 300, hauteurPx = 450,
                    echelleContenu = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = if (tel) 16.dp else 36.dp, vertical = 16.dp)
        ) {
            Text(meta, color = DISCRET, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(
                text = titre,
                color = Color.White,
                fontSize = if (tel) 19.sp else 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = if (tel) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (tel) 23.sp else 34.sp
            )
            val resume = fiche?.resume.orEmpty()
            if (!tel && resume.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    resume,
                    color = TEXTE,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(if (tel) 12.dp else 20.dp))
            Row(remonter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (film.estSerie) {
                    BoutonBlanc("▶  Épisodes") { ouvrirFiche(film) }
                } else {
                    val debut = Reglages.position(film.url)
                    BoutonBlanc(if (debut > 30) "▶  Reprendre" else "▶  Lecture") {
                        contexte.startActivity(intentSimple(contexte, film, film.url, debut))
                    }
                    if (!tel) BoutonPlein("Plus d'infos") { ouvrirFiche(film) }
                }
            }
        }
    }
}

/** .btn-play : fond blanc, texte noir. Le bouton principal du bandeau. */
@Composable
private fun BoutonBlanc(titre: String, surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }
    val echelle by animateFloatAsState(
        if (enFocus) 1.06f else 1f, animationSpec = RESSORT_FOCUS, label = "echelle"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(PILULE)
            .background(Color.White)
            .border(if (enFocus) 3.dp else 0.dp, if (enFocus) ACCENT else Color.Transparent, PILULE)
            .padding(horizontal = 28.dp, vertical = 12.dp)
    ) {
        Text(titre, color = Color(0xFF111111), fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/* ---------------------------------------------------------------- Sections */

@Composable
private fun Grille(rangees: List<Rangee>, prefixe: String, avant: List<Rangee> = emptyList()) {
    if (rangees.isEmpty() && avant.isEmpty()) {
        Message("Rien ici avec les langues cochées.")
        return
    }
    // Cette liste ne défile JAMAIS derrière une barre flottante : ContenuTuile
    // pose juste avant un entête fixe (titre, total, bouton retour).
    ColonneRangees(haut = 8.dp) {
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = insetBas(60.dp)),
            verticalArrangement = Arrangement.spacedBy(espaceRangees())
        ) {
            // « Populaire » et « Ajouté récemment » : déjà nommées, pas de
            // préfixe de plateforme à leur ajouter.
            items(avant, key = { "avant-${it.titre}" }, contentType = { "rangee-films" }) { r ->
                RangeeFilms(r)
            }
            items(rangees, key = { it.titre }, contentType = { "rangee-films" }) { r ->
                RangeeFilms(Rangee("$prefixe · ${r.titre}", r.films))
            }
        }
    }
}

@Composable
private fun GrilleChaines(rangees: List<RangeeChaines>) {
    if (rangees.isEmpty()) {
        Message("Aucune chaîne avec les langues cochées.")
        return
    }
    ColonneRangees(haut = 8.dp) {
        LazyColumn(
            contentPadding = PaddingValues(top = 8.dp, bottom = insetBas(60.dp)),
            verticalArrangement = Arrangement.spacedBy(espaceRangees())
        ) {
            items(
                rangees,
                key = { it.titre },
                contentType = { "rangee-chaines" }
            ) { r -> RangeeChainesVue(Rangee2("TV · ${r.titre}", r.chaines)) }
        }
    }
}

@Composable
private fun ListeReplay(
    bouquets: List<GroupeBouquet>,
    surProgrammes: (Chaine) -> Unit
) {
    // Même parcours que renderReplay() sous Windows : les bouquets d'abord,
    // puis les chaînes rejouables du bouquet, en grille d'affiches.
    var choisi by remember { mutableStateOf<String?>(null) }

    // Seules les chaînes que le serveur annonce comme rejouables.
    val avecArchive = remember(bouquets) {
        bouquets.mapNotNull { g ->
            val rangees = g.rangees
                .map { RangeeChaines(it.titre, it.chaines.filter { c -> c.archive }) }
                .filter { it.chaines.isNotEmpty() }
            if (rangees.isEmpty()) null else GroupeBouquet(g.id, rangees)
        }
    }

    BackHandler(enabled = choisi != null) { choisi = null }

    if (avecArchive.isEmpty()) {
        Message("Aucune chaîne archivée par ton fournisseur.")
        return
    }

    if (choisi == null) {
        GrilleTuiles(
            entete = "Replay",
            total = avecArchive.sumOf { g -> g.rangees.sumOf { it.chaines.size } },
            tuiles = avecArchive.map {
                Tuile(it.id, Plateformes.infoBouquet(it.id), it.rangees.sumOf { r -> r.chaines.size })
            },
            surChoix = { choisi = it }
        )
        return
    }

    val rangees = avecArchive.find { it.id == choisi }?.rangees ?: emptyList()
    val info = Plateformes.infoBouquet(choisi!!)
    val total = rangees.sumOf { it.chaines.size }

    Column(Modifier.fillMaxSize()) {
        // .cat-head : retour, titre du bouquet, nombre de chaînes rejouables.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = margeEcran(), end = margeEcran(), top = insetHaut(), bottom = 14.dp
            )
        ) {
            BoutonPlein("← Bouquets") { choisi = null }
            Spacer(Modifier.width(16.dp))
            Text(info.libelle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Text("$total chaînes rejouables", color = DISCRET, fontSize = 14.sp)
        }

        ColonneRangees(haut = 0.dp) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = insetBas(60.dp)),
                verticalArrangement = Arrangement.spacedBy(espaceRangees())
            ) {
                items(
                    rangees,
                    key = { it.titre },
                    contentType = { "rangee-replay" }
                ) { r -> RangeeReplay(r, surProgrammes) }
            }
        }
    }
}

/** Une rangée de chaînes rejouables : badge « N j » et ouverture des programmes. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RangeeReplay(rangee: RangeeChaines, surProgrammes: (Chaine) -> Unit) {
    val premiere = remember { FocusRequester() }

    Column {
        TitreRangee(rangee.titre, nombre = rangee.chaines.size)
        DefilementRangee {
            LazyRow(
                modifier = Modifier.focusRestorer(premiere),
                contentPadding = PaddingValues(horizontal = margeEcran()),
                horizontalArrangement = Arrangement.spacedBy(ECART_CARTES)
            ) {
                itemsIndexed(rangee.chaines, key = { _, c -> c.id }) { rang, chaine ->
                    CarteChaine(
                        chaine = chaine,
                        modifier = if (rang == 0) Modifier.focusRequester(premiere) else Modifier,
                        badge = chaine.joursArchive.takeIf { it > 0 }?.let { "$it j" },
                        surClic = { surProgrammes(chaine) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(texte: String) {
    Text(texte, color = DISCRET, fontSize = 17.sp, modifier = Modifier.padding(margeEcran()))
}

/* ----------------------------------------------------------------- Options */

/** L'enveloppe commune des écrans d'options : carte centrée sur fond assombri. */
@Composable
private fun CadreModal(
    titre: String,
    sousTitre: String,
    contenu: @Composable ColumnScope.() -> Unit
) {
    val tel = LocalForme.current == Forme.TELEPHONE
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (tel) FOND else Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier =
                // Téléphone : plein écran, fond jusque sous les barres système
                // mais contenu à l'intérieur. Ailleurs : carte centrée
                // (.modal-card { width: min(860px, 96vw); max-height: 88vh }).
                if (tel) Modifier
                    .fillMaxSize()
                    .background(SURFACE)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                else Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, LIGNE, RoundedCornerShape(8.dp))
                    .background(SURFACE)
        ) {
            Column(
                Modifier.padding(
                    start = 24.dp, end = 24.dp,
                    top = if (tel) 22.dp else 18.dp, bottom = 16.dp
                )
            ) {
                Text(titre, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(sousTitre, color = DISCRET, fontSize = 13.sp)
            }
            Separateur()
            contenu()
        }
    }
}

/**
 * Le menu des options, réduit à l'essentiel.
 *
 * Un seul écran : la gestion des catégories, puis une rangée courte pour le
 * reste (affiches TMDB, profil, rechargement, accès). Tout ce qui touche aux
 * affiches TMDB — l'interrupteur, les compteurs, l'échange avec le PC — est
 * derrière le bouton « Affiches (TMDB) », dans un sous-écran : ce sont des
 * outils d'entretien, pas des réglages du quotidien.
 */
@Composable
private fun Options(
    catalogue: CatalogueComplet?,
    surRetour: () -> Unit,
    surChangerProfil: () -> Unit,
    surRecharger: () -> Unit,
    surReappairer: () -> Unit
) {
    var vueTmdb by remember { mutableStateOf(false) }
    // Dans le sous-écran, la touche retour revient au menu plutôt que de tout
    // fermer. Ce BackHandler est composé après celui du parent, donc prioritaire.
    BackHandler(enabled = vueTmdb) { vueTmdb = false }

    if (vueTmdb) {
        CadreModal(
            "Affiches (TMDB)",
            "Récupération et entretien des affiches. Rien d'indispensable au quotidien."
        ) {
            PanneauTmdb(catalogue) { vueTmdb = false }
        }
    } else {
        CadreModal("Options", "Choisis les catégories à afficher.") {
            PanneauCategories(
                catalogue = catalogue,
                surAppliquer = surRetour,
                surChangerProfil = surChangerProfil,
                surRecharger = surRecharger,
                surReappairer = surReappairer,
                surOuvrirTmdb = { vueTmdb = true }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PanneauCategories(
    catalogue: CatalogueComplet?,
    surAppliquer: () -> Unit,
    surChangerProfil: () -> Unit,
    surRecharger: () -> Unit,
    surReappairer: () -> Unit,
    surOuvrirTmdb: () -> Unit
) {
    val toutes = catalogue?.categories ?: emptyList()
    var masquees by remember { mutableStateOf(Reglages.masquees) }
    var filtre by remember { mutableStateOf("") }
    // Repliée par défaut : sinon la touche bas plonge droit dans plusieurs
    // centaines de catégories, dont on ne ressort qu'en les parcourant toutes.
    var listeOuverte by remember { mutableStateOf(false) }

    val visibles = remember(filtre, toutes) {
        if (filtre.isBlank()) toutes
        else toutes.filter { sansAccents(it.nom).contains(sansAccents(filtre)) }
    }

    Column(Modifier.padding(horizontal = 24.dp).weight(1f)) {

        TitrePanneau("Catégories")

        val actives = toutes.count { !masquees.contains(it.cle) }

        // Rangée DÉFILABLE : à trois boutons, une Row ordinaire peut écraser
        // le dernier sur un écran étroit. Sans catalogue chargé, `toutes` est
        // vide : appuyer alors sur ces boutons écrirait un filtre vide et
        // effacerait tous les masquages — donc on n'agit que si on a la liste.
        if (toutes.isEmpty()) {
            Text(
                "Catalogue pas encore chargé — reviens ici une fois l'accueil affiché.",
                color = DISCRET, fontSize = 13.sp
            )
        } else FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoutonPlein("Français seulement") {
                Reglages.garderFrancaisSeulement(toutes)
                masquees = Reglages.masquees
            }
            BoutonPlein("Tout cocher") {
                Reglages.toutCocher()
                masquees = Reglages.masquees
            }
            BoutonPlein("Tout décocher") {
                Reglages.toutDecocher(toutes.map { it.cle })
                masquees = Reglages.masquees
            }
        }
        Spacer(Modifier.height(12.dp))

        BoutonPlein(
            if (listeOuverte) "▴  Masquer la liste" else "▾  Modifier les catégories"
        ) {
            listeOuverte = !listeOuverte
            if (!listeOuverte) filtre = ""
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "$actives affichée(s) sur ${toutes.size}",
            color = DISCRET,
            fontSize = 13.sp
        )
        if (listeOuverte) {
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = filtre,
                onValueChange = { filtre = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(ACCENT),
                decorationBox = { interieur ->
                    Box {
                        if (filtre.isEmpty()) {
                            Text("Filtrer…", color = DISCRET, fontSize = 14.sp)
                        }
                        interieur()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(CARTE)
                    .border(1.dp, LIGNE, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        Spacer(Modifier.height(12.dp))

        if (toutes.isEmpty()) {
            // Message déjà affiché plus haut à la place des boutons d'action.
        } else if (!listeOuverte) {
            Text(
                "La liste est masquée. Ouvre-la pour cocher ou décocher " +
                        "des catégories une par une.",
                color = DISCRET,
                fontSize = 13.sp
            )
        } else {
            // .group-list : encadré, coins arrondis, léger rembourrage
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, LIGNE, RoundedCornerShape(6.dp))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                listOf("films" to "Films", "series" to "Séries", "tv" to "Chaînes TV")
                    .forEach { (type, libelle) ->
                        val lot = visibles.filter { it.type == type }
                        if (lot.isNotEmpty()) {
                            item(key = "entete-$type") {
                                Text(
                                    text = libelle.uppercase() + "  (${lot.size})",
                                    color = ACCENT,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    modifier = Modifier.padding(
                                        start = 10.dp, top = 14.dp, bottom = 6.dp
                                    )
                                )
                            }
                            items(lot, key = { it.cle }) { c ->
                                LigneCategorie(
                                    categorie = c,
                                    coche = !masquees.contains(c.cle)
                                ) {
                                    Reglages.basculeCategorie(c.cle)
                                    masquees = Reglages.masquees
                                }
                            }
                        }
                    }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    Separateur()

    // Le reste, en bas, toujours visible : les boutons secondaires puis la sortie.
    Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoutonPlein("Affiches (TMDB)  ›", surClic = surOuvrirTmdb)
            BoutonPlein("Profil", surClic = surChangerProfil)
            BoutonPlein("Recharger du serveur", surClic = surRecharger)
            // Réappairer sans désinstaller : pour changer d'abonnement ou
            // reconfigurer un boîtier.
            BoutonPlein("Changer les accès") {
                Reglages.oublieAcces()
                surReappairer()
            }
        }
        Spacer(Modifier.height(12.dp))
        BoutonPlein("Appliquer et revenir", surClic = surAppliquer)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PanneauTmdb(
    catalogue: CatalogueComplet?,
    surRetour: () -> Unit
) {
    var tmdb by remember { mutableStateOf(Reglages.affichesTmdb) }

    // Échange avec le PC : le boîtier fournit les clés déjà calculées, le PC
    // ne fait que le réseau. Voir le script mrflix-affiches.js.
    val contexteEchange = LocalContext.current
    val porteeEchange = rememberCoroutineScope()
    var messageEchange by remember { mutableStateOf<String?>(null) }
    var echangeEnCours by remember { mutableStateOf(false) }

    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .weight(1f)
            .verticalScroll(rememberScrollState())
    ) {
        TitrePanneau("Affiches")

        BoutonPlein(
            if (tmdb) "Affiches TMDB : activées" else "Affiches TMDB : désactivées"
        ) {
            Reglages.affichesTmdb = !tmdb
            tmdb = Reglages.affichesTmdb
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Affiches connues : ${Tmdb.taille}  —  sans image : ${Tmdb.sansImage}" +
                    "  —  titres introuvables : ${Tmdb.introuvables}",
            color = DISCRET,
            fontSize = 13.sp
        )

        // La mesure qui tranche : combien de cartes n'ont AUCUNE image à
        // afficher, ni TMDB ni fournisseur. Hors du fil principal — enCache()
        // nettoie le titre, soit une dizaine d'expressions régulières par
        // appel, sur des dizaines de milliers de titres.
        var sansAffiche by remember(catalogue) {
            mutableStateOf<Pair<Int, Int>?>(null)
        }
        LaunchedEffect(catalogue) {
            val c = catalogue ?: return@LaunchedEffect
            sansAffiche = withContext(Dispatchers.Default) {
                val tous = (c.films + c.series).flatMap { it.films }
                    .distinctBy { it.id + "|" + it.estSerie }
                val vides = tous.count { f ->
                    (Tmdb.enCache(f.nom, f.estSerie)?.fiche?.affiche
                        ?: f.affiche) == null
                }
                vides to tous.size
            }
        }
        Text(
            text = sansAffiche?.let { (vides, total) ->
                "Cartes sans aucune image : $vides sur $total"
            } ?: "Cartes sans aucune image : calcul en cours…",
            color = DISCRET,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(16.dp))
        TitrePanneau("Échange avec le PC")

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoutonPlein("Exporter les titres") {
                val dossier = Tmdb.dossierEchange(contexteEchange)
                when {
                    echangeEnCours -> Unit
                    catalogue == null || dossier == null ->
                        messageEchange = "Catalogue ou stockage indisponible"
                    else -> {
                        echangeEnCours = true
                        messageEchange = "Export en cours…"
                        porteeEchange.launch {
                            val n = try {
                                Tmdb.exporteTitres(
                                    File(dossier, "titres.json"),
                                    (catalogue.films + catalogue.series)
                                        .flatMap { r -> r.films }
                                        .map { Tmdb.ATrouver(it.nom, it.estSerie) }
                                ) { fait, total ->
                                    messageEchange = "Export : $fait / $total titres"
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                -1
                            }
                            echangeEnCours = false
                            messageEchange = if (n < 0) "Export impossible"
                            else "$n titres écrits dans titres.json"
                        }
                    }
                }
            }
            BoutonPlein("Exporter les introuvables") {
                val dossier = Tmdb.dossierEchange(contexteEchange)
                when {
                    echangeEnCours -> Unit
                    dossier == null -> messageEchange = "Stockage indisponible"
                    else -> {
                        echangeEnCours = true
                        messageEchange = "Export en cours…"
                        porteeEchange.launch {
                            val n = try {
                                Tmdb.exporteIntrouvables(
                                    File(dossier, "introuvables.json"),
                                    catalogue
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                -1
                            }
                            echangeEnCours = false
                            messageEchange = if (n < 0) "Export impossible"
                            else "$n titres écrits dans introuvables.json"
                        }
                    }
                }
            }
            BoutonPlein("Rattacher les introuvables") {
                when {
                    echangeEnCours -> Unit
                    else -> {
                        echangeEnCours = true
                        messageEchange = "Rapprochement en cours…"
                        porteeEchange.launch {
                            val n = try {
                                Tmdb.rattacheIntrouvables()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                -1
                            }
                            echangeEnCours = false
                            messageEchange = if (n < 0) "Rapprochement impossible"
                            else "$n titres rattachés à leur forme correcte"
                        }
                    }
                }
            }
            BoutonPlein("Réparer les titres amputés") {
                when {
                    echangeEnCours -> Unit
                    !Tmdb.actif -> messageEchange = "Clé TMDB absente"
                    else -> {
                        echangeEnCours = true
                        messageEchange = "Réparation en cours…"
                        porteeEchange.launch {
                            val n = try {
                                Tmdb.reessayeReparables { fait, total ->
                                    messageEchange = "Réparation : $fait / $total"
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                -1
                            }
                            echangeEnCours = false
                            messageEchange = if (n < 0) "Réparation impossible"
                            else "$n affiches retrouvées, " +
                                    "${Tmdb.dernieresPannes} échecs réseau"
                        }
                    }
                }
            }
            BoutonPlein("Oublier les introuvables") {
                when {
                    echangeEnCours -> Unit
                    else -> {
                        echangeEnCours = true
                        messageEchange = "Nettoyage en cours…"
                        porteeEchange.launch {
                            val n = try {
                                Tmdb.oublieIntrouvables()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                -1
                            }
                            echangeEnCours = false
                            messageEchange = if (n < 0) "Nettoyage impossible"
                            else "$n verdicts effacés, les titres seront recherchés à nouveau"
                        }
                    }
                }
            }
            BoutonPlein("Importer les affiches") {
                val dossier = Tmdb.dossierEchange(contexteEchange)
                when {
                    echangeEnCours -> Unit
                    dossier == null -> messageEchange = "Stockage indisponible"
                    else -> {
                        echangeEnCours = true
                        messageEchange = "Import en cours…"
                        porteeEchange.launch {
                            val n = try {
                                Tmdb.importeAffiches(File(dossier, "tmdb.json"))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                -1
                            }
                            echangeEnCours = false
                            messageEchange =
                                if (n < 0) "Aucun tmdb.json lisible dans le dossier"
                                else "$n affiches importées"
                        }
                    }
                }
            }
        }
        messageEchange?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DISCRET, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
    }

    Separateur()

    Column(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
        BoutonPlein("‹  Retour aux options", surClic = surRetour)
    }
}

/** .panel h3 : petites majuscules espacées, en gris. */
@Composable
private fun TitrePanneau(texte: String) {
    Text(
        text = texte.uppercase(),
        color = DISCRET,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
    )
}

/** Le trait de séparation entre panneaux : 1 px, couleur --line. */
@Composable
private fun Separateur() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LIGNE))
}

@Composable
private fun LigneCategorie(categorie: CategorieBrute, coche: Boolean, surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enFocus) SURFACE else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (coche) ACCENT else CARTE)
                .border(1.dp, LIGNE, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (coche) Text("✓", color = Color.White, fontSize = 13.sp)
        }

        Spacer(Modifier.width(14.dp))
        Text(
            text = categorie.nom,
            color = if (coche) Color.White else DISCRET,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = Catalogue.libelleLangue(categorie.langue).uppercase(),
            color = DISCRET,
            fontSize = 10.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(CARTE)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        Spacer(Modifier.width(14.dp))
        Text(
            text = categorie.nombre.toString(),
            color = DISCRET,
            fontSize = 13.sp
        )
    }
}

/* --------------------------------------------------------------- Recherche */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Recherche(catalogue: CatalogueComplet?, surRetour: () -> Unit) {
    var saisie by remember { mutableStateOf("") }
    val champ = remember { FocusRequester() }

    // Le clavier virtuel du téléviseur s'ouvre quand le champ prend le focus.
    LaunchedEffect(Unit) { champ.requestFocus() }

    // Le filtrage porte désormais sur des dizaines de milliers de titres :
    // le faire sur le fil principal à chaque lettre bloquerait la saisie.
    // On attend une courte pause, puis on filtre en arrière-plan.
    // Séparés dès la recherche, comme searchGrouped() dans catalog.js :
    // mélanger films et séries oblige à trier à l'œil dans les résultats.
    var films by remember { mutableStateOf<List<Film>>(emptyList()) }
    var series by remember { mutableStateOf<List<Film>>(emptyList()) }
    var chaines by remember { mutableStateOf<List<Chaine>>(emptyList()) }
    // Les épisodes que le fournisseur a rangés en VOD, un par ligne, ramenés
    // sous un seul titre. clusterLoose() de catalog.js.
    var groupes by remember { mutableStateOf<List<IndexRecherche.GroupeEpisodes>>(emptyList()) }

    var enCours by remember { mutableStateOf(false) }
    // Le texte tel qu'il était au moment où l'utilisateur a appuyé sur la
    // loupe. Sert à savoir si cette recherche-ci est une validation explicite.
    var saisieValidee by remember { mutableStateOf<String?>(null) }
    var valide by remember { mutableIntStateOf(0) }
    val clavier = LocalSoftwareKeyboardController.current

    LaunchedEffect(saisie, catalogue, valide) {
        if (saisie.length < 2 || catalogue == null) {
            films = emptyList()
            series = emptyList()
            chaines = emptyList()
            groupes = emptyList()
            enCours = false
            return@LaunchedEffect
        }
        // Laisse le temps de taper la lettre suivante sans relancer la
        // recherche. Mais une validation par la loupe part immédiatement :
        // l'utilisateur a fini de taper, il n'y a plus rien à attendre.
        if (saisieValidee != saisie) delay(200)
        enCours = true
        // L'index est déjà prêt en temps normal ; cet appel ne fait rien.
        IndexRecherche.prepare(catalogue)
        // Les films passent par le regroupement : un paquet d'au moins trois
        // épisodes du même titre devient un groupe, le reste sort tel quel.
        val partage = IndexRecherche.filmsGroupes(saisie, Config.RESULTATS_MAX)
        groupes = partage.first
        films = partage.second
        series = IndexRecherche.titres(saisie, Config.RESULTATS_MAX, estSerie = true)
        chaines = IndexRecherche.chaines(saisie, Config.RESULTATS_MAX)
        enCours = false
    }

    val surTelephone = LocalForme.current == Forme.TELEPHONE

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = margeEcran(), vertical = if (surTelephone) 14.dp else 28.dp)
    ) {

        // Au doigt, un retour visible : la télécommande a sa touche, pas l'écran.
        if (surTelephone) {
            BoutonPlein("‹  Retour", surClic = surRetour)
            Spacer(Modifier.height(12.dp))
        }

        BasicTextField(
            value = saisie,
            onValueChange = { saisie = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
            cursorBrush = SolidColor(ACCENT),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            // Sans ceci, la loupe du clavier est affichée mais ne fait rien :
            // il fallait sortir du champ pour voir le résultat.
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    saisieValidee = saisie
                    valide += 1
                    clavier?.hide()
                }
            ),
            modifier = Modifier
                .focusRequester(champ)
                .then(if (surTelephone) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.6f))
                .clip(RoundedCornerShape(4.dp))
                .background(SURFACE)
                .border(1.dp, LIGNE, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            decorationBox = { interieur ->
                if (saisie.isEmpty()) {
                    Text(
                        if (surTelephone) "Rechercher…"
                        else "Rechercher un film, une série, une chaîne…",
                        color = DISCRET,
                        fontSize = if (surTelephone) 16.sp else 18.sp,
                        maxLines = 1
                    )
                }
                interieur()
            }
        )

        Spacer(Modifier.height(10.dp))

        // Diagnostic : dit ce que la recherche a réellement sous la main.
        Text(
            if (enCours) "Recherche…" else "${IndexRecherche.nombreTitres} titres en mémoire",
            color = DISCRET, fontSize = 13.sp
        )

        Spacer(Modifier.height(14.dp))

        when {
            saisie.length < 2 -> Text(
                "Tape au moins deux lettres.",
                color = DISCRET, fontSize = 15.sp
            )

            films.isEmpty() && series.isEmpty() && chaines.isEmpty() &&
                    groupes.isEmpty() && !enCours -> Text(
                "Aucun résultat pour « $saisie ».",
                color = DISCRET, fontSize = 16.sp
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(espaceRangees())) {
                // Séries d'abord : c'est l'ordre de searchGrouped() sous Windows.
                if (series.isNotEmpty()) {
                    item {
                        SectionResultats("Séries", series.size) {
                            items(series, key = { it.id + it.nom }) { CarteFilm(it) }
                        }
                    }
                }
                // Juste après les séries : ce sont les groupes qu'on cherche
                // quand on tape le nom d'un feuilleton, pas ses 200 épisodes.
                if (groupes.isNotEmpty()) {
                    item {
                        SectionResultats("Épisodes groupés", groupes.size) {
                            items(groupes, key = { it.titre }) { CarteGroupe(it) }
                        }
                    }
                }
                if (films.isNotEmpty()) {
                    item {
                        SectionResultats("Films", films.size) {
                            items(films, key = { it.id + it.nom }) { CarteFilm(it) }
                        }
                    }
                }
                if (chaines.isNotEmpty()) {
                    item {
                        SectionResultats("Chaînes TV", chaines.size) {
                            items(chaines, key = { it.id }) { CarteChaine(it) }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Carte d'un groupe d'épisodes. Même dessin que CarteFilm, avec un badge
 * indiquant combien d'épisodes il rassemble.
 *
 * L'affiche est cherchée sur le TITRE DE BASE, pas sur le nom d'un épisode :
 * « Dragon Ball Z » trouve une affiche, « Dragon Ball Z 042 VF » non.
 */
@Composable
private fun CarteGroupe(groupe: IndexRecherche.GroupeEpisodes) {
    val ouvrirGroupe = LocalOuvrirGroupe.current
    var enFocus by remember { mutableStateOf(false) }
    val echelle = animateFloatAsState(if (enFocus) 1.07f else 1f, label = "echelle")

    val avecTmdb = LocalAffichesTmdb.current
    // Une saga se cherche comme un FILM, un feuilleton comme une série.
    // Interroger TMDB dans la mauvaise catégorie ne renvoyait rien pour les
    // sagas : « Rocky » n'existe pas en tant que série télévisée.
    val commeSerie = !groupe.estSaga
    // Consulté sans coroutine, comme pour les affiches de films : une fiche
    // déjà connue s'affiche dès la première image.
    val connu = remember(groupe.titre, avecTmdb) {
        if (avecTmdb) Tmdb.enCache(groupe.titre, commeSerie) else null
    }
    var fiche by remember(groupe.titre, avecTmdb) { mutableStateOf(connu?.fiche) }

    LaunchedEffect(groupe.titre, avecTmdb) {
        if (avecTmdb && Tmdb.actif && connu == null) {
            delay(200)
            try {
                fiche = Tmdb.fiche(groupe.titre, commeSerie)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fiche = null
            }
        }
    }

    val affiche = fiche?.affiche ?: groupe.episodes.firstOrNull { it.affiche != null }?.affiche
    val titre = if (Config.CORRIGER_TITRES) (fiche?.titreCorrige ?: groupe.libelle) else groupe.libelle

    Column(
        modifier = Modifier
            .width(carteL())
            .graphicsLayer {
                scaleX = echelle.value
                scaleY = echelle.value
            }
            .onFocusChanged { enFocus = it.isFocused }
            .clickable { ouvrirGroupe(groupe) }
    ) {
        Box(
            modifier = Modifier
                .width(carteL())
                .height(carteH())
                .clip(RoundedCornerShape(RAYON))
                .background(CARTE)
                .border(
                    width = if (enFocus) 3.dp else 0.dp,
                    color = if (enFocus) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(RAYON)
                )
        ) {
            if (affiche != null) {
                Image(
                    url = affiche,
                    description = titre,
                    largeurPx = 154, hauteurPx = 231,
                    echelleContenu = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // .ep-badge : dit d'un coup d'œil que la carte cache un paquet.
            // On compte les opus, pas les fichiers : les déclinaisons de
            // qualité annonceraient sinon trois fois trop d'épisodes.
            Text(
                text = "${groupe.opus.size} " + if (groupe.estSaga) "films" else "ép.",
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = titre,
            color = Color.White,
            fontSize = 12.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Les épisodes d'un groupe, dans l'ordre déduit de leur nom.
 *
 * Un groupe n'est pas une série au sens d'Xtream : il n'a pas d'identifiant,
 * donc Xtream.episodes() ne peut rien en faire et la fiche habituelle ne
 * s'applique pas. On liste ce qui a été trouvé, et un clic envoie tout le
 * groupe au lecteur pour que l'enchaînement fonctionne.
 */
@Composable
private fun EcranGroupe(groupe: IndexRecherche.GroupeEpisodes, surRetour: () -> Unit) {
    val contexte = LocalContext.current
    val premier = remember { FocusRequester() }
    LaunchedEffect(groupe.titre) { premier.requestFocus() }

    // Rang de l'opus dont on attend le choix de qualité. Null : rien d'ouvert.
    var choixPour by remember(groupe.titre) { mutableStateOf<Int?>(null) }
    // Déclaré APRÈS celui de l'écran : c'est le dernier enregistré qui reçoit
    // la touche retour, donc fermer le choix ne quitte pas le groupe.
    BackHandler(enabled = choixPour != null) { choixPour = null }

    val lance = { rang: Int, film: Film ->
        contexte.startActivity(
            intentGroupe(contexte, groupe, rang, Reglages.position(film.url), film)
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = margeEcran(), vertical = 28.dp)) {
            BoutonPlein("← Recherche", surClic = surRetour)
            Spacer(Modifier.height(18.dp))
            Text(groupe.libelle, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${groupe.opus.size} titres trouvés dans le catalogue",
                color = DISCRET,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(18.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(groupe.opus, key = { _, o -> o.nom + o.numero }) { rang, o ->
                    val defaut = o.versions.first()
                    LigneGroupe(
                        numero = if (o.numero > 0) o.numero else rang + 1,
                        nom = o.nom,
                        // Une seule version : la pastille n'apprendrait rien.
                        qualites = if (o.versions.size > 1) o.qualites else emptyList(),
                        avancement = Reglages.position(defaut.url),
                        modifier = if (rang == 0) Modifier.focusRequester(premier) else Modifier
                    ) {
                        // Plusieurs qualités : on demande. Une seule : on lance.
                        if (o.versions.size > 1) choixPour = rang else lance(rang, defaut)
                    }
                }
            }
        }

        choixPour?.let { rang ->
            val o = groupe.opus.getOrNull(rang)
            if (o == null) choixPour = null
            else ChoixQualite(
                opus = o,
                surAnnuler = { choixPour = null },
                surChoix = { film ->
                    choixPour = null
                    lance(rang, film)
                }
            )
        }
    }
}

/**
 * Choix de la qualité, quand le fournisseur propose le même titre plusieurs
 * fois. Même présentation que les options : carte centrée sur fond assombri.
 */
@Composable
private fun ChoixQualite(
    opus: IndexRecherche.Opus,
    surAnnuler: () -> Unit,
    surChoix: (Film) -> Unit
) {
    val premier = remember { FocusRequester() }
    LaunchedEffect(opus.nom) { premier.requestFocus() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SURFACE)
                .border(1.dp, LIGNE, RoundedCornerShape(8.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(opus.nom, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Quelle qualité ?", color = DISCRET, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))

            opus.versions.forEachIndexed { i, film ->
                val avancement = Reglages.position(film.url)
                LigneQualite(
                    libelle = IndexRecherche.qualiteLisible(film.nom),
                    detail = if (avancement > 30) "vu jusqu'à ${duree(avancement)}" else null,
                    modifier = if (i == 0) Modifier.focusRequester(premier) else Modifier
                ) { surChoix(film) }
            }

            Spacer(Modifier.height(4.dp))
            BoutonPlein("Annuler", surClic = surAnnuler)
        }
    }
}

@Composable
private fun LigneQualite(
    libelle: String,
    detail: String?,
    modifier: Modifier = Modifier,
    surClic: () -> Unit
) {
    var enFocus by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enFocus) Color.White else CARTE)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(
            text = libelle,
            color = if (enFocus) Color.Black else Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (detail != null) Text(
            text = detail,
            color = if (enFocus) Color.DarkGray else DISCRET,
            fontSize = 13.sp
        )
    }
}

/** Une ligne d'épisode de groupe : pas de vignette, le fournisseur n'en a pas. */
@Composable
private fun LigneGroupe(
    numero: Int,
    nom: String,
    qualites: List<String>,
    avancement: Int,
    modifier: Modifier = Modifier,
    surClic: () -> Unit
) {
    var enFocus by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .width(760.dp)
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enFocus) SURFACE else Color.Transparent)
            .border(
                width = if (enFocus) 2.dp else 1.dp,
                color = if (enFocus) Color.White else LIGNE,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = numero.toString(),
            color = if (enFocus) Color.White else DISCRET,
            fontSize = 22.sp,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center
        )
        Column(Modifier.weight(1f)) {
            Text(nom, color = Color.White, fontSize = 15.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            if (avancement > 30) {
                Spacer(Modifier.height(3.dp))
                Text("vu jusqu'à ${duree(avancement)}", color = DISCRET, fontSize = 13.sp)
            }
        }
        // Les qualités disponibles, annoncées avant le clic : sans elles,
        // rien ne laisse deviner qu'un choix va s'ouvrir.
        qualites.forEach { q ->
            Text(
                text = q,
                color = DISCRET,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(CARTE)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * Tout le groupe envoyé au lecteur, positionné sur un épisode.
 *
 * Même forme que intentSaison : c'est la liste complète qui permet
 * l'enchaînement, media3 grisant son bouton « suivant » dès qu'il n'y a qu'un
 * seul élément.
 */
private fun intentGroupe(
    contexte: Context,
    groupe: IndexRecherche.GroupeEpisodes,
    rang: Int,
    reprise: Int,
    /** La version retenue pour l'opus cliqué. Les autres restent au meilleur. */
    choisi: Film? = null
): Intent {
    val films = groupe.episodes.toMutableList()
    if (choisi != null && rang in films.indices) films[rang] = choisi

    return Intent(contexte, LecteurActivity::class.java)
        .putStringArrayListExtra("urls", ArrayList(films.map { it.url }))
        .putStringArrayListExtra(
            "titres",
            ArrayList(films.mapIndexed { i, f ->
                val n = IndexRecherche.numeroEpisode(f.nom)
                "${if (n > 0) n else i + 1}. ${f.nom}"
            })
        )
        .putExtra("index", rang)
        .putExtra("titre", groupe.libelle)
        .putExtra("reprise", reprise)
        // Pas de clé de reprise : un groupe n'est pas un titre du catalogue, il
        // n'a rien à quoi rattacher une position. Les positions par épisode, elles,
        // continuent d'être enregistrées par URL.
        .putExtra("origine", "")
}

/** Une section de résultats : un titre, un compte, une rangée d'affiches. */
@Composable
private fun SectionResultats(
    titre: String,
    nombre: Int,
    contenu: LazyListScope.() -> Unit
) {
    Column {
        Text(
            "$titre ($nombre)",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = contenu)
    }
}

/** Comparaison sans accents ni casse, comme la recherche de l'application Windows. */
private val DIACRITIQUES = Regex("\\p{InCombiningDiacriticalMarks}+")

private fun sansAccents(texte: String): String =
    java.text.Normalizer.normalize(texte, java.text.Normalizer.Form.NFD)
        .replace(DIACRITIQUES, "")
        .lowercase()

/* ------------------------------------------------------------- Démarrage */

/** Durée totale de l'intro. Le plancher de l'écran de démarrage s'y aligne. */
private const val INTRO_MS = 4400

/** Combien de rubans verticaux composent le mot pendant l'intro. */
private const val RUBANS = 24

/**
 * Le premier écran d'un boîtier neuf : le QR code d'appairage.
 *
 * Le boîtier ouvre un serveur sur le réseau local et affiche son adresse. Le
 * téléphone, sur le même wifi, saisit les accès du fournisseur. Rien ne sort
 * du réseau, aucun hébergeur n'est nécessaire.
 *
 * L'adresse est aussi écrite en clair sous le code : tous les téléviseurs ne
 * sont pas à portée de téléphone, et une adresse se recopie.
 */
@Composable
private fun EcranAppairage(surFini: () -> Unit) {
    // Au doigt (téléphone, tablette), on tient l'appareil en main : impossible
    // de scanner son propre écran. On saisit alors les accès directement.
    // Sur TV, où l'on n'a qu'une télécommande, le QR + serveur local restent.
    if (LocalForme.current.tactile) {
        FormulaireAcces(surFini)
        return
    }

    // L'adresse ne change pas tant que l'écran est là : la calculer une fois
    // évite de reconstruire le QR code à chaque recomposition.
    val adresse = remember { Appairage.adresseLocale() }
    val lien = remember(adresse) { adresse?.let { Appairage.url(it) } }
    val qr = remember(lien) { lien?.let { Appairage.qrCode(it) } }
    var expire by remember { mutableStateOf(false) }

    LaunchedEffect(lien) {
        if (lien == null) return@LaunchedEffect
        val recu = Appairage.attend()
        if (recu == null) {
            // Délai dépassé : on le dit plutôt que de laisser un QR code mort
            // à l'écran, que l'utilisateur scannerait sans comprendre.
            expire = true
            return@LaunchedEffect
        }
        Reglages.enregistreAcces(
            hote = recu.hote,
            utilisateur = recu.utilisateur,
            motDePasse = recu.motDePasse,
            cleTmdb = recu.cleTmdb
        )
        surFini()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FOND)
            .padding(horizontal = margeEcran(), vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Connecter MrFlix", color = Color.White, fontSize = 34.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        when {
            adresse == null -> Text(
                "Aucun réseau détecté. Vérifie que le boîtier est connecté au wifi " +
                        "ou au câble, puis relance l'application.",
                color = DISCRET, fontSize = 16.sp, textAlign = TextAlign.Center
            )

            expire -> Text(
                "Le délai est passé. Quitte et relance l'application pour " +
                        "afficher un nouveau code.",
                color = DISCRET, fontSize = 16.sp, textAlign = TextAlign.Center
            )

            else -> {
                Text(
                    "Scanne ce code avec ton téléphone, connecté au même réseau.",
                    color = DISCRET, fontSize = 16.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                if (qr != null) {
                    androidx.compose.foundation.Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = "Code d'appairage",
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                Spacer(Modifier.height(20.dp))
                // L'adresse en clair sous le code : tous les téléviseurs ne
                // sont pas à portée de téléphone, et une adresse se recopie.
                Text(
                    lien ?: "",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "ou tape cette adresse dans le navigateur du téléphone",
                    color = DISCRET, fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Saisie directe des accès du fournisseur (téléphone, tablette).
 *
 * Même résultat que l'appairage par QR : `Reglages.enregistreAcces` puis
 * `surFini()`. La clé TMDB laissée vide garde celle déjà en place.
 */
@Composable
private fun FormulaireAcces(surFini: () -> Unit) {
    var hote by remember { mutableStateOf("") }
    var utilisateur by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var cleTmdb by remember { mutableStateOf("") }
    var erreur by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FOND)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 460.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Connecter MrFlix", color = Color.White, fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Saisis les accès de ton fournisseur. Ils restent sur l'appareil.",
                color = DISCRET, fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            ChampTexte("Adresse du serveur", hote, { hote = it }, "http://exemple.com:8080")
            ChampTexte("Utilisateur", utilisateur, { utilisateur = it })
            ChampTexte("Mot de passe", motDePasse, { motDePasse = it })
            ChampTexte(
                "Clé TMDB (facultatif)", cleTmdb, { cleTmdb = it },
                "laisser vide pour garder l'actuelle"
            )

            erreur?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            BoutonPlein("Connecter") {
                val h = hote.trim()
                val u = utilisateur.trim()
                val p = motDePasse.trim()
                if (h.isEmpty() || u.isEmpty() || p.isEmpty()) {
                    erreur = "Adresse, utilisateur et mot de passe sont obligatoires."
                } else {
                    Reglages.enregistreAcces(
                        hote = Appairage.normaliseHote(h),
                        utilisateur = u,
                        motDePasse = p,
                        cleTmdb = cleTmdb.trim()
                    )
                    surFini()
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ChampTexte(
    label: String,
    valeur: String,
    surValeur: (String) -> Unit,
    indice: String = ""
) {
    Text(
        label, color = DISCRET, fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp)
    )
    BasicTextField(
        value = valeur,
        onValueChange = surValeur,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(ACCENT),
        decorationBox = { interieur ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CARTE)
                    .border(1.dp, LIGNE, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (valeur.isEmpty() && indice.isNotEmpty()) {
                    Text(indice, color = DISCRET, fontSize = 14.sp)
                }
                interieur()
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * L'écran pendant que le cache des affiches se charge.
 *
 * Intro dans l'esprit de celle de Netflix : le mot « MRFLIX » (police Bebas
 * Neue, condensée) est reconstitué par des rubans rouges verticaux qui
 * montent du bas, décalés de gauche à droite en vague. Un front lumineux les
 * accompagne. Tout est masqué par la forme des lettres, donc seules les
 * lettres se remplissent ; à la fin il ne reste que le logo rouge plein, sur
 * une légère poussée avant.
 *
 * La signature sonore (`delay(1900)` dans Application) tombe au passage du
 * front lumineux.
 */
@Composable
private fun EcranDemarrage() {
    val contexte = LocalContext.current

    val marche = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        marche.animateTo(1f, animationSpec = tween(INTRO_MS, easing = LinearEasing))
    }
    val t = marche.value

    fun lisse(debut: Float, fin: Float): Float {
        val x = ((t - debut) / (fin - debut)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    val front = lisse(0.06f, 0.72f)                                   // balayage lumineux, 0 -> 1
    val eclat = (1f - kotlin.math.abs(t - 0.44f) / 0.40f).coerceIn(0f, 1f)
    val echelle = 0.92f + 0.08f * t                                   // poussée avant continue

    // « MRFLIX » -> chemin (via Bebas Neue), une seule fois. Rendu en Compose
    // pur : `clipPath` sur les lettres, aucun PorterDuffXfermode (qui ne
    // compose pas de façon fiable dans une couche matérielle — d'où le
    // rectangle rouge des premières tentatives).
    val police = remember { ResourcesCompat.getFont(contexte, R.font.bebas_neue) }
    val motPath = remember(police) {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = police
            textSize = 240f
            letterSpacing = 0.055f
        }
        android.graphics.Path().also { p.getTextPath("MRFLIX", 0, 6, 0f, 0f, it) }
    }
    val bornes = remember(motPath) {
        android.graphics.RectF().also { motPath.computeBounds(it, true); it.inset(-16f, -16f) }
    }
    val lettres = remember(motPath) { motPath.asComposePath() }
    val ratio = (bornes.width() / bornes.height()).takeIf { it.isFinite() && it > 0f } ?: 4f

    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier
                .fillMaxWidth(0.5f)
                .aspectRatio(ratio)
                .graphicsLayer { scaleX = echelle; scaleY = echelle }
        ) {
            val k = size.width / bornes.width()
            val w = bornes.width()
            val bas = bornes.bottom + 12f
            val plein = bornes.top - 12f   // position « haut » quand un ruban est plein
            val largeurRuban = w / RUBANS

            withTransform({
                scale(k, k, pivot = Offset.Zero)
                translate(-bornes.left, -bornes.top)
            }) {
                // Tout ce qui suit ne peint QUE dans la forme des lettres.
                clipPath(lettres) {
                    for (i in 0 until RUBANS) {
                        val phase = i.toFloat() / (RUBANS - 1)
                        // Décalage pseudo-aléatoire : scintillement des rubans.
                        val alea = (kotlin.math.sin(i * 12.9898) * 43758.5453)
                            .let { it - kotlin.math.floor(it) }.toFloat()
                        // Vague plus lente : chaque ruban met ~0.24 de l'intro
                        // à monter, et le décalage gauche->droite s'étale sur
                        // plus de la moitié de l'intro. On voit l'assemblage.
                        val debut = 0.05f + 0.52f * phase + 0.06f * alea
                        val p = lisse(debut, debut + 0.22f + 0.08f * alea)
                        if (p <= 0f) continue

                        val x0 = bornes.left + i * largeurRuban
                        val haut = bas + (plein - bas) * p   // monte du bas vers le haut
                        clipRect(x0, haut, x0 + largeurRuban + 0.75f, bas) {
                            drawRect(
                                color = ACCENT,
                                topLeft = Offset(bornes.left - 20f, bornes.top - 20f),
                                size = Size(w + 40f, bornes.height() + 40f)
                            )
                        }
                    }

                    // Front lumineux qui balaie : bande claire centrée sur `fx`.
                    if (eclat > 0.02f) {
                        val fx = bornes.left + w * front
                        val demi = w * 0.17f
                        val clair = Color(0xFFFFD9DC).copy(alpha = 0.55f * eclat)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, clair, Color.Transparent),
                                startX = fx - demi,
                                endX = fx + demi
                            ),
                            topLeft = Offset(bornes.left - 20f, bornes.top - 20f),
                            size = Size(w + 40f, bornes.height() + 40f)
                        )
                    }
                }
            }
        }
    }
}

/* --------------------------------------------------------------- Profils */

/**
 * Choix du profil à l'ouverture, comme sur Windows.
 *
 * Chaque profil garde ses propres catégories cochées. Les identifiants du
 * fournisseur restent communs : un seul abonnement, plusieurs personnes.
 */
@Composable
private fun EcranProfils(surChoix: (Profil) -> Unit) {
    var liste by remember { mutableStateOf(Reglages.profils) }
    var enEdition by remember { mutableStateOf<Profil?>(null) }
    var creation by remember { mutableStateOf(false) }

    // Un profil ouvert en modification : la touche retour ferme l'éditeur
    // plutôt que de quitter l'application.
    BackHandler(enabled = enEdition != null || creation) {
        enEdition = null
        creation = false
    }

    if (enEdition != null || creation) {
        EditeurProfil(
            profil = enEdition,
            surValider = { nom, couleur, image ->
                if (creation) Reglages.ajouterProfil(nom, couleur, image)
                else enEdition?.let { Reglages.renommerProfil(it.id, nom, couleur, image) }
                liste = Reglages.profils
                enEdition = null
                creation = false
            },
            surSupprimer = {
                enEdition?.let { Reglages.supprimerProfil(it.id) }
                liste = Reglages.profils
                enEdition = null
            },
            surAnnuler = { enEdition = null; creation = false }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Qui regarde ?", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        LazyRow(
            // Centrées tant qu'elles tiennent dans la largeur : avec deux ou
            // trois profils, une rangée collée à gauche fait négligé.
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            // La rangée découpe ce qui dépasse d'elle : sans cette marge, la
            // première et la dernière pastille sont rognées, d'autant plus
            // qu'elles grandissent au focus. Le surbalayage du téléviseur
            // ampute déjà quelques pour cent de chaque bord.
            contentPadding = PaddingValues(horizontal = margeEcran(), vertical = 16.dp)
        ) {
            items(liste) { p ->
                Pastille(
                    nom = p.nom,
                    couleur = Color(p.couleur),
                    initiale = Profils.initiale(p),
                    image = p.image,
                    surClic = { surChoix(p) },
                    surLong = { enEdition = p }
                )
            }
            item {
                Pastille(
                    nom = "Nouveau",
                    couleur = CARTE,
                    initiale = "+",
                    surClic = { creation = true },
                    surLong = { creation = true }
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Appui long sur une pastille pour la modifier",
            color = DISCRET,
            fontSize = 13.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Pastille(
    nom: String,
    couleur: Color,
    initiale: String,
    image: String? = null,
    surClic: () -> Unit,
    surLong: () -> Unit
) {
    var enFocus by remember { mutableStateOf(false) }
    val echelle by animateFloatAsState(if (enFocus) 1.12f else 1f, label = "pastille")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // Réserve la place que prend l'agrandissement au focus : 132 dp à
        // 1,12 débordent de 8 dp de chaque côté.
        modifier = Modifier.padding(horizontal = 10.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(132.dp)
                .scale(echelle)
                .onFocusChanged { enFocus = it.isFocused }
                .combinedClickable(onClick = surClic, onLongClick = surLong)
                .clip(CircleShape)
                // Un dégradé plutôt qu'un aplat : la pastille prend du relief,
                // comme les dégradés de profiles.js.
                .background(
                    Brush.linearGradient(
                        listOf(couleur, couleur.copy(alpha = 0.55f))
                    )
                )
                .border(
                    width = if (enFocus) 4.dp else 0.dp,
                    color = if (enFocus) Color.White else Color.Transparent,
                    shape = CircleShape
                )
        ) {
            if (image != null) {
                Image(
                    url = image,
                    description = nom,
                    largeurPx = 264, hauteurPx = 264,
                    echelleContenu = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Text(initiale, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            nom,
            color = if (enFocus) Color.White else DISCRET,
            fontSize = 16.sp,
            maxLines = 1
        )
    }
}

/** Création ou modification d'un profil : nom et couleur. */
@Composable
private fun EditeurProfil(
    profil: Profil?,
    surValider: (String, Long, String?) -> Unit,
    surSupprimer: () -> Unit,
    surAnnuler: () -> Unit
) {
    var nom by remember { mutableStateOf(profil?.nom ?: "") }
    var couleur by remember { mutableStateOf(profil?.couleur ?: Profils.COULEURS[0]) }
    var image by remember { mutableStateOf(profil?.image) }
    var rechercheAvatar by remember { mutableStateOf("") }
    var propositions by remember { mutableStateOf<List<Tmdb.Avatar>>(emptyList()) }
    val champ = remember { FocusRequester() }

    LaunchedEffect(Unit) { champ.requestFocus() }

    // Vide au départ : TMDB renvoie alors les tendances de la semaine, ce qui
    // donne tout de suite des affiches à choisir sans rien taper.
    LaunchedEffect(rechercheAvatar) {
        delay(300)
        propositions = try {
            Tmdb.avatars(rechercheAvatar)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(margeEcran()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                if (profil == null) "Nouveau profil" else "Modifier le profil",
                color = ACCENT,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            // Le clavier virtuel du téléviseur s'ouvre au focus du champ.
            BasicTextField(
                value = nom,
                onValueChange = { nom = it.take(20) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 20.sp),
                cursorBrush = SolidColor(ACCENT),
                modifier = Modifier
                    .focusRequester(champ)
                    .width(520.dp)
                    .clip(RoundedCornerShape(RAYON))
                    .background(CARTE)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text("Photo de profil", color = DISCRET, fontSize = 15.sp)
        }

        item {
            BasicTextField(
                value = rechercheAvatar,
                onValueChange = { rechercheAvatar = it },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(ACCENT),
                decorationBox = { interieur ->
                    Box {
                        if (rechercheAvatar.isEmpty()) {
                            Text("Chercher une affiche…", color = DISCRET, fontSize = 17.sp)
                        }
                        interieur()
                    }
                },
                modifier = Modifier
                    .width(520.dp)
                    .clip(RoundedCornerShape(RAYON))
                    .background(CARTE)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Première vignette : revenir à la pastille colorée.
                item {
                    ChoixAvatar(null, "Aa", choisi = image == null) { image = null }
                }
                items(propositions) { a ->
                    ChoixAvatar(a.image, a.libelle, choisi = a.image == image) { image = a.image }
                }
            }
        }

        item { Text("Couleur", color = DISCRET, fontSize = 15.sp) }

        // Huit par ligne : la palette de profiles.js, dans le même ordre.
        items(Profils.COULEURS.chunked(8)) { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ligne.forEach { c ->
                    CarreCouleur(Color(c), choisie = c == couleur) { couleur = c }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoutonPlein("Enregistrer") { surValider(nom, couleur, image) }
                BoutonPlein("Annuler", surClic = surAnnuler)
                if (profil != null) BoutonPlein("Supprimer", surClic = surSupprimer)
            }
        }
    }
}

@Composable
private fun ChoixAvatar(url: String?, libelle: String, choisi: Boolean, surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }
    val cadre = Modifier
        .size(78.dp)
        .onFocusChanged { enFocus = it.isFocused }
        .clickable(onClick = surClic)
        .clip(CircleShape)
        .background(CARTE)
        .border(
            width = if (enFocus || choisi) 3.dp else 0.dp,
            color = if (enFocus) Color.White else if (choisi) ACCENT else Color.Transparent,
            shape = CircleShape
        )

    if (url == null) {
        Box(cadre, contentAlignment = Alignment.Center) {
            Text(libelle, color = DISCRET, fontSize = 20.sp)
        }
    } else {
        Image(
            url = url,
            description = libelle,
            largeurPx = 156, hauteurPx = 156,
            echelleContenu = ContentScale.Crop,
            modifier = cadre
        )
    }
}

@Composable
private fun CarreCouleur(couleur: Color, choisie: Boolean, surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(RoundedCornerShape(RAYON))
            .background(couleur)
            .border(
                width = if (enFocus || choisie) 3.dp else 0.dp,
                color = if (enFocus) Color.White else if (choisie) ACCENT else Color.Transparent,
                shape = RoundedCornerShape(RAYON)
            )
    )
}

/* -------------------------------------------------------------- Reprendre */

/**
 * Rangée « Reprendre la lecture » : un clic ouvre la FICHE du titre, pas la
 * lecture. C'est là que se trouve le bouton « Reprendre », qui connaît la
 * saison entière et peut donc enchaîner sur l'épisode suivant.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RangeeReprise(titre: String, elements: List<EnCours>) {
    val premiere = remember { FocusRequester() }

    Column {
        TitreRangee(titre)
        DefilementRangee {
            LazyRow(
                modifier = Modifier.focusRestorer(premiere),
                contentPadding = PaddingValues(horizontal = margeEcran()),
                horizontalArrangement = Arrangement.spacedBy(ECART_CARTES)
            ) {
                itemsIndexed(elements, key = { _, e -> e.url }) { rang, e ->
                    // Aucun surClic : CarteFilm ouvre alors la fiche, exactement
                    // comme depuis n'importe quelle autre rangée.
                    CarteFilm(
                        film = e.film,
                        modifier = if (rang == 0) Modifier.focusRequester(premiere) else Modifier,
                        enCours = true
                    )
                }
            }
        }
    }
}

/**
 * Lecture d'un seul fichier : un film, une chaîne, un programme de replay.
 * Le lecteur reçoit une URL et rien d'autre.
 */
private fun intentSimple(
    contexte: Context,
    film: Film,
    url: String,
    reprise: Int
): Intent = Intent(contexte, LecteurActivity::class.java)
    .putExtra("url", url)
    .putExtra("titre", film.nom)
    .putExtra("reprise", reprise)
    .putExtra("origine", Reglages.cleFavori(film))

/**
 * Une saison entière envoyée au lecteur, positionnée sur un épisode.
 *
 * C'est la SEULE forme qui autorise l'enchaînement, et c'est aussi elle qui
 * fait apparaître le bouton ☰ du panneau épisodes : media3 grise son bouton
 * « suivant » dès que la liste ne compte qu'un élément. Le bouton
 * « Reprendre » et la liste des épisodes passent tous les deux par ici.
 */
private fun intentSaison(
    contexte: Context,
    film: Film,
    saison: List<Episode>,
    rang: Int,
    reprise: Int
): Intent = Intent(contexte, LecteurActivity::class.java)
    .putStringArrayListExtra("urls", ArrayList(saison.map { it.url }))
    // Les titres accompagnent les URL : le panneau du lecteur doit pouvoir
    // nommer chaque épisode.
    .putStringArrayListExtra(
        "titres",
        ArrayList(saison.mapIndexed { i, ep ->
            "${ep.numero.takeIf { it > 0 } ?: (i + 1)}. ${ep.titre}"
        })
    )
    .putExtra("index", rang)
    .putExtra("titre", film.nom)
    .putExtra("reprise", reprise)
    .putExtra("origine", Reglages.cleFavori(film))

/**
 * Retrouve l'épisode correspondant à une URL enregistrée.
 *
 * L'URL vient de `Episode.url`, l'égalité directe suffit donc en temps normal.
 * Le repli sur le nom de fichier couvre le fournisseur qui change son hôte ou
 * son jeton entre deux sessions.
 */
private fun episodeDeLUrl(episodes: List<Episode>, url: String): Episode? =
    episodes.firstOrNull { it.url == url }
        ?: episodes.firstOrNull {
            it.url.substringAfterLast('/') == url.substringAfterLast('/')
        }

/** « 12 min », « 1 h 07 » — pour dire où on s'était arrêté. */
private fun duree(secondes: Int): String {
    val heures = secondes / 3600
    val minutes = (secondes % 3600) / 60
    return if (heures > 0) "$heures h ${minutes.toString().padStart(2, '0')}"
    else "$minutes min"
}

/**
 * Titres commencés, retrouvés dans le catalogue depuis leur URL.
 *
 * Même principe que findByUrl() sous Windows : on ne stocke que l'URL, et le
 * titre est retrouvé dans le catalogue courant. Un épisode de série est
 * rattaché à sa série, dont l'URL est vide — on le reconnaît à l'identifiant
 * présent dans l'adresse.
 */
/** Un titre commencé, avec le fichier exact et l'avancement. */
@Immutable
private class EnCours(val film: Film, val url: String, val secondes: Int)

/**
 * Tous les titres du catalogue rangés par clé de favori (« s:1234 »,
 * « f:5678 ») : c'est ce que le lecteur enregistre dans « origine ».
 *
 * À `remember(catalogue)` une seule fois : c'est la partie coûteuse (aplatir
 * et dédoublonner des dizaines de milliers de `Film`). Le rapprochement avec
 * les positions en cours, lui, est instantané.
 */
private fun indexParCleFavori(catalogue: CatalogueComplet): Map<String, Film> =
    ((catalogue.films + catalogue.series).flatMap { it.films } +
            (catalogue.plateformesFilms + catalogue.plateformesSeries).flatMap { it.titres })
        .distinctBy { it.id + "|" + it.nom }
        .associateBy { Reglages.cleFavori(it) }

private fun titresEnCours(parCleFavori: Map<String, Film>, serie: Boolean?): List<EnCours> {
    val reprises = Reglages.enCours().filter { it.origine.isNotBlank() }
    if (reprises.isEmpty()) return emptyList()

    val sortie = mutableListOf<EnCours>()
    for (r in reprises) {
        val film = parCleFavori[r.origine] ?: continue
        if (serie != null && film.estSerie != serie) continue
        // Une série n'apparaît qu'une fois, sur l'épisode le plus récent.
        if (sortie.none { it.film.id == film.id && it.film.estSerie == film.estSerie }) {
            sortie.add(EnCours(film, r.url, r.secondes))
        }
    }
    return sortie
}

/**
 * Titres « déjà vu » de cet onglet, du plus récent au plus ancien.
 *
 * Reglages.vus() donne les clés (« f:… » / « s:… ») dans l'ordre de
 * visionnage ; on retrouve le `Film` correspondant dans les plateformes de
 * l'onglet et on garde cet ordre.
 */
private fun titresVus(groupes: List<GroupePlateforme>): List<Film> {
    val ordre = Reglages.vus()
    if (ordre.isEmpty()) return emptyList()
    val rang = HashMap<String, Int>(ordre.size * 2)
    ordre.forEachIndexed { i, c -> rang[c] = i }

    return groupes.flatMap { it.titres }
        .distinctBy { it.id + "|" + it.nom }
        .mapNotNull { f -> rang[Reglages.cleFavori(f)]?.let { it to f } }
        .sortedBy { it.first }
        .map { it.second }
}

/* --------------------------------------------------------------- Ma liste */

/**
 * Favoris, séparés en séries et en films — comme myListRows() sous Windows,
 * mais avec la distinction de type que tu voulais dans la recherche.
 */
@Composable
private fun MaListe(catalogue: CatalogueComplet) {
    val cles = Reglages.maListe

    val (series, films) = remember(catalogue, cles) {
        val tous = ((catalogue.films + catalogue.series).flatMap { it.films } +
                (catalogue.plateformesFilms + catalogue.plateformesSeries).flatMap { it.titres })
            .distinctBy { it.id + "|" + it.nom }
            .filter { cles.contains(Reglages.cleFavori(it)) }
        tous.filter { it.estSerie } to tous.filter { !it.estSerie }
    }

    if (cles.isEmpty()) {
        Message("Ta liste est vide. Ouvre une fiche et appuie sur « Ma liste » pour l'y ajouter.")
        return
    }
    if (series.isEmpty() && films.isEmpty()) {
        Message("Les titres de ta liste ne sont pas dans le catalogue actuel. " +
                "Vérifie les catégories cochées dans les options.")
        return
    }

    val haut = insetHaut(8.dp)
    ColonneRangees(haut = haut) {
        LazyColumn(
            contentPadding = PaddingValues(top = haut, bottom = insetBas(60.dp)),
            verticalArrangement = Arrangement.spacedBy(espaceRangees())
        ) {
            if (series.isNotEmpty()) {
                item { RangeeFilms(Rangee("Séries (${series.size})", series)) }
            }
            if (films.isNotEmpty()) {
                item { RangeeFilms(Rangee("Films (${films.size})", films)) }
            }
        }
    }
}

/* ------------------------------------------------------------ Plateformes */

/**
 * Onglet Séries ou Films : grille de tuiles, puis contenu de la plateforme.
 * Transposition de renderPlatforms() et renderGenres() dans app.js.
 */
/** Tuile synthétique « Déjà vu », en tête de la grille des plateformes. */
private const val TUILE_VUS = "__vus"

@Composable
private fun ParPlateforme(
    groupes: List<GroupePlateforme>,
    prefixe: String,
    reprises: List<EnCours> = emptyList(),
    vus: List<Film> = emptyList()
) {
    var choisie by remember { mutableStateOf<String?>(null) }

    // Toutes les plateformes réunies, sans doublon (un titre peut être rangé
    // chez deux plateformes). Calculé une fois par catalogue.
    val tousLesTitres = remember(groupes) {
        groupes.flatMap { it.titres }.distinctBy { it.id + "|" + it.nom }
    }

    // Le retour aux tuiles doit précéder le retour au catalogue, sinon la
    // touche de la télécommande sort de l'application depuis une plateforme.
    BackHandler(enabled = choisie != null) { choisie = null }

    if (choisie == null) {
        val tuiles = buildList {
            // « Tous les contenus » en tête : toutes les plateformes réunies.
            if (groupes.size > 1) {
                add(Tuile(TUILE_TOUT, INFO_TOUT, tousLesTitres.size))
            }
            if (vus.isNotEmpty()) {
                add(Tuile(TUILE_VUS, INFO_VUS, vus.size))
            }
            groupes.forEach { add(Tuile(it.id, Plateformes.info(it.id), it.titres.size)) }
        }
        GrilleTuiles(
            entete = prefixe,
            total = groupes.sumOf { it.titres.size },
            tuiles = tuiles,
            reprises = reprises,
            surChoix = { choisie = it }
        )
        return
    }

    val vu = choisie == TUILE_VUS
    val tout = choisie == TUILE_TOUT
    val titres = when {
        vu -> vus
        tout -> tousLesTitres
        else -> groupes.find { it.id == choisie }?.titres ?: emptyList()
    }
    val libelle = when {
        vu -> "Déjà vu"
        tout -> "Tous les contenus"
        else -> Plateformes.info(choisie!!).libelle
    }

    var rangees by remember(choisie) { mutableStateOf<List<Rangee>>(emptyList()) }
    var classement by remember(choisie) { mutableStateOf<Pair<Int, Int>?>(null) }
    var populairesPlateforme by remember(choisie) { mutableStateOf<List<Film>>(emptyList()) }

    LaunchedEffect(choisie, titres) {
        // Premier classement avec ce qu'on sait déjà : l'écran se remplit
        // tout de suite au lieu d'attendre le réseau.
        rangees = withContext(Dispatchers.Default) { classeParGenre(titres) }

        // Le rapprochement popularité ne peut se faire qu'avec les fiches déjà
        // en cache : refait une deuxième fois après le préchargement, il en
        // retrouve davantage. Hors du fil principal pour la même raison que
        // classeParGenre() : correspondances() appelle enCache() sur chaque
        // titre de la plateforme.
        val populaires = Tmdb.populaires(titres.firstOrNull()?.estSerie ?: false)
        populairesPlateforme = withContext(Dispatchers.Default) {
            Tmdb.correspondances(populaires, titres, Config.POPULAIRES_MAX)
        }

        // Puis on complète : chez un fournisseur qui nomme toutes ses
        // catégories « FR | NETFLIX », le genre ne peut venir que de TMDB.
        val aChercher = titres.take(GENRES_A_CLASSER)
            .map { Tmdb.ATrouver(it.nom, it.estSerie) }
        Tmdb.precharge(aChercher) { fait, total ->
            classement = if (fait >= total) null else fait to total
        }
        classement = null
        rangees = withContext(Dispatchers.Default) { classeParGenre(titres) }
        populairesPlateforme = withContext(Dispatchers.Default) {
            Tmdb.correspondances(populaires, titres, Config.POPULAIRES_MAX)
        }
    }

    // Sur cette seule plateforme : pas besoin de réseau, l'ajout est déjà lu.
    val recentsPlateforme = remember(titres) {
        titres.filter { it.ajoute > 0 }.sortedByDescending { it.ajoute }.take(Config.RECENTS_MAX)
    }
    // « Ajouté récemment » d'abord, « Populaire » ensuite — jamais l'inverse.
    // recentsPlateforme est connu tout de suite (aucun réseau), alors que
    // populairesPlateforme arrive après coup ; l'insérer EN TÊTE d'une
    // LazyColumn déjà affichée décalait la rangée déjà là vers le bas et la
    // faisait remesurer trop petite (repéré : sa carte s'écrasait à un
    // cinquième de sa hauteur, sans titre visible). Ajouter la nouvelle
    // rangée à la SUITE d'une rangée déjà stable, jamais avant, évite le
    // problème — c'est déjà comme ça que fonctionne l'accueil.
    val avant = remember(populairesPlateforme, recentsPlateforme) {
        buildList {
            if (recentsPlateforme.isNotEmpty()) add(Rangee("Ajouté récemment", recentsPlateforme))
            if (populairesPlateforme.isNotEmpty()) add(Rangee("Populaire", populairesPlateforme))
        }
    }

    ContenuTuile(libelle, titres.size, "Plateformes", { choisie = null }) {
        classement?.let { (fait, total) ->
            Text(
                "Classement par genre : $fait / $total",
                color = DISCRET,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = margeEcran(), bottom = 4.dp)
            )
        }
        Grille(rangees, prefixe, avant)
    }
}

/** Tuile synthétique « Tous les contenus » : toutes les plateformes réunies. */
private const val TUILE_TOUT = "__tout"
private val INFO_TOUT = Plateformes.Plateforme(TUILE_TOUT, "Tous les contenus", null, 0xFFE50914, 0xFF3A0A4A)

/** Le vert sobre de la palette des profils, pour la tuile « Déjà vu ». */
private val INFO_VUS = Plateformes.Plateforme(TUILE_VUS, "Déjà vu", null, 0xFF2E7D32, 0xFF14351C)

/** Au-delà, on ne cherche plus le genre : « Autres » suffit. */
private const val GENRES_A_CLASSER = 600

/**
 * Classe des titres par genre, la rangée la plus fournie en premier.
 *
 * Trois sources, dans l'ordre : le genre TMDB s'il est connu, sinon le mot de
 * genre lu dans le nom de catégorie, sinon la catégorie elle-même.
 *
 * À appeler hors du fil principal : enCache() nettoie le titre, ce qui fait
 * une dizaine d'expressions régulières par élément.
 */
private fun classeParGenre(titres: List<Film>): List<Rangee> =
    titres.groupBy { t ->
        Tmdb.enCache(t.nom, t.estSerie)?.fiche?.genre?.ifBlank { null }
            ?: t.genre.ifBlank { null }
            ?: t.categorie
    }
        .map { Rangee(it.key, it.value) }
        .sortedWith(
            compareBy(
                { if (it.titre == "Autres" || it.titre == "Non classé") 1 else 0 },
                { -it.films.size }
            )
        )

/** Alterne deux listes, films et séries, plutôt que de mettre l'une à la suite de l'autre. */
private fun <T> entrelace(a: List<T>, b: List<T>): List<T> {
    val sortie = ArrayList<T>(a.size + b.size)
    for (i in 0 until maxOf(a.size, b.size)) {
        if (i < a.size) sortie.add(a[i])
        if (i < b.size) sortie.add(b[i])
    }
    return sortie
}

/** Onglet Chaînes TV : grille de bouquets, puis les chaînes du bouquet. */
@Composable
private fun ParBouquet(groupes: List<GroupeBouquet>) {
    var choisi by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = choisi != null) { choisi = null }

    if (choisi == null) {
        GrilleTuiles(
            entete = "Chaînes TV",
            total = groupes.sumOf { g -> g.rangees.sumOf { it.chaines.size } },
            tuiles = groupes.map {
                Tuile(it.id, Plateformes.infoBouquet(it.id), it.rangees.sumOf { r -> r.chaines.size })
            },
            surChoix = { choisi = it }
        )
        return
    }

    val rangees = groupes.find { it.id == choisi }?.rangees ?: emptyList()
    val info = Plateformes.infoBouquet(choisi!!)

    ContenuTuile(info.libelle, rangees.sumOf { it.chaines.size }, "Bouquets", { choisi = null }) {
        GrilleChaines(rangees)
    }
}

/* ------------------------------------------------- Grille de tuiles commune */

private class Tuile(
    val id: String,
    val info: Plateformes.Plateforme,
    val nombre: Int
)

@Composable
private fun GrilleTuiles(
    entete: String,
    total: Int,
    tuiles: List<Tuile>,
    /** Titres commencés dans cet onglet, posés au-dessus des tuiles. */
    reprises: List<EnCours> = emptyList(),
    surChoix: (String) -> Unit
) {
    if (tuiles.isEmpty()) {
        Message("Rien ici avec les catégories cochées.")
        return
    }

    // Téléphone : deux colonnes qui remplissent la largeur (des tuiles à taille
    // fixe débordaient l'écran et la troisième colonne se retrouvait coupée).
    // TV et tablette : on garde le gabarit d'origine, 230 dp, trois par ligne.
    val tel = LocalForme.current == Forme.TELEPHONE
    val colonnes = if (tel) 2 else 3
    val ecart = 12.dp
    val marge = margeEcran()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largeurTuile = if (tel)
            (maxWidth - marge * 2 - ecart * (colonnes - 1)) / colonnes
        else 230.dp

        LazyColumn(
            contentPadding = PaddingValues(
                start = marge, end = marge, top = insetHaut(8.dp), bottom = insetBas(32.dp)
            ),
            verticalArrangement = Arrangement.spacedBy(ecart)
        ) {
            item {
                Text(entete, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("$total titres", color = DISCRET, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
            }

            if (reprises.isNotEmpty()) {
                item {
                    // offset et non padding : Compose refuse une marge négative,
                    // et RangeeFilms applique déjà la sienne par-dessus celle de
                    // la grille.
                    Box(Modifier.offset(x = -marge).padding(bottom = 8.dp)) {
                        RangeeReprise("Reprendre la lecture", reprises)
                    }
                }
            }

            // Une grille imbriquée dans une LazyColumn n'est pas permise, on
            // découpe donc les lignes nous-mêmes.
            items(tuiles.chunked(colonnes)) { ligne ->
                Row(horizontalArrangement = Arrangement.spacedBy(ecart)) {
                    ligne.forEach { t -> TuilePlateforme(t, largeurTuile) { surChoix(t.id) } }
                }
            }
        }
    }
}

/** Entête « ← Retour » commune au contenu d'une tuile. */
@Composable
private fun ContenuTuile(
    titre: String,
    total: Int,
    retour: String,
    surRetour: () -> Unit,
    contenu: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(
                start = margeEcran(), end = margeEcran(), top = insetHaut(), bottom = 12.dp
            )
        ) {
            Text(titre, color = ACCENT, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("$total titres", color = DISCRET, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            BoutonPlein("← $retour", surClic = surRetour)
        }
        contenu()
    }
}

/** Le « Tout voir » d'une rangée : une grille pleine page plutôt qu'une rangée sans fin. */
@Composable
private fun EcranTousLesTitres(titre: String, titres: List<Film>, surRetour: () -> Unit) {
    ContenuTuile(titre, titres.size, "Accueil", surRetour) {
        GrillePosters(titres)
    }
}

/**
 * Grille d'affiches à colonnes fixes, sur le même principe de découpage que
 * GrilleTuiles : une grille imbriquée dans une LazyColumn n'est pas permise,
 * les lignes sont donc chunkées nous-mêmes.
 */
@Composable
private fun GrillePosters(titres: List<Film>) {
    val marge = margeEcran()
    val ecart = 8.dp

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val colonnes = ((maxWidth - marge * 2 + ecart) / (carteL() + ecart))
            .toInt()
            .coerceAtLeast(1)

        LazyColumn(
            contentPadding = PaddingValues(
                start = marge, end = marge, top = 8.dp, bottom = insetBas(32.dp)
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(titres.chunked(colonnes), key = { it.first().id + it.first().nom }) { ligne ->
                Row(horizontalArrangement = Arrangement.spacedBy(ecart)) {
                    ligne.forEach { film -> CarteFilm(film) }
                }
            }
        }
    }
}

@Composable
private fun TuilePlateforme(tuile: Tuile, largeur: Dp, surClic: () -> Unit) {
    val info = tuile.info
    var enFocus by remember { mutableStateOf(false) }
    val echelle by animateFloatAsState(
        if (enFocus) 1.05f else 1f,
        animationSpec = RESSORT_FOCUS,
        label = "echelle"
    )

    Box(
        modifier = Modifier
            .width(largeur)
            .height((largeur * 0.55f).coerceAtLeast(96.dp))
            .scale(echelle)
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(RoundedCornerShape(RAYON))
            // Le dégradé de platforms.js : c1 en haut à gauche, c2 en bas à droite.
            .background(
                Brush.linearGradient(listOf(Color(info.c1), Color(info.c2)))
            )
            .border(
                width = if (enFocus) 3.dp else 0.dp,
                color = if (enFocus) Color.White else Color.Transparent,
                shape = RoundedCornerShape(RAYON)
            )
            .padding(16.dp)
    ) {
        Column {
            Text(info.libelle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${tuile.nombre} titre" + if (tuile.nombre > 1) "s" else "",
                color = Color(0xCCFFFFFF),
                fontSize = 13.sp
            )
        }
    }
}

/* ------------------------------------------------------------------ Fiche */

/**
 * Fiche détaillée d'un film ou d'une série, comme le panneau de app.js :
 * affiche, résumé, distribution, puis les saisons et épisodes s'il y en a.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FicheDetail(titre: Film, surRetour: () -> Unit) {
    val contexte = LocalContext.current

    val connu = remember(titre.id) { Tmdb.enCache(titre.nom, titre.estSerie) }
    var fiche by remember(titre.id) { mutableStateOf(connu?.fiche) }
    var detail by remember(titre.id) { mutableStateOf<Detail?>(null) }

    LaunchedEffect(titre.id) {
        if (!Tmdb.actif) return@LaunchedEffect
        try {
            if (connu == null) fiche = Tmdb.fiche(titre.nom, titre.estSerie)
            detail = Tmdb.detail(titre.nom, titre.estSerie)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            detail = null
        }
    }

    // Position enregistrée pour ce titre, quelle que soit la façon dont on est
    // arrivé ici : la clé est celle que le lecteur écrit dans « origine ».
    // enCours() est rangé du plus récent au plus ancien, le premier est donc
    // le dernier épisode regardé.
    val reprise = remember(titre.id) {
        Reglages.enCours().firstOrNull { it.origine == Reglages.cleFavori(titre) }
    }

    // Épisodes : chargés seulement pour une série, et seulement ici.
    var episodes by remember(titre.id) { mutableStateOf<List<Episode>?>(null) }
    var erreurEpisodes by remember(titre.id) { mutableStateOf<String?>(null) }
    var saison by remember(titre.id) { mutableIntStateOf(0) }

    LaunchedEffect(titre.id) {
        if (!titre.estSerie) return@LaunchedEffect
        try {
            val recus = Xtream.episodes(titre.id)
            if (recus.isEmpty()) erreurEpisodes = "aucun épisode renvoyé"
            episodes = recus
            // On ouvre sur la saison de l'épisode commencé, pas sur la
            // première : la liste du bas montre alors directement le bon
            // voisinage.
            val commence = reprise?.url?.let { episodeDeLUrl(recus, it) }
            saison = commence?.saison ?: recus.firstOrNull()?.saison ?: 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            erreurEpisodes = e.message ?: e.javaClass.simpleName
        }
    }

    val saisons = remember(episodes) {
        episodes?.map { it.saison }?.distinct()?.sorted() ?: emptyList()
    }
    val visibles = remember(episodes, saison) {
        episodes?.filter { it.saison == saison } ?: emptyList()
    }

    // Beaucoup de fournisseurs ne donnent ni synopsis ni image par épisode :
    // on complète depuis TMDB, une requête par saison affichée.
    var infosSaison by remember(titre.id) {
        mutableStateOf<Map<Int, Tmdb.EpisodeInfo>>(emptyMap())
    }
    LaunchedEffect(titre.id, saison) {
        infosSaison = emptyMap()
        if (titre.estSerie && saison > 0 && Tmdb.actif) {
            infosSaison = try {
                Tmdb.saison(titre.nom, saison)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    // L'épisode exact sur lequel on s'était arrêté, une fois la liste arrivée.
    val episodeRepris = remember(episodes, reprise) {
        val tous = episodes ?: return@remember null
        val url = reprise?.url ?: return@remember null
        episodeDeLUrl(tous, url)
    }

    val nomAffiche = if (Config.CORRIGER_TITRES) fiche?.titreCorrige ?: titre.nom else titre.nom

    val surTelephone = LocalForme.current == Forme.TELEPHONE

    LazyColumn(
        modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = margeEcran(), end = margeEcran(),
            top = if (surTelephone) 12.dp else 24.dp, bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val urlAffiche = fiche?.affiche ?: titre.affiche
            val cadre = Modifier
                .width(if (surTelephone) 150.dp else 180.dp)
                .height(if (surTelephone) 225.dp else 270.dp)
                .clip(RoundedCornerShape(RAYON))
                .background(CARTE)
            val affiche: @Composable () -> Unit = {
                if (urlAffiche != null) {
                    Image(
                        url = urlAffiche,
                        description = nomAffiche,
                        largeurPx = 360, hauteurPx = 540,
                        echelleContenu = ContentScale.Crop,
                        modifier = cadre
                    )
                } else {
                    Box(cadre)
                }
            }

            // Le corps de la fiche : titre, infos, résumé, réalisation, actions.
            // Posé à droite de l'affiche sur grand écran, en dessous au doigt.
            val details: @Composable ColumnScope.() -> Unit = {
                    Text(nomAffiche, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)

                    // Ligne d'informations, dans l'ordre de sheet-meta sous Windows.
                    val morceaux = buildList {
                        detail?.let { d ->
                            if (d.duree > 0) add("${d.duree} min")
                            if (d.saisons > 0) add("${d.saisons} saison" + if (d.saisons > 1) "s" else "")
                            if (d.note > 0) add("★ %.1f".format(d.note))
                            if (d.genres.isNotEmpty()) add(d.genres.take(3).joinToString(", "))
                        }
                    }
                    if (morceaux.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(morceaux.joinToString("  ·  "), color = DISCRET, fontSize = 15.sp)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        detail?.resume?.ifBlank { null }
                            ?: fiche?.resume?.ifBlank { null }
                            ?: "Pas de résumé disponible.",
                        color = Color(0xFFCCCCCC),
                        fontSize = 16.sp
                    )

                    detail?.realisation?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(10.dp))
                        Text("Réalisation : $it", color = DISCRET, fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(18.dp))
                    var favori by remember(titre.id) { mutableStateOf(Reglages.estFavori(titre)) }
                    var vu by remember(titre.id) {
                        mutableStateOf(Reglages.estVu(Reglages.cleFavori(titre)))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        // Reprendre passe en premier : c'est le bouton qu'on
                        // vise en arrivant depuis la rangée « Reprendre ».
                        if (titre.estSerie) {
                            episodeRepris?.let { ep ->
                                val numero = ep.numero.takeIf { it > 0 }
                                val ou = "S${ep.saison}" + (numero?.let { "E$it" } ?: "")
                                BoutonPlein(
                                    "▶  Reprendre $ou · ${duree(reprise?.secondes ?: 0)}"
                                ) {
                                    val saisonDeLEpisode =
                                        episodes.orEmpty().filter { it.saison == ep.saison }
                                    contexte.startActivity(
                                        intentSaison(
                                            contexte = contexte,
                                            film = titre,
                                            saison = saisonDeLEpisode,
                                            rang = saisonDeLEpisode.indexOf(ep)
                                                .coerceAtLeast(0),
                                            reprise = reprise?.secondes ?: 0
                                        )
                                    )
                                }
                            }
                        } else {
                            val debut = Reglages.position(titre.url)
                            BoutonPlein(
                                if (debut > 30) "▶  Reprendre · ${duree(debut)}" else "Lire"
                            ) {
                                contexte.startActivity(
                                    intentSimple(contexte, titre, titre.url, debut)
                                )
                            }
                        }

                        BoutonPlein(
                            if (favori) "✓  Dans ma liste" else "+  Ma liste"
                        ) {
                            favori = Reglages.basculeFavori(titre)
                        }
                        BoutonPlein(
                            if (vu) "✓  Déjà vu" else "Marquer déjà vu"
                        ) {
                            vu = Reglages.basculeVu(titre)
                        }
                        BoutonPlein("Retour", surClic = surRetour)
                    }
            }

            if (surTelephone) {
                Column(Modifier.fillMaxWidth()) {
                    affiche()
                    Spacer(Modifier.height(16.dp))
                    details()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    affiche()
                    Column(Modifier.weight(1f), content = details)
                }
            }
        }

        // Distribution
        detail?.acteurs?.takeIf { it.isNotEmpty() }?.let { acteurs ->
            item {
                Spacer(Modifier.height(12.dp))
                Text("Distribution", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(acteurs) { a -> CarteActeur(a) }
                }
            }
        }

        // Saisons et épisodes
        if (titre.estSerie) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    when {
                        erreurEpisodes != null -> "Épisodes indisponibles ($erreurEpisodes)"
                        episodes == null -> "Chargement des épisodes…"
                        else -> "${visibles.size} épisode(s)"
                    },
                    color = if (erreurEpisodes != null) Color(0xFFFF6B6B) else DISCRET,
                    fontSize = 15.sp
                )
            }

            if (saisons.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(saisons) { s ->
                            BoutonChoix("Saison $s", choisi = s == saison) { saison = s }
                        }
                    }
                }
            }

            itemsIndexed(visibles) { rang, e ->
                LigneEpisode(
                    episode = e,
                    rang = rang,
                    repris = e.id == episodeRepris?.id,
                    avancement = Reglages.position(e.url),
                    infoTmdb = infosSaison[e.numero.takeIf { it > 0 } ?: (rang + 1)]
                ) {
                    // Toute la saison part au lecteur, avec le rang choisi :
                    // c'est ce qui permet l'enchaînement automatique.
                    contexte.startActivity(
                        intentSaison(
                            contexte = contexte,
                            film = titre,
                            saison = visibles,
                            rang = rang,
                            reprise = Reglages.position(e.url)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CarteActeur(acteur: Acteur) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(110.dp)
    ) {
        if (acteur.photo != null) {
            Image(
                url = acteur.photo,
                description = acteur.nom,
                largeurPx = 192, hauteurPx = 192,
                echelleContenu = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(48.dp))
                    .background(CARTE)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(CARTE)
            ) {
                Text(
                    acteur.nom.take(1).uppercase(),
                    color = DISCRET,
                    fontSize = 32.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(acteur.nom, color = Color.White, fontSize = 13.sp, maxLines = 2)
        if (acteur.role.isNotBlank()) {
            Text(acteur.role, color = DISCRET, fontSize = 12.sp, maxLines = 2)
        }
    }
}

@Composable
private fun BoutonChoix(texte: String, choisi: Boolean, surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(RoundedCornerShape(RAYON))
            .background(if (choisi) ACCENT else CARTE)
            .border(
                width = if (enFocus) 3.dp else 0.dp,
                color = if (enFocus) Color.White else Color.Transparent,
                shape = RoundedCornerShape(RAYON)
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(texte, color = Color.White, fontSize = 15.sp)
    }
}

/* -------------------------------------------------------------- Programmes */

@Composable
private fun Programmes(chaine: Chaine, surRetour: () -> Unit) {
    // Téléphone : les programmes en affiches, comme un catalogue.
    if (LocalForme.current == Forme.TELEPHONE) {
        ProgrammesTel(chaine, surRetour)
        return
    }
    val contexte = LocalContext.current
    var programmes by remember { mutableStateOf<List<Programme>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chaine.id) {
        try {
            programmes = Xtream.replay(chaine)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            erreur = e.message ?: e.javaClass.simpleName
        }
    }

    val jourFormat = remember { SimpleDateFormat("EEEE d MMMM", Locale.FRENCH) }
    val heureFormat = remember { SimpleDateFormat("HH:mm", Locale.FRENCH) }

    // Regroupement par jour, du plus récent au plus ancien : c'est ce que fait
    // renderReplayProgrammes() sous Windows, et c'est ce qui rend une liste de
    // plusieurs centaines de programmes lisible.
    val parJour = remember(programmes) {
        val map = LinkedHashMap<String, MutableList<Programme>>()
        programmes?.forEach { p ->
            val jour = jourFormat.format(Date(p.debut * 1000))
                .replaceFirstChar { it.uppercase() }
            map.getOrPut(jour) { mutableListOf() }.add(p)
        }
        map
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = margeEcran(), end = margeEcran(), top = insetHaut(), bottom = insetBas(32.dp)
        )
    ) {
        item {
            // .cat-head : bouton de retour, titre, et la durée d'archive à côté.
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoutonPlein("← Chaînes", surClic = surRetour)
                Spacer(Modifier.width(16.dp))
                Text(chaine.nom, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                Text(
                    if (chaine.joursArchive > 0) "${chaine.joursArchive} jours disponibles"
                    else "replay",
                    color = DISCRET,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        when {
            erreur != null -> item {
                Text("Échec : $erreur", color = Color(0xFFFF6B6B), fontSize = 17.sp)
            }
            programmes == null -> item { CircularProgressIndicator(color = ACCENT) }
            programmes!!.isEmpty() -> item {
                Text(
                    "Aucun programme passé annoncé pour cette chaîne.",
                    color = DISCRET,
                    fontSize = 16.sp
                )
            }
            else -> parJour.forEach { (jour, lot) ->
                // .replay-jour : le jour en tête, première lettre en majuscule.
                item(key = "jour-$jour") {
                    Text(
                        text = jour,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
                    )
                }
                items(lot, key = { it.debut }) { p ->
                    LigneProgramme(
                        heure = heureFormat.format(Date(p.debut * 1000)),
                        titre = p.titre,
                        detail = "${p.dureeMinutes} min" +
                                if (p.resume.isNotBlank()) " — " + p.resume.take(120) else "",
                        vignette = chaine.logo
                    ) {
                        contexte.startActivity(
                            Intent(contexte, LecteurActivity::class.java)
                                .putExtra("url", Xtream.urlReplay(chaine, p))
                                .putExtra("titre", chaine.nom + " — " + p.titre)
                        )
                    }
                }
            }
        }
    }
}

/** .episode : heure, vignette 150x84, titre et durée. */
@Composable
private fun LigneProgramme(
    heure: String,
    titre: String,
    detail: String,
    vignette: String?,
    surClic: () -> Unit
) {
    var enFocus by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .background(if (enFocus) SURFACE else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (enFocus) Color.White else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = heure,
            color = if (enFocus) Color.White else DISCRET,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(52.dp)
        )

        val cadre = Modifier
            .width(150.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CARTE)
        if (vignette != null) {
            Image(
                url = vignette,
                description = titre,
                largeurPx = 300, hauteurPx = 168,
                echelleContenu = ContentScale.Crop,
                modifier = cadre
            )
        } else {
            Box(cadre)
        }

        Column {
            Text(titre, color = Color.White, fontSize = 16.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = DISCRET, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ------------------------------------------------------------- Composants */

/** Rangée générique de chaînes (le nom évite la collision avec RangeeChaines). */
private data class Rangee2(val titre: String, val chaines: List<Chaine>)

/**
 * Image redimensionnée AVANT décodage.
 *
 * Les affiches des fournisseurs font souvent 1000x1500 pour un affichage
 * en 150x225 : sans cette contrainte, le Chromecast décode quarante fois
 * trop de pixels et la navigation saccade.
 */
@Composable
private fun Image(
    url: String,
    description: String,
    largeurPx: Int,
    hauteurPx: Int,
    echelleContenu: ContentScale,
    modifier: Modifier = Modifier,
    /** Diagnostic : appelé quand l'URL existe mais que l'image ne charge pas. */
    surEchec: (() -> Unit)? = null,
    /** Transparence appliquée au dessin lui-même : pas de calque hors écran. */
    alpha: Float = 1f
) {
    // La requête ne dépend que de l'URL et de la taille de décodage : la
    // reconstruire à chaque recomposition d'une grille qui défile est du
    // travail pur perte. L'échec passe par le paramètre `onError` d'AsyncImage,
    // pour ne pas remettre un `listener` (fermeture instable) dans le modèle.
    val plateforme = LocalPlatformContext.current
    val requete = remember(url, largeurPx, hauteurPx) {
        ImageRequest.Builder(plateforme)
            .data(url)
            .size(largeurPx, hauteurPx)
            // Deux octets par pixel au lieu de quatre : une affiche est
            // opaque, la couche de transparence ne sert à rien. La mémoire
            // par image est divisée par deux.
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .crossfade(false)     // une animation par affiche, c'est trop
            .build()
    }
    AsyncImage(
        model = requete,
        contentDescription = description,
        contentScale = echelleContenu,
        onError = surEchec?.let { rappel -> { _ -> rappel() } },
        alpha = alpha,
        modifier = modifier
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RangeeFilms(
    rangee: Rangee,
    surVoirTout: (() -> Unit)? = null
) {
    // Les affiches du DÉBUT de la rangée sont demandées à Coil dès que la
    // rangée entre en composition, c'est-à-dire pendant que l'utilisateur
    // regarde encore celle du dessus.
    PrechargeAffiches(rangee.films)

    // focusRestorer : en revenant dans une rangée, on retrouve la carte qu'on
    // y avait laissée (épinglée, donc jamais détruite). À la première visite,
    // rien à restaurer : la première carte sert de repli.
    val premiere = remember { FocusRequester() }

    Column {
        TitreRangee(rangee.titre)
        DefilementRangee {
            LazyRow(
                modifier = Modifier.focusRestorer(premiere),
                contentPadding = PaddingValues(horizontal = margeEcran()),
                horizontalArrangement = Arrangement.spacedBy(ECART_CARTES)
            ) {
                itemsIndexed(rangee.films, key = { _, f -> f.id + f.nom }) { rang, film ->
                    CarteFilm(
                        film = film,
                        modifier = if (rang == 0) Modifier.focusRequester(premiere) else Modifier
                    )
                }
                if (surVoirTout != null) {
                    item(key = "voir-tout") { CarteVoirTout(surClic = surVoirTout) }
                }
            }
        }
    }
}

/** Espace entre deux cartes d'une rangée. */
private val ECART_CARTES = 12.dp

/** Titre d'une rangée, en gras comme les .grid-title de MrFlix Web. */
@Composable
private fun TitreRangee(titre: String, nombre: Int? = null) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(start = margeEcran(), end = margeEcran(), bottom = 10.dp)
    ) {
        Text(
            text = titre,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (nombre != null) {
            Spacer(Modifier.width(10.dp))
            Text(nombre.toString(), color = DISCRET, fontSize = 14.sp)
        }
    }
}

/** Dernière carte d'une rangée : ouvre la grille complète, comme sur Netflix. */
@Composable
private fun CarteVoirTout(surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }
    val echelle = animateFloatAsState(
        if (enFocus) ECHELLE_FOCUS else 1f,
        animationSpec = RESSORT_FOCUS,
        label = "echelle"
    )
    Column(
        modifier = Modifier
            .width(carteL())
            .graphicsLayer {
                scaleX = echelle.value
                scaleY = echelle.value
            }
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
    ) {
        Box(
            modifier = Modifier
                .width(carteL())
                .height(carteH())
                .clip(RoundedCornerShape(RAYON))
                .background(if (enFocus) Color.White else CARTE)
                .border(
                    width = if (enFocus) 0.dp else 1.dp,
                    color = LIGNE,
                    shape = RoundedCornerShape(RAYON)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "›",
                color = if (enFocus) Color.Black else Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Tout voir", color = TEXTE, fontSize = 13.sp)
    }
}

/**
 * Demande à Coil de charger d'avance les premières affiches d'une rangée.
 *
 * `enqueue` place la requête dans la file du chargeur sans rien afficher :
 * l'image atterrit dans le cache mémoire et disque, et sera instantanée quand
 * la carte apparaîtra vraiment.
 */
@Composable
private fun PrechargeAffiches(films: List<Film>) {
    val plateforme = LocalPlatformContext.current
    val avecTmdb = LocalAffichesTmdb.current

    LaunchedEffect(films.firstOrNull()?.id, avecTmdb) {
        // Assez long pour qu'une rangée survolée pendant un défilement soutenu
        // n'émette RIEN : seules les rangées près desquelles on s'arrête
        // déclenchent le préchargement. C'est ce qui garde le défilement
        // vertical fluide.
        delay(350)
        val chargeur = SingletonImageLoader.get(plateforme)
        // Les requêtes émises doivent être ANNULÉES quand la rangée quitte
        // l'écran. Sans ça, `enqueue` laissait derrière lui du travail que
        // plus personne n'attendait : en descendant dix rangées, des
        // centaines de téléchargements s'accumulaient devant une file large
        // de quatre, et les affiches réellement visibles passaient derrière.
        // Elles restaient grises non pas faute d'URL, mais faute de tour.
        val demandes = mutableListOf<coil3.request.Disposable>()
        try {
            // enCache() enchaîne des expressions régulières : le résoudre pour
            // douze titres par rangée reste hors du fil principal.
            withContext(Dispatchers.IO) {
                for (film in films.take(AFFICHES_PAR_RANGEE)) {
                    val url = (if (avecTmdb) Tmdb.enCache(film.nom, film.estSerie)?.fiche?.affiche else null)
                        ?: film.affiche ?: continue
                    demandes.add(
                        chargeur.enqueue(
                            ImageRequest.Builder(plateforme)
                                .data(url)
                                .size(154, 231)
                                .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                                .build()
                        )
                    )
                }
            }
            // La coroutine reste en vie tant que la rangée est composée : sa
            // fin est justement le signal d'annulation.
            awaitCancellation()
        } finally {
            demandes.forEach { it.dispose() }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RangeeChainesVue(rangee: Rangee2) {
    val premiere = remember { FocusRequester() }

    Column {
        TitreRangee(rangee.titre)
        DefilementRangee {
            LazyRow(
                modifier = Modifier.focusRestorer(premiere),
                contentPadding = PaddingValues(horizontal = margeEcran()),
                horizontalArrangement = Arrangement.spacedBy(ECART_CARTES)
            ) {
                itemsIndexed(rangee.chaines, key = { _, c -> c.id }) { rang, chaine ->
                    CarteChaine(
                        chaine = chaine,
                        modifier = if (rang == 0) Modifier.focusRequester(premiere) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun CarteFilm(
    film: Film,
    modifier: Modifier = Modifier,
    /** Null = ouvrir la fiche. La rangée « Reprendre » reprend la lecture. */
    surClic: (() -> Unit)? = null,
    /** Petit badge « Reprendre » : titre commencé, dans la rangée de reprise. */
    enCours: Boolean = false
) {
    val ouvrirFiche = LocalOuvrirFiche.current
    var enFocus by remember { mutableStateOf(false) }
    // Le grossissement est un graphicsLayer : purement visuel, il ne change
    // ni la mesure ni le défilement (le pivot se fait sur la taille réelle).
    val echelle = animateFloatAsState(
        if (enFocus) ECHELLE_FOCUS else 1f,
        animationSpec = RESSORT_FOCUS,
        label = "echelle"
    )

    // Le cache est consulté SANS coroutine : une affiche déjà connue est là
    // dès la première image, sans carte vide ni redessin.
    val avecTmdb = LocalAffichesTmdb.current
    val connu = remember(film.id, avecTmdb) {
        if (avecTmdb) Tmdb.enCache(film.nom, film.estSerie) else null
    }
    var fiche by remember(film.id, avecTmdb) { mutableStateOf(connu?.fiche) }

    // L'effet n'est déclaré QUE si le titre est inconnu : pendant un
    // défilement, la plupart des cartes sont déjà en cache et n'ont alors
    // aucune coroutine à créer ni à annuler.
    val aChercher = avecTmdb && Tmdb.actif && connu == null
    if (aChercher) {
        LaunchedEffect(film.id, avecTmdb) {
            // Le défilement détruit les cartes qu'on traverse : ce délai laisse
            // le temps de les annuler avant qu'elles n'atteignent le réseau.
            delay(200)
            try {
                fiche = Tmdb.fiche(film.nom, film.estSerie)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fiche = null
            }
        }
    }

    val affiche = fiche?.affiche ?: film.affiche
    val titre = if (Config.CORRIGER_TITRES) (fiche?.titreCorrige ?: film.nom) else film.nom
    val annee = remember(film.nom) { anneeDe(film.nom) }
    val forme = RoundedCornerShape(RAYON)

    Column(
        modifier = modifier
            .width(carteL())
            // graphicsLayer avec un bloc, et non Modifier.scale : la valeur
            // animée est lue au dessin, pas à la composition.
            .graphicsLayer {
                scaleX = echelle.value
                scaleY = echelle.value
            }
            .onFocusChanged { enFocus = it.isFocused }
            // Films comme séries passent par la fiche : le résumé et la
            // distribution avant la lecture, comme sur Windows.
            .clickable { if (surClic != null) surClic() else ouvrirFiche(film) }
    ) {
        Box(
            modifier = Modifier
                .width(carteL())
                .height(carteH())
                .clip(forme)
                .background(Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))))
        ) {
            if (affiche != null) {
                var echec by remember(affiche) { mutableStateOf(false) }
                Image(
                    url = affiche,
                    description = titre,
                    // Aligné sur la source TMDB (w154) : décoder plus grand
                    // ne ferait qu'agrandir une image qui n'a pas les pixels.
                    largeurPx = 154, hauteurPx = 231,
                    echelleContenu = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    surEchec = { echec = true }
                )
                // L'URL existe et le chargement a ÉCHOUÉ : on écrit le titre
                // dans la carte plutôt que de laisser un rectangle vide.
                if (echec) TitreSansAffiche(titre)
            } else {
                TitreSansAffiche(titre)
            }

            // .card-year : l'année en pastille, en haut à gauche.
            if (annee != null) Badge(annee, Modifier.align(Alignment.TopStart).padding(7.dp))
            if (enCours) Badge("Reprendre", Modifier.align(Alignment.BottomStart).padding(7.dp), ACCENT)

            // Contour : un filet de verre au repos, un trait blanc au focus.
            Box(
                Modifier
                    .matchParentSize()
                    .border(
                        width = if (enFocus) 3.dp else 1.dp,
                        color = if (enFocus) Color.White else Color(0x14FFFFFF),
                        shape = forme
                    )
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = titre,
            color = if (enFocus) Color.White else TEXTE,
            fontSize = 13.sp,
            fontWeight = if (enFocus) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Grossissement d'une carte au focus. */
private const val ECHELLE_FOCUS = 1.07f

/** Une carte sans image : le titre en grand, comme les cartes de MrFlix Web. */
@Composable
private fun BoxScope.TitreSansAffiche(titre: String) {
    Text(
        text = titre.uppercase(),
        color = DISCRET,
        fontSize = 15.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).padding(12.dp)
    )
}

/** .card-year : petite pastille sombre, posée sur une affiche. */
@Composable
private fun Badge(texte: String, modifier: Modifier = Modifier, fond: Color = Color(0x99000000)) {
    Text(
        text = texte,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(PILULE)
            .background(fond)
            .border(1.dp, Color(0x26FFFFFF), PILULE)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun CarteChaine(
    chaine: Chaine,
    modifier: Modifier = Modifier,
    /** .ep-badge : « 7 j » en haut à droite pour les chaînes rejouables. */
    badge: String? = null,
    /** Null = lecture directe. Le replay ouvre l'écran des programmes. */
    surClic: (() -> Unit)? = null
) {
    val contexte = LocalContext.current
    var enFocus by remember { mutableStateOf(false) }
    val echelle = animateFloatAsState(
        if (enFocus) ECHELLE_FOCUS else 1f,
        animationSpec = RESSORT_FOCUS,
        label = "echelle"
    )
    val forme = RoundedCornerShape(RAYON)

    Column(
        modifier = modifier
            .width(190.dp)
            .graphicsLayer {
                scaleX = echelle.value
                scaleY = echelle.value
            }
            .onFocusChanged { enFocus = it.isFocused }
            .clickable {
                if (surClic != null) surClic()
                else contexte.startActivity(
                    Intent(contexte, LecteurActivity::class.java)
                        .putExtra("url", chaine.url)
                        .putExtra("titre", chaine.nom)
                )
            }
    ) {
        Box(
            modifier = Modifier
                .width(190.dp)
                .height(108.dp)
                .clip(forme)
                // .card.live : le logo entier, posé sur du verre.
                .background(Brush.linearGradient(listOf(Color(0x24FFFFFF), Color(0x0DFFFFFF))))
                .border(
                    width = if (enFocus) 3.dp else 1.dp,
                    color = if (enFocus) Color.White else Color(0x14FFFFFF),
                    shape = forme
                ),
            contentAlignment = Alignment.Center
        ) {
            if (chaine.logo != null) {
                Image(
                    url = chaine.logo,
                    description = chaine.nom,
                    largeurPx = 380, hauteurPx = 216,
                    echelleContenu = ContentScale.Fit,
                    modifier = Modifier.padding(16.dp).fillMaxSize()
                )
            } else {
                Text(
                    chaine.nom,
                    color = DISCRET,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (badge != null) {
                Badge(badge, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = chaine.nom,
            color = if (enFocus) Color.White else TEXTE,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * `.episode` de styles.css, pour la liste des épisodes : numéro à gauche,
 * vignette 150x84, « Épisode N » et le nom brut en dessous.
 *
 * Même dessin que LigneProgramme dans le replay, avec deux ajouts que la
 * version Windows n'a pas : un contour blanc au focus, indispensable à la
 * télécommande, et un repère sur ce qui a déjà été commencé.
 */
@Composable
private fun LigneEpisode(
    episode: Episode,
    rang: Int,
    repris: Boolean,
    avancement: Int,
    infoTmdb: Tmdb.EpisodeInfo? = null,
    surClic: () -> Unit
) {
    var enFocus by remember { mutableStateOf(false) }
    val numero = episode.numero.takeIf { it > 0 } ?: (rang + 1)

    // L'image TMDB d'abord : c'est une vraie photo de CET épisode. Celle du
    // fournisseur, quand elle existe, n'est souvent que l'affiche de la série.
    val vignette = infoTmdb?.image ?: episode.vignette
    val resume = episode.resume.ifBlank { infoTmdb?.resume ?: "" }
    // Le titre du fournisseur est presque toujours un nom de fichier
    // (« |FR| Série 2015 FHD - S01E06 ») : on ne le garde que s'il a l'air
    // d'un vrai titre, sinon le nom TMDB, sinon rien.
    val titrePropre = episode.titre.takeIf {
        it.isNotBlank() && it != "Épisode" && !it.contains('|') && it.length < 70 &&
                !Regex("s\\d{1,2}\\s*e\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(it)
    }
    val titreEpisode = infoTmdb?.nom?.takeIf { it.isNotBlank() } ?: titrePropre
    // Au doigt, l'espace est compté : on laisse tomber la colonne du numéro
    // (« Épisode N » le redit) et la vignette est un peu plus petite.
    val surTelephone = LocalForme.current == Forme.TELEPHONE
    val largeurVignette = if (surTelephone) 128.dp else 150.dp

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enFocus) SURFACE else Color.Transparent)
            .border(
                width = if (enFocus) 2.dp else 1.dp,
                color = when {
                    enFocus -> Color.White
                    repris -> ACCENT
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(4.dp)
            )
            .padding(12.dp)
    ) {
        if (!surTelephone) {
            Text(
                text = numero.toString(),
                color = if (enFocus) Color.White else DISCRET,
                fontSize = 22.sp,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
        }

        // La vignette porte le repère de lecture : c'est là que l'œil va.
        Box {
            val cadre = Modifier
                .width(largeurVignette)
                .height(largeurVignette * 9f / 16f)
                .clip(RoundedCornerShape(4.dp))
                .background(CARTE)
            if (vignette != null) {
                Image(
                    url = vignette,
                    description = titreEpisode ?: "Épisode $numero",
                    largeurPx = 300, hauteurPx = 168,
                    echelleContenu = ContentScale.Crop,
                    modifier = cadre
                )
            } else {
                Box(cadre)
            }

            // .ep-badge : ce qui a déjà été regardé se voit sans avoir à lire.
            if (avancement > 30) {
                Text(
                    text = duree(avancement),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.2f),
                            RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = titreEpisode?.let { "Épisode $numero · $it" } ?: "Épisode $numero",
                color = if (repris) ACCENT else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (resume.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = resume,
                    color = Color(0xFFB0B0B0),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BoutonPlein(titre: String, surClic: () -> Unit) {
    var enFocus by remember { mutableStateOf(false) }
    val echelle by animateFloatAsState(
        if (enFocus) 1.06f else 1f, animationSpec = RESSORT_FOCUS, label = "echelle"
    )
    // Bouton « verre » de MrFlix Web : translucide au repos, blanc au focus.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer { scaleX = echelle; scaleY = echelle }
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(PILULE)
            .background(if (enFocus) Color.White else Color(0x1FFFFFFF))
            .border(1.dp, if (enFocus) Color.White else LIGNE, PILULE)
            .padding(horizontal = 24.dp, vertical = 11.dp)
    ) {
        Text(
            text = titre,
            color = if (enFocus) Color(0xFF111111) else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/* ======================================================================
 * VERSION TÉLÉPHONE — calquée sur MrFlix Web (iPhone)
 *
 * Même structure que public/index.html : barre du haut (logo, titre de la
 * section, recherche, •••), rangée de pastilles (Catégories, Français
 * uniquement, plateformes), grande carte vedette, puis une GRILLE
 * d'affiches — et une barre d'onglets flottante en bas, en verre.
 * La TV garde ses rangées et sa navigation à la télécommande.
 * ====================================================================== */

/** Titre affiché en haut de chaque section, comme #section-title. */
private fun titreSectionTel(section: String): String = when (section) {
    "accueil" -> "Accueil"
    "series" -> "Séries"
    "films" -> "Films"
    "chaines" -> "Direct"
    "liste" -> "Ma liste"
    "replay" -> "Replay"
    else -> "MrFlix"
}

/** .topbar : logo, titre de la section, recherche et réglages. */
@Composable
private fun BarreHautTel(section: String, surRecherche: () -> Unit, surOptions: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xD909070C),
                    1f to Color(0x8C09070C)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        ImageComposable(
            painter = painterResource(R.drawable.logo_mrflix),
            contentDescription = "MrFlix",
            modifier = Modifier.height(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = titreSectionTel(section),
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        BoutonIcone(Icone.RECHERCHE, "Rechercher", surRecherche)
        Spacer(Modifier.width(10.dp))
        BoutonIcone(Icone.PLUS, "Réglages", surOptions)
    }
}

/** Les icônes de la barre d'onglets, au trait, sur une grille de 24 comme les SVG du web. */
private enum class IconeOnglet { ACCUEIL, DIRECT, FILMS, SERIES, PLUS }

@Composable
private fun DessinOnglet(icone: IconeOnglet, couleur: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val trait = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2f * u,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        fun ligne(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            couleur, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u),
            strokeWidth = 2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        fun cadre(x: Float, y: Float, l: Float, h: Float, r: Float) = drawRoundRect(
            couleur, topLeft = Offset(x * u, y * u),
            size = androidx.compose.ui.geometry.Size(l * u, h * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * u), style = trait
        )
        when (icone) {
            IconeOnglet.ACCUEIL -> {
                val chemin = androidx.compose.ui.graphics.Path().apply {
                    moveTo(3f * u, 11f * u); lineTo(12f * u, 3.5f * u); lineTo(21f * u, 11f * u)
                    moveTo(5.5f * u, 9.5f * u); lineTo(5.5f * u, 20f * u); lineTo(18.5f * u, 20f * u)
                    lineTo(18.5f * u, 9.5f * u)
                    moveTo(10f * u, 20f * u); lineTo(10f * u, 14f * u); lineTo(14f * u, 14f * u)
                    lineTo(14f * u, 20f * u)
                }
                drawPath(chemin, couleur, style = trait)
            }
            IconeOnglet.DIRECT -> {
                cadre(3f, 5f, 18f, 12f, 2.5f); ligne(8f, 21f, 16f, 21f); ligne(12f, 17f, 12f, 21f)
            }
            IconeOnglet.FILMS -> {
                cadre(4f, 3f, 16f, 18f, 2.5f)
                ligne(4f, 8f, 20f, 8f); ligne(4f, 16f, 20f, 16f)
                ligne(9f, 3f, 9f, 21f); ligne(15f, 3f, 15f, 21f)
            }
            IconeOnglet.SERIES -> {
                cadre(3f, 7f, 18f, 14f, 2.5f); ligne(7f, 3.5f, 17f, 3.5f); ligne(5f, 5.5f, 19f, 5.5f)
            }
            IconeOnglet.PLUS -> {
                listOf(5f, 12f, 19f).forEach { x -> drawCircle(couleur, 1.8f * u, Offset(x * u, 12f * u)) }
            }
        }
    }
}

/** .tabs : la barre d'onglets flottante, une pilule de verre en bas de l'écran. */
@Composable
private fun OngletsTel(
    section: String,
    surSection: (String) -> Unit,
    surPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onglets = listOf(
        Triple("accueil", IconeOnglet.ACCUEIL, "Accueil"),
        Triple("chaines", IconeOnglet.DIRECT, "Direct"),
        Triple("films", IconeOnglet.FILMS, "Films"),
        Triple("series", IconeOnglet.SERIES, "Séries"),
        Triple("plus", IconeOnglet.PLUS, "Plus")
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .height(68.dp)
                .shadow(18.dp, PILULE, clip = false)
                .clip(PILULE)
                .background(Color(0xE61E1B24))
                .border(1.dp, LIGNE, PILULE)
                .padding(6.dp)
        ) {
            for ((cle, icone, nom) in onglets) {
                val actif = when (cle) {
                    "plus" -> section == "liste" || section == "replay"
                    else -> section == cle
                }
                val couleur = if (actif) Color.White else DISCRET
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PILULE)
                        .background(if (actif) Color(0x1FFFFFFF) else Color.Transparent)
                        .clickable { if (cle == "plus") surPlus() else surSection(cle) }
                ) {
                    DessinOnglet(icone, couleur)
                    Spacer(Modifier.height(3.dp))
                    Text(nom, color = couleur, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** .chip : pilule de verre ; blanche pleine quand elle est active. */
@Composable
private fun PastilleTel(
    texte: String,
    actif: Boolean,
    nombre: Int? = null,
    fleche: Boolean = false,
    surClic: () -> Unit
) {
    val couleurTexte = if (actif) Color(0xFF111111) else Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(38.dp)
            .clip(PILULE)
            .background(if (actif) Color.White else Color(0x1AFFFFFF))
            .border(1.dp, if (actif) Color.White else LIGNE, PILULE)
            .clickable(onClick = surClic)
            .padding(horizontal = 16.dp)
    ) {
        Text(texte, color = couleurTexte, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (nombre != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                "%,d".format(nombre).replace(',', ' '),
                color = couleurTexte.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (fleche) {
            Spacer(Modifier.width(6.dp))
            Text("⌄", color = couleurTexte, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Une rangée qui déborde jusqu'aux bords de l'écran malgré la marge de la
 * grille, comme .chips { margin: 0 -18px } sur le web.
 */
private fun Modifier.pleineLargeur(marge: Dp): Modifier = this.layout { mesurable, contraintes ->
    val margePx = marge.roundToPx()
    val largeur = contraintes.maxWidth + margePx * 2
    val place = mesurable.measure(contraintes.copy(minWidth = largeur, maxWidth = largeur))
    layout(contraintes.maxWidth, place.height) { place.place(-margePx, 0) }
}

/** La rangée de pastilles d'un écran du téléphone. */
@Composable
private fun RangeePastillesTel(contenu: LazyListScope.() -> Unit) {
    LazyRow(
        modifier = Modifier.pleineLargeur(MARGE_TEL).padding(top = 4.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = MARGE_TEL),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = contenu
    )
}

/** .scroller { padding: 0 18px } */
private val MARGE_TEL = 18.dp

/** .grid-title */
@Composable
private fun TitreGrilleTel(texte: String) {
    Text(
        texte,
        color = Color.White,
        fontSize = 19.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** La feuille « Catégories » (#category-sheet) : une liste de verre qui monte du bas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleListeTel(
    titre: String,
    choix: List<String>,
    choisi: String?,
    libelleTout: String,
    surChoix: (String?) -> Unit,
    surFermer: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = surFermer,
        containerColor = Color(0xF21C1922),
        shape = RoundedCornerShape(topStart = RAYON_GRAND, topEnd = RAYON_GRAND),
        dragHandle = null
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titre, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x17FFFFFF))
                        .border(1.dp, LIGNE, CircleShape)
                        .clickable(onClick = surFermer)
                ) { Text("✕", color = Color.White, fontSize = 15.sp) }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                item { LigneFeuilleTel(libelleTout, choisi == null) { surChoix(null) } }
                items(choix) { c -> LigneFeuilleTel(c, choisi == c) { surChoix(c) } }
            }
        }
    }
}

@Composable
private fun LigneFeuilleTel(texte: String, actif: Boolean, surClic: () -> Unit) {
    Text(
        texte,
        color = if (actif) Color(0xFF111111) else Color.White,
        fontSize = 15.sp,
        fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RAYON))
            .background(if (actif) Color.White else Color(0x12FFFFFF))
            .border(1.dp, if (actif) Color.White else LIGNE, RoundedCornerShape(RAYON))
            .clickable(onClick = surClic)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

/** Affiche et titre d'un film : TMDB s'il est connu, sinon le fournisseur. */
@Composable
private fun afficheEtTitre(film: Film): Pair<String?, String> {
    val avecTmdb = LocalAffichesTmdb.current
    val connu = remember(film.id, avecTmdb) {
        if (avecTmdb) Tmdb.enCache(film.nom, film.estSerie) else null
    }
    var fiche by remember(film.id, avecTmdb) { mutableStateOf(connu?.fiche) }
    if (avecTmdb && Tmdb.actif && connu == null) {
        LaunchedEffect(film.id, avecTmdb) {
            // Une grille qui défile vite détruit les cartes qu'elle traverse :
            // ce délai les laisse s'annuler avant d'atteindre le réseau.
            delay(250)
            try {
                fiche = Tmdb.fiche(film.nom, film.estSerie)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fiche = null
            }
        }
    }
    val titre = if (Config.CORRIGER_TITRES) (fiche?.titreCorrige ?: film.nom) else film.nom
    return (fiche?.affiche ?: film.affiche) to titre
}

/** .card : l'affiche 2:3, l'année en pastille, le nom dessous. */
@Composable
private fun CarteGrilleTel(film: Film) {
    val ouvrirFiche = LocalOuvrirFiche.current
    val (affiche, titre) = afficheEtTitre(film)
    val annee = remember(film.nom) { anneeDe(film.nom) }
    val forme = RoundedCornerShape(RAYON)
    Column(Modifier.clickable { ouvrirFiche(film) }) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(forme)
                .background(Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))))
                .border(1.dp, Color(0x14FFFFFF), forme)
        ) {
            // Les initiales restent visibles tant que l'image n'est pas là.
            Text(
                titre.take(2).uppercase(),
                color = DISCRET, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
            )
            if (affiche != null) {
                Image(
                    url = affiche,
                    description = titre,
                    largeurPx = 154, hauteurPx = 231,
                    echelleContenu = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (annee != null) Badge(annee, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Text(
            titre,
            color = TEXTE,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(30.dp).padding(start = 2.dp, top = 7.dp)
        )
    }
}

/** .card.live : le logo entier, posé sur du verre. Un appui lance la chaîne. */
@Composable
private fun CarteChaineTel(chaine: Chaine, badge: String? = null, surClic: (() -> Unit)? = null) {
    val contexte = LocalContext.current
    val forme = RoundedCornerShape(RAYON)
    Column(
        Modifier.clickable {
            if (surClic != null) surClic()
            else contexte.startActivity(
                Intent(contexte, LecteurActivity::class.java)
                    .putExtra("url", chaine.url)
                    .putExtra("titre", chaine.nom)
            )
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 0.6f)
                .clip(forme)
                .background(Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))))
                .border(1.dp, Color(0x14FFFFFF), forme)
        ) {
            Text(
                chaine.nom.take(2).uppercase(),
                color = DISCRET, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold
            )
            if (chaine.logo != null) {
                Image(
                    url = chaine.logo,
                    description = chaine.nom,
                    largeurPx = 300, hauteurPx = 180,
                    echelleContenu = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(14.dp)
                )
            }
            if (badge != null) Badge(badge, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            chaine.nom,
            color = TEXTE,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(30.dp).padding(start = 2.dp, top = 7.dp)
        )
    }
}

/**
 * .hero-card, version téléphone : l'affiche à gauche, le titre et le gros
 * bouton blanc « Regarder » à droite.
 */
@Composable
private fun HerosTel(film: Film, plateforme: String?) {
    val contexte = LocalContext.current
    val ouvrirFiche = LocalOuvrirFiche.current
    val fiche by rememberFiche(film)
    val titre = if (Config.CORRIGER_TITRES) (fiche?.titreCorrige ?: film.nom) else film.nom
    val affiche = fiche?.affiche ?: film.affiche
    val annee = remember(film.nom) { anneeDe(film.nom) }
    val meta = listOfNotNull(annee, plateforme).joinToString(" · ")
    val forme = RoundedCornerShape(RAYON_GRAND)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp)
            .height(200.dp)
            .shadow(24.dp, forme, clip = false)
            .clip(forme)
            .background(Color(0xFF1A1720))
            .border(1.dp, LIGNE, forme)
    ) {
        Box(Modifier.fillMaxHeight().aspectRatio(2f / 3f).background(CARTE)) {
            if (affiche != null) {
                Image(
                    url = affiche, description = titre,
                    largeurPx = 300, hauteurPx = 450,
                    echelleContenu = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)
        ) {
            if (meta.isNotEmpty()) {
                Text(meta, color = DISCRET, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                titre, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold,
                lineHeight = 23.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(PILULE)
                    .background(Color.White)
                    .clickable {
                        if (film.estSerie) ouvrirFiche(film)
                        else {
                            val debut = Reglages.position(film.url)
                            contexte.startActivity(intentSimple(contexte, film, film.url, debut))
                        }
                    }
            ) {
                Text("▶", color = Color(0xFF111111), fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text("Regarder", color = Color(0xFF111111), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Le fond coloré de MrFlix Web : les couleurs floues de l'affiche vedette. */
@Composable
private fun FondAmbiantTel(url: String?) {
    if (url == null) return
    Image(
        url = url, description = "",
        largeurPx = 48, hauteurPx = 72,
        echelleContenu = ContentScale.Crop,
        alpha = 0.55f,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)
    )
    Box(
        Modifier.fillMaxWidth().fillMaxHeight(0.7f).background(
            Brush.verticalGradient(0f to Color(0x3309070C), 0.6f to Color(0x9909070C), 1f to FOND)
        )
    )
}

/**
 * Films ou Séries sur téléphone : exactement l'écran « Movies » de MrFlix
 * Web. Pastilles (catégories, français, plateformes), carte vedette, puis la
 * grille des titres, du plus récent au plus ancien.
 */
@Composable
private fun CatalogueTel(
    groupes: List<GroupePlateforme>,
    francais: Boolean,
    surFrancais: () -> Unit
) {
    var plateforme by remember(groupes) { mutableStateOf<String?>(null) }
    var categorie by remember(groupes) { mutableStateOf<String?>(null) }
    var feuille by remember { mutableStateOf(false) }

    // Le tri et les regroupements sont faits une fois, hors de l'affichage.
    val tous = remember(groupes) {
        groupes.flatMap { it.titres }.distinctBy { it.id + "|" + it.nom }
            .sortedByDescending { it.ajoute }
    }
    val source = remember(tous, plateforme) {
        if (plateforme == null) tous
        else groupes.find { it.id == plateforme }?.titres.orEmpty().sortedByDescending { it.ajoute }
    }
    val categories = remember(source) {
        source.groupingBy { it.categorie }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }
    }
    val titres = remember(source, categorie) {
        if (categorie == null) source else source.filter { it.categorie == categorie }
    }
    val vedette = remember(titres) { titres.firstOrNull { it.affiche != null } ?: titres.firstOrNull() }
    val nomPlateforme = plateforme?.let { Plateformes.info(it).libelle }
    val ficheVedette = vedette?.let { rememberFiche(it) }

    Box(Modifier.fillMaxSize()) {
        FondAmbiantTel(ficheVedette?.value?.affiche ?: vedette?.affiche)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(
                start = MARGE_TEL, end = MARGE_TEL,
                top = insetHaut(4.dp), bottom = insetBas(36.dp)
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "pastilles", span = { GridItemSpan(maxLineSpan) }) {
                RangeePastillesTel {
                    item {
                        PastilleTel(categorie ?: "Catégories", actif = categorie != null, fleche = true) {
                            feuille = true
                        }
                    }
                    item { PastilleTel("Français uniquement", actif = francais, surClic = surFrancais) }
                    if (groupes.size > 1) {
                        item {
                            PastilleTel("Toutes les plateformes", plateforme == null, nombre = tous.size) {
                                plateforme = null; categorie = null
                            }
                        }
                        items(groupes, key = { it.id }) { g ->
                            PastilleTel(Plateformes.info(g.id).libelle, plateforme == g.id, nombre = g.titres.size) {
                                plateforme = g.id; categorie = null
                            }
                        }
                    }
                }
            }
            if (vedette != null) {
                item(key = "vedette", span = { GridItemSpan(maxLineSpan) }) {
                    HerosTel(vedette, Plateformes.info(vedette.plateforme).libelle.takeIf { vedette.plateforme != "autre" })
                }
            }
            item(key = "titre", span = { GridItemSpan(maxLineSpan) }) {
                TitreGrilleTel(categorie ?: nomPlateforme ?: "Les plus récents")
            }
            if (titres.isEmpty()) {
                item(key = "vide", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Aucun résultat.", color = DISCRET, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                    )
                }
            }
            items(titres, key = { it.id + "|" + it.nom }, contentType = { "affiche" }) { film ->
                CarteGrilleTel(film)
            }
        }
    }

    if (feuille) {
        FeuilleListeTel(
            titre = "Catégories",
            choix = categories,
            choisi = categorie,
            libelleTout = "Tout",
            surChoix = { categorie = it; feuille = false },
            surFermer = { feuille = false }
        )
    }
}

/** Ce que le téléphone retient d'un écran à l'autre tant que l'appli est ouverte. */
private object EtatTel {
    val replay = mutableStateOf(false)
}

/** Direct sur téléphone : l'écran « Live TV » de MrFlix Web, plus les bouquets. */
@Composable
private fun ChainesTel(
    bouquets: List<GroupeBouquet>,
    francais: Boolean,
    surFrancais: () -> Unit,
    surProgrammes: (Chaine) -> Unit
) {
    var bouquet by remember(bouquets) { mutableStateOf<String?>(null) }
    var categorie by remember(bouquets) { mutableStateOf<String?>(null) }
    var feuille by remember { mutableStateOf(false) }
    // « Replay » : seulement les chaînes rejouables ; un appui ouvre alors
    // leurs programmes passés, en affiches, au lieu du direct. Retenu hors
    // de l'écran : revenir des programmes doit retomber sur le replay.
    var replay by EtatTel.replay

    val rangees = remember(bouquets, bouquet, replay) {
        (if (bouquet == null) bouquets else bouquets.filter { it.id == bouquet }).flatMap { it.rangees }
            .let { liste ->
                if (!replay) liste
                else liste.map { RangeeChaines(it.titre, it.chaines.filter { c -> c.archive }) }
                    .filter { it.chaines.isNotEmpty() }
            }
    }
    val nombreReplay = remember(bouquets) {
        bouquets.flatMap { it.rangees }.flatMap { it.chaines }.filter { it.archive }.distinctBy { it.id }.size
    }
    val categories = remember(rangees) { rangees.map { it.titre }.distinct() }
    val chaines = remember(rangees, categorie) {
        rangees.filter { categorie == null || it.titre == categorie }
            .flatMap { it.chaines }.distinctBy { it.id }
    }
    val total = remember(bouquets) {
        bouquets.flatMap { it.rangees }.flatMap { it.chaines }.distinctBy { it.id }.size
    }
    val nomBouquet = bouquet?.let { Plateformes.infoBouquet(it).libelle }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(
            start = MARGE_TEL, end = MARGE_TEL,
            top = insetHaut(4.dp), bottom = insetBas(36.dp)
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "pastilles", span = { GridItemSpan(maxLineSpan) }) {
            RangeePastillesTel {
                item {
                    PastilleTel(categorie ?: "Catégories", actif = categorie != null, fleche = true) {
                        feuille = true
                    }
                }
                item { PastilleTel("Français uniquement", actif = francais, surClic = surFrancais) }
                if (nombreReplay > 0) {
                    item {
                        PastilleTel("Replay", replay, nombre = nombreReplay) {
                            replay = !replay; categorie = null
                        }
                    }
                }
                if (bouquets.size > 1) {
                    item {
                        PastilleTel("Toutes les chaînes", bouquet == null, nombre = total) {
                            bouquet = null; categorie = null
                        }
                    }
                    items(bouquets, key = { it.id }) { g ->
                        PastilleTel(
                            Plateformes.infoBouquet(g.id).libelle, bouquet == g.id,
                            nombre = g.rangees.sumOf { it.chaines.size }
                        ) { bouquet = g.id; categorie = null }
                    }
                }
            }
        }
        item(key = "titre", span = { GridItemSpan(maxLineSpan) }) {
            TitreGrilleTel(
                categorie ?: nomBouquet ?: if (replay) "Chaînes en replay" else "Toutes les chaînes"
            )
        }
        if (chaines.isEmpty()) {
            item(key = "vide", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Aucun résultat.", color = DISCRET, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                )
            }
        }
        items(chaines, key = { it.id }, contentType = { "chaine" }) { c ->
            if (replay) CarteChaineTel(
                c,
                badge = c.joursArchive.takeIf { it > 0 }?.let { "$it j" },
                surClic = { surProgrammes(c) }
            ) else CarteChaineTel(c)
        }
    }

    if (feuille) {
        FeuilleListeTel(
            titre = "Catégories",
            choix = categories,
            choisi = categorie,
            libelleTout = "Tout",
            surChoix = { categorie = it; feuille = false },
            surFermer = { feuille = false }
        )
    }
}

/* ------------------------------------------------------- Replay (téléphone)
 *
 * Les guides des fournisseurs n'ont pas d'image : l'affiche de chaque
 * programme est cherchée sur TMDB à partir de son titre (série d'abord pour
 * une émission courte, film d'abord au-delà de 80 minutes). Sans résultat —
 * journal, météo, sport — on montre le logo de la chaîne.
 */

/** Affiche TMDB d'un programme de replay, ou null si TMDB ne le connaît pas. */
@Composable
private fun afficheProgramme(p: Programme): String? {
    val filmDabord = p.dureeMinutes >= 80
    val enCache = remember(p.titre) {
        Tmdb.enCache(p.titre, !filmDabord)?.fiche?.affiche
            ?: Tmdb.enCache(p.titre, filmDabord)?.fiche?.affiche
    }
    var affiche by remember(p.titre) { mutableStateOf(enCache) }
    if (enCache == null && Tmdb.actif) {
        LaunchedEffect(p.titre) {
            delay(250)
            try {
                affiche = Tmdb.fiche(p.titre, !filmDabord)?.affiche
                    ?: Tmdb.fiche(p.titre, filmDabord)?.affiche
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                affiche = null
            }
        }
    }
    return affiche
}

/** Une carte de programme : l'affiche, l'heure en pastille, le titre et la durée. */
@Composable
private fun CarteProgrammeTel(p: Programme, chaine: Chaine, heure: String) {
    val contexte = LocalContext.current
    val affiche = afficheProgramme(p)
    val forme = RoundedCornerShape(RAYON)
    Column(
        Modifier.clickable {
            contexte.startActivity(
                Intent(contexte, LecteurActivity::class.java)
                    .putExtra("url", Xtream.urlReplay(chaine, p))
                    .putExtra("titre", chaine.nom + " — " + p.titre)
            )
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(forme)
                .background(Brush.linearGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))))
                .border(1.dp, Color(0x14FFFFFF), forme)
        ) {
            if (affiche != null) {
                Image(
                    url = affiche, description = p.titre,
                    largeurPx = 154, hauteurPx = 231,
                    echelleContenu = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Pas d'affiche : le logo de la chaîne et le titre, sur du verre.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(10.dp)
                ) {
                    if (chaine.logo != null) {
                        Image(
                            url = chaine.logo, description = chaine.nom,
                            largeurPx = 200, hauteurPx = 120,
                            echelleContenu = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        p.titre.uppercase(), color = DISCRET, fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                        maxLines = 4, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Badge(heure, Modifier.align(Alignment.TopStart).padding(6.dp))
            Badge("${p.dureeMinutes} min", Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
        Text(
            p.titre,
            color = TEXTE,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(30.dp).padding(start = 2.dp, top = 7.dp)
        )
    }
}

/** Les programmes passés d'une chaîne, en affiches, rangés par jour. */
@Composable
private fun ProgrammesTel(chaine: Chaine, surRetour: () -> Unit) {
    var programmes by remember { mutableStateOf<List<Programme>?>(null) }
    var erreur by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chaine.id) {
        try {
            programmes = Xtream.replay(chaine)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            erreur = e.message ?: e.javaClass.simpleName
        }
    }

    val jourFormat = remember { SimpleDateFormat("EEEE d MMMM", Locale.FRENCH) }
    val heureFormat = remember { SimpleDateFormat("HH:mm", Locale.FRENCH) }
    val parJour = remember(programmes) {
        val map = LinkedHashMap<String, MutableList<Programme>>()
        programmes?.forEach { p ->
            val jour = jourFormat.format(Date(p.debut * 1000)).replaceFirstChar { it.uppercase() }
            map.getOrPut(jour) { mutableListOf() }.add(p)
        }
        map
    }

    Box(Modifier.fillMaxSize().background(FOND)) {
        FondAmbiantTel(chaine.logo)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(
                start = MARGE_TEL, end = MARGE_TEL,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "entete", span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x17FFFFFF))
                            .border(1.dp, LIGNE, CircleShape)
                            .clickable(onClick = surRetour)
                    ) { Text("‹", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            chaine.nom, color = Color.White, fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (chaine.joursArchive > 0) "Replay · ${chaine.joursArchive} jours disponibles"
                            else "Replay",
                            color = DISCRET, fontSize = 13.sp
                        )
                    }
                }
            }
            when {
                erreur != null -> item(key = "erreur", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Échec : $erreur", color = Color(0xFFFF6B6B), fontSize = 16.sp)
                }
                programmes == null -> item(key = "attente", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ACCENT)
                    }
                }
                programmes!!.isEmpty() -> item(key = "vide", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Aucun programme passé annoncé pour cette chaîne.",
                        color = DISCRET, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                    )
                }
                else -> parJour.forEach { (jour, lot) ->
                    item(key = "jour-$jour", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            jour, color = Color.White, fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                    items(lot, key = { it.debut }, contentType = { "programme" }) { p ->
                        CarteProgrammeTel(p, chaine, heureFormat.format(Date(p.debut * 1000)))
                    }
                }
            }
        }
    }
}
