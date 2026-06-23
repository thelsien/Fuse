package com.fuse.ui.nav

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.ui.settings.SettingsScreen
import com.fuse.ui.settings.SettingsScreenTags
import com.fuse.ui.theme.FuseTheme
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * IAP-4 — verifies the paywall is REACHABLE from Settings: the "Remove Ads" row exists and tapping
 * it invokes the `onOpenRemoveAds` callback. In `App()` that callback navigates to the
 * `REMOVE_ADS` route ([com.fuse.ui.iap.RemoveAdsScreen]); the NavHost routing + back is already
 * covered by [AppNavigationUiTest], and the paywall screen itself by `RemoveAdsScreenUiTest` — so
 * this test deterministically pins the Settings entry → open-action wiring without re-driving a
 * NavHost crossfade (whose timing fights the RemoveAdsStore's Main-scope coroutine).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RemoveAdsNavigationUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun settingsRemoveAdsRowInvokesOpenCallback() = runComposeUiTest {
        var opened = 0
        setContent {
            FuseTheme {
                SettingsScreen(
                    sound = true,
                    haptics = true,
                    reducedMotion = false,
                    colorblind = false,
                    onToggleSound = {},
                    onToggleHaptics = {},
                    onToggleReducedMotion = {},
                    onToggleColorblind = {},
                    onBack = {},
                    onOpenRemoveAds = { opened++ },
                )
            }
        }

        // Scroll the row into view first — it sits below the four toggles, so in the test
        // viewport it can be below the fold and a bare performClick would miss.
        onNodeWithTag(SettingsScreenTags.REMOVE_ADS_ROW).performScrollTo().performClick()
        assertEquals(1, opened, "tapping the Settings 'Remove Ads' row opens the paywall")
    }
}
