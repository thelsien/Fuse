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
 * @property spawnedId the id of the tile that SPAWNED on this move (a brand-new id that
 *   is NOT a merge result), or `null` when nothing spawned (blocked / new-game / resume).
 *   Drives the FEL-3 entrance (scale+fade, started after the slide settles). A spawned
 *   tile is never also a merge result — see [isSpawn] / [fromOutcome] for that invariant.
 */
data class BoardTransition(
    val resultValueById: Map<Long, Int>,
    val spawnedId: Long? = null,
) {
    /** True if [tileId] is a merge result that should pop this transition (FEL-2). */
    fun isMergeResult(tileId: Long): Boolean = resultValueById.containsKey(tileId)

    /** Resulting value of the merge that produced [tileId], or `null` if not a result. */
    fun resultValue(tileId: Long): Int? = resultValueById[tileId]

    /**
     * True if [tileId] is the tile that spawned this move and should play the FEL-3
     * entrance. Mutually exclusive with [isMergeResult]: a spawn is never a merge result,
     * so a given new id plays at most one of the two animations.
     */
    fun isSpawn(tileId: Long): Boolean = spawnedId != null && tileId == spawnedId &&
        !isMergeResult(tileId)

    companion object {
        /** No pop / no spawn — semantically identical to a `null` transition. */
        val None: BoardTransition = BoardTransition(emptyMap())

        /**
         * Builds a transition from a move's [merges] (the store's `lastMerges`).
         * Maps each merge result id to its resulting value so the renderer can pop it.
         * Carries no spawn id — prefer [fromOutcome] to also animate the spawned tile.
         */
        fun fromMerges(merges: List<BoardMergeEvent>): BoardTransition =
            fromOutcome(merges, spawnedId = null)

        /**
         * FEL-3 — full transition from an accepted move: the [merges] (FEL-2 pops) plus the
         * [spawnedId] of the tile that appeared (FEL-3 entrance). The spawn id is only kept
         * when it is NOT also a merge result, preserving the spawn/merge mutual exclusion.
         * Returns [None] only when there is nothing at all to animate.
         */
        fun fromOutcome(merges: List<BoardMergeEvent>, spawnedId: Long?): BoardTransition {
            val byId = merges.associate { it.resultId to it.resultingValue }
            // Guard: a spawned tile must never be a merge result too.
            val spawn = spawnedId?.takeIf { it !in byId }
            return if (byId.isEmpty() && spawn == null) {
                None
            } else {
                BoardTransition(resultValueById = byId, spawnedId = spawn)
            }
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

/**
 * FEL-3 — the spawn entrance's linear progress in `[0f, 1f]` at [elapsedMs] after the move,
 * given the [slideMs] (the entrance is held until the slide settles) and the [entranceMs]
 * (how long the scale+fade takes). Pure so the renderer, tests, and reduced-motion all agree:
 *
 *  - While `elapsedMs <= slideMs` it is exactly `0f` — the tile stays hidden DURING the slide,
 *    which is the acceptance criterion ("enters AFTER the movement settles").
 *  - Then it ramps linearly to `1f` over [entranceMs], clamped at `1f` afterwards.
 *
 * Under reduced motion both durations are ~1ms, so this snaps to `1f` almost immediately.
 * The renderer applies an easing on top of this for the actual animation; the contract this
 * function fixes is the *gate* (zero until the slide settles) and the bounds.
 */
fun spawnEntranceProgress(elapsedMs: Int, slideMs: Int, entranceMs: Int): Float {
    if (elapsedMs <= slideMs) return 0f
    if (entranceMs <= 0) return 1f
    val into = (elapsedMs - slideMs).toFloat() / entranceMs
    return into.coerceIn(0f, 1f)
}

/** FEL-3 — scale at a given entrance [progress]: from [SPAWN_START_SCALE] up to 1.0. */
fun spawnEntranceScale(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return SPAWN_START_SCALE + (1f - SPAWN_START_SCALE) * p
}

/** The scale a spawned tile starts at (alpha 0) before its entrance grows it to 1.0. */
const val SPAWN_START_SCALE = 0.3f
