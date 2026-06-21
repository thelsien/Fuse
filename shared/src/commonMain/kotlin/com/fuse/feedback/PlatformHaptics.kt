package com.fuse.feedback

import org.koin.core.module.Module

/**
 * FEL-4 — per-platform Koin module that binds the [Haptics] implementation, mirroring
 * UIB-6's `platformSettingsModule`.
 *
 * Each platform produces native feedback through different APIs, and Android needs the
 * `Context` that already lives in the Koin graph (contributed by `androidContext(...)` in
 * `FuseApplication`), while iOS needs nothing extra. Expressing this as an
 * `expect`/`actual` Koin **module** keeps the wiring inside the DI graph (the Android
 * actual resolves `Context` via Koin's `androidContext()`), exactly like the settings seam.
 *
 *  - Android (`PlatformHaptics.android.kt`): a `Vibrator`/`VibratorManager` driven by
 *    `VibrationEffect` predefined effects (`EFFECT_TICK` / `EFFECT_HEAVY_CLICK`), guarded
 *    for API level and missing hardware (no crash if there is no vibrator).
 *  - iOS (`PlatformHaptics.ios.kt`): `UIImpactFeedbackGenerator` (light/heavy) +
 *    `UINotificationFeedbackGenerator` (error) for the buzz.
 *
 * Bound as a `single` so the generators can be prepared/reused; resolved by the
 * `HapticsCoordinator` wiring in `GameScreen`.
 */
expect val platformHapticsModule: Module
