# Règles R8 pour le build release.
#
# Coil3 (coil-network-okhttp) et Media3 (exoplayer, ui) embarquent leurs
# propres "consumer rules" : rien à ajouter pour eux.
#
# L'application fait toute sa (dé)sérialisation à la main avec org.json et
# n'utilise aucune réflexion : aucune classe de com.mrflix.tv n'a besoin
# d'être préservée. R8 peut réduire, renommer et optimiser librement.
#
# Conserver la table des sources de ligne pour des traces lisibles.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
