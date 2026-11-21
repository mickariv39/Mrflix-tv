import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Secrets (identifiants du fournisseur, clé TMDB) sortis du code source.
 *
 * Ils sont lus, dans l'ordre : `secrets.properties` à la racine du projet
 * (non versionné), puis les variables d'environnement, puis vide. Voir
 * `secrets.properties.example` pour le modèle.
 */
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(cle: String): String =
    secrets.getProperty(cle) ?: System.getenv(cle) ?: ""

android {
    namespace = "com.mrflix.tv"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mrflix.tv"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "1.7"

        // Les accès du fournisseur ne sont JAMAIS compilés dans l'APK : ils
        // viennent de l'appairage (voir Config.kt). L'APK peut donc être
        // publié sans exposer l'abonnement.
        buildConfigField("String", "TMDB_KEY", "\"${secret("TMDB_KEY")}\"")
    }

    buildTypes {
        release {
            // Signée avec la clé de debug : l'application ne va pas sur le Play
            // Store, elle ne tourne que sur le boîtier. Sans signature, Android
            // refuse d'installer l'APK.
            signingConfig = signingConfigs.getByName("debug")
            // R8 : réduction + optimisation + obfuscation. Coil3 et Media3
            // embarquent leurs propres règles ; l'app (dé)sérialise à la main
            // avec org.json, donc rien à garder pour la réflexion.
            // À VÉRIFIER sur un build release : lecture vidéo, affiches, catalogue.
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

composeCompiler {
    // Porteurs de données traités comme stables : rend « skippables » les
    // composables qui les reçoivent (cartes, rangées, accueil).
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf")
    )
}

dependencies {
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.zxing.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
