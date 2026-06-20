package com.fuse.engine

import kotlinx.serialization.Serializable

/**
 * The four directions a 2048 move can go. Each maps onto the single
 * toward-index-0 line primitive ([resolveLine]) by choosing which rows/columns to
 * extract and whether to reverse them — see [Board.move].
 *
 *  - [LEFT]  : each row, natural order (index 0 = left edge).
 *  - [RIGHT] : each row reversed (right edge maps to index 0).
 *  - [UP]    : each column, top -> bottom (index 0 = top edge).
 *  - [DOWN]  : each column reversed (bottom edge maps to index 0).
 */
enum class Direction { UP, DOWN, LEFT, RIGHT }

/**
 * A single merge expressed in BOARD coordinates (as opposed to [MergeEvent], which
 * is in line-indices). [Board.move] translates each per-line [MergeEvent] into one
 * of these, un-reversing RIGHT/DOWN line-indices and mapping the line-index back to
 * the originating row/column.
 *
 * This is the board-level event animation/scoring consumes:
 *  - **ENG-7 (scoring)** sums [resultingValue] across a move's events.
 *  - **Animation** slides the two source tiles ([sourceIdA] @ [sourcePosA],
 *    [sourceIdB] @ [sourcePosB]) into [resultPos] and swaps in the fresh
 *    merged tile ([resultId]).
 *
 * @property resultingValue value of the merged tile (twice each source value).
 * @property resultId the NEW id minted for the merged tile.
 * @property sourceIdA id of the wall-side source tile (nearer the move direction's edge).
 * @property sourceIdB id of the other source tile.
 * @property sourcePosA pre-move board position of [sourceIdA].
 * @property sourcePosB pre-move board position of [sourceIdB].
 * @property resultPos final board position of the merged tile after the move.
 */
@Serializable
data class BoardMergeEvent(
    val resultingValue: Int,
    val resultId: Long,
    val sourceIdA: Long,
    val sourceIdB: Long,
    val sourcePosA: Position,
    val sourcePosB: Position,
    val resultPos: Position,
)

/**
 * The pure geometric result of a move: the new [board], whether anything
 * [changed], and the board-level [merges].
 *
 * This is intentionally the GEOMETRIC transition only — it does NOT spawn a new
 * tile (ENG-6) and does NOT score (ENG-7). When [changed] is `false` the move was
 * a no-op and [board] is structurally equal to the input board (downstream treats
 * an unchanged move as invalid: no spawn, no score, no turn counted).
 *
 * @property board the board after sliding + merging in the requested direction.
 * @property changed `true` iff any row/column changed (slide or merge).
 * @property merges every merge that happened, in board coordinates. Within a single
 *   line they remain wall-side-first; across lines they follow extraction order.
 */
@Serializable
data class MoveResult(
    val board: Board,
    val changed: Boolean,
    val merges: List<BoardMergeEvent>,
)

/**
 * Applies a move in [direction] to this board: extracts each row/column, resolves
 * it toward index 0 via [resolveLine] (reusing ENG-3 compaction + ENG-4 merges),
 * writes the resolved cells back to the correct board positions, and translates
 * every [MergeEvent] into a board-coordinate [BoardMergeEvent].
 *
 * ## Direction mapping
 * [resolveLine] always works toward index 0, so each direction is just a choice of
 * line orientation:
 *  - **LEFT**  : line = `board[r, 0..size-1]`; line-index `i` -> `(r, i)`.
 *  - **RIGHT** : line = row reversed; line-index `i` -> `(r, size-1-i)`.
 *  - **UP**    : line = `board[0..size-1, c]`; line-index `i` -> `(i, c)`.
 *  - **DOWN**  : line = column reversed; line-index `i` -> `(size-1-i, c)`.
 *
 * For RIGHT/DOWN the line is reversed before resolving, so a line-index `i`
 * (whether in the resolved line or in a [MergeEvent]) maps back to board offset
 * `size - 1 - i` along the moving axis. LEFT/UP map directly (`i`).
 *
 * ## Ids
 * The SAME [idSource] is threaded through every line of the move, so all merged
 * tiles minted during one move get distinct ids. Slid (non-merged) tiles keep
 * their original ids (animation continuity); merged tiles carry the fresh ids from
 * [resolveLine].
 *
 * ## changed / no-op
 * [MoveResult.changed] is the OR of every line's `changed` flag. When it is
 * `false`, the returned board is structurally equal to `this`.
 *
 * @param direction the direction to slide/merge toward.
 * @param idSource the monotonic id minter, shared across all lines of this move.
 * @return a [MoveResult] (geometric only; no spawn, no score).
 */
fun Board.move(direction: Direction, idSource: TileIdSource): MoveResult {
    val n = size
    // `vertical` => we iterate columns; `reversed` => the edge is the high index.
    val vertical = direction == Direction.UP || direction == Direction.DOWN
    val reversed = direction == Direction.RIGHT || direction == Direction.DOWN

    var result = this
    var changedAny = false
    val boardMerges = ArrayList<BoardMergeEvent>()

    // For each line (row for L/R, column for U/D): map line-index -> board Position.
    fun posFor(lineIndex: Int, lineNo: Int): Position {
        val axisOffset = if (reversed) n - 1 - lineIndex else lineIndex
        return if (vertical) Position(axisOffset, lineNo) else Position(lineNo, axisOffset)
    }

    for (lineNo in 0 until n) {
        // Extract the line in toward-index-0 order for this direction.
        val line = ArrayList<Tile?>(n)
        for (lineIndex in 0 until n) {
            val p = posFor(lineIndex, lineNo)
            line.add(this[p.row, p.col])
        }

        val resolution = resolveLine(line, idSource)
        if (resolution.changed) changedAny = true

        // Write the resolved cells back to their board positions.
        for (lineIndex in 0 until n) {
            val p = posFor(lineIndex, lineNo)
            val tile = resolution.line[lineIndex]
            result = if (tile == null) result.without(p.row, p.col) else result.withTile(p.row, p.col, tile)
        }

        // Translate each line-index merge event into board Positions.
        for (m in resolution.merges) {
            val posA = posFor(m.sourceIndexA, lineNo)
            val posB = posFor(m.sourceIndexB, lineNo)
            // The merged tile lands at the resolved-line index where resultId sits.
            val resultLineIndex = resolution.line.indexOfFirst { it?.id == m.resultId }
            val resultPos = posFor(resultLineIndex, lineNo)
            boardMerges.add(
                BoardMergeEvent(
                    resultingValue = m.resultingValue,
                    resultId = m.resultId,
                    sourceIdA = m.sourceIdA,
                    sourceIdB = m.sourceIdB,
                    sourcePosA = posA,
                    sourcePosB = posB,
                    resultPos = resultPos,
                ),
            )
        }
    }

    // No-op moves must return a board structurally equal to the input.
    val finalBoard = if (changedAny) result else this
    return MoveResult(finalBoard, changedAny, boardMerges)
}
