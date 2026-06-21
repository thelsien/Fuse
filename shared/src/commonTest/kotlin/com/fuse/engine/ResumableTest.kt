package com.fuse.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SHL-4 — unit tests for the [isResumable] predicate (the single rule that decides
 * whether a saved game is offered as a Resume). Pure, so it lives in commonTest and
 * runs on JVM + iOS Native.
 *
 * The rule under test: resumable iff non-null AND not [GamePhase.Lost] AND `moveCount > 0`.
 */
class ResumableTest {

    private fun stateWith(
        phase: GamePhase = GamePhase.Playing,
        moveCount: Int = 1,
    ): GameState = GameState(
        board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 4, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        ),
        score = Score.zero,
        phase = phase,
        rngState = 1L,
        nextTileId = 100L,
        moveCount = moveCount,
    )

    @Test
    fun nullSaveIsNotResumable() {
        assertFalse(isResumable(null), "no save → nothing to resume")
    }

    @Test
    fun freshUntouchedGameIsNotResumable() {
        // A brand-new game (moveCount 0) is indistinguishable from "start new", so it is
        // NOT offered as a resume.
        val fresh = newGame(seed = 42L)
        assertFalse(isResumable(fresh), "moveCount 0 → not resumable")
        assertFalse(isResumable(stateWith(moveCount = 0)), "explicit moveCount 0 → not resumable")
    }

    @Test
    fun inProgressPlayingGameIsResumable() {
        assertTrue(
            isResumable(stateWith(phase = GamePhase.Playing, moveCount = 1)),
            "in-progress, moved at least once → resumable",
        )
        assertTrue(
            isResumable(stateWith(phase = GamePhase.Playing, moveCount = 37)),
            "deep in-progress game → resumable",
        )
    }

    @Test
    fun wonButContinuableGameIsResumable() {
        // Classic 2048 lets you keep merging past 2048, so a Won(canContinue) game is
        // still resumable.
        assertTrue(
            isResumable(stateWith(phase = GamePhase.Won(canContinue = true), moveCount = 5)),
            "Won-continue game → resumable",
        )
    }

    @Test
    fun lostGameIsNotResumable() {
        assertFalse(
            isResumable(stateWith(phase = GamePhase.Lost, moveCount = 200)),
            "a finished (lost) game → not resumable, however many moves",
        )
    }
}
