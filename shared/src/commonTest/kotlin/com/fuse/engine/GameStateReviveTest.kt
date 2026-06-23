package com.fuse.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * ADS-2 — the pure REVIVE transition: a lost game becomes playable again by freeing space
 * (removing the lowest-valued tiles), the once-per-game [GameState.revivedThisGame] latch, and
 * serialization back-compat (the new field defaults so old saved blobs still decode).
 *
 * Runs on JVM + iOS Native (commonTest), no coroutines needed (revive is synchronous + pure).
 */
class GameStateReviveTest {

    private val json = Json

    private fun board(vararg rows: IntArray) = Board.fromValues(arrayOf(*rows))

    private fun stateOf(
        board: Board,
        phase: GamePhase = GamePhase.Playing,
        score: Score = Score.zero,
        moveCount: Int = 0,
        target: Int = DEFAULT_WIN_TARGET,
        revivedThisGame: Boolean = false,
    ): GameState = GameState(
        board = board,
        score = score,
        phase = phase,
        rngState = SeededRng(1L).state,
        nextTileId = 1000L,
        moveCount = moveCount,
        target = target,
        revivedThisGame = revivedThisGame,
    )

    /** A full, stuck 4x4 (no adjacent equals): [isGameOver] true. */
    private fun stuckBoard(): Board = board(
        intArrayOf(2, 4, 2, 4),
        intArrayOf(4, 2, 4, 2),
        intArrayOf(2, 4, 2, 4),
        intArrayOf(4, 2, 4, 2),
    )

    // --- the space-freeing rule -------------------------------------------------

    @Test
    fun reviveOnLostGameSetsPlayingFreesSpaceAndPreservesProgress() {
        val state = stateOf(
            board = stuckBoard(),
            phase = GamePhase.Lost,
            score = Score(current = 4321L, best = 9999L),
            moveCount = 77,
            target = 4096,
        )
        assertTrue(state.canRevive)
        assertTrue(state.isGameOver)

        val revived = state.revive()

        // Phase back to Playing; no longer game-over.
        assertTrue(revived.phase is GamePhase.Playing)
        assertFalse(revived.isGameOver)

        // The documented rule: at least ceil(16/3) = 6 free cells.
        assertEquals(6, reviveTargetEmptyCells(state.board))
        assertTrue(
            revived.board.emptyCells().size >= 6,
            "revive frees >= ceil(cellCount/3) cells (got ${revived.board.emptyCells().size})",
        )

        // Score / moveCount / target / rng / id all preserved.
        assertEquals(state.score, revived.score)
        assertEquals(state.moveCount, revived.moveCount)
        assertEquals(state.target, revived.target)
        assertEquals(state.rngState, revived.rngState)
        assertEquals(state.nextTileId, revived.nextTileId)

        // The once-per-game latch is now set.
        assertTrue(revived.revivedThisGame)
        assertFalse(revived.canRevive)
    }

    @Test
    fun reviveRemovesTheLOWESTValuedTilesFirst() {
        // Highest tiles must survive; only the cheapest are sacrificed to open the board.
        val state = stateOf(
            board = board(
                intArrayOf(2, 4, 8, 16),
                intArrayOf(32, 64, 128, 256),
                intArrayOf(512, 1024, 2, 4),
                intArrayOf(8, 16, 32, 64),
            ),
            phase = GamePhase.Lost,
        )
        val revived = state.revive()
        val survivingValues = revived.board.tiles().map { it.value }

        // The biggest tiles are untouched.
        assertTrue(512 in survivingValues, "512 survives")
        assertTrue(1024 in survivingValues, "1024 survives")
        assertTrue(256 in survivingValues, "256 survives")

        // 6 cleared → the 6 lowest-valued tiles removed. Lowest values here are the two 2s,
        // the two 4s, the two 8s (by ascending value, position tie-break). No 2 should remain.
        assertFalse(survivingValues.any { it == 2 }, "all the lowest (2) tiles are cleared")
    }

    @Test
    fun reviveIsDeterministicAndTieBrokenStablyByPosition() {
        val state = stateOf(board = stuckBoard(), phase = GamePhase.Lost)
        val a = state.revive()
        val b = state.revive()
        assertEquals(a.board, b.board, "same lost board always revives to the same board")
    }

    // --- guards: non-lost / already-revived -> no-op ----------------------------

    @Test
    fun reviveOnNonLostGameIsANoOp() {
        val playing = stateOf(
            board = board(
                intArrayOf(2, 4, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
            phase = GamePhase.Playing,
        )
        assertFalse(playing.canRevive)
        assertSame(playing, playing.revive(), "revive on a Playing game returns the same instance")

        val won = playing.copy(phase = GamePhase.Won())
        assertFalse(won.canRevive)
        assertSame(won, won.revive(), "revive on a Won game returns the same instance")
    }

    @Test
    fun reviveASecondTimeIsANoOp() {
        val state = stateOf(board = stuckBoard(), phase = GamePhase.Lost)
        val once = state.revive()
        assertTrue(once.revivedThisGame)

        // Force the (revived, then somehow lost again) board back to Lost to prove the latch,
        // not the phase, is what blocks a second revive.
        val lostAgain = once.copy(phase = GamePhase.Lost)
        assertFalse(lostAgain.canRevive, "an already-revived game cannot revive again")
        assertSame(lostAgain, lostAgain.revive(), "second revive is a no-op")
    }

    @Test
    fun newGameStartsWithRevivedFlagFalse() {
        val fresh = newGame(seed = 42L)
        assertFalse(fresh.revivedThisGame, "a fresh game has never been revived")
    }

    // --- serialization back-compat ---------------------------------------------

    @Test
    fun gameStateRoundTripsIncludingTheNewField() {
        val revived = stateOf(board = stuckBoard(), phase = GamePhase.Lost).revive()
        val decoded = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), revived))
        assertEquals(revived, decoded)
        assertTrue(decoded.revivedThisGame)
    }

    @Test
    fun oldBlobWithoutRevivedFieldDecodesToFalse() {
        // A persisted blob from BEFORE ADS-2: it has no `revivedThisGame` key. It must still
        // decode (the field defaults to false), so existing saves are not broken.
        val reference = stateOf(
            board = board(
                intArrayOf(2, 4, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
            phase = GamePhase.Playing,
            score = Score(current = 8L, best = 8L),
            moveCount = 3,
        )
        // kotlinx.serialization omits default values by default, so a normal encode of a
        // never-revived state is already byte-identical to an "old" blob with no revive key.
        val oldBlob = json.encodeToString(GameState.serializer(), reference)
        assertFalse(oldBlob.contains("revivedThisGame"), "the (old-style) blob has no revive key")

        val decoded = json.decodeFromString(GameState.serializer(), oldBlob)
        assertFalse(decoded.revivedThisGame, "missing field defaults to false")
        assertEquals(reference, decoded, "the rest of the state still decodes intact")
    }
}
