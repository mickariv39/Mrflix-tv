package com.mrflix.tv

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

private val SURFACE = Color(0xFF141414)
private val CARTE = Color(0xFF1F1F1F)
private val LIGNE = Color(0xFF2A2A2A)
private val ACCENT = Color(0xFFE50914)
private val DISCRET = Color(0xFF9A9A9A)

/**
 * Lecture plein écran.
 *
 * Rien à convertir : ExoPlayer gère MKV, MP4, AC-3 et MPEG-TS nativement.
 * C'est tout le travail de compat.js qui disparaît ici.
 *
 * Un panneau escamotable sur la droite donne accès aux pistes audio, aux
 * sous-titres et à la liste des épisodes. Les touches seules ne suffisaient
 * pas : rien à l'écran n'indiquait que ces réglages existaient.
 */
@OptIn(UnstableApi::class)
class LecteurActivity : ComponentActivity() {

    private var lecteur by mutableStateOf<ExoPlayer?>(null)
    private var minuteur: Runnable? = null
    private var panneauOuvert by mutableStateOf(false)
    private var enPip by mutableStateOf(false)
    private var vuePlayer: PlayerView? = null

    // Le lecteur vit dans LecteurService (voir ce fichier) : un service au
    // premier plan survit aux gestionnaires de batterie des constructeurs là
    // où une simple Activity en PiP peut se faire couper. On ne fait que s'y
    // relier ici.
    private var lienService: LecteurService? = null
    private var connexionService: android.content.ServiceConnection? = null

    // Où on en était, conservé d'un onStop au onStart suivant (retour dans
    // l'appli) et à travers une mise à mort du système (onSaveInstanceState).
    // Sans ça, onStart repartait des extras d'intent — donc du point de
    // départ, pas de là où l'utilisateur avait mis pause.
    private var reprendreIndex = 0
    private var reprendrePosition = -1L
    private var reprendreEnLecture = true

    /** L'appareil sait-il faire du Picture-in-Picture ? (Faux sur la plupart des TV.) */
    private val pipDispo: Boolean by lazy {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                packageManager.hasSystemFeature(
                    android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
                )
    }

    /* Boutons de la vignette : reculer, lecture/pause, avancer. */
    private val ACTION_PIP = "com.mrflix.tv.PIP"
    private val PIP_RECULE = 1
    private val PIP_LECTURE = 2
    private val PIP_AVANCE = 3
    private var recepteurPip: android.content.BroadcastReceiver? = null

    /**
     * Construit les paramètres de la vignette : ratio de la vidéo, boutons de
     * contrôle, redimensionnement fluide. Rappelé à chaque changement d'état
     * pour que l'icône lecture/pause suive.
     */
    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun parametresPip(): android.app.PictureInPictureParams {
        val f = lecteur?.videoFormat
        val ratio = if (f != null && f.width > 0 && f.height > 0) {
            val r = (f.width.toFloat() / f.height).coerceIn(0.42f, 2.38f)
            android.util.Rational((r * 1000).toInt(), 1000)
        } else {
            android.util.Rational(16, 9)
        }
        val b = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(actionsPip())
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun actionsPip(): List<android.app.RemoteAction> {
        val enLecture = lecteur?.isPlaying == true
        fun bouton(code: Int, icone: Int, titre: String): android.app.RemoteAction {
            val pi = android.app.PendingIntent.getBroadcast(
                this, code,
                android.content.Intent(ACTION_PIP).setPackage(packageName)
                    .putExtra("code", code),
                android.app.PendingIntent.FLAG_IMMUTABLE or
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val ic = android.graphics.drawable.Icon.createWithResource(this, icone)
            return android.app.RemoteAction(ic, titre, titre, pi)
        }
        return listOf(
            bouton(PIP_RECULE, android.R.drawable.ic_media_rew, "Reculer"),
            if (enLecture) bouton(PIP_LECTURE, android.R.drawable.ic_media_pause, "Pause")
            else bouton(PIP_LECTURE, android.R.drawable.ic_media_play, "Lecture"),
            bouton(PIP_AVANCE, android.R.drawable.ic_media_ff, "Avancer")
        )
    }

    /** Réécrit les paramètres de la vignette (utile pour l'icône lecture/pause). */
    private fun rafraichitPip() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            isInPictureInPictureMode
        ) {
            try {
                setPictureInPictureParams(parametresPip())
            } catch (e: Exception) {
            }
        }
    }

    /** Passe la lecture en vignette flottante. */
    private fun entrePip() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        if (!pipDispo) return
        if (lecteur == null) return
        try {
            enterPictureInPictureMode(parametresPip())
        } catch (e: Exception) {
            // Certains constructeurs refusent le PiP malgré la feature déclarée.
        }
    }

    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        // Cette activité peut être recréée seule après une mise à mort du
        // système : elle ne peut pas compter sur MainActivity pour l'init.
        Reglages.init(this)

        // Les boutons de la vignette (reculer / lecture-pause / avancer)
        // reviennent ici par diffusion interne.
        recepteurPip = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                val p = lecteur ?: return
                when (i?.getIntExtra("code", 0)) {
                    PIP_LECTURE -> if (p.isPlaying) p.pause() else p.play()
                    PIP_RECULE -> p.seekTo((p.currentPosition - 15_000).coerceAtLeast(0))
                    PIP_AVANCE -> p.seekTo(p.currentPosition + 15_000)
                }
                rafraichitPip()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this, recepteurPip, android.content.IntentFilter(ACTION_PIP),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        etat?.let {
            reprendreIndex = it.getInt("ml_index", 0)
            reprendrePosition = it.getLong("ml_position", -1L)
            reprendreEnLecture = it.getBoolean("ml_lecture", true)
        }

        // Lecture immersive : l'image occupe tout l'écran, encoche comprise, et
        // les barres système s'effacent (un balayage les ramène). media3 décale
        // lui-même ses contrôles pour ne pas passer dessous ou sous l'encoche.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams
                .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val surTv = estTelevision(this)

        val liste = intent.getStringArrayListExtra("urls")
        val titres = intent.getStringArrayListExtra("titres")
        val url = intent.getStringExtra("url")
        if (liste.isNullOrEmpty() && url.isNullOrBlank()) {
            signaler("Aucune URL reçue")
            finish()
            return
        }

        setContent {
            // Le bouton « Quitter » suit l'apparition des contrôles de media3.
            var controlesVisibles by remember { mutableStateOf(true) }

            Box(Modifier.fillMaxSize().background(Color.Black)) {

                AndroidView(
                    factory = { contexte ->
                        PlayerView(contexte).also { vue ->
                            vuePlayer = vue
                            // L'écran allumé est géré par la fenêtre (voir
                            // repousseVeille) : un keepScreenOn sur la vue
                            // l'empêcherait de s'éteindre au bout des 2 h.
                            vue.keepScreenOn = false
                            // Le bouton CC de media3 : sélection native des
                            // sous-titres, dans la barre de contrôle, à côté
                            // de la roue des réglages qui gère l'audio.
                            vue.setShowSubtitleButton(true)
                            vue.setShowNextButton(true)
                            vue.setShowPreviousButton(true)
                            vue.controllerShowTimeoutMs = 6000
                            vue.setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { v ->
                                    controlesVisibles = v == android.view.View.VISIBLE
                                }
                            )
                            // Boutons INSÉRÉS dans la barre de media3, juste à
                            // gauche du CC, au lieu de flotter par-dessus :
                            // sinon la télécommande navigue entre les icônes de
                            // la barre et ne les atteint jamais. Sur téléphone
                            // le « Quitter » est un bouton Compose en haut à
                            // gauche (voir plus bas), plus naturel au doigt.
                            if (!titres.isNullOrEmpty()) {
                                insereBoutonBarre(vue, "☰", "Épisodes") { panneauOuvert = true }
                            }
                            if (surTv) {
                                insereBoutonBarre(vue, "✕", "Quitter") { finish() }
                            }
                        }
                    },
                    update = { it.player = lecteur },
                    modifier = Modifier.fillMaxSize()
                )

                val p = lecteur

                // Boutons flottants, visibles avec les contrôles, cachés en
                // PiP. Touche seulement — la TV a sa barre et sa télécommande.
                if (!surTv && controlesVisibles && !enPip) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .safeDrawingPadding()
                            .padding(start = 12.dp, top = 12.dp)
                    ) {
                        BoutonLecteur("✕  Quitter") { finish() }
                    }
                    if (pipDispo) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .safeDrawingPadding()
                                .padding(end = 12.dp, top = 12.dp)
                        ) {
                            BoutonLecteur("⧉  Vignette") { entrePip() }
                        }
                    }
                }

                // Le panneau se ferme d'abord : sinon la touche retour quitte
                // la lecture alors qu'on voulait juste refermer la liste.
                // BackHandler remplace onBackPressed, qui n'est plus appelé
                // pour les gestes de retour.
                BackHandler(enabled = panneauOuvert) { panneauOuvert = false }

                if (panneauOuvert && p != null) {
                    Panneau(
                        lecteur = p,
                        titresEpisodes = titres,
                        surFermer = { panneauOuvert = false },
                        surEpisode = { rang ->
                            p.seekTo(rang, C.TIME_UNSET)
                            p.play()
                            panneauOuvert = false
                        }
                    )
                }
            }
        }
    }

    /**
     * Ajoute un bouton texte dans la barre de contrôle de media3, à la place
     * du bouton sous-titres (les nôtres s'empilent juste à sa gauche). Focusable
     * pour la télécommande, cliquable au doigt.
     */
    private fun insereBoutonBarre(
        vue: PlayerView,
        texte: String,
        description: String,
        action: () -> Unit
    ) {
        // `exo_subtitle`, c'est le bouton CC. On calque notre bouton dessus —
        // même gabarit de vue, même rembourrage, même fond cliquable — pour
        // qu'il s'aligne exactement avec CC et la roue crantée dans la barre.
        val ancre = vue.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_subtitle)
        val barre = ancre?.parent as? android.view.ViewGroup ?: return
        val bouton = android.widget.TextView(vue.context).apply {
            text = texte
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            isFocusable = true
            isClickable = true
            contentDescription = description
            setPaddingRelative(
                ancre.paddingStart, ancre.paddingTop,
                ancre.paddingEnd, ancre.paddingBottom
            )
            ancre.background?.constantState?.newDrawable()?.let { background = it.mutate() }
            setOnClickListener { action() }
        }
        bouton.layoutParams = when (val lp = ancre.layoutParams) {
            is android.widget.LinearLayout.LayoutParams ->
                android.widget.LinearLayout.LayoutParams(lp)
            is android.view.ViewGroup.MarginLayoutParams ->
                android.view.ViewGroup.MarginLayoutParams(lp)
            else -> android.view.ViewGroup.LayoutParams(lp)
        }
        barre.addView(bouton, barre.indexOfChild(ancre))
    }

    override fun onStart() {
        super.onStart()

        val liste = intent.getStringArrayListExtra("urls")
        val url = intent.getStringExtra("url")
        if (liste.isNullOrEmpty() && url.isNullOrBlank()) return

        // Déjà lié (retour de PiP sans être vraiment sorti, l'Activity n'a
        // jamais été arrêtée pour de bon) : le lecteur tourne toujours dans
        // le service, rien à refaire.
        if (lienService != null) return

        val intentService = android.content.Intent(this, LecteurService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, intentService)
        val connexion = object : android.content.ServiceConnection {
            override fun onServiceConnected(
                nom: android.content.ComponentName?,
                binder: android.os.IBinder?
            ) {
                val service = (binder as LecteurService.Lien).service()
                lienService = service
                val p = service.assureLecteur()
                lecteur = p
                demarreLecture(p)
            }

            override fun onServiceDisconnected(nom: android.content.ComponentName?) {
                lienService = null
            }
        }
        connexionService = connexion
        bindService(intentService, connexion, android.content.Context.BIND_AUTO_CREATE)
    }

    /**
     * Configure une session de lecture toute neuve sur le lecteur fourni par
     * le service : pistes, reprise, minuteur de sauvegarde. Appelé une seule
     * fois par lancement (pas à chaque retour de PiP).
     */
    private fun demarreLecture(p: ExoPlayer) {
        val liste = intent.getStringArrayListExtra("urls")
        val url = intent.getStringExtra("url")

        appliquePreferences(p)

        p.addListener(object : Player.Listener {
            override fun onPlayerError(erreur: PlaybackException) {
                signaler("${erreur.errorCodeName} : ${erreur.message}")
            }

            /** L'icône lecture/pause de la vignette suit l'état réel du lecteur. */
            override fun onIsPlayingChanged(enLecture: Boolean) {
                rafraichitPip()
            }

            /**
             * Retient la langue choisie pour l'audio et les sous-titres, pour
             * que `appliquePreferences()` la reprenne à l'épisode suivant.
             * On retient la LANGUE, jamais le numéro de piste : l'ordre change
             * d'un fichier à l'autre.
             */
            override fun onTracksChanged(pistes: androidx.media3.common.Tracks) {
                for (groupe in pistes.groups) {
                    if (!groupe.isSelected) continue
                    if (groupe.type != C.TRACK_TYPE_AUDIO && groupe.type != C.TRACK_TYPE_TEXT) continue
                    for (i in 0 until groupe.length) {
                        if (!groupe.isTrackSelected(i)) continue
                        val langue = groupe.getTrackFormat(i).language ?: continue
                        if (langue != "und" && langue.length in 2..3) {
                            Reglages.enregistreLangue(groupe.type, langue)
                        }
                    }
                }
            }
        })

        // Retour dans l'appli / recréation : on a un point de reprise exact.
        val repriseExacte = reprendrePosition >= 0

        if (!liste.isNullOrEmpty()) {
            p.setMediaItems(liste.map { MediaItem.fromUri(it) })
            val idx = (if (repriseExacte) reprendreIndex else intent.getIntExtra("index", 0))
                .coerceIn(0, liste.size - 1)
            p.seekTo(idx, if (repriseExacte) reprendrePosition else C.TIME_UNSET)
        } else {
            p.setMediaItem(MediaItem.fromUri(url!!))
            if (repriseExacte) p.seekTo(reprendrePosition)
        }

        if (repriseExacte) {
            // On reprend pile où on en était, sans redemander.
            p.prepare()
            p.playWhenReady = reprendreEnLecture
        } else {
            // Premier lancement. Reprise : au-delà d'une minute on demande,
            // comme sous Windows. Entre trente secondes et une minute on
            // repart directement, l'écart est trop faible pour une question.
            val reprise = intent.getIntExtra("reprise", 0)
            if (reprise > 60) {
                demandeReprise(p, reprise)
            } else {
                if (reprise > 30) p.seekTo(reprise * 1000L)
                p.prepare()
                p.playWhenReady = true
            }
        }

        // Toutes les quinze secondes : une coupure ou une mise à mort par le
        // système ne doit pas faire perdre la progression.
        val tic = object : Runnable {
            override fun run() {
                enregistrePosition()
                window.decorView.postDelayed(this, 15000)
            }
        }
        minuteur = tic
        window.decorView.postDelayed(tic, 15000)
    }

    /** Boîte de reprise, sur le modèle de demandeReprise() dans player.js. */
    private fun demandeReprise(p: ExoPlayer, secondes: Int) {
        val minutes = secondes / 60
        val restant = secondes % 60
        val position = if (minutes > 0) "$minutes min $restant s" else "$restant s"

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Reprendre la lecture ?")
            .setMessage("Tu t'étais arrêté à $position.")
            .setPositiveButton("Reprendre") { _, _ ->
                p.seekTo(secondes * 1000L)
                p.prepare()
                p.playWhenReady = true
            }
            .setNegativeButton("Depuis le début") { _, _ ->
                p.prepare()
                p.playWhenReady = true
            }
            .setCancelable(false)
            .show()
    }

    /** Applique la langue préférée dès l'ouverture. */
    private fun appliquePreferences(p: ExoPlayer) {
        val audio = Reglages.languePreferee(C.TRACK_TYPE_AUDIO)
        val sous = Reglages.languePreferee(C.TRACK_TYPE_TEXT)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(audio)
            .setPreferredTextLanguage(sous)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, sous == null)
            .build()
    }

    /**
     * Le panneau s'ouvre au bouton MENU, à la touche jaune, à la touche
     * sous-titres, ou avec T et S. Cinq entrées parce qu'aucune de ces touches
     * n'existe sur toutes les télécommandes.
     */
    override fun onKeyDown(code: Int, evenement: KeyEvent?): Boolean {
        when (code) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_CAPTIONS,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_S -> {
                panneauOuvert = !panneauOuvert
                return true
            }
        }
        return super.onKeyDown(code, evenement)
    }

    /* ------------------------------------------------------- Mise en veille
     *
     * Pendant la lecture, l'écran reste allumé. Mais si personne ne touche au
     * téléphone (ou à la télécommande) pendant DEUX HEURES, on laisse le
     * système l'éteindre normalement : on s'est sûrement endormi devant.
     * Chaque contact repart pour deux heures.
     */
    private val DELAI_VEILLE_MS = 2L * 60 * 60 * 1000
    private val finVeille = Runnable {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun repousseVeille() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.removeCallbacks(finVeille)
        window.decorView.postDelayed(finVeille, DELAI_VEILLE_MS)
    }

    /** Toucher l'écran ou appuyer sur une touche : deux heures de plus. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        repousseVeille()
    }

    override fun onResume() {
        super.onResume()
        repousseVeille()
    }

    override fun onStop() {
        super.onStop()
        // En PiP, l'activité est « arrêtée » mais la vignette joue encore : on
        // ne touche à rien. onStop est rappelé, sans le PiP, quand la vignette
        // est fermée — c'est là qu'on relâche.
        if (isInPictureInPictureMode) return
        window.decorView.removeCallbacks(finVeille)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        minuteur?.let { window.decorView.removeCallbacks(it) }
        // Où on en est, AVANT de relâcher : c'est ce que onStart relira au
        // retour dans l'appli. Sauf si le lecteur n'a pas encore démarré
        // (boîte « reprendre ? » ouverte) : là on ne veut pas figer un 0.
        lecteur?.takeIf { it.playbackState != Player.STATE_IDLE }?.let {
            reprendreIndex = it.currentMediaItemIndex
            reprendrePosition = it.currentPosition.coerceAtLeast(0)
            reprendreEnLecture = it.playWhenReady
        }
        // Synchrone ici : l'écran quitte déjà, une micro-pause ne se voit pas,
        // et on veut la position finale écrite avant une éventuelle mise à mort.
        enregistrePosition(async = false)
        // Sortie réelle (pas juste un passage en PiP) : le service n'a plus
        // de raison de tourner, on le relâche pour de bon.
        lienService?.libere()
        connexionService?.let { runCatching { unbindService(it) } }
        connexionService = null
        lienService = null
        lecteur = null
    }

    override fun onSaveInstanceState(sortie: Bundle) {
        super.onSaveInstanceState(sortie)
        val p = lecteur
        if (p != null && p.playbackState != Player.STATE_IDLE) {
            sortie.putInt("ml_index", p.currentMediaItemIndex)
            sortie.putLong("ml_position", p.currentPosition.coerceAtLeast(0))
            sortie.putBoolean("ml_lecture", p.playWhenReady)
        } else if (reprendrePosition >= 0) {
            sortie.putInt("ml_index", reprendreIndex)
            sortie.putLong("ml_position", reprendrePosition)
            sortie.putBoolean("ml_lecture", reprendreEnLecture)
        }
    }

    /** Quitter l'appli pendant la lecture bascule en vignette flottante. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!panneauOuvert && lecteur?.isPlaying == true) entrePip()
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        enPictureInPicture: Boolean,
        config: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(enPictureInPicture, config)
        enPip = enPictureInPicture
        // En vignette : pas de barre de contrôle, pas de panneau.
        vuePlayer?.useController = !enPictureInPicture
        if (enPictureInPicture) panneauOuvert = false
    }

    override fun onDestroy() {
        super.onDestroy()
        recepteurPip?.let { runCatching { unregisterReceiver(it) } }
        ecrivainPosition.shutdown()
        // Simple précaution : onStop() a normalement déjà tout dénoué. S'il
        // reste un lien (l'Activity a été détruite pendant un PiP, sans passer
        // par la branche de sortie réelle), on se contente de se délier — le
        // service continue de jouer, ce n'est pas une vraie sortie.
        connexionService?.let { runCatching { unbindService(it) } }
        connexionService = null
        lienService = null
    }

    /** Écrit les positions hors du fil principal pour ne pas figer la lecture. */
    private val ecrivainPosition = java.util.concurrent.Executors.newSingleThreadExecutor()

    /**
     * La position est notée en quittant, mais AUSSI périodiquement : le
     * Chromecast tue les applications sans prévenir, et onStop n'est alors
     * jamais appelé.
     */
    private fun enregistrePosition(async: Boolean = true) {
        val p = lecteur ?: return
        // Les valeurs du lecteur se lisent sur le fil principal…
        val url = intent.getStringArrayListExtra("urls")
            ?.getOrNull(p.currentMediaItemIndex)
            ?: intent.getStringExtra("url")
            ?: return
        val duree = p.duration
        if (duree <= 0) return
        val secondes = (p.currentPosition / 1000).toInt()
        val dureeSec = (duree / 1000).toInt()
        val origine = intent.getStringExtra("origine")
        // …mais l'écriture (relecture + réécriture de JSON dans les
        // préférences) part hors du fil principal quand c'est le tic de 15 s.
        if (async) {
            ecrivainPosition.execute {
                Reglages.enregistrePosition(url, secondes, dureeSec, origine)
            }
        } else {
            Reglages.enregistrePosition(url, secondes, dureeSec, origine)
        }
    }

    private fun signaler(message: String) {
        Log.e("MrFlix", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

/* ------------------------------------------------------------------ Panneau */

@OptIn(UnstableApi::class)
@Composable
private fun Panneau(
    lecteur: ExoPlayer,
    titresEpisodes: List<String>?,
    surFermer: () -> Unit,
    surEpisode: (Int) -> Unit
) {
    val premier = remember { FocusRequester() }

    LaunchedEffect(Unit) { premier.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .safeDrawingPadding(),
        contentAlignment = Alignment.CenterEnd
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight()
                .background(SURFACE.copy(alpha = 0.97f))
                .border(1.dp, LIGNE),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            item { TitreSection("Épisodes") }

            itemsIndexed(titresEpisodes ?: emptyList()) { rang, nom ->
                LignePanneau(
                    texte = nom,
                    choisie = rang == lecteur.currentMediaItemIndex,
                    modifier = if (rang == 0) Modifier.focusRequester(premier) else Modifier
                ) { surEpisode(rang) }
            }

            item {
                Spacer(Modifier.height(18.dp))
                Explication(
                    "Les pistes audio et les sous-titres se choisissent dans la " +
                            "barre de lecture : la roue pour l'audio, l'icône CC pour " +
                            "les sous-titres."
                )
            }

            item {
                Spacer(Modifier.height(20.dp))
                LignePanneau("Fermer", false, surClic = surFermer)
            }
        }
    }
}

@Composable
private fun TitreSection(texte: String) {
    Text(
        text = texte.uppercase(),
        color = DISCRET,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun Explication(texte: String) {
    Text(texte, color = DISCRET, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
private fun LignePanneau(
    texte: String,
    choisie: Boolean,
    modifier: Modifier = Modifier,
    surClic: () -> Unit
) {
    var enFocus by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { enFocus = it.isFocused }
            .clickable(onClick = surClic)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enFocus) CARTE else Color.Transparent)
            .border(
                width = if (enFocus) 2.dp else 0.dp,
                color = if (enFocus) Color.White else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = (if (choisie) "•  " else "     ") + texte,
            color = if (choisie) ACCENT else Color.White,
            fontSize = 15.sp
        )
    }
}

/** Pastille sombre semi-transparente, pour le bouton « Quitter » sur l'image. */
@Composable
private fun BoutonLecteur(texte: String, surClic: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = surClic)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(texte, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}