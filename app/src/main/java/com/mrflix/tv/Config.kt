package com.mrflix.tv

/**
 * Réglages fixes de l'application.
 *
 * Les accès du fournisseur ne sont plus compilés dans l'APK : ils viennent de
 * l'appairage par QR code, enregistrés dans `Reglages`. C'est ce qui permet
 * de distribuer l'application sans distribuer l'abonnement — une valeur passée
 * par `BuildConfig` reste extractible en quelques minutes du fichier APK.
 *
 * Ce sont des `get()` et non des `val` : lus à chaque appel, ils suivent un
 * réappairage sans qu'il faille relancer l'application.
 */
object Config {
    val HOTE: String get() = Reglages.hote
    val UTILISATEUR: String get() = Reglages.utilisateur
    val MOTDEPASSE: String get() = Reglages.motDePasse

    /**
     * La clé TMDB, elle, garde son repli sur `BuildConfig`.
     *
     * Elle n'ouvre l'accès à rien de personnel — au pire elle sert à quelqu'un
     * d'autre à chercher des affiches, et elle se régénère gratuitement. La
     * laisser dans le build évite de la ressaisir sur chaque boîtier, tout en
     * restant remplaçable depuis le formulaire d'appairage.
     */
    val CLE_TMDB: String
        get() = Reglages.cleTmdb.ifBlank { BuildConfig.TMDB_KEY }

    /** Le même que sur Windows : certains serveurs ne servent que VLC. */
    const val AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    /** Garde-fous pour un appareil à 2 Go de RAM. */
    const val CATEGORIES_MAX = 300
    const val FILMS_PAR_CATEGORIE = 5000

    const val TITRES_PAR_PLATEFORME = 10000

    const val CHAINES_PAR_CATEGORIE = 2000

    const val REPLAY_MAX = 60

    const val RECENTS_MAX = 20

    /** Plafond du « Tout voir » de la rangée Ajouté récemment. */
    const val RECENTS_TOUT_MAX = 300

    /** Popularité TMDB : titres retenus par rangée. */
    const val POPULAIRES_MAX = 20

    const val RANGEES_ACCUEIL = 3

    const val RESULTATS_MAX = 30

    const val LANGUE_TMDB = "fr-FR"

    const val CORRIGER_TITRES = true
}
