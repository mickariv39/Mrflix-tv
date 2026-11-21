package com.mrflix.tv

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import kotlin.coroutines.coroutineContext

/**
 * Appairage par le réseau local.
 *
 * Le boîtier ouvre un petit serveur HTTP et affiche un QR code contenant son
 * adresse. Le téléphone, sur le même wifi, ouvre la page et saisit les
 * identifiants du fournisseur. Le boîtier les enregistre et referme tout.
 *
 * Pas de serveur distant, pas d'hébergement, pas de compte : le seul prérequis
 * est que le téléphone et le boîtier soient sur le même réseau. Pour installer
 * chez soi ou chez quelqu'un, on est de toute façon devant la télé.
 *
 * Ce que ça remplace : des identifiants compilés dans l'APK, extractibles en
 * quelques minutes par qui possède le fichier. L'application peut désormais
 * être distribuée sans distribuer l'abonnement.
 */
object Appairage {

    /** Au-dessus de 1024, aucun privilège nécessaire ; assez haut pour être libre. */
    const val PORT = 8099

    /**
     * Au-delà, on referme. Un serveur laissé ouvert sur le réseau est une porte
     * dérobée : n'importe qui sur le même wifi pourrait reconfigurer le boîtier.
     */
    private const val DUREE_MAX_MS = 5 * 60 * 1000L

    /** Le temps d'attente d'une connexion, pour rendre la boucle interruptible. */
    private const val ATTENTE_MS = 1000

    data class Identifiants(
        val hote: String,
        val utilisateur: String,
        val motDePasse: String,
        val cleTmdb: String
    )

    /**
     * L'adresse du boîtier sur le réseau local, ou null s'il n'est pas connecté.
     *
     * On parcourt les interfaces plutôt que d'interroger le gestionnaire wifi :
     * ça évite une permission, et ça marche aussi en Ethernet — les Chromecast
     * branchés en filaire ne sont pas rares.
     */
    fun adresseLocale(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    /** L'adresse à mettre dans le QR code. */
    fun url(adresse: String) = "http://$adresse:$PORT"

    /**
     * Ouvre le serveur et attend une saisie. Renvoie les identifiants reçus,
     * ou null si le délai est passé ou si l'attente a été annulée.
     *
     * Une SEULE saisie est acceptée : le serveur se ferme aussitôt après.
     */
    suspend fun attend(): Identifiants? = withContext(Dispatchers.IO) {
        var recu: Identifiants? = null
        val debut = System.currentTimeMillis()

        try {
            ServerSocket().use { serveur ->
                // SO_REUSEADDR avant le bind : sans lui, la prise d'un écran
                // d'appairage à peine quitté reste en TIME_WAIT ~1 s et le
                // nouveau bind échoue.
                serveur.reuseAddress = true
                serveur.bind(java.net.InetSocketAddress(PORT))
                // Sans ce délai, accept() bloque indéfiniment et la coroutine ne
                // peut plus être annulée : quitter l'écran laisserait le serveur
                // ouvert pour toujours.
                serveur.soTimeout = ATTENTE_MS

                while (coroutineContext.isActive && recu == null) {
                    if (System.currentTimeMillis() - debut > DUREE_MAX_MS) break

                    val client = try {
                        serveur.accept()
                    } catch (e: SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        break
                    }

                    client.use { prise ->
                        try {
                            prise.soTimeout = 5000
                            recu = traite(prise.getInputStream(), prise.getOutputStream())
                        } catch (e: Exception) {
                            // Une requête malformée ne doit pas fermer le serveur :
                            // un navigateur envoie aussi des demandes parasites
                            // (favicon, sondes de connectivité).
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Port déjà pris (un écran d'appairage à peine quitté met jusqu'à
            // une seconde à libérer la prise), pile réseau indisponible : on
            // renvoie null plutôt que de laisser l'exception remonter du
            // LaunchedEffect et fermer l'application.
            return@withContext null
        }
        recu
    }

    /**
     * Une requête HTTP, lue et répondue à la main.
     *
     * Deux cas seulement : GET renvoie le formulaire, POST lit les champs.
     * Écrire ça à la main plutôt que d'embarquer un serveur complet évite une
     * dépendance de plusieurs mégaoctets pour deux pages.
     */
    private fun traite(
        entree: java.io.InputStream,
        sortie: java.io.OutputStream
    ): Identifiants? {
        val lecteur = BufferedReader(InputStreamReader(entree, Charsets.UTF_8))
        val premiere = lecteur.readLine() ?: return null
        val methode = premiere.substringBefore(' ')

        var taille = 0
        while (true) {
            val ligne = lecteur.readLine() ?: break
            if (ligne.isEmpty()) break
            if (ligne.startsWith("Content-Length:", ignoreCase = true)) {
                taille = ligne.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }

        if (methode != "POST" || taille <= 0) {
            repond(sortie, PAGE_FORMULAIRE)
            return null
        }

        // Le corps est lu caractère par caractère : readLine() attendrait un
        // retour à la ligne que le dernier champ n'a pas.
        val corps = CharArray(taille)
        var lus = 0
        while (lus < taille) {
            val n = lecteur.read(corps, lus, taille - lus)
            if (n < 0) break
            lus += n
        }

        val champs = champsDe(String(corps, 0, lus))
        val hote = champs["hote"]?.trim().orEmpty()
        val utilisateur = champs["utilisateur"]?.trim().orEmpty()
        val motDePasse = champs["motdepasse"]?.trim().orEmpty()

        if (hote.isEmpty() || utilisateur.isEmpty() || motDePasse.isEmpty()) {
            repond(sortie, pageErreur("Tous les champs sont obligatoires."))
            return null
        }

        repond(sortie, PAGE_MERCI)
        return Identifiants(
            hote = normaliseHote(hote),
            utilisateur = utilisateur,
            motDePasse = motDePasse,
            cleTmdb = champs["tmdb"]?.trim().orEmpty()
        )
    }

    /**
     * L'hôte tel que Xtream l'attend : un préfixe et pas de barre finale.
     *
     * Une adresse recopiée depuis un courriel arrive souvent avec un « / » à la
     * fin, ou sans « http:// ». Les deux cassent la construction des URL.
     *
     * Public : le formulaire de saisie directe (téléphone) l'utilise aussi.
     */
    fun normaliseHote(brut: String): String {
        var h = brut.trim().trimEnd('/')
        if (!h.startsWith("http://") && !h.startsWith("https://")) h = "http://$h"
        return h
    }

    private fun champsDe(corps: String): Map<String, String> =
        corps.split('&').mapNotNull { paire ->
            val i = paire.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val cle = decode(paire.substring(0, i))
            val valeur = decode(paire.substring(i + 1))
            cle to valeur
        }.toMap()

    private fun decode(t: String): String = try {
        URLDecoder.decode(t.replace('+', ' '), "UTF-8")
    } catch (e: Exception) {
        t
    }

    private fun repond(sortie: java.io.OutputStream, corps: String) {
        val octets = corps.toByteArray(Charsets.UTF_8)
        val entete = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${octets.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        sortie.write(entete.toByteArray(Charsets.US_ASCII))
        sortie.write(octets)
        sortie.flush()
    }

    /* ------------------------------------------------------------- Le QR code */

    /**
     * Le QR code de l'adresse, en noir sur blanc.
     *
     * Fond blanc et non transparent : beaucoup de lecteurs de QR échouent sur
     * un code clair posé sur un fond sombre, et l'application est noire.
     */
    fun qrCode(texte: String, tailleP: Int = 480): Bitmap? = try {
        val matrice = QRCodeWriter().encode(
            texte,
            BarcodeFormat.QR_CODE,
            tailleP,
            tailleP,
            mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
        val image = Bitmap.createBitmap(tailleP, tailleP, Bitmap.Config.RGB_565)
        val pixels = IntArray(tailleP * tailleP)
        for (y in 0 until tailleP) {
            val ligne = y * tailleP
            for (x in 0 until tailleP) {
                pixels[ligne + x] = if (matrice[x, y]) Color.BLACK else Color.WHITE
            }
        }
        image.setPixels(pixels, 0, tailleP, 0, 0, tailleP, tailleP)
        image
    } catch (e: Exception) {
        null
    }

    /* ----------------------------------------------------------- Les deux pages */

    private const val STYLE = """
        <style>
          :root { color-scheme: dark; }
          body { margin:0; padding:24px; background:#0b0b0b; color:#fff;
                 font-family:system-ui,-apple-system,sans-serif; }
          h1 { font-size:22px; margin:0 0 4px; }
          p.sous { color:#9a9a9a; font-size:14px; margin:0 0 24px; }
          label { display:block; font-size:14px; color:#c8c8c8; margin:16px 0 6px; }
          input { width:100%; box-sizing:border-box; padding:14px;
                  font-size:16px; border-radius:8px; border:1px solid #333;
                  background:#161616; color:#fff; }
          button { width:100%; margin-top:28px; padding:16px; font-size:17px;
                   font-weight:600; border:0; border-radius:8px;
                   background:#fff; color:#000; }
          .note { margin-top:20px; font-size:13px; color:#7a7a7a; }
          .ok { font-size:20px; margin-top:40px; }
        </style>
    """

    private val PAGE_FORMULAIRE = """
        <!doctype html><html lang="fr"><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>MrFlix — appairage</title>$STYLE</head><body>
        <h1>Connecter MrFlix</h1>
        <p class="sous">Saisis les accès de ton fournisseur. Ils resteront sur le boîtier.</p>
        <form method="post" action="/">
          <label for="hote">Adresse du serveur</label>
          <input id="hote" name="hote" inputmode="url" autocapitalize="off"
                 autocomplete="off" placeholder="http://exemple.com:8080" required>
          <label for="utilisateur">Utilisateur</label>
          <input id="utilisateur" name="utilisateur" autocapitalize="off"
                 autocomplete="off" required>
          <label for="motdepasse">Mot de passe</label>
          <input id="motdepasse" name="motdepasse" type="password"
                 autocomplete="off" required>
          <label for="tmdb">Clé TMDB (facultatif)</label>
          <input id="tmdb" name="tmdb" autocapitalize="off" autocomplete="off"
                 placeholder="laisser vide pour garder l'actuelle">
          <button type="submit">Connecter</button>
        </form>
        <p class="note">Cette page n'est accessible que depuis ton réseau local,
        et se ferme dès que la connexion est faite.</p>
        </body></html>
    """.trimIndent()

    private val PAGE_MERCI = """
        <!doctype html><html lang="fr"><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>MrFlix</title>$STYLE</head><body>
        <h1>C'est fait</h1>
        <p class="ok">Tu peux revenir à la télévision, le catalogue se charge.</p>
        </body></html>
    """.trimIndent()

    private fun pageErreur(message: String) = """
        <!doctype html><html lang="fr"><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>MrFlix</title>$STYLE</head><body>
        <h1>Impossible</h1>
        <p class="sous">$message</p>
        <p><a href="/" style="color:#fff">Revenir au formulaire</a></p>
        </body></html>
    """.trimIndent()
}
