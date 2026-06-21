package com.fuse.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * FEL-5 / SHL-3 — the mute toggle that gates all sound effects.
 *
 * ## Separate from haptics, on purpose
 * Sound and haptics are independent channels a player tunes separately (mute the phone in a
 * meeting but keep the buzz in your hand, or vice-versa). So this is a DISTINCT holder from
 * [HapticsSettings] with its own [soundEnabled] flag — never share one toggle for both.
 *
 * ## SHL-3 — Compose-state-backed + persisted
 * [soundEnabled] is now backed by a Compose [mutableStateOf] (like [ReducedMotionSettings]) so the
 * Settings `Switch` reflects and drives it. The [SoundCoordinator] reads it at dispatch time, so a
 * flip mutes/unmutes from the **next event** with no restart (applied live).
 *
 * Persistence delegates to [FeedbackPreferences]: the holder is **seeded** from the persisted
 * value (the Koin graph passes a [SettingsFeedbackPreferences]-seeded `soundEnabled` at startup, so
 * a relaunch restores the user's choice) and **writes through** on every flip via [setEnabled].
 * Tests/previews use the [NoOpFeedbackPreferences] default, so no real `Settings` is required.
 *
 * @param soundEnabled the initial mute value (seeded from persistence by Koin; defaults ON for
 *   direct construction in tests/previews).
 * @param preferences the write-through persistence seam; defaults to [NoOpFeedbackPreferences].
 */
class SoundSettings(
    soundEnabled: Boolean = true,
    private val preferences: FeedbackPreferences = NoOpFeedbackPreferences,
) {
    /**
     * `true` to allow sound effects; `false` mutes all of them. Compose-state-backed so a flip
     * recomposes readers. Flip via [setEnabled] to also persist; the setter stays public so
     * existing call sites that assign directly keep compiling (those simply don't persist).
     */
    var soundEnabled: Boolean by mutableStateOf(soundEnabled)

    /** Flip the mute AND persist the new value so it survives relaunch (the Settings-screen path). */
    fun setEnabled(enabled: Boolean) {
        soundEnabled = enabled
        preferences.saveSound(enabled)
    }
}
