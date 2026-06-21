package com.fuse.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuse.ui.theme.FuseBrand
import com.fuse.ui.theme.FuseTheme

/**
 * SHL-1 — the Fuse **Home screen** (app shell launch surface).
 *
 * Presentational + value-driven: it takes the [best] score as a plain `Long` and the
 * entry-point actions as callbacks, so it renders identically under preview/test with no
 * Koin and no store. A thin stateful wrapper (`App()` / a future SHL-2 nav graph) resolves
 * the best from the live [com.fuse.presentation.GameStore] and wires the callbacks to
 * navigation.
 *
 * ## Acceptance criteria covered
 *  - **Entry points** to **Classic** (primary CTA — [onPlayClassic]), **Daily** (placeholder
 *    "Coming soon" — Sprint 5; the [onOpenDaily] callback still fires so it's testable but the
 *    button reads as disabled), and **Settings** (entry point — SHL-3; [onOpenSettings]).
 *  - **Shows the best score** prominently in a token-styled card up top.
 *  - **Thumb-zone layout** — the brand title + best-score card sit in the UPPER region inside a
 *    weighted spacer that pushes the interactive controls (Classic / Daily / Settings) into the
 *    LOWER / thumb area for one-handed reach on tall devices.
 *
 * ## Styling — tokens only (no literal hex)
 * Background is `FuseTheme.colors.bg`; the title uses the brand mint/accent; the best-score card
 * is a `card`-clipped, hair-lined surface with a `gold` value (the trophy accent, matching the
 * HUD's BEST). The Classic CTA fills with the brand **accent gradient** (`FuseBrand.accentGradient`,
 * the doc's "Primary CTAs / hero"); Daily uses the **gold gradient** but at reduced emphasis since
 * it is a placeholder. All copy is rendered via stable [HomeScreenTags] so tests target nodes by
 * tag, not text.
 *
 * @param best the best score to display (sourced from the game store — see `App()`).
 * @param onPlayClassic invoked when the player taps the primary **Classic** CTA.
 * @param onOpenDaily invoked when the player taps **Daily** (placeholder for Sprint 5).
 * @param onOpenSettings invoked when the player taps **Settings** (SHL-3 wires the real screen).
 * @param modifier outer modifier.
 * @param dailyEnabled whether the Daily entry point is active; defaults to `false` (placeholder).
 */
@Composable
fun HomeScreen(
    best: Long,
    onPlayClassic: () -> Unit,
    onOpenDaily: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    dailyEnabled: Boolean = false,
) {
    val c = FuseTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .testTag(HomeScreenTags.ROOT),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Upper region: brand + best score ──────────────────────────────────────
            Spacer(Modifier.heightIn(min = 24.dp).weight(0.6f))

            Text(
                text = "FUSE",
                style = FuseTheme.type.displayL.copy(color = c.good),
                modifier = Modifier.testTag(HomeScreenTags.TITLE),
            )
            Spacer(Modifier.heightIn(min = 8.dp))
            Text(
                text = "Merge to 2048",
                style = FuseTheme.type.headingM.copy(color = c.sub),
            )

            Spacer(Modifier.heightIn(min = 28.dp))

            BestScoreCard(best = best)

            // ── Weighted gap pushes the controls into the lower / thumb zone ──────────
            Spacer(Modifier.weight(1f))

            // ── Lower region: interactive entry points (thumb-reachable) ──────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .testTag(HomeScreenTags.ACTIONS),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GradientButton(
                    label = "Play Classic",
                    gradient = FuseBrand.accentGradient,
                    contentColor = Color.White,
                    onClick = onPlayClassic,
                    tag = HomeScreenTags.CLASSIC,
                )
                GradientButton(
                    label = if (dailyEnabled) "Daily" else "Daily — Coming soon",
                    gradient = FuseBrand.goldGradient,
                    contentColor = FuseBrand.navy,
                    onClick = onOpenDaily,
                    enabled = dailyEnabled,
                    tag = HomeScreenTags.DAILY,
                )
                OutlineButton(
                    label = "Settings",
                    onClick = onOpenSettings,
                    tag = HomeScreenTags.SETTINGS,
                )
            }

            Spacer(Modifier.heightIn(min = 8.dp))
        }
    }
}

/** The prominent best-score card (mirrors the in-game HUD's BEST tint). */
@Composable
private fun BestScoreCard(best: Long, modifier: Modifier = Modifier) {
    val c = FuseTheme.colors
    Column(
        modifier = modifier
            .widthIn(min = 160.dp)
            .clip(FuseTheme.shapes.largeCard)
            .background(c.card)
            .border(1.dp, c.line, FuseTheme.shapes.largeCard)
            .padding(horizontal = 28.dp, vertical = 16.dp)
            .testTag(HomeScreenTags.BEST_CARD)
            .semantics { contentDescription = "Best ${formatHomeScore(best)}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "BEST",
            style = FuseTheme.type.caption.copy(color = c.sub),
        )
        Text(
            text = formatHomeScore(best),
            style = FuseTheme.type.titleL.copy(color = c.gold),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(HomeScreenTags.BEST_VALUE),
        )
    }
}

/** A full-width gradient-filled button (token-styled; no Material container color). */
@Composable
private fun GradientButton(
    label: String,
    gradient: List<Color>,
    contentColor: Color,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = FuseTheme.shapes.card
    val brush = if (enabled) {
        Brush.horizontalGradient(gradient)
    } else {
        // Muted, flat fill for the disabled placeholder so it reads as inactive.
        Brush.horizontalGradient(listOf(FuseTheme.colors.card2, FuseTheme.colors.card2))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(brush)
            .then(if (enabled) Modifier.clickableButton(onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FuseTheme.type.headingM.copy(
                color = if (enabled) contentColor else FuseTheme.colors.sub,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

/** A full-width outlined (secondary) button for the Settings entry point. */
@Composable
private fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.card
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.dp, c.line, shape)
            .clickableButton(onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FuseTheme.type.headingM.copy(color = c.text),
            textAlign = TextAlign.Center,
        )
    }
}

/** Clickable that exposes click semantics for tests / accessibility. */
private fun Modifier.clickableButton(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/**
 * Locale-safe thousands grouping for the Home best score (space every three digits),
 * identical behavior on JVM and iOS — mirrors the HUD's `formatScore` so Home and the
 * in-game best read the same. Kept local so Home stays independent of the game package.
 */
internal fun formatHomeScore(value: Long): String {
    val negative = value < 0
    val digits = (if (negative) -value else value).toString()
    val grouped = StringBuilder()
    val n = digits.length
    for (i in 0 until n) {
        if (i > 0 && (n - i) % 3 == 0) grouped.append(' ')
        grouped.append(digits[i])
    }
    return if (negative) "-$grouped" else grouped.toString()
}

/** Stable test tags so UI tests target Home nodes without depending on copy. */
object HomeScreenTags {
    const val ROOT: String = "home_screen"
    const val TITLE: String = "home_title"
    const val ACTIONS: String = "home_actions"
    const val BEST_CARD: String = "home_best_card"
    const val BEST_VALUE: String = "home_best_value"
    const val CLASSIC: String = "home_classic"
    const val DAILY: String = "home_daily"
    const val SETTINGS: String = "home_settings"
}
