package com.fuse.ui.board

import com.fuse.engine.BoardMergeEvent
import kotlin.math.ln

/**
 * FEL-2 — merge-pop model + intensity curve.
 *
 * When two tiles fuse, the resulting (new-id) tile gets a brief scale-bounce "pop"
 * plus a transient glow. Both the bounce's peak overshoot and the glow's strength
 * scale with the resulting value's TIER, so a 1024 pop reads bigger than a 4 pop.
 *
 * The renderer ([BoardView]) is told which just-rendered tile ids are merge results
 * via a [BoardTransition]; the per-id pop magnitude is [mergeIntensity] of that
 * result's value. The curve is pure (no Compose) so it is unit-tested in commonTest
 * on every platform.
 */

/**
 * What the just-accepted move did, in render terms — fed to [BoardView] so it can pop
 * the merged-result tiles. Defaulting [BoardView]'s `transition` to `null` keeps the
 * plain `BoardView(board)` callers unchanged (no pop, FEL-1 behaviour).
 *
 * Carries the set of merge-result tile ids (the new ids that should pop) keyed to their
 * resulting value (which drives [mergeIntensity]). Source positions are retained for a
 * possible later source-slide polish but are not consumed yet (see [BoardView] notes).
 *
 * @property resultIntensityById result tile id -> its resulting value, for tiles that
 *   should play the pop on this frame. Empty ⇒ nothing pops (equivalent to `null`).
 */
data class BoardTransition(
    val resultValueById: Map<Long, Int>,
) {
    /** True if [tileId] is a merge result that should pop this transition. */
    fun isMergeResult(tileId: Long): Boolean = resultValueById.containsKey(tileId)

    /** Resulting value of the merge that produced [tileId], or `null` if not a result. */
    fun resultValue(tileId: Long): Int? = resultValueById[tileId]

    companion object {
        /** No pop — semantically identical to a `null` transition. */
        val None: BoardTransition = BoardTransition(emptyMap())

        /**
         * Builds a transition from a move's [merges] (the store's `lastMerges`).
         * Maps each merge result id to its resulting value so the renderer can pop it.
         */
        fun fromMerges(merges: List<BoardMergeEvent>): BoardTransition =
            if (merges.isEmpty()) {
                None
            } else {
                BoardTransition(merges.associate { it.resultId to it.resultingValue })
            }
    }
}

/**
 * Normalised pop/glow intensity in `[0f, 1f]` for a merge that produced [value].
 *
 * Intensity rises with the tile TIER (its `log2`), not linearly with the value, so the
 * jump from 4→8 carries as much extra punch as 512→1024. It is anchored so the smallest
 * real merge (a 4) is gentle and the brand 2048 tier is at/near the cap, and it is
 * monotonic non-decreasing and clamped to `[0,1]`. Values at or below the first merge
 * tier floor at the minimum; values above 2048 stay capped at `1f`.
 *
 * Pure — unit-tested in commonTest (monotonic, bounded, capped).
 */
fun mergeIntensity(value: Int): Float {
    if (value <= MIN_MERGE_VALUE) return 0f
    // tier = log2(value). The first real merge is 4 (tier 2); the brand tier is 2048
    // (tier 11). Map tier 2 -> 0f and tier 11 -> 1f, linearly, then clamp.
    val tier = ln(value.toFloat()) / LN2
    val normalized = (tier - MIN_TIER) / (MAX_TIER - MIN_TIER)
    return normalized.coerceIn(0f, 1f)
}

/** The lowest value that can be a merge result is 4; anything <= 2 has no pop. */
private const val MIN_MERGE_VALUE = 2

/** log2 of the smallest merge result (4) and of the brand tier (2048). */
private const val MIN_TIER = 2f  // log2(4)
private const val MAX_TIER = 11f // log2(2048)

private val LN2 = ln(2f)

/**
 * The peak scale the result tile reaches at the top of its bounce, for a given
 * [intensity] (0..1). A gentle floor so even a small merge visibly pops, growing to a
 * punchier overshoot for high tiers. Pure so the renderer and tests agree on the number.
 */
fun mergePopPeakScale(intensity: Float): Float =
    POP_PEAK_MIN + (POP_PEAK_MAX - POP_PEAK_MIN) * intensity.coerceIn(0f, 1f)

/** Peak scale bounds: a 4 pops to ~1.10x, a 2048 to ~1.30x. */
private const val POP_PEAK_MIN = 1.10f
private const val POP_PEAK_MAX = 1.30f

/**
 * Peak glow alpha for a given [intensity] (0..1) — the transient highlight strength.
 * Pure; the renderer fades from this to 0 over the glow duration.
 */
fun mergeGlowPeakAlpha(intensity: Float): Float =
    GLOW_ALPHA_MIN + (GLOW_ALPHA_MAX - GLOW_ALPHA_MIN) * intensity.coerceIn(0f, 1f)

private const val GLOW_ALPHA_MIN = 0.25f
private const val GLOW_ALPHA_MAX = 0.85f
