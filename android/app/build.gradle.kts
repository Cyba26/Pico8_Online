import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Une version = un APK publié sur le site. Monter versionCode à chaque
// publication : c'est lui que l'app compare à releases/version.json.
val appVersionCode = 1
val appVersionName = "1.0"

// Le site (dossier public/ du dépôt) est embarqué tel quel dans l'APK : c'est
// ce qui s'affiche au premier lancement, même sans réseau. Pas de copie dans
// git, la source reste public/.
val siteDir = rootProject.file("../public")
val siteAssets = layout.buildDirectory.dir("generated/site")
val copySite by tasks.registering(Sync::class) {
    from(siteDir) {
        include("index.html", "cartouches.json", "cartouches/**")
        exclude("**/README.md")
    }
    into(siteAssets)
}

// Clé de signature hors dépôt (voir README). Sans elle, seul le debug se construit.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "fr.cybai.p8"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.cybai.p8"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    sourceSets["main"].assets.srcDir(siteAssets)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.named("preBuild") { dependsOn(copySite) }

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
}

// ./gradlew publishApk : construit l'APK signé et le dépose dans
// public/releases/ avec le version.json que lit l'app pour proposer la mise à jour.
tasks.register("publishApk") {
    dependsOn("assembleRelease")
    doLast {
        val releases = File(siteDir, "releases").apply { mkdirs() }
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        check(apk.exists()) { "APK non signé : créer android/keystore.properties (voir README)" }
        apk.copyTo(File(releases, "Pico8_Online.apk"), overwrite = true)
        File(releases, "version.json").writeText(
            """{ "versionCode": $appVersionCode, "versionName": "$appVersionName" }""" + "\n"
        )
        println("✓ public/releases/Pico8_Online.apk (v$appVersionName, code $appVersionCode)")
    }
}
