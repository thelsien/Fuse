package com.fuse.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * SHL-3 — the app-wide **colorblind mode** switch.
 *
 * ## The seam (toggle now, palette later)
 * The Settings screen needs a fourth toggle — Colorblind mode — that persists and is "applied
 * live". This holder is that seam, mirroring [ReducedMotionSettings] exactly:
 *  - [colorblindEnabled] is Compose-state-backed (default OFF) so the Settings `Switch` reflects
 *    and drives it.
 *  - `App()` reads it inside composition and feeds it into `FuseTheme(colorblind = …)`, so flipping
 *    it recomposes `App()` and re-themes the whole tree LIVE, no restart — the same chain
 *    reduced-motion uses.
 *  - [setEnabled] writes through to [FeedbackPreferences] so the choice survives relaunch; the
 *    Koin graph seeds the holder from persistence at startup.
 *
 * ## Palette is ACC-1 (Sprint 10), NOT this story
 * There is no colorblind-safe palette yet. SHL-3 delivers the **toggle** (it exists, persists, and
 * flows live through the `FuseTheme(colorblind = …)` seam). For now `FuseTheme` maps
 * `colorblind = true` to a near-identity palette (a TODO marker) — the visible change is
 * intentionally deferred. **ACC-1** fills the full colorblind-safe palette + tile patterns behind
 * THIS existing flag: it only has to swap the palette inside `FuseTheme` (and add patterns); the
 * toggle, persistence, and live theme seam are already wired here. No call sites change.
 *
 * @param colorblindEnabled the initial value (seeded from persistence by Koin; defaults OFF for
 *   direct construction in tests/previews).
 * @param preferences the write-through persistence seam; defaults to [NoOpFeedbackPreferences].
 * @property colorblindEnabled `true` to request the colorblind-safe palette (ACC-1); `false`
 *   (default) for the standard palette. Compose-state-backed → flipping it re-themes live.
 */
class ColorblindSettings(
    colorblindEnabled: Boolean = false,
    private val preferences: FeedbackPreferences = NoOpFeedbackPreferences,
) {
    var colorblindEnabled: Boolean by mutableStateOf(colorblindEnabled)

    /** Flip the mode AND persist so it survives relaunch (the Settings-screen path). */
    fun setEnabled(enabled: Boolean) {
        colorblindEnabled = enabled
        preferences.saveColorblind(enabled)
    }
}
