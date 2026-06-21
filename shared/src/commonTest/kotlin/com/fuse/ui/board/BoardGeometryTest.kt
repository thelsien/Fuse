package com.fuse.ui.board

import com.fuse.ui.theme.Dimens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FEL-1 pure-logic tests for [BoardGeometry] — the position->offset math the slide
 * animation animates between. No Compose / no device; runs on every platform target.
 *
 * These pin the geometry so the animation's start/end offsets are well-defined and
 * the UI test can rely on row/col -> offset being deterministic.
 */
class BoardGeometryTest {

    private val tol = 0.001f

    @Test
    fun firstCellSitsAtThePad() {
        val g = BoardGeometry.forBoard(n = 4, side = 1000f)
        // Cell (0,0) starts exactly at the outer padding on both axes.
        assertEquals(g.pad, g.offsetX(0), tol)
        assertEquals(g.pad, g.offsetY(0), tol)
    }

    @Test
    fun consecutiveCellsAreOneCellPlusGapApart() {
        val g = BoardGeometry.forBoard(n = 4, side = 1000f)
        val step = g.cell + g.gap
        assertEquals(step, g.offsetX(1) - g.offsetX(0), tol)
        assertEquals(step, g.offsetX(3) - g.offsetX(2), tol)
        assertEquals(step, g.offsetY(1) - g.offsetY(0), tol)
    }

    @Test
    fun boardFitsExactlyInsideTheGivenSide() {
        val side = 800f
        val n = 4
        val g = BoardGeometry.forBoard(n, side)
        // Right edge of the last cell + trailing pad == side (board is square & flush).
        val rightEdge = g.offsetX(n - 1) + g.cell + g.pad
        assertEquals(side, rightEdge, tol)
    }

    @Test
    fun sizesTrackTheRatioTokens() {
        val g = BoardGeometry.forBoard(n = 4, side = 1000f)
        // cell:gap:pad keep the 72:11:12 proportions from Dimens.
        assertEquals(Dimens.cellRatio / Dimens.gapRatio, g.cell / g.gap, tol)
        assertEquals(Dimens.cellRatio / Dimens.padRatio, g.cell / g.pad, tol)
    }

    @Test
    fun isSizeAgnostic_threeByThreeStillFlush() {
        val side = 600f
        val n = 3
        val g = BoardGeometry.forBoard(n, side)
        val rightEdge = g.offsetX(n - 1) + g.cell + g.pad
        assertEquals(side, rightEdge, tol)
        assertTrue(g.cell > 0f && g.gap > 0f && g.pad > 0f)
    }
}
