package com.fuse.ui.theme

import com.fuse.cosmetics.Cosmetics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * COS-2/COS-4 — pure tests for the style-resolution layer ([CosmeticStyles]): styleId → tile ramp /
 * board colors. These prove the MECHANISM independent of Compose:
 *  - DEFAULT_STYLE tile ramp IS the current [TileRamp] (identity — the AC "default always available
 *    renders the current look").
 *  - COS-4: every non-default tile skin (`goldRush`, `midnight`) is REAL art — it differs from the
 *    default ramp AND from the other skin, is populated for every default tier with legible fg≠bg.
 *  - COS-4: `midnight` is no longer a default fallback (asserts it now differs from default).
 *  - the `ocean` + `sunset` board themes override board-relevant fields vs default while keeping
 *    text/accent coherent.
 *  - unknown styleIds fall back to default (documented safe fallback).
 */
class CosmeticStylesTest {

    private val sampleValues = listOf(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096)

    /** The exact tier breakpoints of the default ramp — every skin must populate the same set. */
    private val defaultTiers = TileRamp.entries.map { it.first }

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
    fun midnightTileSkin_isRealArt_differsFromDefault() {
        // COS-4: `midnight` is no longer a placeholder — it must NOT be the default ramp object,
        // and must visibly differ from the default ramp at representative values.
        val midnight = CosmeticStyles.tileRamp(CosmeticStyles.MIDNIGHT)
        assertNotSame(TileRamp, midnight)
        val anyDifferent = sampleValues.any { v ->
            val d = TileRamp.forValue(v)
            val m = midnight.forValue(v)
            d.bg != m.bg || d.fg != m.fg
        }
        assertTrue(anyDifferent, "midnight must differ from the default ramp for some value")
        assertNotEquals(TileRamp.forValue(8).bg, midnight.forValue(8).bg)
    }

    @Test
    fun midnight_isResolvedFromCatalogStyleId() {
        val styleId = Cosmetics.MidnightTileSkin.styleId
        val resolved = CosmeticStyles.tileRamp(styleId)
        assertNotEquals(TileRamp.forValue(8).bg, resolved.forValue(8).bg)
    }

    @Test
    fun goldRush_and_midnight_differFromEachOther() {
        // The two non-default skins must be visibly distinct from one another (warm gold vs cool
        // indigo/violet) at every default tier — not just both "different from default".
        val gold = CosmeticStyles.tileRamp(CosmeticStyles.GOLD_RUSH)
        val midnight = CosmeticStyles.tileRamp(CosmeticStyles.MIDNIGHT)
        for (v in defaultTiers) {
            assertNotEquals(
                gold.forValue(v).bg,
                midnight.forValue(v).bg,
                "goldRush and midnight must differ in bg at tier $v",
            )
        }
    }

    @Test
    fun nonDefaultTileSkins_populateEveryDefaultTier_withLegibleContrast() {
        // COS-4 quality bar: each non-default skin must define a row for every default tier
        // (same breakpoints), and every tier must have fg != bg (a legible numeral).
        for (styleId in listOf(CosmeticStyles.GOLD_RUSH, CosmeticStyles.MIDNIGHT)) {
            val ramp = CosmeticStyles.tileRamp(styleId)
            val tiers = ramp.entries.map { it.first }
            assertEquals(
                defaultTiers,
                tiers,
                "$styleId must populate the same tier breakpoints as the default ramp",
            )
            for ((value, colors) in ramp.entries) {
                assertNotEquals(
                    colors.bg,
                    colors.fg,
                    "$styleId tier $value must have a legible numeral (fg != bg)",
                )
            }
            // The >2048 fallback row must also be legible.
            val over = ramp.forValue(4096)
            assertNotEquals(over.bg, over.fg, "$styleId >2048 fallback must have fg != bg")
        }
    }

    @Test
    fun goldRush_topTier_carriesAGlow() {
        // The 2048 "win" moment must keep a glow, like the default brand ramp.
        val gold = CosmeticStyles.tileRamp(CosmeticStyles.GOLD_RUSH)
        assertNotNull(gold.forValue(2048).glow, "goldRush 2048 must carry a glow")
    }

    @Test
    fun midnight_topTier_carriesAGlow() {
        val midnight = CosmeticStyles.tileRamp(CosmeticStyles.MIDNIGHT)
        assertNotNull(midnight.forValue(2048).glow, "midnight 2048 must carry a glow")
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
    fun sunsetBoardTheme_overridesBoardRelevantFields_keepsTextAndAccent() {
        // COS-4 added a free 4th starter (`sunset`): a warm dusk board, distinct from default AND
        // from ocean for the swatch-relevant fields, while keeping text/accent coherent.
        val base = FuseColors.Dark
        val sunset = CosmeticStyles.boardColors(CosmeticStyles.SUNSET, base)
        assertNotEquals(base.boardBg, sunset.boardBg)
        assertNotEquals(base.bg, sunset.bg)
        assertNotEquals(base.card2, sunset.card2)
        assertEquals(base.text, sunset.text)
        assertEquals(base.accent, sunset.accent)

        // sunset must be visibly different from ocean (the two free board themes are distinct).
        val ocean = CosmeticStyles.boardColors(CosmeticStyles.OCEAN, base)
        assertNotEquals(ocean.boardBg, sunset.boardBg)
        assertNotEquals(ocean.bg, sunset.bg)
        assertNotEquals(ocean.card2, sunset.card2)
    }

    @Test
    fun sunset_isResolvedFromCatalogStyleId() {
        val styleId = Cosmetics.SunsetBoardTheme.styleId
        val sunset = CosmeticStyles.boardColors(styleId, FuseColors.Dark)
        assertNotEquals(FuseColors.Dark.boardBg, sunset.boardBg)
    }

    @Test
    fun unknownBoardStyleId_fallsBackToBase() {
        val base = FuseColors.Dark
        assertEquals(base, CosmeticStyles.boardColors("nope", base))
    }
}
