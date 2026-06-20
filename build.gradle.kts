plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // FND-5: coverage. Applied at the root so `./gradlew koverHtmlReport` /
    // `koverXmlReport` aggregate the :shared module's coverage (it is added as a
    // Kover dependency in shared/build.gradle.kts). Reports land under
    // shared/build/reports/kover/.
    alias(libs.plugins.kover)
}

dependencies {
    // Aggregate coverage of the shared module into the root Kover reports.
    kover(project(":shared"))
}
