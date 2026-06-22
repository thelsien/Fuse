package com.fuse.ui.theme

import androidx.compose.ui.graphics.Color
import com.fuse.cosmetics.Cosmetics

/**
 * COS-2 — the STYLE RESOLUTION layer: maps a cosmetic [com.fuse.cosmetics.Cosmetic.styleId]
 * (a stable string handle minted in COS-1) to a CONCRETE renderable style — a [TileRampStyle]
 * for a TILE_SKIN, or a [FuseColors] override for a BOARD_THEME.
 *
 * This is the seam COS-2 wires into `FuseTheme`: `App()` reads the equipped ids, calls
 * [CosmeticStyles.tileRamp] / [CosmeticStyles.boardColors] here, and feeds the results through
 * `LocalTileRamp` / `LocalFuseColors`. It is PURE (no Compose), so the resolution itself is
 * unit-testable in commonTest.
 *
 * ## Ship-ready styles (COS-4 — all real now)
 *  - `goldRush` (TILE_SKIN, gated on REACHED_2048) — a polished warm cream→amber→orange→deep-gold
 *    ramp, top tier carries a gold glow. Visibly different from default + from `midnight`.
 *  - `midnight` (TILE_SKIN, free) — a real dark indigo→violet→magenta "midnight" ramp (NO longer a
 *    default fallback), distinct from default and goldRush, legible numerals at every tier.
 *  - `ocean` (BOARD_THEME, free) — a cohesive deep teal/ocean board (page bg, board bg, empty cell).
 *  - `sunset` (BOARD_THEME, free) — a warm dusk plum/maroon board, the rounding-out 4th starter.
 *  - **Unknown** styleId → the default for that type (documented safe fallback).
 *
 * No call-site changes (App/BoardView/FuseTheme/CollectionScreen already read whatever this
 * resolves to) — filling these tables finishes the previews + the live equipped look.
 */
object CosmeticStyles {

    /**
     * Resolve a TILE_SKIN [styleId] to a [TileRampStyle].
     *  - [Cosmetics.DEFAULT_STYLE] → [TileRamp] (identity; the current look).
     *  - a known non-default style → its variant ramp.
     *  - anything else (unknown) → [TileRamp] (safe default).
     */
    fun tileRamp(styleId: String): TileRampStyle = when (styleId) {
        Cosmetics.DEFAULT_STYLE -> TileRamp
        GOLD_RUSH -> CosmeticTileSkins.GoldRush
        MIDNIGHT -> CosmeticTileSkins.Midnight
        else -> TileRamp
    }

    /**
     * Resolve a BOARD_THEME [styleId] to a [FuseColors] override, given the [base] palette to
     * start from (so the board theme inherits text/accent etc. and only re-skins board-relevant
     * fields). [Cosmetics.DEFAULT_STYLE] and any unknown style return [base] unchanged (identity).
     */
    fun boardColors(styleId: String, base: FuseColors): FuseColors = when (styleId) {
        Cosmetics.DEFAULT_STYLE -> base
        OCEAN -> CosmeticBoardThemes.ocean(base)
        SUNSET -> CosmeticBoardThemes.sunset(base)
        else -> base
    }

    // styleId constants (mirror Cosmetics catalog handles; kept here so resolution is exhaustive).
    const val GOLD_RUSH: String = "goldRush"
    const val MIDNIGHT: String = "midnight"
    const val OCEAN: String = "ocean"
    const val SUNSET: String = "sunset"
}

/**
 * COS-4 — concrete, ship-ready tile-skin ramps. Each is a [TableTileRampStyle] over the SAME tier
 * breakpoints as the default [TileRamp] (2..2048 + a `>2048` fallback), so a skin only re-colors —
 * it never changes which values get their own swatch. Numerals stay legible (light fg on dark bg,
 * dark fg on light bg) at every tier.
 */
object CosmeticTileSkins {

    /**
     * `goldRush` — the 2048-milestone reward skin (gated on REACHED_2048). A coherent WARM
     * progression that reads as molten gold climbing in value:
     *   cream (2) → pale/amber gold (4–8) → amber (16–32) → orange (64–128) → burnt orange (256) →
     *   bronze (512) → deep bronze (1024) → a bright radiant gold 2048 with a gold glow.
     * Visibly different from the default blue/violet ramp AND from `midnight`. Low tiers use a
     * dark-brown numeral on light gold; mid/high tiers use white for contrast; the 1024 deep bronze
     * uses pale gold text; 2048 returns to dark-brown on radiant gold (the brand "win" moment).
     */
    val GoldRush: TileRampStyle = TableTileRampStyle(
        ramp = mapOf(
            2 to TileColors(bg = Color(0xFFFFF6D8), fg = Color(0xFF5C3B00)),
            4 to TileColors(bg = Color(0xFFFFE9A6), fg = Color(0xFF5C3B00)),
            8 to TileColors(bg = Color(0xFFFFD75E), fg = Color(0xFF5C3B00)),
            16 to TileColors(bg = Color(0xFFFBBF24), fg = Color(0xFF4A2E00)),
            32 to TileColors(bg = Color(0xFFF59E0B), fg = Color(0xFF2E1B00)),
            64 to TileColors(bg = Color(0xFFEA7C0B), fg = Color(0xFFFFFFFF)),
            128 to TileColors(bg = Color(0xFFD9620A), fg = Color(0xFFFFFFFF)),
            256 to TileColors(bg = Color(0xFFC04A0E), fg = Color(0xFFFFFFFF)),
            512 to TileColors(bg = Color(0xFFA8500C), fg = Color(0xFFFFE9A6)),
            1024 to TileColors(bg = Color(0xFF7E3F0A), fg = Color(0xFFFFE9A6)),
            2048 to TileColors(
                bg = Color(0xFFFFC22E),
                fg = Color(0xFF5C3B00),
                glow = Color(0xFFFFD84D),
            ),
        ),
        // > 2048: a deep gold plate with bright gold numerals (mirrors the default's >2048 row).
        fallback = TileColors(bg = Color(0xFF5C3B00), fg = Color(0xFFFFD84D)),
    )

    /**
     * `midnight` — a free dark/indigo/violet skin (COS-4 makes it REAL; it no longer falls back to
     * the default ramp). A cool nocturnal progression climbing from deep slate-indigo into violet
     * and a magenta crown:
     *   deep indigo (2–8) → indigo/blue-violet (16–64) → violet (128–256) → orchid/magenta (512–1024)
     *   → a luminous magenta 2048 with a magenta glow.
     * Distinct from both the default (blue→teal→violet→pink) and goldRush (warm gold) — it stays in
     * the cool indigo/violet band, darker and moodier. Fills are all dark, so numerals are a light
     * lavender on the two deepest tiers and near-white above — legible at every tier; the magenta
     * 2048 flips to a dark-plum numeral on its bright fill.
     */
    val Midnight: TileRampStyle = TableTileRampStyle(
        ramp = mapOf(
            2 to TileColors(bg = Color(0xFF2A2C5C), fg = Color(0xFFD7DBFF)),
            4 to TileColors(bg = Color(0xFF353878), fg = Color(0xFFE3E6FF)),
            8 to TileColors(bg = Color(0xFF3F3FA0), fg = Color(0xFFFFFFFF)),
            16 to TileColors(bg = Color(0xFF4B45C4), fg = Color(0xFFFFFFFF)),
            32 to TileColors(bg = Color(0xFF5A4FE0), fg = Color(0xFFFFFFFF)),
            64 to TileColors(bg = Color(0xFF6D54F0), fg = Color(0xFFFFFFFF)),
            128 to TileColors(bg = Color(0xFF8257F2), fg = Color(0xFFFFFFFF)),
            256 to TileColors(bg = Color(0xFF9A5BF0), fg = Color(0xFFFFFFFF)),
            512 to TileColors(bg = Color(0xFFB45BEC), fg = Color(0xFFFFFFFF)),
            1024 to TileColors(bg = Color(0xFFCF5BE0), fg = Color(0xFFFFFFFF)),
            2048 to TileColors(
                bg = Color(0xFFE85AD6),
                fg = Color(0xFF2A0A36),
                glow = Color(0xFFF06CE0),
            ),
        ),
        // > 2048: a very deep indigo plate with luminous violet numerals.
        fallback = TileColors(bg = Color(0xFF1B1B40), fg = Color(0xFFC9A6FF)),
    )
}

/**
 * COS-2 — concrete board-theme palette overrides. Each takes the active [FuseColors] base and
 * overrides only the board-relevant fields ([FuseColors.boardBg], [FuseColors.card2],
 * [FuseColors.bg]) so the rest of the UI (text/accent) stays coherent.
 */
object CosmeticBoardThemes {

    /**
     * `ocean` — a free, cohesive DEEP TEAL board, visibly different from the default navy board.
     * A nautical depth gradient: a near-black abyssal page bg, a deep teal board panel, and a
     * lighter teal empty-cell tint so the grid reads clearly. Inherits text/accent from [base] so
     * numerals stay legible with the default tile ramp and the cosmetic skins alike.
     */
    fun ocean(base: FuseColors): FuseColors = base.copy(
        bg = Color(0xFF041C2C),
        card = Color(0xFF0A3142),
        card2 = Color(0xFF12586E),
        boardBg = Color(0xFF073B4C),
    )

    /**
     * `sunset` — a free, warm DUSK board (the rounding-out 4th starter), the tonal opposite of
     * `ocean`. A deep plum page bg, a maroon/wine board panel, and a warm rosewood empty-cell tint.
     * Inherits text/accent from [base] (the default light text stays legible on these dark warm
     * fills) so it stays coherent with every tile skin.
     */
    fun sunset(base: FuseColors): FuseColors = base.copy(
        bg = Color(0xFF24122B),
        card = Color(0xFF3A1A36),
        card2 = Color(0xFF5C2740),
        boardBg = Color(0xFF45203C),
    )
}
