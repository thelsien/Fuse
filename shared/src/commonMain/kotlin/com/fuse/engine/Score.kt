package com.fuse.engine

import kotlinx.serialization.Serializable

/**
 * Pure, immutable scoring state for a single game (ENG-7).
 *
 * Scoring in 2048 is entirely a function of merges: when two tiles fuse, the
 * value of the **resulting** tile is added to the running score. There is no
 * score for sliding, spawning, or no-op moves. See [scoreDelta].
 *
 * @property current the running score for the in-progress game. Starts at 0 and
 *   only ever grows during a game (deltas are non-negative); it is zeroed by
 *   [startNewGame].
 * @property best the highest [current] reached. It rises with [current] (via
 *   [add]) and never decreases — in particular it survives [startNewGame]. This
 *   models the **within-session** best.
 *
 * ## Where `best` is scoped
 * `best` here is the best score reached across games **in this engine/session**:
 * it is carried by [startNewGame] but starts at 0 for a brand-new [Score]. A
 * *persisted, cross-session* best (survives app restart) is **not** this story —
 * it is Sprint 2 `UIB-4`/persistence, which will load a stored best into the
 * initial [Score.best] and write back [Score.best] after each game. Modeling
 * `best` as part of the state now means that layer has nothing new to compute.
 *
 * ## Numeric range
 * Both fields are [Long]. A maxed-out 4x4 board tops out well within [Int], but
 * larger boards and very long sessions can accumulate beyond [Int.MAX_VALUE], so
 * the running/best totals use [Long] to be safe. Individual move deltas are also
 * computed as [Long] (see [scoreDelta]).
 *
 * Construct the initial state with [zero]; the state machine (ENG-9) holds one
 * [Score] and replaces it after each accepted move via [add].
 */
@Serializable
data class Score(
    val current: Long = 0L,
    val best: Long = 0L,
) {
    init {
        require(current >= 0L) { "current score must be non-negative; was $current" }
        require(best >= current) { "best ($best) must be >= current ($current)" }
    }

    /**
     * Returns a new [Score] with [delta] added to [current], raising [best] to the
     * new maximum. [delta] must be non-negative (a move never lowers the score; a
     * no-op/no-merge move contributes 0).
     */
    fun add(delta: Long): Score {
        require(delta >= 0L) { "score delta must be non-negative; was $delta" }
        if (delta == 0L) return this
        val next = current + delta
        return Score(current = next, best = maxOf(best, next))
    }

    /**
     * Starts a new game: zeroes [current] but **preserves** [best]. This is what
     * ENG-9 calls on "new game"/reset — the player's best score for the session
     * carries over while the live score restarts at 0.
     */
    fun startNewGame(): Score = if (current == 0L) this else Score(current = 0L, best = best)

    companion object {
        /** The starting score for a fresh game/session: `current = 0`, `best = 0`. */
        val zero: Score = Score(0L, 0L)
    }
}

/**
 * The score gained by a single [MoveResult]: the sum of the resulting tile values
 * across all of the move's merges. A no-op or merge-free move yields 0.
 *
 * This is the canonical way ENG-9 turns an accepted move into a score delta:
 * `score = score.add(moveResult.scoreDelta())`.
 */
fun MoveResult.scoreDelta(): Long = merges.scoreDelta()

/**
 * The score gained by a list of merge events: the sum of their [BoardMergeEvent.resultingValue].
 * Exposed separately so callers that only have the merge list (or want to test the
 * pure summation) need not build a full [MoveResult].
 */
fun List<BoardMergeEvent>.scoreDelta(): Long = sumOf { it.resultingValue.toLong() }
