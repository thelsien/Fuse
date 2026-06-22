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
 * ## Real vs placeholder (COS-4 finishes these)
 *  - **Real, visibly-different** styles (the AC's "at least one"):
 *    - `goldRush` (TILE_SKIN) — a warm gold-shifted ramp, demonstrably different from default.
 *    - `ocean` (BOARD_THEME) — a deep teal/blue board, demonstrably different from default.
 *  - **Placeholder → default** (no art yet, falls back to identity so nothing breaks):
 *    - `midnight` (TILE_SKIN) — currently resolves to the default ramp. COS-4 fills it.
 *  - **Unknown / unfinished** styleId → the default for that type (documented fallback). This is
 *    why equipping a not-yet-arted cosmetic is always safe: it just looks like default.
 *
 * COS-4's job is purely to fill real tables/palettes behind each non-default styleId HERE — no
 * call-site changes (App/BoardView/FuseTheme already read whatever this resolves to).
 */
object CosmeticStyles {

    /**
     * Resolve a TILE_SKIN [styleId] to a [TileRampStyle].
     *  - [Cosmetics.DEFAULT_STYLE] → [TileRamp] (identity; the current look).
     *  - a known non-default style → its variant ramp.
     *  - anything else (unknown / placeholder without art) → [TileRamp] (safe default).
     */
    fun tileRamp(styleId: String): TileRampStyle = when (styleId) {
        Cosmetics.DEFAULT_STYLE -> TileRamp
        GOLD_RUSH -> CosmeticTileSkins.GoldRush
        // `midnight` has no art yet (COS-4); fall back to the default ramp.
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
        else -> base
    }

    // styleId constants (mirror Cosmetics catalog handles; kept here so resolution is exhaustive).
    const val GOLD_RUSH: String = "goldRush"
    const val MIDNIGHT: String = "midnight"
    const val OCEAN: String = "ocean"
}

/**
 * COS-2 — concrete tile-skin ramps. Real-but-rough until COS-4 polishes them.
 */
object CosmeticTileSkins {

    /**
     * `goldRush` — the 2048-milestone reward skin. A warm, gold/amber-shifted ramp that is
     * VISIBLY different from the default blue/violet ramp (this is the AC's demonstrable skin).
     * Rough palette; COS-4 refines. Low tiles read pale gold, mid tiles deepen to amber/orange,
     * and 2048 lands on a bright gold with a gold glow.
     */
    val GoldRush: TileRampStyle = TableTileRampStyle(
        ramp = mapOf(
            2 to TileColors(bg = Color(0xFFFFF3CC), fg = Color(0xFF5C3B00)),
            4 to TileColors(bg = Color(0xFFFFE49B), fg = Color(0xFF5C3B00)),
            8 to TileColors(bg = Color(0xFFFFD24D), fg = Color(0xFF5C3B00)),
            16 to TileColors(bg = Color(0xFFFBBF24), fg = Color(0xFF4A2E00)),
            32 to TileColors(bg = Color(0xFFF59E0B), fg = Color(0xFFFFFFFF)),
            64 to TileColors(bg = Color(0xFFEA8C0B), fg = Color(0xFFFFFFFF)),
            128 to TileColors(bg = Color(0xFFD97706), fg = Color(0xFFFFFFFF)),
            256 to TileColors(bg = Color(0xFFC2620A), fg = Color(0xFFFFFFFF)),
            512 to TileColors(bg = Color(0xFFB45309), fg = Color(0xFFFFFFFF)),
            1024 to TileColors(bg = Color(0xFF92400E), fg = Color(0xFFFFE49B)),
            2048 to TileColors(bg = Color(0xFFFFC400), fg = Color(0xFF5C3B00), glow = Color(0xFFFFD84D)),
        ),
        fallback = TileColors(bg = Color(0xFF5C3B00), fg = Color(0xFFFFD84D)),
    )
}

/**
 * COS-2 — concrete board-theme palette overrides. Each takes the active [FuseColors] base and
 * overrides only the board-relevant fields ([FuseColors.boardBg], [FuseColors.card2],
 * [FuseColors.bg]) so the rest of the UI (text/accent) stays coherent.
 */
object CosmeticBoardThemes {

    /**
     * `ocean` — a deep teal/blue board, VISIBLY different from the default navy board (the AC's
     * demonstrable board theme). Rough; COS-4 refines. Overrides the page bg, board bg and empty
     * cell color to ocean tones.
     */
    fun ocean(base: FuseColors): FuseColors = base.copy(
        bg = Color(0xFF041C2C),
        boardBg = Color(0xFF073B4C),
        card2 = Color(0xFF0E5468),
    )
}
