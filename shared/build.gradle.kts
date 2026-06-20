import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
    // FND-5: code coverage for this module; reports aggregated at the root.
    alias(libs.plugins.kover)
    // ENG-1: kotlinx.serialization compiler plugin for @Serializable Board/Tile.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            // DI graph is portable code -> Koin lives in commonMain.
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            // ENG-1: engine serialization (Board/Tile JSON round-trip).
            implementation(libs.kotlinx.serialization.json)
            // UIB-3: StateFlow-backed MVI store in the presentation layer.
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // UIB-3: drive the GameStore deterministically (runTest, dispatchers).
            implementation(libs.kotlinx.coroutines.test)
            // Koin's test helpers (verify/checkModules) run on every CI target.
            implementation(libs.koin.test)
            // FND-5: Compose Multiplatform UI-test API (`runComposeUiTest`,
            // `onNodeWithText`, …). Lives in commonTest so the one shared UI test
            // runs on BOTH the Android JVM target (Robolectric-backed, headless)
            // and the iOS native target — no device/emulator needed.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        // On Android, Compose's `runComposeUiTest` executes as a local JVM unit
        // test through Robolectric (configured via testOptions below), so it runs
        // in the existing `:shared:testDebugUnitTest` CI step with no emulator.
        androidUnitTest.dependencies {
            // RobolectricTestRunner runs the Compose UI test as a headless local
            // JVM test (no emulator) in the existing `:shared:testDebugUnitTest`
            // step. The Compose `runComposeUiTest` API itself comes from
            // `compose.uiTest` (declared in commonTest).
            implementation(libs.robolectric)
            // Provides the empty `ComponentActivity` (in a test manifest) that
            // `runComposeUiTest` launches via ActivityScenario to host the
            // composition under Robolectric. No Compose-MP DSL alias exists, so
            // the androidx artifact is referenced directly (pinned to 1.8.2).
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

android {
    namespace = "com.fuse.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // FND-5: let Compose UI tests run as local JVM unit tests via Robolectric.
    // `includeAndroidResources` gives Robolectric the merged resources/manifest
    // it needs to inflate the Compose host; `isReturnDefaultValues` keeps any
    // not-yet-shadowed android.* calls from throwing during the headless run.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}
