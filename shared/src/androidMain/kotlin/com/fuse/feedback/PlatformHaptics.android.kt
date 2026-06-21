package com.fuse.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * FEL-4 (Android) — binds [Haptics] backed by the system [Vibrator].
 *
 * The `Context` is resolved from the Koin graph (contributed by
 * `androidContext(this@FuseApplication)`), mirroring `platformSettingsModule`. If the
 * device has no usable vibrator we bind [NoOpHaptics] so feedback is silently absent
 * rather than crashing (emulators and some hardware report no vibrator).
 */
actual val platformHapticsModule: Module = module {
    single<Haptics> {
        val context = get<Context>()
        val vibrator = context.systemVibrator()
        if (vibrator != null && vibrator.hasVibrator()) {
            AndroidHaptics(vibrator)
        } else {
            NoOpHaptics
        }
    }
}

/** Resolves the platform [Vibrator] across the API split (VibratorManager on API 31+). */
private fun Context.systemVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

/**
 * Vibrator-backed [Haptics]. Each intent maps to a distinct cue:
 *  - [tick] → predefined `EFFECT_TICK` (light, API 29+) / a short one-shot below.
 *  - [thunk] → predefined `EFFECT_HEAVY_CLICK` (heavy milestone impact) / a longer one-shot.
 *  - [buzz] → a two-pulse waveform (distinct "error" feel for a blocked move).
 *
 * Three API tiers (minSdk 24), with the version guard inline at each call so lint can see it:
 *  - **29+**: predefined effects for the taps; a waveform for the buzz — the richest feel.
 *  - **26–28**: `VibrationEffect` exists but predefined effects don't, so timed one-shots /
 *    a waveform are used.
 *  - **24–25**: no `VibrationEffect` at all; fall back to the deprecated `vibrate(long[]/long)`.
 *
 * All calls are defensive — a failed `vibrate` never propagates (a haptic is best-effort).
 */
private class AndroidHaptics(private val vibrator: Vibrator) : Haptics {

    override fun tick() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(TICK_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(TICK_MILLIS)
            }
        }
    }

    override fun thunk() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK),
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(THUNK_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(THUNK_MILLIS)
            }
        }
    }

    override fun buzz() {
        // A two-pulse pattern reads as a distinct "no" / error, unlike the single taps.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(BUZZ_PATTERN, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(BUZZ_PATTERN, -1)
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 10L
        const val THUNK_MILLIS = 40L

        /** off, on, off, on — a double tap that feels like an error. */
        val BUZZ_PATTERN = longArrayOf(0L, 30L, 60L, 30L)
    }
}
