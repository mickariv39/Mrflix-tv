# MrFlix

Lecteur IPTV (Xtream Codes) pour **Android TV / Chromecast avec Google TV** et **téléphones Android**.
MrFlix ne fournit aucun contenu : il lit l'abonnement que tu possèdes déjà.

## Installer sur un téléphone Android

1. Sur le téléphone, ouvre la page **[Releases](https://github.com/mickariv39/Mrflix-tv/releases/latest)** dans Chrome.
2. Touche **`MrFlix-1.7.apk`** pour le télécharger.
3. Ouvre le fichier téléchargé. Android demande d'autoriser Chrome à « installer des applications inconnues » : accepte, puis touche **Installer**.
4. Au premier lancement, saisis l'adresse de ton serveur, ton identifiant et ton mot de passe IPTV.

Pour une mise à jour, installe simplement le nouvel APK par-dessus : tes réglages et ta liste sont conservés.

## Installer sur Chromecast avec Google TV

Le plus simple : depuis Android Studio (débogage sans fil activé sur la TV), ou avec `adb install MrFlix-1.7.apk`.
Au premier lancement, un QR code s'affiche : scanne-le avec ton téléphone pour envoyer tes accès à la TV.

## Ce qu'il y a dedans

- **TV** : rangées à la télécommande (la carte choisie reste toujours au même endroit), carte vedette, plateformes (Netflix, Prime Video…) dont « Tous les contenus », chaînes, replay, fiches détaillées TMDB.
- **Téléphone** : même présentation que MrFlix Web — pastilles Catégories / Français uniquement / plateformes, grille d'affiches, barre d'onglets flottante, replay avec les affiches des programmes.
- **Lecteur intégré** (Media3/ExoPlayer) : MKV, AC-3, DTS, sous-titres, reprise de lecture, vignette (PiP). L'écran reste allumé pendant la lecture et ne se met en veille qu'après 2 h sans toucher l'appareil.

## Compiler

Ouvre le dossier dans Android Studio, copie `secrets.properties.example` en `secrets.properties` (clé TMDB facultative), choisis la variante **release** et lance.
