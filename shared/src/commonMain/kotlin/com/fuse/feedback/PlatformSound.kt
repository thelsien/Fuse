package com.fuse.feedback

import org.koin.core.module.Module

/**
 * FEL-5 — per-platform Koin module that binds the [Sound] implementation, mirroring
 * [platformHapticsModule].
 *
 * Each platform synthesises audio through different APIs, and Android needs nothing from the
 * graph (it builds its own `AudioTrack`), while iOS needs nothing extra either. Expressing
 * this as an `expect`/`actual` Koin **module** keeps the wiring inside the DI graph, exactly
 * like the haptics seam. Bound as a `single` so the audio pipeline (track / engine) is built
 * and pre-warmed once and reused.
 *
 *  - Android (`PlatformSound.android.kt`): an `AudioTrack` that synthesises short sine bursts
 *    at a frequency derived from the normalised pitch — no bundled asset, so nothing to
 *    package and nothing to fail to load. Fully defensive: any audio failure is swallowed so
 *    a missing/!busy output never crashes the game (emulators may have no working audio).
 *  - iOS (`PlatformSound.ios.kt`): an `AVAudioEngine` + `AVAudioPlayerNode` synthesising the
 *    same sine bursts, with the `AVAudioSession` configured for ambient game SFX. Defensive
 *    so the Simulator (often no audio) and a failed engine start never crash.
 */
expect val platformSoundModule: Module
