package com.fuse.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ScoreTest {

    // ---- Pure Score.add ----------------------------------------------------

    @Test
    fun addIncreasesCurrentAndRaisesBest() {
        val s = Score.zero.add(4)
        assertEquals(4L, s.current)
        assertEquals(4L, s.best)
    }

    @Test
    fun addAccumulatesCurrentAndTracksRunningMaxAsBest() {
        val s = Score.zero.add(4).add(8).add(16)
        assertEquals(28L, s.current)
        assertEquals(28L, s.best)
    }

    @Test
    fun addZeroIsANoOpAndReturnsSameInstance() {
        val s = Score(current = 10, best = 20)
        assertSame(s, s.add(0))
    }

    @Test
    fun addRejectsNegativeDelta() {
        assertFailsWith<IllegalArgumentException> { Score.zero.add(-1) }
    }

    @Test
    fun bestNeverDecreasesWhenCurrentIsReset() {
        // Reach a best, reset current, then climb again but stay below old best.
        val played = Score.zero.add(100) // current=100, best=100
        val newGame = played.startNewGame() // current=0, best=100
        val laterMove = newGame.add(30) // current=30, best stays 100
        assertEquals(30L, laterMove.current)
        assertEquals(100L, laterMove.best)
    }

    @Test
    fun bestRisesOnceCurrentExceedsOldBest() {
        val s = Score(current = 0, best = 100).add(50).add(60) // current=110
        assertEquals(110L, s.current)
        assertEquals(110L, s.best)
    }

    // ---- startNewGame ------------------------------------------------------

    @Test
    fun startNewGameZeroesCurrentAndKeepsBest() {
        val s = Score(current = 256, best = 512).startNewGame()
        assertEquals(0L, s.current)
        assertEquals(512L, s.best)
    }

    @Test
    fun startNewGameOnAlreadyZeroCurrentReturnsSameInstance() {
        val s = Score(current = 0, best = 512)
        assertSame(s, s.startNewGame())
    }

    // ---- Invariants --------------------------------------------------------

    @Test
    fun constructorRejectsNegativeCurrent() {
        assertFailsWith<IllegalArgumentException> { Score(current = -1, best = 0) }
    }

    @Test
    fun constructorRejectsBestBelowCurrent() {
        assertFailsWith<IllegalArgumentException> { Score(current = 10, best = 5) }
    }

    // ---- scoreDelta from a real Board.move ---------------------------------

    @Test
    fun singleMergeAddsResultingTileValue() {
        // 2 2 _ _ left -> a single 4. delta = 4.
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 2, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )
        val result = board.move(Direction.LEFT, TileIdSource(100L))
        assertEquals(1, result.merges.size)
        assertEquals(4L, result.scoreDelta())
        assertEquals(4L, Score.zero.add(result.scoreDelta()).current)
    }

    @Test
    fun fourTwosLeftYieldsTwoFoursForDeltaEight() {
        // 2 2 2 2 left -> 4 4 (two independent merges). delta = 4 + 4 = 8.
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 2, 2, 2),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )
        val result = board.move(Direction.LEFT, TileIdSource(100L))
        assertEquals(2, result.merges.size)
        assertEquals(listOf(4, 4), result.merges.map { it.resultingValue })
        assertEquals(8L, result.scoreDelta())
    }

    @Test
    fun multipleMergesAcrossRowsSumTogether() {
        // Two rows each "2 2 _ _" left -> two 4s. delta = 8.
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 2, 0, 0),
                intArrayOf(4, 4, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )
        val result = board.move(Direction.LEFT, TileIdSource(100L))
        // resulting tiles: 4 and 8 -> delta 12.
        assertEquals(12L, result.scoreDelta())
    }

    @Test
    fun noMergeMoveAddsZero() {
        // 2 _ _ 4 left -> tiles slide but do not merge. delta = 0.
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 0, 0, 4),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )
        val result = board.move(Direction.LEFT, TileIdSource(100L))
        assertEquals(0, result.merges.size)
        assertEquals(0L, result.scoreDelta())
        assertSame(Score.zero, Score.zero.add(result.scoreDelta()))
    }

    @Test
    fun noOpMoveAddsZero() {
        // Already compacted, no merges: LEFT is a no-op.
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 4, 8, 16),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )
        val result = board.move(Direction.LEFT, TileIdSource(100L))
        assertEquals(false, result.changed)
        assertEquals(0L, result.scoreDelta())
    }

    @Test
    fun runningTotalAccumulatesAcrossSeveralMoves() {
        // Drive a few real moves and accumulate the score the way ENG-9 will.
        var board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 2, 0, 0),
                intArrayOf(2, 2, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )
        val ids = TileIdSource(100L)
        var score = Score.zero

        // Move 1: LEFT -> each row "2 2" merges to 4. delta = 8.
        val m1 = board.move(Direction.LEFT, ids)
        board = m1.board
        score = score.add(m1.scoreDelta())
        assertEquals(8L, score.current)

        // Move 2: UP -> the two 4s in column 0 merge to 8. delta = 8.
        val m2 = board.move(Direction.UP, ids)
        board = m2.board
        score = score.add(m2.scoreDelta())
        assertEquals(16L, score.current)
        assertEquals(16L, score.best)
    }

    // ---- scoreDelta on a raw merge list ------------------------------------

    @Test
    fun emptyMergeListHasZeroDelta() {
        assertEquals(0L, emptyList<BoardMergeEvent>().scoreDelta())
    }

    @Test
    fun mergeListDeltaSumsResultingValues() {
        val merges = listOf(
            BoardMergeEvent(4, 1L, 2L, 3L, Position(0, 0), Position(0, 1), Position(0, 0)),
            BoardMergeEvent(8, 4L, 5L, 6L, Position(1, 0), Position(1, 1), Position(1, 0)),
        )
        assertEquals(12L, merges.scoreDelta())
    }

    // ---- Serialization round-trip ------------------------------------------

    @Test
    fun scoreRoundTripsThroughJson() {
        val score = Score(current = 1234, best = 5678)
        val encoded = Json.encodeToString(Score.serializer(), score)
        val decoded = Json.decodeFromString(Score.serializer(), encoded)
        assertEquals(score, decoded)
    }

    @Test
    fun zeroScoreRoundTripsThroughJson() {
        val encoded = Json.encodeToString(Score.serializer(), Score.zero)
        val decoded = Json.decodeFromString(Score.serializer(), encoded)
        assertEquals(Score.zero, decoded)
    }
}
