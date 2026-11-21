package com.mrflix.tv

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Le lecteur vit ici, pas dans LecteurActivity : un service au premier plan
 * avec une session média est ce que le système — et surtout les gestionnaires
 * de batterie des constructeurs (Samsung, Xiaomi, Huawei…) — reconnaissent
 * comme une lecture active à ne pas couper quand l'appli n'est plus au
 * premier plan (PiP, écran éteint). Une simple Activity en PiP n'a pas ce
 * statut protégé et peut se faire mettre en pause ou tuer après un moment.
 *
 * L'Activity ne fait que s'y relier pour récupérer le lecteur et afficher
 * l'image ; elle ne le crée ni ne le détruit plus elle-même.
 */
@OptIn(UnstableApi::class)
class LecteurService : MediaSessionService() {

    var lecteur: ExoPlayer? = null
        private set
    private var session: MediaSession? = null

    private val CANAL_ID = "lecture"
    private val NOTIF_ID = 1

    inner class Lien : android.os.Binder() {
        fun service(): LecteurService = this@LecteurService
    }
    private val lien = Lien()

    /** Crée le lecteur au premier appel d'une session de lecture ; le renvoie tel quel ensuite. */
    fun assureLecteur(): ExoPlayer {
        lecteur?.let { return it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Config.AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        lecteur = p
        session = MediaSession.Builder(this, p).build()
        // Avoir une session ne suffit pas : sans un vrai passage au premier
        // plan (notification + startForeground), le système ne nous traite
        // pas différemment d'une appli en arrière-plan ordinaire.
        passeAuPremierPlan()
        return p
    }

    private fun passeAuPremierPlan() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val gestionnaire = getSystemService(android.app.NotificationManager::class.java)
            if (gestionnaire.getNotificationChannel(CANAL_ID) == null) {
                gestionnaire.createNotificationChannel(
                    android.app.NotificationChannel(
                        CANAL_ID, "Lecture en cours",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val notif = android.app.Notification.Builder(this, CANAL_ID)
            .setContentTitle("MrFlix")
            .setContentText("Lecture en cours")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onBind(intent: android.content.Intent?): android.os.IBinder? {
        // Le protocole media3 (contrôleurs système, notification) passe par
        // le binder standard ; LecteurActivity se lie avec le sien pour
        // récupérer directement l'instance d'ExoPlayer.
        return if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else lien
    }

    /** Fin réelle de la lecture (l'utilisateur quitte) : on relâche et on s'arrête. */
    fun libere() {
        session?.run {
            player.release()
            release()
        }
        session = null
        lecteur = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        lecteur = null
        super.onDestroy()
    }
}
