package com.fuse.engine

/**
 * The result of compacting a single line.
 *
 * @property line the compacted line: all non-null tiles slid toward index 0 with
 *   their relative order preserved, nulls filling the tail. Always the same
 *   length as the input line.
 * @property changed `true` iff compaction moved at least one tile (i.e. the input
 *   had a gap before some tile). `false` when the input was already compact —
 *   ENG-5 uses this to decide whether a board move actually changed anything.
 */
data class CompactionResult(
    val line: List<Tile?>,
    val changed: Boolean,
)

/**
 * Slides all tiles in a single [line] toward index 0 (the "edge"), removing gaps
 * while preserving the relative order of the tiles. **No merging happens here** —
 * this is pure gravity. ENG-4 layers merge resolution on top of this primitive.
 *
 * ## Direction convention (read this, ENG-4 / ENG-5)
 * This function is intentionally direction-agnostic: it always compacts toward
 * **index 0**. The caller is responsible for direction:
 *  - LEFT / UP  : extract the row/column in natural order, compact, write back.
 *  - RIGHT / DOWN: extract the row/column **reversed**, compact, then reverse the
 *    result before writing back.
 * Keeping a single toward-index-0 primitive means the merge logic (ENG-4) and the
 * board-move mapping (ENG-5) never branch on direction — they reverse the line.
 *
 * ## Id preservation
 * A tile that slides keeps its **id** (and value): this is the "same" logical
 * tile that merely moved, which is what gives the UI animation continuity. No new
 * ids are minted here. Minting fresh ids for merged tiles is ENG-4's job.
 *
 * ## Idempotency
 * Compacting an already-compact line returns an equal line with `changed = false`.
 * Therefore `compactLine(compactLine(x).line)` always reports `changed = false`.
 *
 * @param line a line of length `size`; each cell is a [Tile] or `null` (empty).
 * @return a [CompactionResult] with the compacted line and a [CompactionResult.changed] flag.
 */
fun compactLine(line: List<Tile?>): CompactionResult {
    val tiles = line.filterNotNull()
    val compacted = ArrayList<Tile?>(line.size)
    compacted.addAll(tiles)
    repeat(line.size - tiles.size) { compacted.add(null) }
    return CompactionResult(compacted, changed = compacted != line)
}
