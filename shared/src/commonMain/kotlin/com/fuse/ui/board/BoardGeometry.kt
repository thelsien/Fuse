package com.fuse.ui.board

import com.fuse.ui.theme.Dimens

/**
 * FEL-1 — pure board geometry, extracted from [BoardView] so the position->offset
 * math is unit-testable in commonTest (no Compose / no device).
 *
 * The board is laid out from the `pad : cell : gap` PROPORTIONS in [Dimens]
 * (12 : 72 : 11) rather than fixed sizes. For an `n x n` board the reference side
 * in proportional units is `pad*2 + n*cell + (n-1)*gap`; each token's fraction of
 * that reference, multiplied by the measured pixel/dp [side], gives its real size.
 * A cell at `(row, col)` sits at `pad + (cell + gap) * col` along x and the same
 * along y.
 *
 * All values are returned in the SAME unit as the [side] passed in (the renderer
 * passes a dp value; tests pass plain floats). Nothing here depends on Compose,
 * so the slide animation's start/end offsets can be asserted directly.
 *
 * @property cell the side length of a single tile cell.
 * @property gap the gap between adjacent cells.
 * @property pad the outer padding on each edge of the board.
 */
class BoardGeometry private constructor(
    val cell: Float,
    val gap: Float,
    val pad: Float,
) {
    /** Top-left x of the cell in column [col]. */
    fun offsetX(col: Int): Float = pad + (cell + gap) * col

    /** Top-left y of the cell in row [row]. */
    fun offsetY(row: Int): Float = pad + (cell + gap) * row

    companion object {
        /**
         * Builds the geometry for an [n] x [n] board occupying a square of the
         * given [side] length (same unit out as in).
         */
        fun forBoard(n: Int, side: Float): BoardGeometry {
            require(n > 0) { "Board size must be positive, was $n" }
            val totalUnits =
                Dimens.padRatio * 2 + Dimens.cellRatio * n + Dimens.gapRatio * (n - 1)
            val unit = side / totalUnits
            return BoardGeometry(
                cell = unit * Dimens.cellRatio,
                gap = unit * Dimens.gapRatio,
                pad = unit * Dimens.padRatio,
            )
        }
    }
}
