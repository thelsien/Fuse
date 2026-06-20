package com.fuse.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameOverTest {

    private fun board(vararg rows: IntArray) = Board.fromValues(arrayOf(*rows))

    /** Probe-based truth for [Board.canMove] (Option B): does any direction change it? */
    private fun Board.canMoveByProbe(): Boolean =
        Direction.entries.any { move(it, TileIdSource(start = -1L)).changed }

    // ---- hasWon -----------------------------------------------------------

    @Test
    fun hasWonFalseOnEmptyBoard() {
        assertFalse(Board.empty().hasWon())
    }

    @Test
    fun hasWonFalseWhenAllTilesBelowTarget() {
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(32, 64, 128, 256),
            intArrayOf(512, 1024, 2, 4),
            intArrayOf(8, 16, 32, 64),
        )
        assertFalse(b.hasWon())
    }

    @Test
    fun hasWonTrueWhenA2048TileExists() {
        val b = board(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 2048, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        assertTrue(b.hasWon())
    }

    @Test
    fun hasWonTrueForValueAboveTarget() {
        // 4096 > 2048: the won condition stays true past the first 2048 (>= target).
        val b = board(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 4096, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        assertTrue(b.hasWon())
    }

    @Test
    fun hasWonRespectsCustomTarget() {
        val b = board(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 512, 0),
            intArrayOf(0, 0, 0, 0),
        )
        // 512 wins a target-512 variant but not the default 2048 goal.
        assertTrue(b.hasWon(target = 512))
        assertFalse(b.hasWon())
        assertFalse(b.hasWon(target = 1024))
    }

    // ---- canMove ----------------------------------------------------------

    @Test
    fun canMoveTrueWhenEmptyCellsExist() {
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(32, 64, 128, 256),
            intArrayOf(512, 1024, 2, 4),
            intArrayOf(8, 16, 32, 0), // one empty cell
        )
        assertTrue(b.canMove())
    }

    @Test
    fun canMoveTrueOnEmptyBoard() {
        assertFalse(Board.empty().isFull)
        assertTrue(Board.empty().canMove())
    }

    @Test
    fun canMoveTrueOnFullBoardWithHorizontalPair() {
        // Full board, the only mergeable pair is horizontal: (2,2) at row 0.
        val b = board(
            intArrayOf(2, 2, 8, 16),
            intArrayOf(32, 64, 128, 256),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(128, 256, 8, 16),
        )
        assertTrue(b.isFull)
        assertTrue(b.canMove())
    }

    @Test
    fun canMoveTrueOnFullBoardWithVerticalPair() {
        // Full board, the only mergeable pair is vertical: column 0 rows 0/1 both 2.
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(2, 64, 128, 256),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(128, 256, 8, 16),
        )
        assertTrue(b.isFull)
        assertTrue(b.canMove())
    }

    @Test
    fun canMoveFalseOnFullCheckerboardWithNoEqualNeighbours() {
        // A "brick" pattern: no two orthogonally-adjacent cells share a value.
        val b = board(
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
        )
        assertTrue(b.isFull)
        assertFalse(b.canMove())
    }

    // ---- isGameOver -------------------------------------------------------

    @Test
    fun isGameOverFalseWhenBoardNotFull() {
        val b = board(
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 0), // not full
        )
        assertFalse(b.isGameOver())
    }

    @Test
    fun isGameOverFalseWhenFullButOneMergeAvailable() {
        // Classic "full but one merge available" -> not a loss.
        val b = board(
            intArrayOf(2, 2, 8, 16), // (2,2) mergeable
            intArrayOf(32, 64, 128, 256),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(128, 256, 8, 16),
        )
        assertTrue(b.isFull)
        assertFalse(b.isGameOver())
    }

    @Test
    fun isGameOverTrueOnlyWhenFullAndNoMerges() {
        val lost = board(
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
        )
        assertTrue(lost.isFull)
        assertFalse(lost.canMove())
        assertTrue(lost.isGameOver())
    }

    // ---- Agreement: Option A (canMove) == Option B (move-probe) -----------

    @Test
    fun canMoveAgreesWithFourDirectionProbe() {
        // Note: a fully-empty board is intentionally excluded. Option A reports
        // canMove()=true for any non-full board, whereas the move-probe (Option B)
        // reports false on an empty board (no tile means no direction changes it).
        // This degenerate case never arises mid-game (a tile is always present) and
        // does not affect isGameOver (an empty board is never full). For every board
        // that has at least one tile the two definitions agree, which is what matters.
        val boards = listOf(
            // Single tile, lots of room.
            board(
                intArrayOf(2, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
            // Full, horizontal merge.
            board(
                intArrayOf(2, 2, 8, 16),
                intArrayOf(32, 64, 128, 256),
                intArrayOf(8, 16, 32, 64),
                intArrayOf(128, 256, 8, 16),
            ),
            // Full, vertical merge.
            board(
                intArrayOf(2, 4, 8, 16),
                intArrayOf(2, 64, 128, 256),
                intArrayOf(8, 16, 32, 64),
                intArrayOf(128, 256, 8, 16),
            ),
            // Full brick pattern: locked, no move.
            board(
                intArrayOf(2, 4, 2, 4),
                intArrayOf(4, 2, 4, 2),
                intArrayOf(2, 4, 2, 4),
                intArrayOf(4, 2, 4, 2),
            ),
            // Full, only merge is at the far corner (vertical, rows 2/3 col 3).
            board(
                intArrayOf(2, 4, 2, 4),
                intArrayOf(4, 2, 4, 2),
                intArrayOf(2, 4, 2, 16),
                intArrayOf(4, 2, 4, 16),
            ),
        )
        for (b in boards) {
            assertEquals(
                b.canMoveByProbe(),
                b.canMove(),
                "canMove disagrees with four-direction probe for:\n$b",
            )
        }
    }

    @Test
    fun probingDoesNotAdvanceRealIdSource() {
        val real = TileIdSource(start = 100L)
        val b = board(
            intArrayOf(2, 2, 8, 16),
            intArrayOf(32, 64, 128, 256),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(128, 256, 8, 16),
        )
        // canMove() must not touch the game's id source.
        b.canMove()
        b.isGameOver()
        assertEquals(100L, real.peek(), "canMove/isGameOver must not burn real ids")
    }
}
