package com.fuse.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Brand colors — fixed identity tokens that do not change between themes.
 * Values are verbatim from `docs/design-tokens.md` (§Brand).
 */
object FuseBrand {
    val navy = Color(0xFF0A0E26)
    val mint = Color(0xFF34F5C5)

    /** Gold gradient stops: `linear-gradient(135deg, #FFD84D, #E8A800)`. */
    val goldGradient = listOf(Color(0xFFFFD84D), Color(0xFFE8A800))

    /**
     * Accent gradient stops: `linear-gradient(150deg, #5B6EF5 0%, #8B5CF6 55%, #D946EF 100%)`.
     * Stops paired with their documented offsets so a Brush can reproduce the angle/spread.
     */
    val accentGradient = listOf(Color(0xFF5B6EF5), Color(0xFF8B5CF6), Color(0xFFD946EF))
    val accentGradientStops = listOf(0.0f, 0.55f, 1.0f)
}

/**
 * Semantic color palette — the themeable surface/text/accent tokens.
 *
 * This is the overridable unit: cosmetics and colorblind mode (later stories)
 * supply their own [FuseColors] instance through [LocalFuseColors] without
 * touching call sites. Dark is the prototype default ([FuseColors.Dark]);
 * [FuseColors.Light] mirrors the doc's light theme.
 *
 * All values are verbatim from `docs/design-tokens.md` (§Semantic palette).
 * Where the doc uses `rgba(...)`, the alpha is encoded in the ARGB literal.
 */
@Immutable
data class FuseColors(
    val bg: Color,
    val card: Color,
    val card2: Color,
    val navBg: Color,
    val line: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val accentSoft: Color,
    val good: Color,
    val gold: Color,
    /** Board background for the 4x4 grid; differs per theme (§Board geometry). */
    val boardBg: Color,
    /** True for the dark palette so consumers (status bar, etc.) can branch. */
    val isDark: Boolean,
) {
    companion object {
        val Dark = FuseColors(
            bg = Color(0xFF0A0E26),
            card = Color(0xFF141A38),
            card2 = Color(0xFF1E2750),
            // rgba(10,14,38,.85) -> alpha 0.85 ~= 0xD9
            navBg = Color(0xD90A0E26),
            // rgba(255,255,255,.08) -> alpha 0.08 ~= 0x14
            line = Color(0x14FFFFFF),
            text = Color(0xFFDCE6FF),
            sub = Color(0xFF8A97D6),
            accent = Color(0xFF6D7DFF),
            accentSoft = Color(0xFF1E2750),
            good = Color(0xFF34F5C5),
            gold = Color(0xFFFACC15),
            boardBg = Color(0xFF141A38),
            isDark = true,
        )

        val Light = FuseColors(
            bg = Color(0xFFEEF2FF),
            card = Color(0xFFFFFFFF),
            card2 = Color(0xFFE9EEFF),
            // rgba(255,255,255,.9) -> alpha 0.9 ~= 0xE6
            navBg = Color(0xE6FFFFFF),
            // rgba(20,30,80,.09) -> alpha 0.09 ~= 0x17
            line = Color(0x17141E50),
            text = Color(0xFF1B2559),
            sub = Color(0xFF6B7BB5),
            accent = Color(0xFF5B6EF5),
            accentSoft = Color(0xFFE7ECFF),
            good = Color(0xFF0FB99A),
            gold = Color(0xFFE8A800),
            boardBg = Color(0xFFDCE7FF),
            isDark = false,
        )
    }
}

/**
 * One step on the tile color ramp: background fill + foreground numeral color.
 * [glow] is non-null only for the 2048 brand moment.
 */
@Immutable
data class TileColors(
    val bg: Color,
    val fg: Color,
    val glow: Color? = null,
)

/**
 * The tile color ramp (`voltColor`) from `docs/design-tokens.md`.
 *
 * Maps a tile value (a power of two) to its [TileColors]. Values above 2048 fall
 * back to the last entry. This is a pure function of the value so the engine/UI
 * can colorize tiles without threading state; it is theme-independent (the ramp
 * is identical in light and dark per the doc).
 */
object TileRamp {
    // Exact ramp from the doc. 2048 carries a glow; >2048 uses the fallback row.
    private val ramp: Map<Int, TileColors> = mapOf(
        2 to TileColors(bg = Color(0xFFD7E6FF), fg = Color(0xFF1E3A8A)),
        4 to TileColors(bg = Color(0xFFAFCBFF), fg = Color(0xFF1E3A8A)),
        8 to TileColors(bg = Color(0xFF5B9DFF), fg = Color(0xFFFFFFFF)),
        16 to TileColors(bg = Color(0xFF3B82F6), fg = Color(0xFFFFFFFF)),
        32 to TileColors(bg = Color(0xFF22D3EE), fg = Color(0xFF06324A)),
        64 to TileColors(bg = Color(0xFF14B8A6), fg = Color(0xFFFFFFFF)),
        128 to TileColors(bg = Color(0xFF8B5CF6), fg = Color(0xFFFFFFFF)),
        256 to TileColors(bg = Color(0xFFA855F7), fg = Color(0xFFFFFFFF)),
        512 to TileColors(bg = Color(0xFFD946EF), fg = Color(0xFFFFFFFF)),
        1024 to TileColors(bg = Color(0xFFEC4899), fg = Color(0xFFFFFFFF)),
        // glow base: rgba(52,245,197,...) == #34F5C5; consumers apply the
        // documented spread/opacity (0 0 0 3px @ .3, 0 0 30px @ .8).
        2048 to TileColors(bg = Color(0xFF34F5C5), fg = Color(0xFF06324A), glow = Color(0xFF34F5C5)),
    )

    /** Fallback for values greater than 2048 (`> 2048` row in the doc). */
    val fallback: TileColors = TileColors(bg = Color(0xFF06324A), fg = Color(0xFF34F5C5))

    /** All discrete ramp entries (2..2048) in ascending order, for previews. */
    val entries: List<Pair<Int, TileColors>> = ramp.toList().sortedBy { it.first }

    /**
     * Colors for a tile [value]. Exact entries for 2..2048; values above 2048
     * return [fallback]. Values not present in the ramp (e.g. 0/1 or non-powers)
     * also resolve to the nearest sensible entry: <=2 maps to the 2 tile.
     */
    fun forValue(value: Int): TileColors = when {
        value > 2048 -> fallback
        else -> ramp[value] ?: ramp[2]!!
    }
}
