package com.fuse.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.fuse.engine.Board
import com.fuse.engine.Tile
import com.fuse.ui.theme.Dimens
import com.fuse.ui.theme.FuseTheme
import com.fuse.ui.theme.TileRamp

/**
 * UIB-1 — the Board renderer.
 *
 * A reusable, size-agnostic Compose Multiplatform composable that renders ANY
 * engine [Board] state as a square grid (4x4 by default, but it iterates
 * `0 until board.size` so 5x5 / 6x6 variants render unchanged).
 *
 * ## State / recomposition
 * [board] is a plain composable parameter — state is hoisted by the caller. Because
 * [Board] is an immutable value type with structural [Board.equals], passing a new
 * `Board` instance recomposes this function and re-lays the grid: the AC's
 * "recomposes on state change". There is no internal mutable state here.
 *
 * ## Geometry (scales from the ratio tokens, nothing hardcoded)
 * The board is laid out from the `pad : cell : gap` PROPORTIONS in [Dimens]
 * (12 : 72 : 11) rather than fixed dp. A [BoxWithConstraints] with a 1:1
 * [aspectRatio] gives a square box of the available width; cell + gap + pad sizes
 * are derived from that width via the ratios so the board scales to any device
 * width and stays square. For an `n x n` board the reference side is
 * `pad*2 + n*cell + (n-1)*gap`, and each token's fraction of that side is multiplied
 * by the measured width.
 *
 * ## Colors / numerals (from tokens only — no literal hex)
 * The board background uses [FuseTheme.colors] `boardBg`; empty cells use `card2`.
 * Each occupied cell is filled with [TileRamp.forValue]`(value).bg` and its numeral
 * is drawn in `.fg`, with corner radius from the shape tokens. The numeral is scaled
 * down for longer numbers ([tileFontSizeSp]) so 4-digit tiles stay readable.
 *
 * ## Animation-readiness (UIB-1 has NO animation requirement)
 * Tiles are keyed by [Tile.id] and positioned by absolute `offset` over a single
 * [Box], so FEL-1 (slide/merge animation, Sprint 3) can later animate the offset
 * per id without restructuring this renderer. UIB-1 itself renders tiles directly
 * at their grid positions.
 */
@Composable
fun BoardView(
    board: Board,
    modifier: Modifier = Modifier,
) {
    val colors = FuseTheme.colors
    val n = board.size

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(FuseTheme.shapes.card)
            .background(colors.boardBg),
    ) {
        // Total proportional units along one side: pad on both ends, n cells, (n-1) gaps.
        val totalUnits =
            Dimens.padRatio * 2 + Dimens.cellRatio * n + Dimens.gapRatio * (n - 1)
        val side = maxWidth
        val unit = side / totalUnits

        val pad = unit * Dimens.padRatio
        val cell = unit * Dimens.cellRatio
        val gap = unit * Dimens.gapRatio

        // Top-left of cell (row, col).
        fun cellOffsetX(col: Int) = pad + (cell + gap) * col
        fun cellOffsetY(row: Int) = pad + (cell + gap) * row

        // Empty cell slots (subtle card2 background) for every grid position.
        for (row in 0 until n) {
            for (col in 0 until n) {
                Box(
                    modifier = Modifier
                        .offset(x = cellOffsetX(col), y = cellOffsetY(row))
                        .size(cell)
                        .clip(FuseTheme.shapes.tile)
                        .background(colors.card2),
                )
            }
        }

        // Occupied tiles, keyed by tile id (animation-ready for FEL-1).
        for ((position, tile) in board.tilesWithPositions()) {
            key(tile.id) {
                TileCell(
                    tile = tile,
                    size = cell,
                    modifier = Modifier.offset(
                        x = cellOffsetX(position.col),
                        y = cellOffsetY(position.row),
                    ),
                )
            }
        }
    }
}

@Composable
private fun TileCell(
    tile: Tile,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val tileColors = TileRamp.forValue(tile.value)
    Box(
        modifier = modifier
            .size(size)
            .clip(FuseTheme.shapes.tile)
            .background(tileColors.bg),
        contentAlignment = Alignment.Center,
    ) {
        val text = tile.value.toString()
        Text(
            text = text,
            color = tileColors.fg,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = tileFontSizeSp(text.length).sp,
        )
    }
}

/**
 * Small self-contained demo of [BoardView] over a known [Board], for manual
 * viewing / a future preview route. Owns its own [FuseTheme] (dark) and is
 * Koin-free, so it drops into any preview or test harness. Renders a board with a
 * spread of ramp values (including a 4-digit 2048) to eyeball geometry + numerals.
 *
 * This is NOT wired into `App()` (UIB-1 ships the reusable renderer, not a screen);
 * swipe input is UIB-2 and the MVI store binding is UIB-3.
 */
@Composable
fun BoardPreview(modifier: Modifier = Modifier) {
    FuseTheme(darkTheme = true) {
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 4, 8, 16),
                intArrayOf(32, 64, 128, 256),
                intArrayOf(512, 1024, 2048, 0),
                intArrayOf(0, 0, 2, 0),
            ),
        )
        BoardView(
            board = board,
            modifier = modifier
                .background(FuseTheme.colors.bg)
                .padding(16.dp),
        )
    }
}

/**
 * Numeral size (sp) chosen by digit count so longer numbers stay inside the cell.
 * Pure function of the display length; unit-tested in commonTest. Sizes track the
 * type scale's tile-numeral roles (28 down to 17 for 4+ digits).
 */
internal fun tileFontSizeSp(digits: Int): Int = when {
    digits <= 1 -> 32
    digits == 2 -> 28
    digits == 3 -> 22
    digits == 4 -> 18
    else -> 15
}
