package com.fuse.engine

import kotlinx.serialization.Serializable

/**
 * A single merge that happened while resolving one line.
 *
 * Emitted by [resolveLine] so downstream consumers can react:
 *  - **ENG-7 (scoring)** sums [resultingValue] across every event of a move to
 *    compute the points earned.
 *  - **Sprint 3 (animation)** slides the two source tiles ([sourceIdA], [sourceIdB])
 *    from their pre-compaction line positions ([sourceIndexA], [sourceIndexB]) into
 *    the destination and then swaps in the fresh merged tile ([resultId]).
 *
 * ## Coordinate space (read this, ENG-5)
 * All indices here are **line-indices into the ORIGINAL (pre-resolution) input
 * line** — not board [Position]s. [resolveLine] is direction-agnostic and works
 * on a single extracted row/column (toward index 0; see [compactLine]). ENG-5,
 * which extracts the row/column for a given direction, is responsible for
 * translating these line-indices back into board [Position]s when it assembles the
 * full move — and for un-reversing them for RIGHT/DOWN moves (where the caller
 * reversed the line before calling [resolveLine]). This type intentionally stays
 * board-agnostic so the same line logic serves all four directions.
 *
 * @property resultingValue value of the tile produced by the merge (= twice each
 *   source value). The score delta for ENG-7 is the sum of these over a move.
 * @property resultId the NEW id minted (via [TileIdSource.next]) for the merged
 *   tile. Distinct from both source ids.
 * @property sourceIdA id of the source tile nearer index 0 (the wall-side tile).
 * @property sourceIdB id of the source tile farther from index 0.
 * @property sourceIndexA index of [sourceIdA] in the original input line.
 * @property sourceIndexB index of [sourceIdB] in the original input line.
 */
@Serializable
data class MergeEvent(
    val resultingValue: Int,
    val resultId: Long,
    val sourceIdA: Long,
    val sourceIdB: Long,
    val sourceIndexA: Int,
    val sourceIndexB: Int,
)

/**
 * The result of fully resolving a single line: compaction + merges + re-compaction.
 *
 * @property line the resolved line, same length as the input, all tiles slid
 *   toward index 0 with gaps closed and any merges applied.
 * @property changed `true` iff resolving changed the line in any way — either
 *   compaction moved a tile OR at least one merge happened. ENG-5 ORs this across
 *   all rows/columns to decide whether a board move did anything (and therefore
 *   whether to spawn a new tile / count the move).
 * @property merges the merges that occurred, in wall-side-first order (the merge
 *   nearest index 0 comes first). Empty when nothing merged.
 */
@Serializable
data class LineResolution(
    val line: List<Tile?>,
    val changed: Boolean,
    val merges: List<MergeEvent>,
)

/**
 * Resolves a single [line] toward index 0: slides tiles to the edge, merges equal
 * adjacent tiles (each tile participating in at most ONE merge), then closes the
 * gaps the merges left behind. Layers directly on top of ENG-3's [compactLine].
 *
 * ## Direction convention (same as [compactLine])
 * Always resolves toward **index 0**. The caller (ENG-5) handles direction by
 * reversing the line for RIGHT/DOWN moves: extract reversed, resolve, reverse the
 * resulting line back, and un-reverse the merge indices. The line logic never
 * branches on direction.
 *
 * ## Algorithm
 * 1. **Compact** via [compactLine] so tiles are packed toward index 0 with no gaps.
 * 2. **Scan left to right** over the packed tiles. When two ADJACENT tiles have
 *    equal value, merge them into one tile of doubled value with a FRESH id from
 *    [idSource], then skip past both — so each tile is consumed at most once. This
 *    makes the tile nearest the wall merge first:
 *      - `2 2 2`   -> `4 2`  (wall-side pair merges; the lone trailing 2 does not)
 *      - `2 2 2 2` -> `4 4`  (two independent merges, never `8`)
 * 3. The merged tiles plus any unmerged tiles are already packed (merging only ever
 *    shortens the run), so the output is padded with nulls to the original length.
 *
 * ## Ids
 * Non-merged tiles keep their original id (and value) — animation continuity. Each
 * merged tile gets a brand-new id from [idSource], distinct from both sources.
 *
 * ## changed
 * `true` if compaction moved anything OR any merge happened. In particular a line
 * that is already compact but has a mergeable pair (e.g. `2 2 _ _`) reports
 * `changed = true` even though [compactLine] alone would report `false`.
 *
 * @param line a line of length `size`; each cell is a [Tile] or `null` (empty).
 * @param idSource the monotonic id minter; [TileIdSource.next] is called once per
 *   merge, in wall-side-first order, so ids are deterministic given a pinned source.
 * @return a [LineResolution] with the resolved line, the `changed` flag and the
 *   ordered list of [MergeEvent]s.
 */
fun resolveLine(line: List<Tile?>, idSource: TileIdSource): LineResolution {
    val compaction = compactLine(line)
    val packed = compaction.line.filterNotNull()

    val resolved = ArrayList<Tile?>(line.size)
    val merges = ArrayList<MergeEvent>()

    // Map each packed tile to its index in the ORIGINAL input line, so merge events
    // can describe where the sources came from (pre-compaction line positions).
    val originalIndices = ArrayList<Int>(packed.size)
    line.forEachIndexed { index, tile -> if (tile != null) originalIndices.add(index) }

    var i = 0
    while (i < packed.size) {
        val current = packed[i]
        val next = if (i + 1 < packed.size) packed[i + 1] else null
        if (next != null && next.value == current.value) {
            val mergedValue = current.value * 2
            val mergedId = idSource.next()
            resolved.add(Tile(mergedValue, mergedId))
            merges.add(
                MergeEvent(
                    resultingValue = mergedValue,
                    resultId = mergedId,
                    sourceIdA = current.id,
                    sourceIdB = next.id,
                    sourceIndexA = originalIndices[i],
                    sourceIndexB = originalIndices[i + 1],
                ),
            )
            i += 2 // both tiles consumed; each participates in at most one merge
        } else {
            resolved.add(current)
            i += 1
        }
    }
    repeat(line.size - resolved.size) { resolved.add(null) }

    val changed = compaction.changed || merges.isNotEmpty()
    return LineResolution(resolved, changed, merges)
}
