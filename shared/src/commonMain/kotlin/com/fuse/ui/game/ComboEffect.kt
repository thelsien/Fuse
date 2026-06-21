package com.fuse.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.fuse.feedback.comboCount
import com.fuse.feedback.comboIntensity
import com.fuse.ui.theme.FuseTheme

/**
 * FEL-7 — the combo COUNTER + escalating VISUAL cue: a brief, self-dismissing **"x{n}" badge**
 * that pops in over the board when a single move produces multiple merges (a combo).
 *
 * ## How it triggers (one-shot, from the dispatch signal)
 * [GameScreen] already collects the store's non-replaying `effects` flow and reacts to
 * `GameEffect.Moved`. FEL-7 extends that collector: when [comboCount] of the move's
 * `mergedValues` is >= 2, it raises a local one-shot [ComboTrigger] (the combo count + a
 * monotonically increasing nonce, so two *consecutive* combos of the same size still restart the
 * effect). This composable is keyed on that token, so a new combo (re)starts the pop from the top.
 * Driving off the one-shot effect (not persistent state) means the badge plays ONCE per combo move
 * and never re-shows on later moves or on recomposition — exactly like the FEL-6 milestone wiring.
 *
 * ## The escalating cue (acceptance criterion)
 * The cue gets STRONGER as the combo grows, driven by the pure [comboIntensity] curve:
 *  - the badge **scales bigger** (a x4 badge is noticeably larger than a x2),
 *  - its color **brightens** from the brand accent toward the mint `good` at higher counts, and
 *  - it carries a soft **glow** ring whose size grows with intensity.
 * The "x{n}" text always reads the exact merge count, so the player sees both the number and the
 * escalation. Lifetime is a fixed [FuseTheme.motion.comboMs] (a quick pop-in + fade).
 *
 * ## Reduced motion (hard acceptance criterion)
 * The whole effect is gated on `!FuseTheme.motion.reduced`. When reduced motion is active the
 * composable renders NOTHING — no badge, no glow. This is the app-wide reduced-motion signal
 * FEL-8 will flip by swapping the provided motion set to `.Reduced`.
 *
 * ## Layering with the milestone effect
 * A move can be BOTH a combo and a milestone (e.g. several pairs fuse and one result is a 512).
 * Both overlays may show: the milestone flash/burst fills the screen and is centred, while THIS
 * badge sits high (top-third) so the "x{n}" stays legible above the burst and neither clobbers the
 * other. They are independent one-shot tokens with independent lifetimes.
 */
@Composable
fun ComboEffect(
    token: ComboTrigger?,
    modifier: Modifier = Modifier,
) {
    // Reduced-motion gate (hard AC): suppress the entire effect. Nothing is composed.
    if (FuseTheme.motion.reduced) return
    if (token == null) return

    val durationMs = FuseTheme.motion.comboMs
    val intensity = comboIntensity(token.count)
    val accent = FuseTheme.colors.accent
    val good = FuseTheme.colors.good

    // One driver, 0→1 over the effect lifetime; keyed on the token so a new combo restarts.
    val progress = remember(token) { Animatable(0f) }
    LaunchedEffect(token) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMs))
    }
    val p = progress.value

    // Escalation: bigger at higher intensity, brighter (accent → mint) at higher intensity.
    val peakScale = COMBO_MIN_SCALE + (COMBO_MAX_SCALE - COMBO_MIN_SCALE) * intensity
    val badgeColor = lerpColor(accent, good, intensity)
    val glowColor = good
    val badgeSizeDp = (COMBO_MIN_BADGE_DP + (COMBO_MAX_BADGE_DP - COMBO_MIN_BADGE_DP) * intensity).dp
    val fontSizeSp = (COMBO_MIN_FONT_SP + (COMBO_MAX_FONT_SP - COMBO_MIN_FONT_SP) * intensity).sp

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(ComboEffectTags.ROOT),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = COMBO_TOP_PADDING_DP.dp)
                .size(badgeSizeDp)
                .scale(comboScale(p, peakScale))
                .alpha(comboAlpha(p))
                .drawBehind {
                    // Soft glow ring that grows with intensity, fading with the badge.
                    val glowAlpha = comboAlpha(p) * (0.35f + 0.45f * intensity)
                    if (glowAlpha > 0f) {
                        drawCircle(
                            color = glowColor.copy(alpha = glowAlpha.coerceIn(0f, 1f)),
                            radius = size.minDimension * (0.55f + 0.25f * intensity),
                        )
                    }
                    drawCircle(color = badgeColor)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "x${token.count}",
                color = Color.White,
                fontSize = fontSizeSp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(ComboEffectTags.LABEL),
            )
        }
    }
}

/**
 * A one-shot combo trigger token raised by [GameScreen] from a multi-merge `GameEffect.Moved`.
 *
 * [count] is the number of merges in the move (drives the "x{n}" text and the escalation);
 * [nonce] makes two *consecutive* combos of the same size distinct values, so the keyed
 * [ComboEffect] restarts the pop rather than treating the repeat as "no change".
 */
data class ComboTrigger(val count: Int, val nonce: Long)

/**
 * PURE scale envelope for the badge at animation [progress] (0..1): a quick overshoot up to
 * [peakScale] early, then a settle toward `1f` of the peak as it fades. Bounded so the badge
 * always reads as a pop. Unit-tested.
 */
fun comboScale(progress: Float, peakScale: Float): Float {
    val pr = progress.coerceIn(0f, 1f)
    return if (pr <= SCALE_PEAK_AT) {
        // Rise from a small start to the peak (overshoot).
        val t = pr / SCALE_PEAK_AT
        COMBO_START_SCALE + (peakScale - COMBO_START_SCALE) * t
    } else {
        // Settle from the peak back toward a resting fraction of the peak.
        val t = (pr - SCALE_PEAK_AT) / (1f - SCALE_PEAK_AT)
        peakScale + (peakScale * SCALE_SETTLE_FRACTION - peakScale) * t
    }
}

/**
 * PURE alpha envelope for the badge at animation [progress] (0..1): fades in fast, holds, then
 * fades out to 0 by the end so the badge reads as a brief pop. Bounded to `[0,1]`; `0f` at
 * `progress >= 1`. Unit-tested.
 */
fun comboAlpha(progress: Float): Float {
    val pr = progress.coerceIn(0f, 1f)
    val env = when {
        pr <= ALPHA_IN_AT -> pr / ALPHA_IN_AT                       // fade in
        pr >= ALPHA_OUT_FROM -> (1f - pr) / (1f - ALPHA_OUT_FROM)   // fade out
        else -> 1f                                                  // hold
    }
    return env.coerceIn(0f, 1f)
}

/** Linear interpolation between two colors by [t] in `[0,1]`. */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue = a.blue + (b.blue - a.blue) * tt,
        alpha = a.alpha + (b.alpha - a.alpha) * tt,
    )
}

// Escalation extents (driven by comboIntensity 0..1).
private const val COMBO_MIN_SCALE = 1.0f
private const val COMBO_MAX_SCALE = 1.6f
private const val COMBO_MIN_BADGE_DP = 64f
private const val COMBO_MAX_BADGE_DP = 96f
private const val COMBO_MIN_FONT_SP = 24f
private const val COMBO_MAX_FONT_SP = 36f
private const val COMBO_TOP_PADDING_DP = 72f

// Scale envelope shape.
private const val COMBO_START_SCALE = 0.6f
private const val SCALE_PEAK_AT = 0.30f
private const val SCALE_SETTLE_FRACTION = 0.92f

// Alpha envelope shape.
private const val ALPHA_IN_AT = 0.15f
private const val ALPHA_OUT_FROM = 0.65f

/** Stable test tags so UI tests target the combo effect without depending on visuals. */
object ComboEffectTags {
    const val ROOT: String = "combo_effect"
    const val LABEL: String = "combo_label"
}
