package com.fuse.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.ui.theme.SwatchScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * FND-5 acceptance criterion #2: ONE headless Compose UI test.
 *
 * Uses the Compose Multiplatform test API (`runComposeUiTest` + `onNodeWithText`),
 * which composes the UI off-screen — no device or emulator. On the Android target
 * Compose's `runComposeUiTest` requires the Robolectric environment (it probes
 * `android.os.Build.FINGERPRINT` to pick its idling strategy), so this test must
 * carry `@RunWith(RobolectricTestRunner::class)`. That runner is a JUnit/Android
 * construct and cannot live in `commonTest`, which is why the test sits in
 * `androidUnitTest` rather than `commonMain`'s shared test set.
 *
 * It runs in the EXISTING `:shared:testDebugUnitTest` CI step (the Android job),
 * fully headless. `@GraphicsMode(NATIVE)` lets Robolectric render real graphics in
 * software so Compose layout/measure runs.
 *
 * [SwatchScreen] is the subject because it renders WITHOUT a started Koin graph
 * (FND-4), so no DI setup is needed — the test composes the screen and asserts a
 * known token-preview node exists, proving the composition succeeded.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SwatchScreenUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun swatchScreenRendersHeaderHeadless() = runComposeUiTest {
        setContent {
            SwatchScreen()
        }

        // Static literals rendered straight from the token layer — their presence
        // proves the screen composed successfully in the headless harness.
        onNodeWithText("Fuse").assertExists()
        onNodeWithText("Design tokens — FND-4").assertExists()
    }
}
