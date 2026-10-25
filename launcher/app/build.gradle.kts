import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Eigener Paketname fuer eine eigene Installation: in gradle.properties "ncos.applicationId=..." setzen
// (die Skripte lesen ihn ueber scripts\common.ps1 mit). Der Code-Namespace bleibt gleich.
val appId = providers.gradleProperty("ncos.applicationId").getOrElse("dev.david.braviahome")

// Release-Schluessel optional in launcher/keystore.properties (nicht im Repo):
//   storeFile=C:/pfad/release.jks  storePassword=...  keyAlias=...  keyPassword=...
// Ohne Datei wird mit dem lokalen Debug-Schluessel signiert (reicht fuer den eigenen TV).
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }

android {
    namespace = "dev.david.braviahome"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 28
        targetSdk = 35
        versionCode = 11
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // UI-Sounds kommen aus ../sounds (siehe prepareSounds unten), nicht aus src/main/res/raw.
    sourceSets.getByName("main").res.srcDir(layout.buildDirectory.dir("generated/sounds"))
}

// UI-Sounds: zuerst die eigenen Standard-Sounds (sounds/default, im Repo), dann ersetzt jede Datei
// aus sounds/custom (privat, nicht im Repo) die gleichnamige. Namen: sfx_boot.wav, sfx_move.wav ...
val prepareSounds by tasks.registering(Sync::class) {
    val sounds = rootProject.file("../sounds")
    from(File(sounds, "default"))
    from(File(sounds, "custom"))
    include("sfx_*.wav")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    into(layout.buildDirectory.dir("generated/sounds/raw"))
}
tasks.named("preBuild") { dependsOn(prepareSounds) }

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
}
