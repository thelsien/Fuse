package com.fuse.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardTest {

    // ---- Tile identity / equality / copy ----------------------------------

    @Test
    fun tilesWithSameValueAndIdAreEqual() {
        assertEquals(Tile(2, 1L), Tile(2, 1L))
        assertEquals(Tile(2, 1L).hashCode(), Tile(2, 1L).hashCode())
    }

    @Test
    fun tilesDifferingByIdAreNotEqual() {
        // Same value, different id -> different logical tile (animation identity).
        assertNotEquals(Tile(2, 1L), Tile(2, 2L))
    }

    @Test
    fun tilesDifferingByValueAreNotEqual() {
        assertNotEquals(Tile(2, 1L), Tile(4, 1L))
    }

    @Test
    fun tileCopyKeepsIdButChangesValue() {
        val moved = Tile(2, 7L)
        // A "moved" tile keeps its id; copy gives value semantics.
        assertEquals(Tile(2, 7L), moved.copy())
        val promoted = moved.copy(value = 4)
        assertEquals(7L, promoted.id)
        assertEquals(4, promoted.value)
    }

    @Test
    fun tileIdSourceIsMonotonicAndDeterministic() {
        val src = TileIdSource()
        assertEquals(1L, src.next())
        assertEquals(2L, src.next())
        assertEquals(3L, src.peek())
        assertEquals(3L, src.next())
        // Deterministic from a given start -> reproducible replays.
        val a = TileIdSource(100L)
        val b = TileIdSource(100L)
        assertEquals(a.next(), b.next())
    }

    // ---- Construction ------------------------------------------------------

    @Test
    fun emptyBoardHasDefaultSizeAndNoTiles() {
        val board = Board.empty()
        assertEquals(Board.DEFAULT_SIZE, board.size)
        assertEquals(16, board.cellCount)
        assertTrue(board.isEmpty)
        assertFalse(board.isFull)
        assertTrue(board.tiles().isEmpty())
        assertEquals(16, board.emptyCells().size)
    }

    @Test
    fun emptyBoardSupportsNonDefaultSize() {
        val board = Board.empty(5)
        assertEquals(5, board.size)
        assertEquals(25, board.cellCount)
    }

    @Test
    fun rejectsWrongCellCount() {
        assertFailsWith<IllegalArgumentException> {
            Board.of(4, listOf(Tile(2, 1L))) // far too few cells
        }
    }

    @Test
    fun rejectsNonPositiveSize() {
        assertFailsWith<IllegalArgumentException> { Board.empty(0) }
    }

    @Test
    fun fromValuesRejectsNonSquareArray() {
        assertFailsWith<IllegalArgumentException> {
            Board.fromValues(arrayOf(intArrayOf(2, 0), intArrayOf(0)))
        }
    }

    // ---- Board-from-literal ------------------------------------------------

    @Test
    fun fromValuesPlacesTilesAndEmpties() {
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 0, 0, 4),
                intArrayOf(0, 8, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 16, 0),
            ),
        )
        assertEquals(2, board[0, 0]?.value)
        assertEquals(4, board[0, 3]?.value)
        assertEquals(8, board[1, 1]?.value)
        assertEquals(16, board[3, 2]?.value)
        assertNull(board[0, 1])
        assertNull(board[2, 2])
        assertEquals(4, board.tiles().size)
        assertEquals(12, board.emptyCells().size)
        assertFalse(board.isEmpty)
    }

    @Test
    fun fromValuesAssignsSequentialIdsInRowMajorOrder() {
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 4),
                intArrayOf(8, 16),
            ),
        )
        assertEquals(Tile(2, 1L), board[0, 0])
        assertEquals(Tile(4, 2L), board[0, 1])
        assertEquals(Tile(8, 3L), board[1, 0])
        assertEquals(Tile(16, 4L), board[1, 1])
    }

    @Test
    fun fromValuesAcceptsExplicitIdSource() {
        val board = Board.fromValues(
            arrayOf(intArrayOf(2, 2), intArrayOf(0, 0)),
            idSource = TileIdSource(50L),
        )
        assertEquals(50L, board[0, 0]?.id)
        assertEquals(51L, board[0, 1]?.id)
    }

    // ---- Indexing & queries ------------------------------------------------

    @Test
    fun getOutOfBoundsThrows() {
        val board = Board.empty()
        assertFailsWith<IndexOutOfBoundsException> { board[4, 0] }
        assertFailsWith<IndexOutOfBoundsException> { board[0, -1] }
    }

    @Test
    fun emptyCellsAndTilesArePartition() {
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 0),
                intArrayOf(0, 4),
            ),
        )
        assertEquals(listOf(Position(0, 1), Position(1, 0)), board.emptyCells())
        val positioned = board.tilesWithPositions()
        assertEquals(Position(0, 0) to Tile(2, 1L), positioned[0])
        assertEquals(Position(1, 1) to Tile(4, 2L), positioned[1])
    }

    @Test
    fun fullBoardHasNoEmptyCells() {
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 4),
                intArrayOf(8, 16),
            ),
        )
        assertTrue(board.isFull)
        assertTrue(board.emptyCells().isEmpty())
    }

    // ---- Immutability of copy helpers -------------------------------------

    @Test
    fun withTileReturnsNewBoardLeavingOriginalUnchanged() {
        val original = Board.empty()
        val placed = original.withTile(1, 2, Tile(2, 42L))

        // Original untouched.
        assertTrue(original.isEmpty)
        assertNull(original[1, 2])

        // New board has the tile.
        assertEquals(Tile(2, 42L), placed[1, 2])
        assertEquals(1, placed.tiles().size)
        assertNotEquals(original, placed)
    }

    @Test
    fun withTileOverwritesExistingCell() {
        val board = Board.empty().withTile(0, 0, Tile(2, 1L))
        val overwritten = board.withTile(0, 0, Tile(4, 9L))
        assertEquals(Tile(4, 9L), overwritten[0, 0])
        // Original board still holds the old tile.
        assertEquals(Tile(2, 1L), board[0, 0])
    }

    @Test
    fun withoutReturnsNewBoardLeavingOriginalUnchanged() {
        val board = Board.empty().withTile(2, 3, Tile(8, 5L))
        val cleared = board.without(2, 3)

        assertNull(cleared[2, 3])
        assertTrue(cleared.isEmpty)
        // Original still has the tile.
        assertEquals(Tile(8, 5L), board[2, 3])
    }

    @Test
    fun ofDefensivelyCopiesItsInput() {
        val mutable = MutableList<Tile?>(4) { null }
        val board = Board.of(2, mutable)
        mutable[0] = Tile(99, 99L) // mutate after construction
        assertNull(board[0, 0]) // board must NOT see the change
    }

    // ---- Equality of boards ------------------------------------------------

    @Test
    fun boardsWithSameTilesAndIdsAreEqual() {
        val a = Board.fromValues(arrayOf(intArrayOf(2, 0), intArrayOf(0, 4)))
        val b = Board.fromValues(arrayOf(intArrayOf(2, 0), intArrayOf(0, 4)))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun boardsDifferingByTileIdAreNotEqual() {
        val a = Board.empty().withTile(0, 0, Tile(2, 1L))
        val b = Board.empty().withTile(0, 0, Tile(2, 2L))
        assertNotEquals(a, b)
    }

    @Test
    fun boardsOfDifferentSizeAreNotEqual() {
        assertNotEquals(Board.empty(4), Board.empty(5))
    }

    // ---- Serialization round-trip -----------------------------------------

    @Test
    fun tileRoundTripsThroughJson() {
        val tile = Tile(2048, 123L)
        val encoded = Json.encodeToString(Tile.serializer(), tile)
        val decoded = Json.decodeFromString(Tile.serializer(), encoded)
        assertEquals(tile, decoded)
    }

    @Test
    fun emptyBoardRoundTripsThroughJson() {
        val board = Board.empty()
        val encoded = Json.encodeToString(Board.serializer(), board)
        val decoded = Json.decodeFromString(Board.serializer(), encoded)
        assertEquals(board, decoded)
    }

    @Test
    fun populatedBoardRoundTripsPreservingTileIds() {
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 0, 4, 0),
                intArrayOf(0, 8, 0, 16),
                intArrayOf(32, 0, 0, 0),
                intArrayOf(0, 0, 64, 128),
            ),
        )
        val encoded = Json.encodeToString(Board.serializer(), board)
        val decoded = Json.decodeFromString(Board.serializer(), encoded)

        // Full structural equality includes ids.
        assertEquals(board, decoded)
        // Spot-check that ids survived, not just values.
        assertEquals(board[0, 0]?.id, decoded[0, 0]?.id)
        assertEquals(board.tiles().map { it.id }, decoded.tiles().map { it.id })
    }

    @Test
    fun nonDefaultSizeBoardRoundTrips() {
        val board = Board.empty(5).withTile(4, 4, Tile(2, 1L))
        val encoded = Json.encodeToString(Board.serializer(), board)
        val decoded = Json.decodeFromString(Board.serializer(), encoded)
        assertEquals(board, decoded)
        assertEquals(5, decoded.size)
    }
}
