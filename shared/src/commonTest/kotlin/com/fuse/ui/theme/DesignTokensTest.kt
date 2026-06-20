package com.fuse.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pure-logic guard for the FND-4 token layer: every value below is asserted
 * against `docs/design-tokens.md` verbatim. Runs on every CI target — JVM via
 * :shared:testDebugUnitTest and Kotlin/Native via :shared:iosSimulatorArm64Test.
 *
 * No Compose runtime / Koin needed — these are plain value assertions, so they
 * stay green without the UI test harness (FND-5).
 */
class DesignTokensTest {

    @Test
    fun tileRamp_matchesDocumentedColorsExactly() {
        assertEquals(Color(0xFFD7E6FF), TileRamp.forValue(2).bg)
        assertEquals(Color(0xFF1E3A8A), TileRamp.forValue(2).fg)
        assertEquals(Color(0xFFAFCBFF), TileRamp.forValue(4).bg)
        assertEquals(Color(0xFF5B9DFF), TileRamp.forValue(8).bg)
        assertEquals(Color(0xFFFFFFFF), TileRamp.forValue(8).fg)
        assertEquals(Color(0xFF3B82F6), TileRamp.forValue(16).bg)
        assertEquals(Color(0xFF22D3EE), TileRamp.forValue(32).bg)
        assertEquals(Color(0xFF06324A), TileRamp.forValue(32).fg)
        assertEquals(Color(0xFF14B8A6), TileRamp.forValue(64).bg)
        assertEquals(Color(0xFF8B5CF6), TileRamp.forValue(128).bg)
        assertEquals(Color(0xFFA855F7), TileRamp.forValue(256).bg)
        assertEquals(Color(0xFFD946EF), TileRamp.forValue(512).bg)
        assertEquals(Color(0xFFEC4899), TileRamp.forValue(1024).bg)
        assertEquals(Color(0xFF34F5C5), TileRamp.forValue(2048).bg)
        assertEquals(Color(0xFF06324A), TileRamp.forValue(2048).fg)
    }

    @Test
    fun tile2048_carriesMintGlow() {
        val tile = TileRamp.forValue(2048)
        assertEquals(Color(0xFF34F5C5), tile.glow)
    }

    @Test
    fun tilesBelow2048_haveNoGlow() {
        listOf(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024).forEach { v ->
            assertNull(TileRamp.forValue(v).glow, "tile $v should not glow")
        }
    }

    @Test
    fun tilesAbove2048_fallBackToLastEntry() {
        listOf(4096, 8192, 16384, 99999).forEach { v ->
            val tile = TileRamp.forValue(v)
            assertEquals(Color(0xFF06324A), tile.bg, "fallback bg for $v")
            assertEquals(Color(0xFF34F5C5), tile.fg, "fallback fg for $v")
            assertNull(tile.glow)
        }
        assertSame(TileRamp.fallback, TileRamp.forValue(4096))
    }

    @Test
    fun tileRamp_hasAllElevenDiscreteEntries() {
        val values = TileRamp.entries.map { it.first }
        assertEquals(listOf(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048), values)
    }

    @Test
    fun darkPalette_matchesDocumentedValues() {
        val d = FuseColors.Dark
        assertEquals(Color(0xFF0A0E26), d.bg)
        assertEquals(Color(0xFF141A38), d.card)
        assertEquals(Color(0xFF1E2750), d.card2)
        assertEquals(Color(0xFFDCE6FF), d.text)
        assertEquals(Color(0xFF8A97D6), d.sub)
        assertEquals(Color(0xFF6D7DFF), d.accent)
        assertEquals(Color(0xFF1E2750), d.accentSoft)
        assertEquals(Color(0xFF34F5C5), d.good)
        assertEquals(Color(0xFFFACC15), d.gold)
        assertEquals(Color(0xFF141A38), d.boardBg)
        assertTrue(d.isDark)
    }

    @Test
    fun lightPalette_matchesDocumentedValues() {
        val l = FuseColors.Light
        assertEquals(Color(0xFFEEF2FF), l.bg)
        assertEquals(Color(0xFFFFFFFF), l.card)
        assertEquals(Color(0xFFE9EEFF), l.card2)
        assertEquals(Color(0xFF1B2559), l.text)
        assertEquals(Color(0xFF6B7BB5), l.sub)
        assertEquals(Color(0xFF5B6EF5), l.accent)
        assertEquals(Color(0xFFE7ECFF), l.accentSoft)
        assertEquals(Color(0xFF0FB99A), l.good)
        assertEquals(Color(0xFFE8A800), l.gold)
        assertEquals(Color(0xFFDCE7FF), l.boardBg)
        assertTrue(!l.isDark)
    }

    @Test
    fun lightAndDark_areDistinctAndComplete() {
        val d = FuseColors.Dark
        val l = FuseColors.Light
        // Every semantic slot differs between themes.
        assertNotEquals(d.bg, l.bg)
        assertNotEquals(d.card, l.card)
        assertNotEquals(d.text, l.text)
        assertNotEquals(d.accent, l.accent)
        assertNotEquals(d.good, l.good)
        assertNotEquals(d.gold, l.gold)
        assertNotEquals(d.isDark, l.isDark)
    }

    @Test
    fun brand_matchesDocumentedValues() {
        assertEquals(Color(0xFF0A0E26), FuseBrand.navy)
        assertEquals(Color(0xFF34F5C5), FuseBrand.mint)
        assertEquals(listOf(Color(0xFFFFD84D), Color(0xFFE8A800)), FuseBrand.goldGradient)
        assertEquals(
            listOf(Color(0xFF5B6EF5), Color(0xFF8B5CF6), Color(0xFFD946EF)),
            FuseBrand.accentGradient,
        )
        assertEquals(listOf(0.0f, 0.55f, 1.0f), FuseBrand.accentGradientStops)
    }

    @Test
    fun boardGeometry_preservesPadCellGapProportions() {
        // Reference side: pad*2 + 4*cell + 3*gap = 345.
        val side = (Dimens.padRatio * 2) + (4 * Dimens.cellRatio) + (3 * Dimens.gapRatio)
        assertEquals(345f, side)
        assertEquals(345, Dimens.boardSidePx)
        assertEquals(12f, Dimens.padRatio)
        assertEquals(72f, Dimens.cellRatio)
        assertEquals(11f, Dimens.gapRatio)
    }

    @Test
    fun typeScale_coversEveryDocumentedSize() {
        assertEquals(
            listOf(84, 52, 34, 28, 26, 24, 22, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10),
            FuseType.sizes,
        )
        // Spot-check representative role styles resolve to the right size/weight.
        assertEquals(34f, FuseType.displayM.fontSize.value)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, FuseType.displayM.fontWeight)
        assertEquals(14f, FuseType.bodyM.fontSize.value)
    }

    @Test
    fun motion_defaultAndReduced_matchDoc() {
        assertEquals(110, FuseMotion.Default.tileSlideMs)
        assertEquals(150, FuseMotion.Default.genericMs)
        assertTrue(!FuseMotion.Default.reduced)
        // Reduced collapses durations to ~none/1ms.
        assertEquals(1, FuseMotion.Reduced.tileSlideMs)
        assertEquals(1, FuseMotion.Reduced.genericMs)
        assertTrue(FuseMotion.Reduced.reduced)
    }
}
