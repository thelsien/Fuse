package com.fuse.engine

import kotlinx.serialization.Serializable

/**
 * An immutable, square game grid of [size] x [size] cells. Each cell either
 * holds a [Tile] or is empty (`null`).
 *
 * The grid is stored row-major in a single fixed-length list of `size * size`
 * cells (index = `row * size + col`). This is cheap to copy (the engine produces
 * a new [Board] on every move) and trivial to index by `(row, col)`.
 *
 * [size] defaults to [DEFAULT_SIZE] (4 -> classic 2048) but is a constructor
 * parameter so 5x5 / 6x6 variants are possible post-MVP without touching the
 * model. Nothing here hardcodes 4.
 *
 * The class is immutable: the backing list is never mutated and never leaked as
 * a mutable type. All mutation-style helpers ([withTile], [without]) are pure
 * and return a brand-new board, leaving the receiver untouched.
 *
 * Construct via [Board.empty], [Board.of] (cell list) or [Board.fromValues]
 * (test-friendly 2D int literal, 0 = empty). The primary constructor is
 * `internal` so callers go through the validated factories.
 */
@Serializable
class Board internal constructor(
    val size: Int,
    private val cells: List<Tile?>,
) {
    init {
        require(size > 0) { "Board size must be positive, was $size" }
        require(cells.size == size * size) {
            "Expected ${size * size} cells for a ${size}x$size board, got ${cells.size}"
        }
    }

    /** Total number of cells (`size * size`). */
    val cellCount: Int get() = cells.size

    /** True when no cell holds a tile. */
    val isEmpty: Boolean get() = cells.all { it == null }

    /** True when every cell holds a tile (no empty cells remain). */
    val isFull: Boolean get() = cells.all { it != null }

    /**
     * The tile at ([row], [col]), or `null` if that cell is empty.
     * @throws IndexOutOfBoundsException if the coordinate is off the board.
     */
    operator fun get(row: Int, col: Int): Tile? {
        checkBounds(row, col)
        return cells[index(row, col)]
    }

    /** All tiles currently on the board, in row-major order (empties skipped). */
    fun tiles(): List<Tile> = cells.filterNotNull()

    /** All tiles paired with their [Position], in row-major order. */
    fun tilesWithPositions(): List<Pair<Position, Tile>> =
        cells.mapIndexedNotNull { i, tile ->
            tile?.let { positionOf(i) to it }
        }

    /** Coordinates of every empty cell, in row-major order. */
    fun emptyCells(): List<Position> =
        cells.mapIndexedNotNull { i, tile ->
            if (tile == null) positionOf(i) else null
        }

    /**
     * Returns a new board identical to this one but with [tile] placed at
     * ([row], [col]), overwriting whatever was there. Pure — `this` is unchanged.
     */
    fun withTile(row: Int, col: Int, tile: Tile): Board {
        checkBounds(row, col)
        val updated = cells.toMutableList()
        updated[index(row, col)] = tile
        return Board(size, updated)
    }

    /**
     * Returns a new board identical to this one but with ([row], [col]) emptied.
     * Pure — `this` is unchanged. Clearing an already-empty cell is a no-op copy.
     */
    fun without(row: Int, col: Int): Board {
        checkBounds(row, col)
        val updated = cells.toMutableList()
        updated[index(row, col)] = null
        return Board(size, updated)
    }

    private fun index(row: Int, col: Int): Int = row * size + col

    private fun positionOf(index: Int): Position = Position(index / size, index % size)

    private fun checkBounds(row: Int, col: Int) {
        if (row !in 0 until size || col !in 0 until size) {
            throw IndexOutOfBoundsException(
                "($row, $col) is outside a ${size}x$size board",
            )
        }
    }

    /**
     * Structural equality over size + every cell (tile value AND id). Two boards
     * are equal iff they have the same dimension and identical tiles (same ids)
     * in the same positions. This is what makes the serialization round-trip
     * assertable, and what move/merge tests compare against.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        return size == other.size && cells == other.cells
    }

    override fun hashCode(): Int = 31 * size + cells.hashCode()

    override fun toString(): String = buildString {
        append("Board(${size}x$size)\n")
        for (row in 0 until size) {
            for (col in 0 until size) {
                val t = cells[index(row, col)]
                append((t?.value ?: 0).toString().padStart(5))
            }
            append('\n')
        }
    }

    companion object {
        /** Classic 2048 dimension. Kept as a constant, never hardcoded inline. */
        const val DEFAULT_SIZE: Int = 4

        /** An empty [size] x [size] board (defaults to [DEFAULT_SIZE]). */
        fun empty(size: Int = DEFAULT_SIZE): Board =
            Board(size, List(size * size) { null })

        /**
         * Builds a board from a row-major [cells] list of length `size * size`.
         * The list is defensively copied, so later mutation of the argument can
         * never reach into the board.
         */
        fun of(size: Int, cells: List<Tile?>): Board = Board(size, cells.toList())

        /**
         * Test-friendly factory: builds a board from a 2D [values] array where
         * `0` means empty and any non-zero entry becomes a [Tile] with that value
         * and an id from [idSource] (default: sequential ids starting at 1, in
         * row-major order). The array must be square.
         *
         * Use the overload that also takes explicit ids when a test needs to pin
         * specific ids (e.g. to assert id continuity across a move).
         */
        fun fromValues(
            values: Array<IntArray>,
            idSource: TileIdSource = TileIdSource(),
        ): Board {
            val size = values.size
            require(values.all { it.size == size }) {
                "fromValues expects a square array; got rows of differing length"
            }
            val cells = ArrayList<Tile?>(size * size)
            for (row in values) {
                for (value in row) {
                    cells.add(if (value == 0) null else Tile(value, idSource.next()))
                }
            }
            return Board(size, cells)
        }
    }
}

/**
 * A board coordinate. [row] and [col] are both 0-based. Lightweight and
 * serializable so move results (ENG-3+) can describe where tiles ended up.
 */
@Serializable
data class Position(val row: Int, val col: Int)
