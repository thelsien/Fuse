package com.fuse.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * Spacing / sizing tokens from `docs/design-tokens.md` (§Board geometry).
 *
 * Absolute px from the prototype are treated as dp ratios: the board scales to
 * fit device width while preserving the pad:cell:gap proportions (12 : 72 : 11).
 * Board UI (Sprint 2) should derive actual sizes from available width using
 * [boardSidePx] as the reference, not consume [cell]/[gap] as fixed dp.
 */
@Immutable
object Dimens {
    // Board geometry (reference px == dp).
    val boardPad = 12.dp
    val cell = 72.dp
    val gap = 11.dp

    /** Reference board side: pad*2 + 4*cell + 3*gap = 345. */
    const val boardSidePx = 345

    // Proportions, for scaling the board to any width.
    const val padRatio = 12f
    const val cellRatio = 72f
    const val gapRatio = 11f
}
