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
    content: @Composable () -> Unit,
) {
    val motion = if (reducedMotion) FuseMotion.Reduced else FuseMotion.Default
    val materialScheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.bg,
            surface = colors.card,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.bg,
            surface = colors.card,
            onBackground = colors.text,
            onSurface = colors.text,
        )
    }

    CompositionLocalProvider(
        LocalFuseColors provides colors,
        LocalFuseMotion provides motion,
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
}

/** Overridable semantic palette; defaults to the prototype's dark theme. */
val LocalFuseColors: ProvidableCompositionLocal<FuseColors> =
    staticCompositionLocalOf { FuseColors.Dark }

/** Overridable motion set; defaults to full motion. */
val LocalFuseMotion: ProvidableCompositionLocal<FuseMotion> =
    staticCompositionLocalOf { FuseMotion.Default }
