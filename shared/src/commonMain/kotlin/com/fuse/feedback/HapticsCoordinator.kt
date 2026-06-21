package com.fuse.feedback

/**
 * FEL-4 — the PURE decision: given the outcome of one move, decide which [Haptics] call(s)
 * to make. All "which buzz/tick/thunk for which event" logic lives here (not in the
 * platform impls), so it is fully unit-tested in `commonTest` with a recording
 * [FakeHaptics] across JVM and iOS Native.
 *
 * ## The mapping (acceptance criteria)
 *  - **A merge happened** (`mergedValues` non-empty) → [Haptics.tick] (one light tap per
 *    move, regardless of how many tiles merged — the move is the unit of feedback).
 *  - **A milestone tile was reached this move** (any merged result is in [MILESTONES],
 *    or `justWon`) → [Haptics.thunk]. We fire **thunk INSTEAD OF tick** for a milestone
 *    move (not both): a milestone is already a merge, and stacking a light tick under the
 *    heavy thunk just muddies the feel — the heavier impact subsumes the lighter one. So a
 *    milestone move emits exactly one, heavier, cue.
 *  - **The move was blocked** (no-op) → [Haptics.buzz] (distinct error pattern). A blocked
 *    move has no merges, so it never also ticks/thunks.
 *
 * ## The gate
 * Every method first consults [settings]; when [HapticsSettings.hapticsEnabled] is `false`
 * it returns immediately and **nothing fires** — the single seam that disables all haptics
 * (see [HapticsSettings] for the SHL-3 persistence/UI plan).
 *
 * ## Why milestone uses values, not the board
 * "Reached a milestone" means a merge *produced* a milestone-valued tile this move. We read
 * that from the move's merge results (`resultingValue`s) — the same data the store already
 * exposes as `GameUiState.lastMerges` — rather than scanning the board, so it is a property
 * of *this move* (you don't re-thunk every subsequent move just because a 512 still sits on
 * the board). `justWon` (first 2048) is folded in for symmetry even though 2048 is in
 * [MILESTONES], so the win also thunks even if the target is ever customised below 2048.
 *
 * @param haptics the platform feedback sink (real on device, [FakeHaptics] in tests).
 * @param settings the enable/disable gate.
 */
class HapticsCoordinator(
    private val haptics: Haptics,
    private val settings: HapticsSettings,
) {
    /**
     * Decide feedback for one ACCEPTED move.
     *
     * @param mergedValues the `resultingValue` of each merge this move (empty if none).
     * @param justWon `true` iff this move first reached the win target.
     */
    fun onMove(mergedValues: List<Int>, justWon: Boolean) {
        if (!settings.hapticsEnabled) return
        val reachedMilestone = justWon || mergedValues.any { it in MILESTONES }
        when {
            reachedMilestone -> haptics.thunk()
            mergedValues.isNotEmpty() -> haptics.tick()
            // A no-merge accepted move (e.g. a pure slide) is silent: no tactile cue.
            else -> {}
        }
    }

    /** Decide feedback for a BLOCKED (no-op) move: a distinct error buzz, gated by [settings]. */
    fun onBlocked() {
        if (!settings.hapticsEnabled) return
        haptics.buzz()
    }

    companion object {
        /**
         * The tile values that count as a "milestone" worthy of the heavier thunk.
         * Notable powers of two near/at the win target; reaching one of these the moment a
         * merge produces it is a satisfying beat. Kept as an explicit set so it is easy to
         * tune and is pure-tested. (Lower tiles like 128/256 merge constantly and would make
         * the thunk lose meaning.)
         */
        val MILESTONES: Set<Int> = setOf(512, 1024, 2048)
    }
}
