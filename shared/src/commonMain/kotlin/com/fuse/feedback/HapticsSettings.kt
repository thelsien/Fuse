package com.fuse.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * FEL-4 / SHL-3 — the toggle seam that gates all haptic feedback.
 *
 * ## Why a holder (and not just a `Boolean`)
 * FEL-4 plumbed the seam without the UI: an injectable holder the [HapticsCoordinator] reads on
 * every event, short-circuiting when [hapticsEnabled] is `false`, so *one* check disables every
 * haptic. SHL-3 built the Settings UI that flips it and gave it persistence.
 *
 * ## SHL-3 — Compose-state-backed + persisted
 * [hapticsEnabled] is now backed by a Compose [mutableStateOf] (like [ReducedMotionSettings]) so
 * the Settings `Switch` reflects and drives it, and any future composable read stays consistent.
 * The coordinator reads it at dispatch time, so a flip takes effect on the **next event** with no
 * restart (applied live).
 *
 * Persistence delegates to [FeedbackPreferences]: the holder is **seeded** from the persisted
 * value (the Koin graph passes a [SettingsFeedbackPreferences]-seeded `hapticsEnabled` at startup,
 * so a relaunch restores the user's choice) and **writes through** on every flip via [setEnabled].
 * Tests/previews use the [NoOpFeedbackPreferences] default, so no real `Settings` is required
 * (mirrors how `GameStore` defaults a NoOp repo).
 *
 * @param hapticsEnabled the initial gate value (seeded from persistence by Koin; defaults ON for
 *   direct construction in tests/previews).
 * @param preferences the write-through persistence seam; defaults to [NoOpFeedbackPreferences].
 */
class HapticsSettings(
    hapticsEnabled: Boolean = true,
    private val preferences: FeedbackPreferences = NoOpFeedbackPreferences,
) {
    /**
     * `true` to allow haptic feedback; `false` silences all of it. Compose-state-backed so a flip
     * recomposes readers. Flip via [setEnabled] to also persist; the setter stays public so
     * existing call sites that assign directly keep compiling (those simply don't persist).
     */
    var hapticsEnabled: Boolean by mutableStateOf(hapticsEnabled)

    /** Flip the gate AND persist the new value so it survives relaunch (the Settings-screen path). */
    fun setEnabled(enabled: Boolean) {
        hapticsEnabled = enabled
        preferences.saveHaptics(enabled)
    }
}
