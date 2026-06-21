package com.fuse.feedback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FEL-8 — the reduced-motion settings holder. Pure JVM+iOS test (commonTest): proves the
 * single switch defaults OFF (full motion out of the box) and flips, mirroring the
 * [HapticsSettings]/[SoundSettings] holders. The SETTING → theme → effect end-to-end chain is
 * proven by the Robolectric `ReducedMotionSwitchUiTest`.
 */
class ReducedMotionSettingsTest {

    @Test
    fun defaultsToFullMotion() {
        // Reduced motion is opt-in: out of the box the switch is OFF (full motion).
        assertFalse(ReducedMotionSettings().reducedMotionEnabled, "reduced motion default OFF")
    }

    @Test
    fun reflectsExplicitInitialValue() {
        assertTrue(ReducedMotionSettings(reducedMotionEnabled = true).reducedMotionEnabled)
    }

    @Test
    fun flipsLikeTheOtherSettingsHolders() {
        val settings = ReducedMotionSettings()

        settings.reducedMotionEnabled = true
        assertTrue(settings.reducedMotionEnabled, "switch ON after flip")

        settings.reducedMotionEnabled = false
        assertFalse(settings.reducedMotionEnabled, "switch OFF after flip back")
    }
}
