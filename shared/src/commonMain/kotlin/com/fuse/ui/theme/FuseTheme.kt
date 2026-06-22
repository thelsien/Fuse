package com.fuse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Theme entry point + token access for Fuse.
 *
 * ## Mechanism
 * The token layer is delivered through Compose [staticCompositionLocalOf]s, not
 * Koin. Reasons: tokens are pure UI values read deep in the composable tree;
 * CompositionLocal is the idiomatic Compose way to scope/override them; and it
 * keeps the token layer Koin-independent (no started graph required for a
 * preview or a UI test). Cosmetics / colorblind mode (later) override by nesting
 * a [FuseTheme] (or providing a custom [FuseColors]) — no call site changes.
 * The empty `uiModule` is intentionally left for Koin-scoped UI services (e.g. a
 * persisted theme-preference repository) rather than the raw tokens.
 *
 * ## Light / dark
 * [FuseColors.Dark] is the prototype default. Callers flip [darkTheme] (e.g. from
 * a stored preference or the system setting) to swap the whole [FuseColors]
 * instance; everything downstream reads [LocalFuseColors] and updates.
 *
 * A minimal Material3 ColorScheme is also provided so any stray Material
 * component (or `MaterialTheme.typography` fallbacks) inherits brand colors, but
 * Fuse screens should read the [FuseTheme] accessors below.
 */
@Composable
fun FuseTheme(
    darkTheme: Boolean = true,
    colors: FuseColors = if (darkTheme) FuseColors.Dark else FuseColors.Light,
    reducedMotion: Boolean = false,
    colorblind: Boolean = false,
    tileRamp: TileRampStyle = TileRamp,
    content: @Composable () -> Unit,
) {
    val motion = if (reducedMotion) FuseMotion.Reduced else FuseMotion.Default
    // SHL-3 — the colorblind-mode SEAM. SHL-3 delivers the persisted, live-flipping toggle
    // (App() reads ColorblindSettings and passes `colorblind = …` here); the actual
    // colorblind-safe palette + tile patterns are ACC-1 (Sprint 10). For now `colorblind = true`
    // maps to a near-identity palette (FuseColors.colorblind()) so the flag flows end to end with
    // no visible regression; ACC-1 only has to fill that palette (and add patterns) behind THIS
    // flag — no call site changes.
    //
    // COS-2 — COLORBLIND PRECEDENCE: colorblind is an ACCESSIBILITY override and WINS over a
    // cosmetic board theme for legibility. `App()` only resolves the equipped board theme into
    // [colors] when colorblind is OFF; when it is ON, `App()` passes the plain base palette and
    // `colorblind()` re-skins it — so a cosmetic can never break colorblind mode. (Tile skins are
    // unaffected here: ACC-1 will add the colorblind tile-pattern overlay; until then the equipped
    // tile ramp still applies, which is harmless for the current near-identity colorblind palette.)
    val effectiveColors = if (colorblind) colors.colorblind() else colors
    val materialScheme = if (effectiveColors.isDark) {
        darkColorScheme(
            primary = effectiveColors.accent,
            background = effectiveColors.bg,
            surface = effectiveColors.card,
            onBackground = effectiveColors.text,
            onSurface = effectiveColors.text,
        )
    } else {
        lightColorScheme(
            primary = effectiveColors.accent,
            background = effectiveColors.bg,
            surface = effectiveColors.card,
            onBackground = effectiveColors.text,
            onSurface = effectiveColors.text,
        )
    }

    CompositionLocalProvider(
        LocalFuseColors provides effectiveColors,
        LocalFuseMotion provides motion,
        LocalTileRamp provides tileRamp,
    ) {
        MaterialTheme(colorScheme = materialScheme) {
            content()
        }
    }
}

/**
 * Token accessors. `FuseTheme.colors`, `.type`, `.shapes`, `.dimens`, `.motion`
 * — the single read surface for screens. Colors/motion are CompositionLocal
 * (themeable); type/shapes/dimens are stateless objects (constant across themes).
 */
object FuseTheme {
    val colors: FuseColors
        @Composable get() = LocalFuseColors.current

    val type: FuseType get() = FuseType

    val shapes: Shapes get() = Shapes

    val dimens: Dimens get() = Dimens

    val motion: FuseMotion
        @Composable get() = LocalFuseMotion.current

    /**
     * COS-2 — the active tile-ramp style (the equipped TILE_SKIN, or the default ramp). The single
     * read surface for tile colors: [com.fuse.ui.board.BoardView] reads `FuseTheme.tiles.forValue(v)`
     * instead of calling the global `TileRamp` directly, which is what makes equipping a skin restyle
     * tiles LIVE (the provided ramp swaps, the board recomposes).
     */
    val tiles: TileRampStyle
        @Composable get() = LocalTileRamp.current
}

/** Overridable semantic palette; defaults to the prototype's dark theme. */
val LocalFuseColors: ProvidableCompositionLocal<FuseColors> =
    staticCompositionLocalOf { FuseColors.Dark }

/** Overridable motion set; defaults to full motion. */
val LocalFuseMotion: ProvidableCompositionLocal<FuseMotion> =
    staticCompositionLocalOf { FuseMotion.Default }

/**
 * COS-2 — overridable tile-ramp style; defaults to [TileRamp] (the app's current tiles, identity).
 * `FuseTheme` provides the equipped TILE_SKIN here; `BoardView`/`TileCell` read it via
 * `FuseTheme.tiles`. The default means existing `BoardView(board)` callers/tests/previews that
 * render without an equipped skin still get the current look with no API change.
 */
val LocalTileRamp: ProvidableCompositionLocal<TileRampStyle> =
    staticCompositionLocalOf { TileRamp }
