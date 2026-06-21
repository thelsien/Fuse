package com.fuse.feedback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * FEL-8 — the single app-wide switch that collapses ALL motion to "reduced".
 *
 * ## The single switch
 * Every Fuse animation reads its timing/gating from [com.fuse.ui.theme.FuseTheme.motion],
 * which `App()` supplies via `FuseTheme(reducedMotion = …)`. That one boolean maps to
 * `FuseMotion.Reduced` (all durations → ~1ms, `reduced = true`), so a single flip here
 * snaps every slide/overshoot and suppresses the milestone burst + flash (FEL-6) and the
 * combo badge (FEL-7) — with no per-effect change. The chain is, end to end:
 *
 *   [reducedMotionEnabled] (user setting)
 *     → FuseTheme(reducedMotion = …)
 *     → LocalFuseMotion = FuseMotion.Reduced  (when ON)
 *     → every FEL-1..7 effect collapses/suppresses.
 *
 * ## Independent from haptics / sound
 * This is a DISTINCT holder from [HapticsSettings] and [SoundSettings], with its own flag:
 * a player may reduce motion (vestibular comfort) while keeping sound and haptics, or any
 * other combination. Never fold these three into one toggle.
 *
 * ## Why a holder (and not just a `Boolean`) — and why Compose-state-backed
 * The Settings *screen* that flips this lives in Sprint 4 (`SHL-3`); FEL-8 plumbs the seam
 * without building the UI. Default is `false` (full motion ON out of the box — opt-in to
 * reduced motion).
 *
 * [reducedMotionEnabled] is backed by a Compose [mutableStateOf]. Because `App()` reads it
 * inside composition to feed `FuseTheme`, flipping it (from the future Settings screen)
 * marks that read dirty and recomposes `App()` → `FuseTheme` re-provides the swapped
 * [com.fuse.ui.theme.FuseMotion]. So the toggle takes effect LIVE, with no app restart — the
 * observability SHL-3's settings toggle needs.
 *
 * ## What SHL-3 will do here
 * SHL-3 will (a) add the Settings UI that flips this flag, and (b) back it with the
 * UIB-6-style `multiplatform-settings` persistence (same pattern as `SettingsGameRepository`
 * and the planned `HapticsSettings`/`SoundSettings` persistence). Either swap this in-memory
 * holder for a `Settings`-backed implementation of the same shape, or keep this class and
 * make [reducedMotionEnabled] read/write through `Settings` (the Compose-state backing stays,
 * so live recomposition still works). The App-root wiring needs no change — the switch stays
 * exactly here.
 *
 * @property reducedMotionEnabled `true` to force reduced motion app-wide; `false` (default)
 *   for full motion. Compose-state-backed, so flipping it live recomposes everything that
 *   reads the motion through [com.fuse.ui.theme.FuseTheme].
 */
class ReducedMotionSettings(reducedMotionEnabled: Boolean = false) {
    var reducedMotionEnabled: Boolean by mutableStateOf(reducedMotionEnabled)
}
