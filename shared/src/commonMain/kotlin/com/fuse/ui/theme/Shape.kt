package com.fuse.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/**
 * Corner-radius tokens from `docs/design-tokens.md` (§Shape).
 *
 * Named by role rather than raw number so call sites read intent. The discrete
 * radii (16/12/11/10/9/18/14) are preserved verbatim; [pill] is the `999`/`50%`
 * rounded-pill case and [bottomSheet] is the `30 30 0 0` top-rounded sheet.
 */
@Immutable
object Shapes {
    // Discrete radii from the doc.
    val card = RoundedCornerShape(16.dp)        // most common card
    val tile = RoundedCornerShape(12.dp)        // tiles, inner cards
    val tileInner = RoundedCornerShape(11.dp)   // inner cards
    val chip = RoundedCornerShape(10.dp)        // chips
    val control = RoundedCornerShape(9.dp)      // small controls
    val largeCard = RoundedCornerShape(18.dp)   // large cards
    val sheet = RoundedCornerShape(14.dp)       // sheets

    /** Pills / circular badges (`999` / `50%`). */
    val pill = RoundedCornerShape(percent = 50)

    /** Bottom sheets: `30 30 0 0` (top corners rounded only). */
    val bottomSheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
}
