package com.fuse.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.ads.NoOpAdProvider
import com.fuse.feedback.ColorblindSettings
import com.fuse.feedback.HapticsCoordinator
import com.fuse.feedback.HapticsSettings
import com.fuse.feedback.Haptics
import com.fuse.feedback.ReducedMotionSettings
import com.fuse.feedback.SoundSettings
import com.fuse.ui.theme.FuseTheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SHL-3 — headless Compose UI tests for the real [SettingsScreen] (androidUnitTest Robolectric,
 * runs in `:shared:testDebugUnitTest`).
 *
 * Covers the acceptance criteria:
 *  - all FOUR switches render reflecting the current values (presentational overload),
 *  - flipping a switch invokes the right `onToggle` callback,
 *  - the stateful (Koin-less, holder-bound) overload flips the holder live when a switch is tapped,
 *  - an end-to-end "applied live" proof: flipping the Haptics holder via the screen mutes the
 *    [HapticsCoordinator] on the very next event (the coordinator reads the flipped flag — no
 *    restart). The reduced-motion live-theme chain is proven separately by
 *    `ReducedMotionSwitchUiTest`; here we also assert reduced-motion's holder flips live.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsScreenUiTest {

    /** A recording [Haptics] so we can prove the live-mute end to end. */
    private class RecordingHaptics : Haptics {
        var calls = 0
        override fun tick() { calls++ }
        override fun thunk() { calls++ }
        override fun buzz() { calls++ }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersAllFourSwitchesReflectingValues() = runComposeUiTest {
        setContent {
            FuseTheme {
                SettingsScreen(
                    sound = true,
                    haptics = false,
                    reducedMotion = true,
                    colorblind = false,
                    onToggleSound = {},
                    onToggleHaptics = {},
                    onToggleReducedMotion = {},
                    onToggleColorblind = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag(SettingsScreenTags.SOUND_SWITCH).assertIsOn()
        onNodeWithTag(SettingsScreenTags.HAPTICS_SWITCH).assertIsOff()
        onNodeWithTag(SettingsScreenTags.REDUCED_MOTION_SWITCH).assertIsOn()
        onNodeWithTag(SettingsScreenTags.COLORBLIND_SWITCH).assertIsOff()
        onNodeWithTag(SettingsScreenTags.TITLE).assertExists()
        onNodeWithTag(SettingsScreenTags.BACK).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun togglingEachSwitchInvokesItsCallbackWithNewValue() = runComposeUiTest {
        var sound: Boolean? = null
        var haptics: Boolean? = null
        var reducedMotion: Boolean? = null
        var colorblind: Boolean? = null
        setContent {
            FuseTheme {
                SettingsScreen(
                    sound = true,
                    haptics = true,
                    reducedMotion = false,
                    colorblind = false,
                    onToggleSound = { sound = it },
                    onToggleHaptics = { haptics = it },
                    onToggleReducedMotion = { reducedMotion = it },
                    onToggleColorblind = { colorblind = it },
                    onBack = {},
                )
            }
        }

        onNodeWithTag(SettingsScreenTags.SOUND_SWITCH).performClick()
        onNodeWithTag(SettingsScreenTags.HAPTICS_SWITCH).performClick()
        onNodeWithTag(SettingsScreenTags.REDUCED_MOTION_SWITCH).performClick()
        onNodeWithTag(SettingsScreenTags.COLORBLIND_SWITCH).performClick()

        assertFalse(sound!!, "sound flipped true→false")
        assertFalse(haptics!!, "haptics flipped true→false")
        assertTrue(reducedMotion!!, "reduced motion flipped false→true")
        assertTrue(colorblind!!, "colorblind flipped false→true")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backAffordanceInvokesOnBack() = runComposeUiTest {
        var backs = 0
        setContent {
            FuseTheme {
                SettingsScreen(
                    sound = true, haptics = true, reducedMotion = false, colorblind = false,
                    onToggleSound = {}, onToggleHaptics = {}, onToggleReducedMotion = {},
                    onToggleColorblind = {}, onBack = { backs++ },
                )
            }
        }
        onNodeWithTag(SettingsScreenTags.BACK).performClick()
        assertEquals(1, backs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun statefulScreenFlipsHoldersLive() = runComposeUiTest {
        // The holder-bound overload, driven by real holders (NoOp persistence — no Settings needed).
        val sound = SoundSettings(soundEnabled = true)
        val haptics = HapticsSettings(hapticsEnabled = true)
        val reducedMotion = ReducedMotionSettings(reducedMotionEnabled = false)
        val colorblind = ColorblindSettings(colorblindEnabled = false)

        setContent {
            FuseTheme {
                SettingsScreen(
                    onBack = {},
                    soundSettings = sound,
                    hapticsSettings = haptics,
                    reducedMotionSettings = reducedMotion,
                    colorblindSettings = colorblind,
                    // ADS-0: the stateful wrapper now resolves an AdProvider for the debug ad
                    // trigger; supply the NoOp default here so the test stays Koin-free.
                    adProvider = NoOpAdProvider,
                )
            }
        }

        // Switches reflect the seeded holder values.
        onNodeWithTag(SettingsScreenTags.HAPTICS_SWITCH).assertIsOn()
        onNodeWithTag(SettingsScreenTags.COLORBLIND_SWITCH).assertIsOff()

        // Tapping flips the holder LIVE and the switch reflects it (Compose-state-backed).
        onNodeWithTag(SettingsScreenTags.HAPTICS_SWITCH).performClick()
        assertFalse(haptics.hapticsEnabled, "haptics holder flipped OFF live")
        onNodeWithTag(SettingsScreenTags.HAPTICS_SWITCH).assertIsOff()

        onNodeWithTag(SettingsScreenTags.REDUCED_MOTION_SWITCH).performClick()
        assertTrue(reducedMotion.reducedMotionEnabled, "reduced motion holder flipped ON live")

        onNodeWithTag(SettingsScreenTags.COLORBLIND_SWITCH).performClick()
        assertTrue(colorblind.colorblindEnabled, "colorblind holder flipped ON live")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun flippingHapticsMutesCoordinatorOnNextEvent() = runComposeUiTest {
        // "Applied live" for haptics: the coordinator reads the holder at dispatch, so a flip from
        // the Settings screen silences the very next event with no restart.
        val haptics = HapticsSettings(hapticsEnabled = true)
        val sink = RecordingHaptics()
        val coordinator = HapticsCoordinator(haptics = sink, settings = haptics)

        setContent {
            FuseTheme {
                SettingsScreen(
                    onBack = {},
                    soundSettings = SoundSettings(),
                    hapticsSettings = haptics,
                    reducedMotionSettings = ReducedMotionSettings(),
                    colorblindSettings = ColorblindSettings(),
                    // ADS-0: supply the NoOp AdProvider so the stateful wrapper stays Koin-free here.
                    adProvider = NoOpAdProvider,
                )
            }
        }

        // Enabled: a merge ticks.
        coordinator.onMove(mergedValues = listOf(4), justWon = false)
        assertEquals(1, sink.calls, "haptic fired while enabled")

        // Flip OFF via the screen, then the next event is silent — live, no restart.
        onNodeWithTag(SettingsScreenTags.HAPTICS_SWITCH).performClick()
        assertFalse(haptics.hapticsEnabled)
        coordinator.onMove(mergedValues = listOf(4), justWon = false)
        assertEquals(1, sink.calls, "no new haptic after live-disable")
    }
}
