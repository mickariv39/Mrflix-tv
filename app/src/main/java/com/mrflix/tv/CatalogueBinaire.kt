package com.mrflix.tv

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.IdentityHashMap

/**
 * Cache binaire du catalogue DÉJÀ analysé et filtré.
 *
 * L'analyse JSON + expressions régulières de Xtream.tout() prend plusieurs
 * secondes sur le boîtier. Une fois faite, on écrit le résultat dans un
 * fichier compact ; au lancement suivant, tant que les fichiers source et les
 * catégories cochées n'ont pas bougé, on le relit directement — plus de
 * parsing, plus de regex, juste des `readUTF` et la reconstruction des listes.
 *
 * Un fichier par profil (les catégories masquées lui sont propres). Toute
 * incohérence -> `lire()` renvoie null et Xtream reprend la main ; le fichier
 * est réécrit après chaque analyse réussie, donc il se répare de lui-même.
 *
 * Le partage d'instances de Film / Chaine entre les rangées « par genre » et
 * « par plateforme » est préservé : on écrit chaque objet UNE fois dans un
 * pool, les rangées ne stockent que des indices.
 */
object CatalogueBinaire {

    private const val MAGIC = 0x4D52464C   // "MRFL"
    private const val VERSION = 1

    private lateinit var dossier: File

    fun init(contexte: Context) {
        dossier = File(contexte.filesDir, "catalogue-bin").apply { mkdirs() }
    }

    private fun fichier(idProfil: String) = File(dossier, "cat_$idProfil.bin")

    /** Ce qui, s'il change, périme le binaire : l'état des 3 fichiers source
     *  et les garde-fous de Config qui pilotent le filtrage. */
    private fun empreinteSource(): Long {
        var h = 1125899906842597L
        for (a in arrayOf("get_vod_streams", "get_series", "get_live_streams")) {
            val f = Cache.fichier(a)
            h = h * 31 + f.length()
            h = h * 31 + f.lastModified()
        }
        for (n in intArrayOf(
            Config.CATEGORIES_MAX, Config.FILMS_PAR_CATEGORIE, Config.TITRES_PAR_PLATEFORME,
            Config.CHAINES_PAR_CATEGORIE, if (Config.CORRIGER_TITRES) 1 else 0
        )) h = h * 31 + n
        return h
    }

    private fun empreinteFiltre(): Int = Reglages.masquees.sorted().hashCode()

    /* ----------------------------------------------------------------- Lire */

    /** Une taille lue depuis le fichier, refusée si absurde : un fichier
     *  corrompu ne doit pas provoquer une allocation géante (OOM). */
    private fun DataInputStream.taille(): Int {
        val n = readInt()
        if (n < 0 || n > 2_000_000) throw java.io.IOException("taille aberrante: $n")
        return n
    }

    fun lire(idProfil: String): CatalogueComplet? {
        val f = fichier(idProfil)
        if (!f.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(f.inputStream(), 1 shl 16)).use { e ->
                if (e.readInt() != MAGIC || e.readInt() != VERSION) return null
                if (e.readLong() != empreinteSource()) return null
                if (e.readInt() != empreinteFiltre()) return null

                val films = Array(e.taille()) { litFilm(e) }
                val chaines = Array(e.taille()) { litChaine(e) }

                val rFilms = litRangees(e, films)
                val rSeries = litRangees(e, films)
                val rChaines = litRangeesChaines(e, chaines)
                val cats = List(e.taille()) {
                    CategorieBrute(e.readUTF(), e.readUTF(), e.readUTF(), e.readUTF(), e.readInt())
                }
                val pFilms = litGroupesPlateforme(e, films)
                val pSeries = litGroupesPlateforme(e, films)
                val bouquets = List(e.taille()) {
                    val id = e.readUTF()
                    GroupeBouquet(id, litRangeesChaines(e, chaines))
                }
                CatalogueComplet(rFilms, rSeries, rChaines, cats, pFilms, pSeries, bouquets)
            }
        } catch (ex: Throwable) {
            f.delete()
            null
        }
    }

    private fun litFilm(e: DataInputStream) = Film(
        id = e.readUTF(), nom = e.readUTF(), extension = e.readUTF(),
        affiche = e.readUTF().ifEmpty { null }, categorie = e.readUTF(),
        genre = e.readUTF(), plateforme = e.readUTF(),
        estSerie = e.readBoolean(), ajoute = e.readLong()
    )

    private fun litChaine(e: DataInputStream) = Chaine(
        id = e.readUTF(), nom = e.readUTF(), logo = e.readUTF().ifEmpty { null },
        categorie = e.readUTF(), bouquet = e.readUTF(),
        archive = e.readBoolean(), joursArchive = e.readInt()
    )

    private fun litRangees(e: DataInputStream, pool: Array<Film>): List<Rangee> =
        List(e.taille()) {
            val titre = e.readUTF()
            Rangee(titre, List(e.taille()) { pool[e.readInt()] })
        }

    private fun litRangeesChaines(e: DataInputStream, pool: Array<Chaine>): List<RangeeChaines> =
        List(e.taille()) {
            val titre = e.readUTF()
            RangeeChaines(titre, List(e.taille()) { pool[e.readInt()] })
        }

    private fun litGroupesPlateforme(e: DataInputStream, pool: Array<Film>): List<GroupePlateforme> =
        List(e.taille()) {
            val id = e.readUTF()
            GroupePlateforme(id, List(e.taille()) { pool[e.readInt()] })
        }

    /* --------------------------------------------------------------- Écrire */

    fun ecrire(idProfil: String, cat: CatalogueComplet) {
        try {
            val idxFilm = IdentityHashMap<Film, Int>()
            val idxChaine = IdentityHashMap<Chaine, Int>()
            fun fi(f: Film) { idxFilm.getOrPut(f) { idxFilm.size } }
            fun ci(c: Chaine) { idxChaine.getOrPut(c) { idxChaine.size } }

            (cat.films + cat.series).forEach { r -> r.films.forEach { fi(it) } }
            (cat.plateformesFilms + cat.plateformesSeries).forEach { g -> g.titres.forEach { fi(it) } }
            cat.chaines.forEach { r -> r.chaines.forEach { ci(it) } }
            cat.bouquets.forEach { g -> g.rangees.forEach { r -> r.chaines.forEach { ci(it) } } }

            val films = arrayOfNulls<Film>(idxFilm.size)
            idxFilm.forEach { (f, i) -> films[i] = f }
            val chaines = arrayOfNulls<Chaine>(idxChaine.size)
            idxChaine.forEach { (c, i) -> chaines[i] = c }

            val provisoire = File(dossier, "cat_$idProfil.bin.part")
            DataOutputStream(BufferedOutputStream(provisoire.outputStream(), 1 shl 16)).use { s ->
                s.writeInt(MAGIC); s.writeInt(VERSION)
                s.writeLong(empreinteSource()); s.writeInt(empreinteFiltre())

                s.writeInt(films.size); films.forEach { ecritFilm(s, it!!) }
                s.writeInt(chaines.size); chaines.forEach { ecritChaine(s, it!!) }

                ecritRangees(s, cat.films, idxFilm)
                ecritRangees(s, cat.series, idxFilm)
                ecritRangeesChaines(s, cat.chaines, idxChaine)
                s.writeInt(cat.categories.size)
                cat.categories.forEach {
                    s.writeUTF(it.cle); s.writeUTF(it.nom); s.writeUTF(it.type)
                    s.writeUTF(it.langue); s.writeInt(it.nombre)
                }
                ecritGroupesPlateforme(s, cat.plateformesFilms, idxFilm)
                ecritGroupesPlateforme(s, cat.plateformesSeries, idxFilm)
                s.writeInt(cat.bouquets.size)
                cat.bouquets.forEach { g ->
                    s.writeUTF(g.id)
                    ecritRangeesChaines(s, g.rangees, idxChaine)
                }
            }
            val cible = fichier(idProfil)
            if (cible.exists()) cible.delete()
            provisoire.renameTo(cible)
        } catch (e: Exception) {
            // Un cache binaire qui échoue ne doit jamais empêcher l'affichage.
        }
    }

    private fun ecritFilm(s: DataOutputStream, f: Film) {
        s.writeUTF(f.id); s.writeUTF(f.nom); s.writeUTF(f.extension)
        s.writeUTF(f.affiche ?: ""); s.writeUTF(f.categorie)
        s.writeUTF(f.genre); s.writeUTF(f.plateforme)
        s.writeBoolean(f.estSerie); s.writeLong(f.ajoute)
    }

    private fun ecritChaine(s: DataOutputStream, c: Chaine) {
        s.writeUTF(c.id); s.writeUTF(c.nom); s.writeUTF(c.logo ?: "")
        s.writeUTF(c.categorie); s.writeUTF(c.bouquet)
        s.writeBoolean(c.archive); s.writeInt(c.joursArchive)
    }

    private fun ecritRangees(s: DataOutputStream, rangees: List<Rangee>, idx: IdentityHashMap<Film, Int>) {
        s.writeInt(rangees.size)
        rangees.forEach { r ->
            s.writeUTF(r.titre); s.writeInt(r.films.size)
            r.films.forEach { s.writeInt(idx[it]!!) }
        }
    }

    private fun ecritRangeesChaines(s: DataOutputStream, rangees: List<RangeeChaines>, idx: IdentityHashMap<Chaine, Int>) {
        s.writeInt(rangees.size)
        rangees.forEach { r ->
            s.writeUTF(r.titre); s.writeInt(r.chaines.size)
            r.chaines.forEach { s.writeInt(idx[it]!!) }
        }
    }

    private fun ecritGroupesPlateforme(s: DataOutputStream, groupes: List<GroupePlateforme>, idx: IdentityHashMap<Film, Int>) {
        s.writeInt(groupes.size)
        groupes.forEach { g ->
            s.writeUTF(g.id); s.writeInt(g.titres.size)
            g.titres.forEach { s.writeInt(idx[it]!!) }
        }
    }

    fun vide() {
        try { dossier.listFiles()?.forEach { it.delete() } } catch (e: Exception) { }
    }
}
