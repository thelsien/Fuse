package com.fuse.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoveTest {

    /** Id source whose first minted id is far above any board-setup id. */
    private fun ids(start: Long = 1000L) = TileIdSource(start)

    /** Snapshot of the board's values, 0 for empty, for easy assertions. */
    private fun Board.values(): Array<IntArray> =
        Array(size) { r -> IntArray(size) { c -> this[r, c]?.value ?: 0 } }

    private fun assertValues(expected: Array<IntArray>, board: Board) {
        for (r in expected.indices) {
            assertEquals(
                expected[r].toList(),
                board.values()[r].toList(),
                "row $r mismatch\n$board",
            )
        }
    }

    private fun board(vararg rows: IntArray) = Board.fromValues(arrayOf(*rows))

    // ---- Basic slide per direction (no merges) ----------------------------

    @Test
    fun leftSlidesTilesToLeftEdge() {
        val b = board(
            intArrayOf(0, 2, 0, 4),
            intArrayOf(0, 0, 8, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(16, 0, 0, 0),
        )
        val r = b.move(Direction.LEFT, ids())
        assertTrue(r.changed)
        assertValues(
            arrayOf(
                intArrayOf(2, 4, 0, 0),
                intArrayOf(8, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(16, 0, 0, 0),
            ),
            r.board,
        )
        assertTrue(r.merges.isEmpty())
    }

    @Test
    fun rightSlidesTilesToRightEdge() {
        val b = board(
            intArrayOf(2, 0, 4, 0),
            intArrayOf(0, 8, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 16),
        )
        val r = b.move(Direction.RIGHT, ids())
        assertTrue(r.changed)
        assertValues(
            arrayOf(
                intArrayOf(0, 0, 2, 4),
                intArrayOf(0, 0, 0, 8),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 16),
            ),
            r.board,
        )
    }

    @Test
    fun upSlidesTilesToTopEdge() {
        val b = board(
            intArrayOf(0, 0, 0, 16),
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 8, 0, 0),
            intArrayOf(0, 0, 4, 0),
        )
        val r = b.move(Direction.UP, ids())
        assertTrue(r.changed)
        assertValues(
            arrayOf(
                intArrayOf(2, 8, 4, 16),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
            r.board,
        )
    }

    @Test
    fun downSlidesTilesToBottomEdge() {
        val b = board(
            intArrayOf(2, 0, 4, 16),
            intArrayOf(0, 8, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.DOWN, ids())
        assertTrue(r.changed)
        assertValues(
            arrayOf(
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(2, 8, 4, 16),
            ),
            r.board,
        )
    }

    // ---- 2 2 2 2 row per direction ----------------------------------------

    @Test
    fun fullTwoRowMergesToFourFourLeft() {
        val b = board(
            intArrayOf(2, 2, 2, 2),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.LEFT, ids())
        assertEquals(listOf(4, 4, 0, 0), r.board.values()[0].toList())
        assertEquals(2, r.merges.size)
        // wall-side first: result tiles land at columns 0 and 1.
        assertEquals(Position(0, 0), r.merges[0].resultPos)
        assertEquals(Position(0, 1), r.merges[1].resultPos)
    }

    @Test
    fun fullTwoRowMergesToFourFourRight() {
        val b = board(
            intArrayOf(2, 2, 2, 2),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.RIGHT, ids())
        assertEquals(listOf(0, 0, 4, 4), r.board.values()[0].toList())
        assertEquals(2, r.merges.size)
        // wall-side (right) first: results at columns 3 then 2.
        assertEquals(Position(0, 3), r.merges[0].resultPos)
        assertEquals(Position(0, 2), r.merges[1].resultPos)
    }

    @Test
    fun fullTwoColumnMergesToFourFourUp() {
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
        )
        val r = b.move(Direction.UP, ids())
        assertEquals(listOf(4, 4, 0, 0), b0Col(r.board))
        assertEquals(2, r.merges.size)
        assertEquals(Position(0, 0), r.merges[0].resultPos)
        assertEquals(Position(1, 0), r.merges[1].resultPos)
    }

    @Test
    fun fullTwoColumnMergesToFourFourDown() {
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
        )
        val r = b.move(Direction.DOWN, ids())
        assertEquals(listOf(0, 0, 4, 4), b0Col(r.board))
        assertEquals(2, r.merges.size)
        // wall-side (bottom) first: results at rows 3 then 2.
        assertEquals(Position(3, 0), r.merges[0].resultPos)
        assertEquals(Position(2, 0), r.merges[1].resultPos)
    }

    private fun b0Col(board: Board): List<Int> =
        (0 until board.size).map { board[it, 0]?.value ?: 0 }

    // ---- Wall-side-first single line merge --------------------------------

    @Test
    fun threeTwosMergeWallSidePairLeft() {
        val b = board(
            intArrayOf(2, 2, 2, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.LEFT, ids())
        assertEquals(listOf(4, 2, 0, 0), r.board.values()[0].toList())
        assertEquals(1, r.merges.size)
        assertEquals(Position(0, 0), r.merges[0].resultPos)
        assertEquals(Position(0, 0), r.merges[0].sourcePosA)
        assertEquals(Position(0, 1), r.merges[0].sourcePosB)
    }

    @Test
    fun threeTwosMergeWallSidePairRight() {
        val b = board(
            intArrayOf(0, 2, 2, 2),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.RIGHT, ids())
        assertEquals(listOf(0, 0, 2, 4), r.board.values()[0].toList())
        assertEquals(1, r.merges.size)
        // wall side is the right edge: sources at col 3 (A) and col 2 (B).
        assertEquals(Position(0, 3), r.merges[0].sourcePosA)
        assertEquals(Position(0, 2), r.merges[0].sourcePosB)
        assertEquals(Position(0, 3), r.merges[0].resultPos)
    }

    // ---- Id preservation & minting ----------------------------------------

    @Test
    fun slidTilesKeepIdsMergedTilesGetFreshIds() {
        // Pin ids: row 0 has tiles ids 1..4 in setup order.
        val b = Board.fromValues(
            arrayOf(
                intArrayOf(2, 2, 4, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        )
        // setup ids: (0,0)=1, (0,1)=2, (0,2)=3 (value 4).
        val src = ids(500L)
        val r = b.move(Direction.LEFT, src)
        // Merged 2+2 -> 4 with fresh id 500; the value-4 tile slides keeping id 3.
        val merged = r.board[0, 0]!!
        val slid = r.board[0, 1]!!
        assertEquals(4, merged.value)
        assertEquals(500L, merged.id)
        assertEquals(4, slid.value)
        assertEquals(3L, slid.id) // original id preserved
        assertEquals(1, r.merges.size)
        assertEquals(500L, r.merges[0].resultId)
        assertEquals(1L, r.merges[0].sourceIdA)
        assertEquals(2L, r.merges[0].sourceIdB)
    }

    @Test
    fun sameIdSourceThreadedAcrossLinesYieldsUniqueIds() {
        val b = board(
            intArrayOf(2, 2, 0, 0),
            intArrayOf(4, 4, 0, 0),
            intArrayOf(8, 8, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.LEFT, ids(700L))
        assertEquals(3, r.merges.size)
        val mintedIds = r.merges.map { it.resultId }.toSet()
        assertEquals(3, mintedIds.size) // all distinct
        assertTrue(mintedIds.all { it >= 700L })
    }

    // ---- Multiple simultaneous merges with correct positions --------------

    @Test
    fun multipleMergesAcrossRowsProduceCorrectBoardPositionsLeft() {
        val b = board(
            intArrayOf(2, 2, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 4, 4, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.LEFT, ids())
        assertEquals(2, r.merges.size)
        // Row 0 merge lands at (0,0); row 2 merge lands at (2,0).
        val resultPositions = r.merges.map { it.resultPos }.toSet()
        assertTrue(Position(0, 0) in resultPositions)
        assertTrue(Position(2, 0) in resultPositions)
        assertEquals(listOf(4, 0, 0, 0), r.board.values()[0].toList())
        assertEquals(listOf(8, 0, 0, 0), r.board.values()[2].toList())
    }

    @Test
    fun verticalMergePositionsTranslateForUp() {
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.UP, ids())
        assertEquals(1, r.merges.size)
        assertEquals(Position(0, 0), r.merges[0].sourcePosA)
        assertEquals(Position(1, 0), r.merges[0].sourcePosB)
        assertEquals(Position(0, 0), r.merges[0].resultPos)
    }

    // ---- No-op moves: changed=false, board equals input -------------------

    @Test
    fun noOpLeftReturnsEqualBoardChangedFalse() {
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(4, 8, 16, 32),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(16, 32, 64, 128),
        )
        val r = b.move(Direction.LEFT, ids())
        assertFalse(r.changed)
        assertEquals(b, r.board)
        assertTrue(r.merges.isEmpty())
    }

    @Test
    fun noOpRightReturnsEqualBoardChangedFalse() {
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(4, 8, 16, 32),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(16, 32, 64, 128),
        )
        val r = b.move(Direction.RIGHT, ids())
        assertFalse(r.changed)
        assertEquals(b, r.board)
    }

    @Test
    fun noOpUpReturnsEqualBoardChangedFalse() {
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(4, 8, 16, 32),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(16, 32, 64, 128),
        )
        val r = b.move(Direction.UP, ids())
        assertFalse(r.changed)
        assertEquals(b, r.board)
    }

    @Test
    fun noOpDownReturnsEqualBoardChangedFalse() {
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(4, 8, 16, 32),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(16, 32, 64, 128),
        )
        val r = b.move(Direction.DOWN, ids())
        assertFalse(r.changed)
        assertEquals(b, r.board)
    }

    @Test
    fun fullImmovableBoardReportsChangedFalseInEveryDirection() {
        // A checkerboard of alternating values has no equal neighbours and no gaps.
        val b = board(
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
        )
        for (d in Direction.entries) {
            val r = b.move(d, ids())
            assertFalse(r.changed, "expected no-op for $d")
            assertEquals(b, r.board, "board must be unchanged for $d")
            assertTrue(r.merges.isEmpty(), "no merges expected for $d")
        }
    }

    @Test
    fun emptyBoardIsNoOpInEveryDirection() {
        val b = Board.empty()
        for (d in Direction.entries) {
            val r = b.move(d, ids())
            assertFalse(r.changed)
            assertEquals(b, r.board)
            assertTrue(r.merges.isEmpty())
        }
    }

    // ---- Combined slide + merge per direction -----------------------------

    @Test
    fun slideThenMergeLeftKeepsTrailingTile() {
        val b = board(
            intArrayOf(0, 2, 2, 4),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.LEFT, ids())
        assertEquals(listOf(4, 4, 0, 0), r.board.values()[0].toList())
        assertEquals(1, r.merges.size)
    }

    @Test
    fun slideThenMergeDownKeepsTrailingTile() {
        val b = board(
            intArrayOf(4, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val r = b.move(Direction.DOWN, ids())
        // bottom-ward: the two 2s (rows 1,2) merge into 4 at the bottom edge,
        // the original 4 stacks above it.
        assertEquals(listOf(0, 0, 4, 4), b0Col(r.board))
        assertEquals(1, r.merges.size)
        assertEquals(Position(3, 0), r.merges[0].resultPos)
    }
}
