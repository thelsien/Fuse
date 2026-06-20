package com.fuse.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuse.ui.theme.FuseTheme

/**
 * UIB-4 — the score HUD: two token-styled stat cards ("SCORE" and "BEST") shown in the
 * top/thumb zone of [GameScreen], replacing UIB-3's placeholder score line.
 *
 * Purely presentational: it takes plain [current]/[best] `Long`s (no store, no Koin) so
 * it renders identically under preview/test and is trivial to assert. [GameScreen] feeds
 * it `state.currentScore` / `state.bestScore`, so it live-updates on every accepted move
 * (the store pushes a new [com.fuse.presentation.GameUiState] and the screen recomposes).
 *
 * ## Styling — tokens only (no literal hex)
 * Each value sits in a [FuseTheme.shapes.card]-clipped card filled with
 * `FuseTheme.colors.card`, hair-lined with `FuseTheme.colors.line`. The label uses the
 * muted `sub` color at caption size; the BEST value is tinted `gold` (the prototype's
 * trophy accent) while the running SCORE uses `text`. Numerals are bold per the type
 * scale (700 = "scores", design-tokens §Typography).
 *
 * ## Session-best semantics
 * The HUD only renders what it's handed; the guarantee that **best tracks the session
 * max across a new game** lives in [com.fuse.presentation.GameStore.reduceNewGame], which
 * carries `maxOf(initialBest, gameState.score.best)` into the fresh game (engine
 * `Score.startNewGame()` preserves best). So [best] never decreases on a NewGame within
 * the session, and rises with [current] as the player scores.
 *
 * @param current the running score for this game.
 * @param best the best score reached this session.
 * @param modifier outer modifier (the HUD fills width and lays the two cards side by side).
 */
@Composable
fun ScoreHud(
    current: Long,
    best: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ScoreHudTags.ROOT),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            label = "SCORE",
            value = current,
            valueColor = FuseTheme.colors.text,
            valueTag = ScoreHudTags.SCORE_VALUE,
            cardTag = ScoreHudTags.SCORE_CARD,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "BEST",
            value = best,
            valueColor = FuseTheme.colors.gold,
            valueTag = ScoreHudTags.BEST_VALUE,
            cardTag = ScoreHudTags.BEST_CARD,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Long,
    valueColor: androidx.compose.ui.graphics.Color,
    valueTag: String,
    cardTag: String,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    Column(
        modifier = modifier
            .widthIn(min = 96.dp)
            .clip(FuseTheme.shapes.card)
            .background(c.card)
            .border(1.dp, c.line, FuseTheme.shapes.card)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(cardTag)
            .semantics { contentDescription = "$label ${formatScore(value)}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = FuseTheme.type.captionS.copy(color = c.sub),
        )
        Text(
            text = formatScore(value),
            style = FuseTheme.type.titleM.copy(color = valueColor),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(valueTag),
        )
    }
}

/**
 * Formats a score for display with simple, locale-safe thousands grouping (a space
 * every three digits, e.g. `12 345`). Kept minimal and dependency-free so it behaves
 * identically on JVM and iOS (no `NumberFormat`/locale divergence). Negative values are
 * not expected (scores are non-negative) but are handled gracefully.
 */
fun formatScore(value: Long): String {
    val SPACE = ' '
    val negative = value < 0
    val digits = (if (negative) -value else value).toString()
    val grouped = StringBuilder()
    val n = digits.length
    for (i in 0 until n) {
        if (i > 0 && (n - i) % 3 == 0) grouped.append(SPACE)
        grouped.append(digits[i])
    }
    return if (negative) "-$grouped" else grouped.toString()
}

/** Stable test tags so UI tests target HUD nodes without depending on copy. */
object ScoreHudTags {
    const val ROOT: String = "score_hud"
    const val SCORE_CARD: String = "score_hud_score_card"
    const val SCORE_VALUE: String = "score_hud_score_value"
    const val BEST_CARD: String = "score_hud_best_card"
    const val BEST_VALUE: String = "score_hud_best_value"
}
