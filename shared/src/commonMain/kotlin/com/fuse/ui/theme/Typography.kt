package com.fuse.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography tokens from `docs/design-tokens.md` (§Typography).
 *
 * The prototype uses the system font stack; we leave [TextStyle.fontFamily] null
 * so Compose resolves the platform default (SF on iOS, Roboto on Android),
 * matching `-apple-system, BlinkMacSystemFont, sans-serif`.
 *
 * Weights map to the doc's roles:
 *  - 700 Bold     -> titles, tile numerals, scores
 *  - 600 SemiBold -> buttons, labels
 *  - 500/400      -> body, secondary
 *
 * Sizes are the observed px scale (treated as sp). Names describe role/size so
 * later screens pick by intent. The full discrete scale is exposed via [sizes]
 * so the swatch screen can render every step.
 */
@Immutable
object FuseType {
    // Display (84, 52, 34)
    val displayXL = style(84, FontWeight.Bold)
    val displayL = style(52, FontWeight.Bold)
    val displayM = style(34, FontWeight.Bold)

    // Titles (28, 26, 24, 22)
    val titleXL = style(28, FontWeight.Bold)
    val titleL = style(26, FontWeight.Bold)
    val titleM = style(24, FontWeight.Bold)
    val titleS = style(22, FontWeight.SemiBold)

    // Headings (20, 19, 18, 17, 16)
    val headingXL = style(20, FontWeight.SemiBold)
    val headingL = style(19, FontWeight.SemiBold)
    val headingM = style(18, FontWeight.SemiBold)
    val headingS = style(17, FontWeight.SemiBold)
    val headingXS = style(16, FontWeight.SemiBold)

    // Body / caption (15, 14, 13, 12, 11, 10)
    val bodyL = style(15, FontWeight.Normal)
    val bodyM = style(14, FontWeight.Normal)
    val bodyS = style(13, FontWeight.Normal)
    val caption = style(12, FontWeight.Medium)
    val captionS = style(11, FontWeight.Medium)
    val captionXS = style(10, FontWeight.Medium)

    /** Every documented size step (px == sp), descending, for the type preview. */
    val sizes: List<Int> = listOf(84, 52, 34, 28, 26, 24, 22, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10)

    private fun style(sizeSp: Int, weight: FontWeight): TextStyle =
        TextStyle(fontSize = sizeSp.sp, fontWeight = weight)
}
