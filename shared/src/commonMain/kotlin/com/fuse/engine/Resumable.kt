package com.fuse.engine

/**
 * SHL-4 — the single, testable predicate that decides whether a saved game is worth
 * offering as a **Resume** (vs. silently auto-resuming or silently discarding).
 *
 * ## The exact rule
 * A [GameState] is *resumable* iff ALL of the following hold:
 *  1. **It exists** — `state != null` (a fresh install / cleared save has nothing to
 *     resume).
 *  2. **It is not over** — `phase` is NOT [GamePhase.Lost]. A finished (lost) game is
 *     done; offering "Continue" on it would be meaningless. (A [GamePhase.Won] game with
 *     `canContinue = true` IS resumable — classic 2048 lets you keep merging past 2048.)
 *  3. **It has actual progress** — `moveCount > 0`. A brand-new, untouched board (the
 *     two starting tiles, no move played) is indistinguishable from "start a new game",
 *     so it is NOT offered as a resume. Only a board the player has actually moved counts.
 *
 * Put plainly: *resumable == a saved, in-progress, not-yet-lost game the player has
 * actually moved at least once.*
 *
 * This is a pure function of [GameState]? so it is trivially unit-testable (commonTest)
 * and reused identically by the store's `canResume` projection and any UI query.
 *
 * @param state the saved game to test (typically `GameRepository.loadGame()`), or `null`.
 * @return `true` iff the saved game should be offered as a Resume on launch.
 */
fun isResumable(state: GameState?): Boolean {
    if (state == null) return false
    if (state.phase.isLost) return false
    return state.moveCount > 0
}
