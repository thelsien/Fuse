package com.fuse.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.fuse.feedback.MILESTONES
import com.fuse.ui.board.mergeIntensity
import com.fuse.ui.theme.FuseTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * FEL-6 — the milestone celebration VISUAL: a brief, self-dismissing **screen flash** plus a
 * **particle burst** that fires when a merge produces a milestone tile (512 / 1024 / 2048;
 * see [MILESTONES]).
 *
 * ## How it triggers (one-shot, from the dispatch signal)
 * [GameScreen] already collects the store's non-replaying `effects` flow and reacts to
 * `GameEffect.Moved`. FEL-6 extends that collector: when [milestoneReached] of the move's
 * `mergedValues` is non-null, it raises a local one-shot `trigger` (a value-typed token: the
 * milestone value + a monotonically increasing nonce, so two *consecutive* milestones of the
 * same tier still restart the effect). This composable is keyed on that token, so a new
 * milestone (re)starts the flash + burst from the top. Driving off the one-shot effect (not
 * persistent state) means the celebration plays ONCE when the milestone is reached and never
 * re-shows on later moves or on recomposition — exactly like the FEL-5/UIB-5 wiring.
 *
 * ## Reduced motion (hard acceptance criterion)
 * The whole effect is gated on `!FuseTheme.motion.reduced`. When reduced motion is active the
 * composable renders NOTHING — no flash, no particles. This is the app-wide reduced-motion
 * signal FEL-8 will flip by swapping the provided [FuseMotion] to `.Reduced`; for now it is
 * `false` by default so the effect shows, but the gate is real and tested.
 *
 * ## The animation
 * One [Animatable] `progress` runs 0→1 over [FuseTheme.motion.milestoneMs] (`tween`). From it:
 *  - the **flash** is a full-bleed scrim of the brand mint (`colors.good`) whose alpha rises
 *    fast then fades to 0 (a quick pulse), and
 *  - the **burst** is a ring of [particleCount] circles launched from the screen centre
 *    outward to a max radius (their positions/alpha/size are the pure [milestoneParticles]).
 * Intensity scales with the milestone tier via [mergeIntensity] (a 2048 burst is bigger/brighter
 * than a 512). A `LaunchedEffect(token)` animates to 1 then is done; recomposition with a null
 * token (the default rest state) clears it.
 */
@Composable
fun MilestoneEffect(
    token: MilestoneTrigger?,
    modifier: Modifier = Modifier,
) {
    // Reduced-motion gate (hard AC): suppress the entire effect. Nothing is composed.
    if (FuseTheme.motion.reduced) return
    if (token == null) return

    val durationMs = FuseTheme.motion.milestoneMs
    val good = FuseTheme.colors.good
    val intensity = mergeIntensity(token.value)

    // One driver, 0→1 over the effect lifetime; keyed on the token so a new milestone restarts.
    val progress = remember(token) { Animatable(0f) }
    LaunchedEffect(token) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMs))
    }

    val p = progress.value
    val flashAlpha = milestoneFlashAlpha(p) * (0.55f + 0.45f * intensity)
    val particles = remember(token) { milestoneParticles(token.value) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(MilestoneEffectTags.ROOT),
    ) {
        // Screen flash — a full-bleed mint pulse that fades out.
        if (flashAlpha > 0f) {
            drawRect(color = good.copy(alpha = flashAlpha.coerceIn(0f, 1f)))
        }

        // Particle burst — circles launched from centre outward, fading as they fly.
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = minOf(size.width, size.height) * (0.45f + 0.15f * intensity)
        val baseDot = minOf(size.width, size.height) * 0.012f
        particles.forEach { particle ->
            val dist = maxRadius * particle.distanceFraction * p
            val x = center.x + cos(particle.angleRad) * dist
            val y = center.y + sin(particle.angleRad) * dist
            val alpha = (particle.baseAlpha * (1f - p)).coerceIn(0f, 1f)
            if (alpha > 0f) {
                drawCircle(
                    color = good.copy(alpha = alpha),
                    radius = baseDot * particle.sizeScale * (0.6f + 0.4f * intensity),
                    center = Offset(x, y),
                )
            }
        }
    }
}

/**
 * A one-shot milestone trigger token raised by [GameScreen] from a milestone `GameEffect.Moved`.
 *
 * [value] is the milestone tile reached (drives intensity); [nonce] makes two *consecutive*
 * milestones of the same value distinct values, so the keyed [MilestoneEffect] restarts the
 * animation rather than treating the repeat as "no change". Concurrent/rapid milestones simply
 * (re)start the effect on the latest one — the documented, simplest acceptable behaviour.
 */
data class MilestoneTrigger(val value: Int, val nonce: Long)

/**
 * One particle in the burst: a launch [angleRad] (radians), how far out it flies as a fraction
 * of the max radius ([distanceFraction]), its starting [baseAlpha], and a [sizeScale]. Pure data
 * computed by [milestoneParticles] so the burst geometry is unit-testable without Compose.
 */
data class MilestoneParticle(
    val angleRad: Float,
    val distanceFraction: Float,
    val baseAlpha: Float,
    val sizeScale: Float,
)

/**
 * PURE particle-burst layout for a milestone [value]: a deterministic ring of particles fanned
 * evenly around the circle, with a stable per-particle jitter (no RNG, so tests are exact) so
 * they don't all fly the same distance. The higher the milestone tier the more particles, so a
 * 2048 burst is visibly bigger than a 512 one (intensity via [mergeIntensity]).
 *
 * Deterministic & Compose-free → unit-tested in commonTest (count grows with tier, angles span
 * the full circle, fractions/alpha bounded).
 */
fun milestoneParticles(value: Int): List<MilestoneParticle> {
    val intensity = mergeIntensity(value)
    val count = (MIN_PARTICLES + ((MAX_PARTICLES - MIN_PARTICLES) * intensity)).toInt()
        .coerceIn(MIN_PARTICLES, MAX_PARTICLES)
    val twoPi = 2f * 3.1415927f
    return (0 until count).map { i ->
        val angle = twoPi * i / count
        // Stable, RNG-free jitter from the index so particles vary without randomness.
        val wobble = ((i * 7) % 5) / 5f // 0.0, 0.2, 0.4, 0.6, 0.8 …
        MilestoneParticle(
            angleRad = angle,
            distanceFraction = 0.7f + 0.3f * wobble,
            baseAlpha = 0.65f + 0.35f * (1f - wobble),
            sizeScale = 0.8f + 0.6f * wobble,
        )
    }
}

private const val MIN_PARTICLES = 12
private const val MAX_PARTICLES = 24

/**
 * PURE flash-alpha envelope for the screen flash at animation [progress] (0..1): a quick rise to
 * a peak early, then a fade to 0 by the end, so the flash reads as a brief pulse rather than a
 * lingering tint. Bounded to `[0,1]`; `0f` at `progress >= 1`. Unit-tested.
 */
fun milestoneFlashAlpha(progress: Float): Float {
    val pr = progress.coerceIn(0f, 1f)
    val env = if (pr <= FLASH_PEAK_AT) {
        pr / FLASH_PEAK_AT                      // rise to the peak
    } else {
        (1f - pr) / (1f - FLASH_PEAK_AT)        // fade back to 0 by progress=1
    }
    return (env * FLASH_PEAK_ALPHA).coerceIn(0f, 1f)
}

/** The flash peaks early (snappy) then fades, and never fully whites out the board. */
private const val FLASH_PEAK_AT = 0.18f
private const val FLASH_PEAK_ALPHA = 0.45f

/** Stable test tags so UI tests target the milestone effect without depending on visuals. */
object MilestoneEffectTags {
    const val ROOT: String = "milestone_effect"
}
