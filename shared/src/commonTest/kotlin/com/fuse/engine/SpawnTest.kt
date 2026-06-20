package com.fuse.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpawnTest {

    private fun board(vararg rows: IntArray) = Board.fromValues(arrayOf(*rows))

    /** Number of tiles currently on the board. */
    private fun Board.tileCount(): Int = tiles().size

    // ---- Deterministic Rng stubs (for boundary tests) ----------------------
    //
    // For the 90/10 boundary we need to pin nextDouble() exactly (a seed can
    // never land on 0.9 precisely). These stubs feed scripted draws while still
    // satisfying the Rng contract, so spawnTile's roll convention is asserted
    // independently of SplitMix64.

    /** Rng that returns scripted values for nextInt / nextDouble in order. */
    private class ScriptedRng(
        private val ints: List<Int> = emptyList(),
        private val doubles: List<Double> = emptyList(),
    ) : Rng {
        private var intCursor = 0
        private var doubleCursor = 0
        override fun nextLong(): Long = 0L
        override fun nextInt(bound: Int): Int = ints[intCursor++]
        override fun nextDouble(): Double = doubles[doubleCursor++]
    }

    // ---- Spawns into the only empty cell -----------------------------------

    @Test
    fun spawnsIntoTheOnlyEmptyCell() {
        // Full board except (2, 3).
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(4, 8, 16, 32),
            intArrayOf(8, 16, 32, 0),
            intArrayOf(16, 32, 64, 128),
        )
        assertEquals(1, b.emptyCells().size)

        val ids = TileIdSource(1000L)
        val result = b.spawnTile(SeededRng(42L), ids)

        assertNotNull(result.spawned)
        assertEquals(Position(2, 3), result.position)
        // The spawned tile is actually on the returned board at that cell.
        assertEquals(result.spawned, result.board[2, 3])
        assertTrue(result.board.isFull)
    }

    // ---- Spawns into a random empty cell, deterministically ----------------

    @Test
    fun spawnsIntoDeterministicRandomCellWithSeed42() {
        // Single tile at (0,0) -> 15 empty cells, bound 15.
        // SeededRng(42): nextInt(15)=6 (-> emptyCells[6] == (1,3)),
        //                nextDouble()=0.1599... (< 0.9 -> value 2).
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val ids = TileIdSource(1000L)
        val result = b.spawnTile(SeededRng(42L), ids)

        assertEquals(Position(1, 3), result.position)
        assertEquals(2, result.spawned?.value)
        assertEquals(2, result.board.tileCount())
    }

    // ---- Value roll: 2 vs 4 per the 90/10 convention -----------------------

    @Test
    fun rollsValueTwoWhenSeedDrawsBelowThreshold() {
        // SeededRng(42): nextDouble after the cell pick is 0.1599... (< 0.9).
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val result = b.spawnTile(SeededRng(42L), TileIdSource())
        assertEquals(2, result.spawned?.value)
    }

    @Test
    fun rollsValueFourWhenSeedDrawsAtOrAboveThreshold() {
        // SeededRng(123): nextInt(15)=1 (-> (0,2)), nextDouble()=0.9765... (>= 0.9 -> 4).
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val result = b.spawnTile(SeededRng(123L), TileIdSource())
        assertEquals(Position(0, 2), result.position)
        assertEquals(4, result.spawned?.value)
    }

    @Test
    fun rollExactlyAtThresholdYieldsFour() {
        // The boundary is strictly `<`: a draw of exactly 0.9 must yield a 4.
        val b = Board.empty()
        val rng = ScriptedRng(ints = listOf(0), doubles = listOf(0.9))
        val result = b.spawnTile(rng, TileIdSource())
        assertEquals(4, result.spawned?.value)
    }

    @Test
    fun rollJustBelowThresholdYieldsTwo() {
        val b = Board.empty()
        val rng = ScriptedRng(ints = listOf(0), doubles = listOf(0.8999999999))
        val result = b.spawnTile(rng, TileIdSource())
        assertEquals(2, result.spawned?.value)
    }

    // ---- Id minted fresh from idSource -------------------------------------

    @Test
    fun spawnedTileGetsFreshIdFromIdSource() {
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val ids = TileIdSource(500L)
        val expectedId = ids.peek() // 500
        val result = b.spawnTile(SeededRng(42L), ids)

        assertEquals(expectedId, result.spawned?.id)
        // idSource advanced exactly once.
        assertEquals(501L, ids.peek())
    }

    // ---- Exactly one tile spawned ------------------------------------------

    @Test
    fun spawnsExactlyOneTile() {
        val b = board(
            intArrayOf(2, 0, 4, 0),
            intArrayOf(0, 8, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 16),
        )
        val before = b.tileCount()
        val result = b.spawnTile(SeededRng(7L), TileIdSource())
        assertEquals(before + 1, result.board.tileCount())
    }

    // ---- Full board: nothing spawns, no throw ------------------------------

    @Test
    fun fullBoardSpawnsNothingAndDoesNotThrow() {
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(4, 8, 16, 32),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(16, 32, 64, 128),
        )
        assertTrue(b.isFull)
        val result = b.spawnTile(SeededRng(42L), TileIdSource())

        assertNull(result.spawned)
        assertNull(result.position)
        assertFalse(result.didSpawn)
        // Board unchanged.
        assertEquals(b, result.board)
    }

    @Test
    fun fullBoardConsumesNoRngDraws() {
        // A scripted rng that would throw if any draw were consumed: the empty
        // check must short-circuit before touching the rng.
        val b = board(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(4, 8, 16, 32),
            intArrayOf(8, 16, 32, 64),
            intArrayOf(16, 32, 64, 128),
        )
        val emptyRng = ScriptedRng() // empty scripts -> any draw is out of bounds
        val result = b.spawnTile(emptyRng, TileIdSource())
        assertFalse(result.didSpawn)
    }

    // ---- Immutability of the input board -----------------------------------

    @Test
    fun originalBoardIsUnchanged() {
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val snapshot = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val result = b.spawnTile(SeededRng(42L), TileIdSource())
        // Receiver still has exactly one tile and equals its pre-spawn snapshot.
        assertEquals(1, b.tileCount())
        assertEquals(snapshot, b)
        // The result board is a different instance with the extra tile.
        assertEquals(2, result.board.tileCount())
    }

    // ---- move + spawn convenience ------------------------------------------

    @Test
    fun moveAndSpawnSpawnsExactlyOneTileOnAChangedMove() {
        // LEFT slides (0,1)->(0,0); a changed move, so one tile spawns.
        val b = board(
            intArrayOf(0, 2, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val ids = TileIdSource(1000L)
        val result = b.moveAndSpawn(Direction.LEFT, SeededRng(42L), ids)

        assertTrue(result.changed)
        assertTrue(result.move.changed)
        assertTrue(result.spawn.didSpawn)
        // One original tile (slid) + one spawned = 2.
        assertEquals(2, result.board.tiles().size)
    }

    @Test
    fun moveAndSpawnDoesNotSpawnOnANoOpMove() {
        // Already left-packed single row -> LEFT is a no-op, no spawn.
        val b = board(
            intArrayOf(2, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val ids = TileIdSource(1000L)
        val result = b.moveAndSpawn(Direction.LEFT, SeededRng(42L), ids)

        assertFalse(result.changed)
        assertFalse(result.spawn.didSpawn)
        assertNull(result.spawn.spawned)
        // Board unchanged: still exactly one tile, no id minted for a spawn.
        assertEquals(1, result.board.tiles().size)
        assertEquals(b, result.board)
    }

    @Test
    fun moveAndSpawnThreadsTheSameIdSourceAcrossMergeAndSpawn() {
        // A row that merges on LEFT: (0,0)=2 + (0,1)=2 -> 4 (fresh merge id),
        // then a tile spawns (fresh spawn id). Both ids come from the same source
        // and must be distinct.
        val b = board(
            intArrayOf(2, 2, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        )
        val ids = TileIdSource(2000L)
        val result = b.moveAndSpawn(Direction.LEFT, SeededRng(42L), ids)

        assertTrue(result.changed)
        val mergeId = result.move.merges.single().resultId
        val spawnId = result.spawn.spawned?.id
        assertNotNull(spawnId)
        assertTrue(mergeId != spawnId, "merge id $mergeId collided with spawn id $spawnId")
    }
}
