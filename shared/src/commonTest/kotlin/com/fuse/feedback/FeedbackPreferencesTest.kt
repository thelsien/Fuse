package com.fuse.feedback

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SHL-3 — persistence round-trips for the four settings toggles (pure JVM + iOS, commonTest).
 *
 * Each test writes through a [SettingsFeedbackPreferences] over a shared `MapSettings`
 * (multiplatform-settings-test, the in-memory analogue of SharedPreferences/NSUserDefaults), then
 * builds a FRESH preferences/holder over the SAME store — a **simulated relaunch** — and asserts
 * the value persisted. Also pins the documented defaults when a key was never set (sound/haptics
 * ON, reduced-motion/colorblind OFF). This is exactly the "survives app relaunch" acceptance
 * criterion, proven without a real platform store.
 */
class FeedbackPreferencesTest {

    @Test
    fun defaultsWhenNothingPersisted() {
        val prefs = SettingsFeedbackPreferences(MapSettings())
        assertTrue(prefs.loadSound(), "sound defaults ON")
        assertTrue(prefs.loadHaptics(), "haptics defaults ON")
        assertFalse(prefs.loadReducedMotion(), "reduced motion defaults OFF")
        assertFalse(prefs.loadColorblind(), "colorblind defaults OFF")
    }

    @Test
    fun soundFlagSurvivesRelaunch() {
        val store = MapSettings()
        SettingsFeedbackPreferences(store).saveSound(false) // user mutes
        // Simulated relaunch: a brand-new repository over the SAME store.
        assertFalse(SettingsFeedbackPreferences(store).loadSound(), "muted sound restored")
    }

    @Test
    fun hapticsFlagSurvivesRelaunch() {
        val store = MapSettings()
        SettingsFeedbackPreferences(store).saveHaptics(false)
        assertFalse(SettingsFeedbackPreferences(store).loadHaptics(), "haptics-off restored")
    }

    @Test
    fun reducedMotionFlagSurvivesRelaunch() {
        val store = MapSettings()
        SettingsFeedbackPreferences(store).saveReducedMotion(true) // user opts in
        assertTrue(
            SettingsFeedbackPreferences(store).loadReducedMotion(),
            "reduced-motion-on restored",
        )
    }

    @Test
    fun colorblindFlagSurvivesRelaunch() {
        val store = MapSettings()
        SettingsFeedbackPreferences(store).saveColorblind(true)
        assertTrue(SettingsFeedbackPreferences(store).loadColorblind(), "colorblind-on restored")
    }

    @Test
    fun holderSeedsFromPersistenceAndWritesThrough() {
        val store = MapSettings()
        val prefs = SettingsFeedbackPreferences(store)

        // A holder built over the prefs seeds from the default (ON) and write-through persists OFF.
        val haptics = HapticsSettings(hapticsEnabled = prefs.loadHaptics(), preferences = prefs)
        assertTrue(haptics.hapticsEnabled, "seeded from default ON")
        haptics.setEnabled(false)

        // Simulated relaunch: a new holder seeded from the SAME store reads the persisted OFF.
        val relaunched = HapticsSettings(hapticsEnabled = prefs.loadHaptics(), preferences = prefs)
        assertFalse(relaunched.hapticsEnabled, "holder restored OFF across relaunch")
    }

    @Test
    fun reducedMotionHolderSeedsAndPersists() {
        val store = MapSettings()
        val prefs = SettingsFeedbackPreferences(store)

        val rm = ReducedMotionSettings(
            reducedMotionEnabled = prefs.loadReducedMotion(),
            preferences = prefs,
        )
        assertFalse(rm.reducedMotionEnabled, "seeded from default OFF")
        rm.setEnabled(true)

        val relaunched = ReducedMotionSettings(
            reducedMotionEnabled = prefs.loadReducedMotion(),
            preferences = prefs,
        )
        assertTrue(relaunched.reducedMotionEnabled, "reduced motion restored ON across relaunch")
    }

    @Test
    fun colorblindHolderSeedsAndPersists() {
        val store = MapSettings()
        val prefs = SettingsFeedbackPreferences(store)

        val cb = ColorblindSettings(
            colorblindEnabled = prefs.loadColorblind(),
            preferences = prefs,
        )
        assertFalse(cb.colorblindEnabled, "seeded from default OFF")
        cb.setEnabled(true)

        val relaunched = ColorblindSettings(
            colorblindEnabled = prefs.loadColorblind(),
            preferences = prefs,
        )
        assertTrue(relaunched.colorblindEnabled, "colorblind restored ON across relaunch")
    }

    @Test
    fun noOpPreferencesNeverPersistsAndReturnsDefaults() {
        // The default holder dependency requires no real Settings (mirrors NoOpGameRepository).
        val haptics = HapticsSettings() // NoOpFeedbackPreferences default
        assertTrue(haptics.hapticsEnabled, "default ON")
        haptics.setEnabled(false)
        // A fresh holder built from the NoOp prefs still reads the documented default (no store).
        assertTrue(NoOpFeedbackPreferences.loadHaptics(), "NoOp always returns default")
    }
}
