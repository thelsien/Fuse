package com.fuse.ui.theme

import com.fuse.cosmetics.Cosmetics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * COS-2 — pure tests for the style-resolution layer ([CosmeticStyles]): styleId → tile ramp /
 * board colors. These prove the MECHANISM independent of Compose:
 *  - DEFAULT_STYLE tile ramp IS the current [TileRamp] (identity — the AC "default always available
 *    renders the current look").
 *  - the non-default `goldRush` skin differs visibly from default for representative values.
 *  - the `ocean` board theme overrides board-relevant fields vs default.
 *  - unknown / unfinished (`midnight`) styleIds fall back to default (documented).
 */
class CosmeticStylesTest {

    private val sampleValues = listOf(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096)

    @Test
    fun defaultStyle_tileRamp_isTheCurrentTileRamp_identity() {
        val resolved = CosmeticStyles.tileRamp(Cosmetics.DEFAULT_STYLE)
        // The default style must be the exact current ramp object (identity, zero visual change).
        assertSame(TileRamp, resolved)
        for (v in sampleValues) {
            assertEquals(
                TileRamp.forValue(v),
                resolved.forValue(v),
                "default ramp must equal TileRamp for value $v",
            )
        }
    }

    @Test
    fun goldRushTileSkin_differsFromDefault_forSomeValues() {
        val gold = CosmeticStyles.tileRamp(CosmeticStyles.GOLD_RUSH)
        // At least some value must visibly differ (bg or fg) from the default ramp.
        val anyDifferent = sampleValues.any { v ->
            val d = TileRamp.forValue(v)
            val g = gold.forValue(v)
            d.bg != g.bg || d.fg != g.fg
        }
        assertTrue(anyDifferent, "goldRush must differ from the default ramp for some value")
        // Spot-check a representative value differs in bg.
        assertNotEquals(TileRamp.forValue(8).bg, gold.forValue(8).bg)
    }

    @Test
    fun goldRush_isResolvedFromCatalogStyleId() {
        // The catalog's Gold Rush cosmetic's styleId must resolve to the gold ramp (not default).
        val styleId = Cosmetics.GoldRushTileSkin.styleId
        val resolved = CosmeticStyles.tileRamp(styleId)
        assertNotEquals(TileRamp.forValue(8).bg, resolved.forValue(8).bg)
    }

    @Test
    fun midnightTileSkin_isPlaceholder_fallsBackToDefault() {
        // `midnight` has no art yet (COS-4); it must resolve to the default ramp (documented).
        val resolved = CosmeticStyles.tileRamp(CosmeticStyles.MIDNIGHT)
        assertSame(TileRamp, resolved)
    }

    @Test
    fun unknownTileStyleId_fallsBackToDefault() {
        val resolved = CosmeticStyles.tileRamp("does.not.exist")
        assertSame(TileRamp, resolved)
    }

    @Test
    fun defaultStyle_boardColors_isIdentity() {
        val base = FuseColors.Dark
        val resolved = CosmeticStyles.boardColors(Cosmetics.DEFAULT_STYLE, base)
        assertEquals(base, resolved)
    }

    @Test
    fun oceanBoardTheme_overridesBoardRelevantFields() {
        val base = FuseColors.Dark
        val ocean = CosmeticStyles.boardColors(CosmeticStyles.OCEAN, base)
        assertNotEquals(base.boardBg, ocean.boardBg)
        assertNotEquals(base.bg, ocean.bg)
        assertNotEquals(base.card2, ocean.card2)
        // Non-board fields stay coherent (inherited from base): text/accent unchanged.
        assertEquals(base.text, ocean.text)
        assertEquals(base.accent, ocean.accent)
    }

    @Test
    fun ocean_isResolvedFromCatalogStyleId() {
        val styleId = Cosmetics.OceanBoardTheme.styleId
        val ocean = CosmeticStyles.boardColors(styleId, FuseColors.Dark)
        assertNotEquals(FuseColors.Dark.boardBg, ocean.boardBg)
    }

    @Test
    fun unknownBoardStyleId_fallsBackToBase() {
        val base = FuseColors.Dark
        assertEquals(base, CosmeticStyles.boardColors("nope", base))
    }
}
