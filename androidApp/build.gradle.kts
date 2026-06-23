import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing material is read from a GITIGNORED `keystore.properties` at the repo root
// (see `keystore.properties.template` + docs/release/android-internal-testing.md). It is loaded
// ONLY when that file exists, so CI and fresh clones — which have no keystore — still configure
// and build a release bundle (unsigned). NEVER commit keystore.properties or the .jks itself.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.fuse.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.fuse.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Created only when keystore.properties is present; otherwise the release build is unsigned.
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            // Shrinking/obfuscation (R8/proguard) is intentionally OFF for now: the Ads (AdMob) and
            // Play Billing SDKs need vetted keep-rules, deferred to a later REL task. Internal-testing
            // builds don't require it. Keep false until that task lands.
            isMinifyEnabled = false
            // Apply the release signing config only when it was created above (keystore present).
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    // Koin Android helpers (androidContext/androidLogger) used by FuseApplication
    // to start the shared graph; KoinApplication type lives in koin-core, pulled
    // in transitively.
    implementation(libs.koin.android)
}
