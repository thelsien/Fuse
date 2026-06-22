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
    /**
     * SHL-3 — the **colorblind-mode** variant of this palette.
     *
     * SHL-3 wires the colorblind toggle end to end (persisted + live through
     * `FuseTheme(colorblind = …)`), but the colorblind-SAFE palette itself is **ACC-1**
     * (Sprint 10). For now this returns a near-identity copy so flipping the flag flows through
     * the theme with no visible regression and no crash.
     *
     * **ACC-1 fills this in:** replace the body with the real colorblind-safe semantic palette
     * (and pair it with the tile-pattern overlay) — every call site already reads
     * [LocalFuseColors], so ACC-1 changes ONLY this function (and [TileRamp] for patterns), nothing
     * else.
     */
    fun colorblind(): FuseColors = this // TODO(ACC-1): swap for the colorblind-safe palette.

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
 * COS-2 — the resolvable tile-ramp abstraction: a `value -> TileColors` mapping.
 *
 * Before COS-2 the ramp was a single global `object TileRamp`. To let an EQUIPPED tile-skin
 * cosmetic restyle the tiles live (the COS-2 acceptance criterion), the ramp had to become a
 * VALUE that the theme can swap: `BoardView` now reads its ramp from [LocalTileRamp] (via
 * `FuseTheme.tiles`) instead of calling a global. The default ([TileRamp]) is the app's current
 * ramp (identity — no visual change), and a non-default skin is just another [TileRampStyle]
 * instance with a shifted palette (see `CosmeticTileSkins`). Pure (no Compose), so it is
 * unit-testable in commonTest and shareable by previews/tests with no started graph.
 */
@Immutable
interface TileRampStyle {
    /** Colors for a tile [value]. Must be defined for every value the board can show. */
    fun forValue(value: Int): TileColors

    /** Discrete entries (e.g. 2..2048) in ascending order, for previews/swatches. */
    val entries: List<Pair<Int, TileColors>>
}

/**
 * A [TileRampStyle] backed by an explicit `value -> TileColors` table plus a `> max` [fallback]
 * (and a `<= min` clamp to the smallest entry). This is how every concrete skin (default + each
 * cosmetic) is built: supply a ramp table + a fallback. Kept data-class-comparable so a pure test
 * can assert two skins differ (or that a placeholder skin equals the default).
 */
@Immutable
data class TableTileRampStyle(
    private val ramp: Map<Int, TileColors>,
    val fallback: TileColors,
) : TileRampStyle {

    override val entries: List<Pair<Int, TileColors>> = ramp.toList().sortedBy { it.first }

    private val smallestKey: Int = ramp.keys.min()
    private val largestKey: Int = ramp.keys.max()

    /**
     * Colors for a tile [value]. Exact entries for the table's keys; values above the largest key
     * return [fallback]; values below the smallest (e.g. 0/1) clamp to the smallest entry.
     */
    override fun forValue(value: Int): TileColors = when {
        value > largestKey -> fallback
        else -> ramp[value] ?: ramp[smallestKey]!!
    }
}

/**
 * The default tile color ramp (`voltColor`) from `docs/design-tokens.md` — the app's current,
 * theme-independent tiles (identical in light/dark). [Cosmetics.DEFAULT_STYLE] resolves to this,
 * so an unequipped / default skin renders exactly today's look (COS-2 identity criterion).
 *
 * It remains a singleton `object` (and keeps the same `forValue` / `entries` / `fallback` surface)
 * so existing call sites and tests that referenced `TileRamp` directly are unchanged; it now also
 * IS a [TileRampStyle] so it can be provided through the theme.
 */
object TileRamp : TileRampStyle {
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
    override val entries: List<Pair<Int, TileColors>> = ramp.toList().sortedBy { it.first }

    /**
     * Colors for a tile [value]. Exact entries for 2..2048; values above 2048
     * return [fallback]. Values not present in the ramp (e.g. 0/1 or non-powers)
     * also resolve to the nearest sensible entry: <=2 maps to the 2 tile.
     */
    override fun forValue(value: Int): TileColors = when {
        value > 2048 -> fallback
        else -> ramp[value] ?: ramp[2]!!
    }
}
