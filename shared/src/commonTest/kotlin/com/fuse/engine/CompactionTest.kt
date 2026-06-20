package com.fuse.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactionTest {

    private fun tile(value: Int, id: Long) = Tile(value, id)

    // ---- Empty / all-null -------------------------------------------------

    @Test
    fun emptyLineStaysEmptyAndUnchanged() {
        val result = compactLine(emptyList())
        assertEquals(emptyList(), result.line)
        assertFalse(result.changed)
    }

    @Test
    fun allNullLineStaysAllNullAndUnchanged() {
        val line = listOf<Tile?>(null, null, null, null)
        val result = compactLine(line)
        assertEquals(line, result.line)
        assertFalse(result.changed)
    }

    // ---- Full line (no gaps) ----------------------------------------------

    @Test
    fun fullLineIsUnchanged() {
        val line = listOf<Tile?>(tile(2, 1), tile(4, 2), tile(8, 3), tile(16, 4))
        val result = compactLine(line)
        assertEquals(line, result.line)
        assertFalse(result.changed)
    }

    // ---- Single tile slides to edge (various positions) -------------------

    @Test
    fun singleTileAlreadyAtEdgeIsUnchanged() {
        val line = listOf<Tile?>(tile(2, 7), null, null, null)
        val result = compactLine(line)
        assertEquals(line, result.line)
        assertFalse(result.changed)
    }

    @Test
    fun singleTileAtIndexOneSlidesToEdgePreservingId() {
        val line = listOf<Tile?>(null, tile(2, 7), null, null)
        val result = compactLine(line)
        assertEquals(listOf<Tile?>(tile(2, 7), null, null, null), result.line)
        assertTrue(result.changed)
        // id (and value) preserved.
        assertEquals(7L, result.line[0]!!.id)
    }

    @Test
    fun singleTileAtFarEdgeSlidesToIndexZeroPreservingId() {
        val line = listOf<Tile?>(null, null, null, tile(32, 9))
        val result = compactLine(line)
        assertEquals(listOf<Tile?>(tile(32, 9), null, null, null), result.line)
        assertTrue(result.changed)
        assertEquals(9L, result.line[0]!!.id)
    }

    // ---- Multiple tiles with gaps collapse, order + ids preserved ---------

    @Test
    fun multipleTilesWithGapsCollapsePreservingOrderAndIds() {
        // [_, 2#11, _, 4#22] -> [2#11, 4#22, _, _]
        val a = tile(2, 11)
        val b = tile(4, 22)
        val line = listOf<Tile?>(null, a, null, b)
        val result = compactLine(line)
        assertEquals(listOf<Tile?>(a, b, null, null), result.line)
        assertTrue(result.changed)
        // Relative order preserved and ids carried over verbatim.
        assertEquals(11L, result.line[0]!!.id)
        assertEquals(22L, result.line[1]!!.id)
    }

    @Test
    fun leadingGapWithTrailingTilesPreservesOrderAndIds() {
        // [_, _, 2#1, 4#2] -> [2#1, 4#2, _, _]
        val a = tile(2, 1)
        val b = tile(4, 2)
        val line = listOf<Tile?>(null, null, a, b)
        val result = compactLine(line)
        assertEquals(listOf<Tile?>(a, b, null, null), result.line)
        assertTrue(result.changed)
        assertEquals(listOf(1L, 2L), result.line.filterNotNull().map { it.id })
    }

    @Test
    fun equalValuedTilesAreNotMergedHere() {
        // No merging in ENG-3: two 2s stay as two distinct tiles, ids preserved.
        val a = tile(2, 1)
        val b = tile(2, 2)
        val line = listOf<Tile?>(a, null, b, null)
        val result = compactLine(line)
        assertEquals(listOf<Tile?>(a, b, null, null), result.line)
        assertTrue(result.changed)
        assertEquals(listOf(1L, 2L), result.line.filterNotNull().map { it.id })
    }

    // ---- changed flag correctness -----------------------------------------

    @Test
    fun changedIsFalseWhenNothingMoves() {
        val line = listOf<Tile?>(tile(2, 1), tile(4, 2), null, null)
        assertFalse(compactLine(line).changed)
    }

    @Test
    fun changedIsTrueWhenAnyTileMoves() {
        val line = listOf<Tile?>(tile(2, 1), null, tile(4, 2), null)
        assertTrue(compactLine(line).changed)
    }

    // ---- Idempotency ------------------------------------------------------

    @Test
    fun compactingTwiceEqualsCompactingOnceAndSecondIsUnchanged() {
        val line = listOf<Tile?>(null, tile(2, 1), null, tile(4, 2))
        val once = compactLine(line)
        val twice = compactLine(once.line)
        assertEquals(once.line, twice.line)
        assertFalse(twice.changed)
    }

    @Test
    fun idempotencyHoldsForAlreadyCompactInput() {
        val line = listOf<Tile?>(tile(8, 5), tile(16, 6), null, null)
        val first = compactLine(line)
        assertFalse(first.changed)
        val second = compactLine(first.line)
        assertEquals(first.line, second.line)
        assertFalse(second.changed)
    }

    // ---- Reverse convention demonstration (for ENG-4 / ENG-5) -------------

    @Test
    fun reverseConventionCompactsTowardOppositeEdge() {
        // Caller reverses for RIGHT/DOWN: extract reversed, compact, reverse back.
        val a = tile(2, 1)
        val b = tile(4, 2)
        val line = listOf<Tile?>(a, null, b, null)
        val result = compactLine(line.reversed()).line.reversed()
        // Tiles slid toward the far (index size-1) edge, order + ids preserved.
        assertEquals(listOf<Tile?>(null, null, a, b), result)
    }
}
