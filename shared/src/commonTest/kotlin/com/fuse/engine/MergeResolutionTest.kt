package com.fuse.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeResolutionTest {

    private fun tile(value: Int, id: Long) = Tile(value, id)

    /** Id source whose first minted id is far above any hand-chosen source id. */
    private fun ids(start: Long = 100L) = TileIdSource(start)

    // ---- No-op / nothing to resolve ---------------------------------------

    @Test
    fun emptyLineResolvesToEmptyUnchangedNoMerges() {
        val result = resolveLine(emptyList(), ids())
        assertEquals(emptyList(), result.line)
        assertFalse(result.changed)
        assertTrue(result.merges.isEmpty())
    }

    @Test
    fun allNullLineIsUnchangedNoMerges() {
        val line = listOf<Tile?>(null, null, null, null)
        val result = resolveLine(line, ids())
        assertEquals(line, result.line)
        assertFalse(result.changed)
        assertTrue(result.merges.isEmpty())
    }

    @Test
    fun singleTileDoesNotMergeAndKeepsItsId() {
        val a = tile(2, 7)
        val line = listOf<Tile?>(a, null, null, null)
        val result = resolveLine(line, ids())
        assertEquals(listOf<Tile?>(a, null, null, null), result.line)
        assertFalse(result.changed)
        assertTrue(result.merges.isEmpty())
        assertEquals(7L, result.line[0]!!.id)
    }

    @Test
    fun nonEqualNeighboursDoNotMerge() {
        val a = tile(2, 1)
        val b = tile(4, 2)
        val line = listOf<Tile?>(a, b, null, null)
        val result = resolveLine(line, ids())
        assertEquals(listOf<Tile?>(a, b, null, null), result.line)
        assertFalse(result.changed)
        assertTrue(result.merges.isEmpty())
        // Ids of non-merged tiles preserved verbatim.
        assertEquals(listOf(1L, 2L), result.line.filterNotNull().map { it.id })
    }

    // ---- Basic merge ------------------------------------------------------

    @Test
    fun twoEqualTilesMergeToDoubledValueWithNewIdSourcesPreservedInEvent() {
        val a = tile(2, 1)
        val b = tile(2, 2)
        val line = listOf<Tile?>(a, b, null, null)
        val idSource = ids(100L)
        val result = resolveLine(line, idSource)

        // Result: one 4 with a fresh id (100), rest null.
        assertEquals(1, result.line.filterNotNull().size)
        val merged = result.line[0]!!
        assertEquals(4, merged.value)
        assertEquals(100L, merged.id)
        // New id distinct from both sources.
        assertTrue(merged.id != 1L && merged.id != 2L)
        assertTrue(result.changed)

        assertEquals(1, result.merges.size)
        val event = result.merges[0]
        assertEquals(4, event.resultingValue)
        assertEquals(100L, event.resultId)
        assertEquals(1L, event.sourceIdA)
        assertEquals(2L, event.sourceIdB)
        assertEquals(0, event.sourceIndexA)
        assertEquals(1, event.sourceIndexB)
    }

    @Test
    fun mergeAcrossAGapStillMergesAndReportsOriginalIndices() {
        // [2#1, _, 2#2, _] compacts to [2,2] then merges to [4].
        val a = tile(2, 1)
        val b = tile(2, 2)
        val line = listOf<Tile?>(a, null, b, null)
        val result = resolveLine(line, ids(100L))
        assertEquals(4, result.line[0]!!.value)
        assertEquals(100L, result.line[0]!!.id)
        assertTrue(result.changed)
        assertEquals(1, result.merges.size)
        // Source indices reference the ORIGINAL line positions (0 and 2), not packed.
        assertEquals(0, result.merges[0].sourceIndexA)
        assertEquals(2, result.merges[0].sourceIndexB)
    }

    @Test
    fun changedTrueForCompactAlreadyOrderedMergeablePairWhereCompactionAloneWouldNot() {
        // [2,2,_,_] is already compact: compactLine -> changed=false, but a merge
        // happens, so resolveLine must report changed=true.
        val line = listOf<Tile?>(tile(2, 1), tile(2, 2), null, null)
        assertFalse(compactLine(line).changed)
        val result = resolveLine(line, ids())
        assertTrue(result.changed)
    }

    // ---- Merge once per move ----------------------------------------------

    @Test
    fun fourEqualTilesMergeIntoTwoPairsNeverEightWallSideFirst() {
        // 2 2 2 2 -> 4 4 (two independent merges), never 8.
        val a = tile(2, 1)
        val b = tile(2, 2)
        val c = tile(2, 3)
        val d = tile(2, 4)
        val line = listOf<Tile?>(a, b, c, d)
        val idSource = ids(100L)
        val result = resolveLine(line, idSource)

        val values = result.line.filterNotNull().map { it.value }
        assertEquals(listOf(4, 4), values)
        // Two merges, in wall-side-first order: first pair gets id 100, second 101.
        assertEquals(2, result.merges.size)
        assertEquals(100L, result.line[0]!!.id)
        assertEquals(101L, result.line[1]!!.id)

        val first = result.merges[0]
        assertEquals(1L, first.sourceIdA)
        assertEquals(2L, first.sourceIdB)
        assertEquals(100L, first.resultId)
        val second = result.merges[1]
        assertEquals(3L, second.sourceIdA)
        assertEquals(4L, second.sourceIdB)
        assertEquals(101L, second.resultId)
        assertTrue(result.changed)
    }

    @Test
    fun threeEqualTilesMergeWallSidePairLeavingTrailingTile() {
        // 2 2 2 -> 4 2 : wall-side pair merges, lone trailing 2 keeps its id.
        val a = tile(2, 1)
        val b = tile(2, 2)
        val c = tile(2, 3)
        val line = listOf<Tile?>(a, b, c, null)
        val result = resolveLine(line, ids(100L))

        assertEquals(listOf(4, 2), result.line.filterNotNull().map { it.value })
        // Merged tile new id; trailing tile keeps original id 3.
        assertEquals(100L, result.line[0]!!.id)
        assertEquals(3L, result.line[1]!!.id)
        assertEquals(1, result.merges.size)
        assertEquals(1L, result.merges[0].sourceIdA)
        assertEquals(2L, result.merges[0].sourceIdB)
        assertTrue(result.changed)
    }

    @Test
    fun wallSidePairMergesNotTheOtherPossiblePairing() {
        // 2 2 2 -> 4 2, NOT 2 4. The first (index 0/1) pair is the one that merges.
        val line = listOf<Tile?>(tile(2, 10), tile(2, 11), tile(2, 12), null)
        val result = resolveLine(line, ids(100L))
        assertEquals(4, result.line[0]!!.value)
        assertEquals(2, result.line[1]!!.value)
        // The surviving lone tile is the LAST one (id 12), proving the wall-side
        // pair (10, 11) merged.
        assertEquals(12L, result.line[1]!!.id)
    }

    // ---- Mixed lines ------------------------------------------------------

    @Test
    fun nonMergingAndMergingTilesCoexist() {
        // 4 2 2 -> 4 4 : leading 4 untouched, trailing pair of 2s merges.
        val four = tile(4, 1)
        val twoA = tile(2, 2)
        val twoB = tile(2, 3)
        val line = listOf<Tile?>(four, twoA, twoB, null)
        val result = resolveLine(line, ids(100L))

        assertEquals(listOf(4, 4), result.line.filterNotNull().map { it.value })
        // Leading 4 keeps its original id; new 4 from merge gets fresh id 100.
        assertEquals(1L, result.line[0]!!.id)
        assertEquals(100L, result.line[1]!!.id)
        assertEquals(1, result.merges.size)
        assertEquals(2L, result.merges[0].sourceIdA)
        assertEquals(3L, result.merges[0].sourceIdB)
        assertEquals(4, result.merges[0].resultingValue)
    }

    @Test
    fun adjacencyAfterCompactionMattersNotOriginalAdjacency() {
        // [2#1, 4#2, _, 2#3] compacts to [2,4,2] -> no merge (no equal adjacents).
        val line = listOf<Tile?>(tile(2, 1), tile(4, 2), null, tile(2, 3))
        val result = resolveLine(line, ids())
        assertEquals(listOf(2, 4, 2), result.line.filterNotNull().map { it.value })
        assertTrue(result.merges.isEmpty())
        // Compaction still moved a tile, so changed is true.
        assertTrue(result.changed)
        assertEquals(listOf(1L, 2L, 3L), result.line.filterNotNull().map { it.id })
    }

    @Test
    fun differentValuedPairsBothMerge() {
        // 2 2 4 4 -> 4 8 : two merges of different values.
        val line = listOf<Tile?>(tile(2, 1), tile(2, 2), tile(4, 3), tile(4, 4))
        val result = resolveLine(line, ids(100L))
        assertEquals(listOf(4, 8), result.line.filterNotNull().map { it.value })
        assertEquals(2, result.merges.size)
        assertEquals(4, result.merges[0].resultingValue)
        assertEquals(8, result.merges[1].resultingValue)
        assertEquals(100L, result.line[0]!!.id)
        assertEquals(101L, result.line[1]!!.id)
    }

    // ---- Reverse convention (direction-agnostic) --------------------------

    @Test
    fun reverseConventionResolvesTowardOppositeEdgeWallSideFirstFromThatEdge() {
        // Caller reverses for RIGHT/DOWN. From the right edge, 2 2 2 -> _ 2 4.
        // line: [2#1, 2#2, 2#3, _]; reverse -> [_, 2#3, 2#2, 2#1]; resolve toward 0.
        val line = listOf<Tile?>(tile(2, 1), tile(2, 2), tile(2, 3), null)
        val result = resolveLine(line.reversed(), ids(100L))
        val backToBoard = result.line.reversed()
        // Wall-side from the right is index 3's tile (id 3) pairing with id 2 -> 4.
        assertEquals(listOf(null, null, 2, 4), backToBoard.map { it?.value })
        // The lone surviving 2 is the one nearest the LEFT (id 1).
        assertEquals(1L, backToBoard.filterNotNull().first().id)
    }

    // ---- Id source consumption --------------------------------------------

    @Test
    fun idSourceConsumedExactlyOncePerMerge() {
        val idSource = ids(100L)
        // 2 2 2 2 -> two merges -> consumes ids 100 and 101.
        resolveLine(listOf<Tile?>(tile(2, 1), tile(2, 2), tile(2, 3), tile(2, 4)), idSource)
        assertEquals(102L, idSource.peek())
    }

    @Test
    fun idSourceNotConsumedWhenNoMerge() {
        val idSource = ids(100L)
        resolveLine(listOf<Tile?>(tile(2, 1), tile(4, 2), null, null), idSource)
        assertEquals(100L, idSource.peek())
    }
}
