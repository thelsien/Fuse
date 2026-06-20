package com.fuse.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * Motion tokens from `docs/design-tokens.md` (§Motion).
 *
 * Durations are in milliseconds. [reduced] collapses every duration to ~1ms so
 * the reduced-motion branch (FEL-8) can swap the whole set without touching call
 * sites — read these through [LocalFuseMotion] rather than hardcoding constants.
 */
@Immutable
data class FuseMotion(
    val tileSlideMs: Int,
    val genericMs: Int,
    val mediumMs: Int,
    val slowMs: Int,
    val tileSlideEasing: Easing,
    val standardEasing: Easing,
    val reduced: Boolean,
) {
    companion object {
        // cubic-bezier(.2,.7,.3,1) — tile slide, ease-out.
        val TileSlideEasing: Easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1.0f)
        // cubic-bezier(.2,.8,.2,1) — standard easing.
        val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1.0f)

        /** Default (full) motion matching the prototype. */
        val Default = FuseMotion(
            tileSlideMs = 110,
            genericMs = 150,   // .15s transform/background
            mediumMs = 170,
            slowMs = 230,
            tileSlideEasing = TileSlideEasing,
            standardEasing = StandardEasing,
            reduced = false,
        )

        /** Reduced-motion branch: durations -> 1ms (disable slide/overshoot/particles). */
        val Reduced = Default.copy(
            tileSlideMs = 1,
            genericMs = 1,
            mediumMs = 1,
            slowMs = 1,
            reduced = true,
        )
    }
}
