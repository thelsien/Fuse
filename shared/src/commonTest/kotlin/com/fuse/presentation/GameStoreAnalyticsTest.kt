package com.fuse.presentation

import com.fuse.analytics.AnalyticsEvents
import com.fuse.analytics.AnalyticsParams
import com.fuse.analytics.AnalyticsValues
import com.fuse.analytics.FakeAnalyticsLogger
import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.Score
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ANL-2 — instrumentation of the Classic [GameStore]: `game_start` on an explicit new game and
 * `game_over` (mode/score/best_tile/moves) on the move that LOSES the game. The store takes the
 * [FakeAnalyticsLogger] so we assert exactly what is logged at the trigger; no PII is passed.
 */
class GameStoreAnalyticsTest {

    @Test
    fun newGameLogsGameStartWithClassicMode() {
        val analytics = FakeAnalyticsLogger()
        val store = GameStore(initialSeed = 1L, analytics = analytics)
        // No game_start on construction/resume — only on the explicit intent.
        assertTrue(analytics.loggedEvents.isEmpty(), "construction logs nothing")

        store.accept(GameIntent.NewGame(seed = 7L))

        val event = analytics.loggedEvents.single()
        assertEquals(AnalyticsEvents.GAME_START, event.name)
        assertEquals(AnalyticsValues.MODE_CLASSIC, event.params[AnalyticsParams.MODE])
    }

    @Test
    fun blockedAndAcceptedNonLosingMovesLogNoGameOver() {
        val analytics = FakeAnalyticsLogger()
        val playing = GameState(
            board = Board.fromValues(
                arrayOf(
                    intArrayOf(2, 2, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                ),
            ),
            score = Score.zero,
            phase = GamePhase.Playing,
            rngState = 1L,
            nextTileId = 100L,
            moveCount = 0,
        )
        val store = GameStore(startState = playing, analytics = analytics)

        store.accept(GameIntent.Move(Direction.UP))   // blocked (single top row)
        store.accept(GameIntent.Move(Direction.LEFT)) // accepted merge, not a loss

        assertTrue(
            analytics.loggedEvents.none { it.name == AnalyticsEvents.GAME_OVER },
            "no game_over until the game is actually lost",
        )
    }

    @Test
    fun losingMoveLogsGameOverWithScoreBestTileAndMovesExactlyOnce() {
        // The spawn after the LEFT merge is driven by the state's rngState. Brute-force a rngState
        // whose spawn locks the board into a loss, so the assertion is deterministic.
        val losingRng = (1L until 20000L).first { rng -> losesOnLeft(rng) }

        val analytics = FakeAnalyticsLogger()
        val store = GameStore(startState = nearLossState(losingRng), analytics = analytics)

        store.accept(GameIntent.Move(Direction.LEFT))
        assertTrue(store.state.value.isGameOver, "precondition: the move lost the game")

        val gameOvers = analytics.loggedEvents.filter { it.name == AnalyticsEvents.GAME_OVER }
        assertEquals(1, gameOvers.size, "exactly one game_over on the losing move")
        val event = gameOvers.single()
        val finalState = store.state.value
        assertEquals(AnalyticsValues.MODE_CLASSIC, event.params[AnalyticsParams.MODE])
        assertEquals(finalState.currentScore.toInt(), event.params[AnalyticsParams.SCORE])
        assertEquals(31, event.params[AnalyticsParams.MOVES], "30 prior moves + this losing move")
        assertEquals(
            finalState.board.tiles().maxOf { it.value },
            event.params[AnalyticsParams.BEST_TILE],
        )
    }

    /**
     * A FULL checkerboard whose only mergeable pair is the two trailing 4s in the last row. A LEFT
     * move merges them (4 2 4 4 → 4 2 8 _), freeing one cell that the spawn refills to a full board;
     * whether that locks into a loss depends on the spawn value/position, so the test brute-forces an
     * [rng] for which it does — making the game-over deterministic.
     */
    private fun nearLossState(rng: Long): GameState = GameState(
        board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 4, 2, 4),
                intArrayOf(4, 2, 4, 2),
                intArrayOf(2, 4, 2, 4),
                intArrayOf(4, 2, 4, 4),
            ),
        ),
        score = Score.zero,
        phase = GamePhase.Playing,
        rngState = rng,
        nextTileId = 100L,
        moveCount = 30,
    )

    /** Whether a LEFT move from the near-loss board with this [rng] ends the game (deterministic). */
    private fun losesOnLeft(rng: Long): Boolean {
        val outcome = nearLossState(rng).applyMove(Direction.LEFT)
        return outcome.accepted && outcome.gameOver
    }
}
