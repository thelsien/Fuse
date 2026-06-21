package com.fuse.feedback

/**
 * FEL-7 — the PURE combo decision used to drive the combo COUNTER + escalating VISUAL cue.
 *
 * A "combo" is a single accepted move that produces MORE THAN ONE merge — line up several
 * equal pairs, swipe once, and they all fuse together. [GameEffect.Moved] carries
 * `mergedValues`, the resulting value of every merge that happened in that one move, so its
 * SIZE is exactly the number of merges — i.e. the combo size.
 *
 * Pure and Compose-free, so the decision (and its escalation curve) is unit-tested on JVM and
 * iOS Native, and the [ComboEffect] composable stays a thin renderer over the result.
 */

/** A move must produce at least this many merges to count as a combo worth showing. */
const val MIN_COMBO: Int = 2

/**
 * The number of merges produced by one accepted move (`mergedValues.size`) — the combo size
 * shown as "x{n}". A pure slide or a non-merging move is 0; a single merge is 1 (not a combo).
 */
fun comboCount(mergedValues: List<Int>): Int = mergedValues.size

/**
 * Whether [mergedValues] represents a combo worth a visual cue: [comboCount] >= [MIN_COMBO].
 * Single-merge and no-merge moves are NOT combos (they already get FEL-2/4/5 per-merge feedback).
 */
fun isCombo(mergedValues: List<Int>): Boolean = comboCount(mergedValues) >= MIN_COMBO

/**
 * The escalation curve for a combo of [count] merges, normalised to `[0f, 1f]`.
 *
 * The cue gets stronger as the combo grows (bigger/brighter/longer-lived badge), so this maps
 * the combo size onto an intensity the [ComboEffect] reads. It is:
 *  - `0f` below the combo threshold (no combo, no cue),
 *  - rising monotonically from the first real combo ([MIN_COMBO]) up to [MAX_COMBO_TIER], and
 *  - clamped to `1f` at and beyond [MAX_COMBO_TIER] (a board can only hold so many pairs, but we
 *    cap defensively so a freak large combo can't overdrive the visual).
 *
 * Pure — unit-tested in commonTest (monotonic non-decreasing, bounded, capped, 0 below threshold).
 */
fun comboIntensity(count: Int): Float {
    if (count < MIN_COMBO) return 0f
    val span = (MAX_COMBO_TIER - MIN_COMBO).toFloat()
    val normalized = (count - MIN_COMBO) / span
    return normalized.coerceIn(0f, 1f)
}

/**
 * The combo size at which the escalation reaches full intensity. On a 4x4 board the most pairs
 * a single axis-aligned move can fuse is small, so x4 is treated as the loud end of the curve;
 * larger combos stay capped at `1f`.
 */
const val MAX_COMBO_TIER: Int = 4
