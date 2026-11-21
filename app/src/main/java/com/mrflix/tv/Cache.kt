package com.mrflix.tv

import android.content.Context
import java.io.File

/**
 * Cache disque des réponses du serveur.
 *
 * Même principe que sur Windows : le catalogue s'affiche immédiatement depuis
 * le disque, puis se rafraîchit en arrière-plan.
 *
 * On expose le FICHIER, jamais son contenu : les réponses font plusieurs
 * dizaines de méga-octets et ne doivent pas transiter par la mémoire.
 */
object Cache {

    private lateinit var dossier: File

    fun init(contexte: Context) {
        dossier = File(contexte.cacheDir, "catalogue").apply { mkdirs() }
    }

    fun fichier(action: String) = File(dossier, action.replace("&", "_") + ".json")

    fun existe(action: String): Boolean {
        val f = fichier(action)
        return f.exists() && f.length() > 0
    }

    fun vide() {
        dossier.listFiles()?.forEach { it.delete() }
    }

    /** Place occupée par le cache, en méga-octets. */
    val tailleMo: Long
        get() = (dossier.listFiles()?.sumOf { it.length() } ?: 0) / (1024 * 1024)

    /**
     * Âge du fichier de cache le plus récent, en minutes. `Long.MAX_VALUE` si
     * le cache est vide : dans ce cas il faut bien aller chercher le catalogue.
     */
    val ageMinutes: Long
        get() {
            val recent = dossier.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                ?.maxOfOrNull { it.lastModified() }
                ?: return Long.MAX_VALUE
            return (System.currentTimeMillis() - recent) / 60_000
        }
}
