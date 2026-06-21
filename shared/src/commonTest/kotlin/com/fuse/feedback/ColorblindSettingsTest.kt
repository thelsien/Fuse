package com.fuse.feedback

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SHL-3 — the colorblind-mode holder. Pure JVM+iOS test (commonTest): proves it defaults OFF
 * (standard palette out of the box) and flips, mirroring [ReducedMotionSettings]. The
 * SETTING → `FuseTheme(colorblind = …)` → live theme chain is proven by the Robolectric
 * `SettingsScreenUiTest`; the colorblind-SAFE palette behind the flag is ACC-1 (Sprint 10).
 */
class ColorblindSettingsTest {

    @Test
    fun defaultsToStandardPalette() {
        assertFalse(ColorblindSettings().colorblindEnabled, "colorblind default OFF")
    }

    @Test
    fun reflectsExplicitInitialValue() {
        assertTrue(ColorblindSettings(colorblindEnabled = true).colorblindEnabled)
    }

    @Test
    fun flipsLikeTheOtherSettingsHolders() {
        val settings = ColorblindSettings()

        settings.setEnabled(true)
        assertTrue(settings.colorblindEnabled, "ON after flip")

        settings.setEnabled(false)
        assertFalse(settings.colorblindEnabled, "OFF after flip back")
    }
}
