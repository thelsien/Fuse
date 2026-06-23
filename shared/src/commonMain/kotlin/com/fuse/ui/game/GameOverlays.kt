package com.fuse.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuse.ui.theme.FuseTheme

/**
 * UIB-5 — the end-of-game overlays: a [WinOverlay] (first-2048 celebration) and a
 * [LoseOverlay] (game-over). Both are purely presentational, value-driven cards over a
 * dimming scrim — no store, no Koin — so they render identically under preview/test and
 * are trivial to assert. [GameScreen] hosts them in a `Box` on top of the board and wires
 * the callbacks to store intents (NewGame / dismiss-win). See [GameScreen] for the
 * win-once-then-keep-going wiring (a one-shot [com.fuse.presentation.GameEffect.Won]).
 *
 * ## Styling — tokens only (no literal hex)
 * Each overlay fills the screen with a translucent scrim ([scrim]) and centers a
 * [FuseTheme.shapes.largeCard]-clipped card filled with `FuseTheme.colors.card`, lined
 * with `FuseTheme.colors.line`. The WIN card leads with the mint brand accent
 * (`colors.good`) — the 2048 brand moment — while the LOSE card uses the calmer muted
 * `colors.sub` for its eyebrow. Numerals/labels are always opaque and readable.
 */

/** A translucent black scrim that dims the board behind an overlay. */
private val scrim: Color = Color(0xB3000000) // ~70% black

/**
 * The win celebration shown ONCE when the player first reaches the win target.
 *
 * Offers two actions: **Keep going** ([onKeepGoing]) — dismiss and continue playing past
 * the target (the game phase stays `Won(canContinue = true)`, so moves still apply) — and
 * **Restart** ([onRestart]) — start a fresh game. The host ([GameScreen]) ensures this is
 * not re-shown on subsequent moves.
 *
 * @param score the current score at the moment of winning.
 * @param best the session best score.
 * @param onKeepGoing dismiss the overlay and continue the current game.
 * @param onRestart start a new game.
 */
@Composable
fun WinOverlay(
    score: Long,
    best: Long,
    onKeepGoing: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    OverlayScaffold(
        rootTag = GameOverlayTags.WIN_ROOT,
        accent = c.good,
    ) {
        Text(
            text = "You win!",
            style = FuseTheme.type.titleXL.copy(color = c.good),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(GameOverlayTags.WIN_TITLE),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "You reached 2048",
            style = FuseTheme.type.bodyM.copy(color = c.sub),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        ScoreSummary(score = score, best = best)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier
                    .weight(1f)
                    .testTag(GameOverlayTags.WIN_RESTART)
                    .semantics { contentDescription = "Restart game" },
            ) {
                Text("Restart")
            }
            Button(
                onClick = onKeepGoing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.good,
                    contentColor = c.bg,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag(GameOverlayTags.WIN_KEEP_GOING)
                    .semantics { contentDescription = "Keep going past 2048" },
            ) {
                Text("Keep going")
            }
        }
    }
}

/**
 * The game-over overlay shown when the game is lost (board stuck, no move changes it).
 *
 * Shows the final [score] (and session [best]) and a **Restart** action ([onRestart]) that
 * starts a fresh game. ADS-2 adds an OPTIONAL **Continue — Watch Ad** action ([onContinue])
 * shown only when [canRevive] is `true` (the game is game-over and has not yet been revived):
 * tapping it asks the host to play a rewarded ad and, on a verified completion, revive the game
 * so play resumes. The Continue action is purely presentational here — the ad flow + reward
 * gating live in [GameScreen]; this overlay only renders the button and forwards the tap.
 *
 * When [canRevive] is `false` (no revive available — e.g. a game already revived once, or a
 * host that never offers revive) the overlay degrades to Restart-only, exactly as before ADS-2.
 *
 * @param score the final score.
 * @param best the session best score.
 * @param onRestart start a new game.
 * @param canRevive whether to show the optional Continue — Watch Ad action.
 * @param onContinue tapped when the player opts into the rewarded revive (no-op by default).
 * @param showNoAdNote whether to show the brief "No ad available" graceful-fallback note
 *   (set by the host after a rewarded attempt that did not earn a reward).
 */
@Composable
fun LoseOverlay(
    score: Long,
    best: Long,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    canRevive: Boolean = false,
    onContinue: () -> Unit = {},
    showNoAdNote: Boolean = false,
) {
    val c = FuseTheme.colors
    OverlayScaffold(
        rootTag = GameOverlayTags.LOSE_ROOT,
        accent = c.line,
    ) {
        Text(
            text = "Game over",
            style = FuseTheme.type.titleXL.copy(color = c.text),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(GameOverlayTags.LOSE_TITLE),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "No moves left",
            style = FuseTheme.type.bodyM.copy(color = c.sub),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        ScoreSummary(score = score, best = best)
        Spacer(Modifier.height(20.dp))
        // ADS-2 — the optional rewarded "continue". Shown ONLY while a revive is available;
        // a successful rewarded watch (handled by the host) revives the game and dismisses
        // this overlay. Leads the card (above Restart) as the encouraged action; styled with
        // the mint brand accent like the win card's primary action.
        if (canRevive) {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.good,
                    contentColor = c.bg,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GameOverlayTags.LOSE_CONTINUE)
                    .semantics { contentDescription = "Continue by watching an ad" },
            ) {
                Text("Continue — Watch Ad")
            }
            // ADS-2 — graceful-fallback note when a rewarded attempt earned no reward
            // (no-fill / dismissed / failed). The overlay stays; the player can retry or restart.
            if (showNoAdNote) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "No ad available — try again",
                    style = FuseTheme.type.bodyS.copy(color = c.sub),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(GameOverlayTags.LOSE_NO_AD_NOTE),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(
                containerColor = c.accent,
                contentColor = c.text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(GameOverlayTags.LOSE_RESTART)
                .semantics { contentDescription = "Restart game" },
        ) {
            Text("Restart")
        }
    }
}

/**
 * Shared overlay chrome: full-bleed scrim + a centered token-styled card. The scrim
 * swallows clicks/swipes (so the dimmed board behind it is not interactable) without a
 * ripple. [accent] tints the card's top border to differentiate win (mint) vs lose (line).
 */
@Composable
private fun OverlayScaffold(
    rootTag: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    val c = FuseTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            // Absorb taps/drags so the board behind the overlay cannot be swiped/tapped.
            // A raw pointerInput (vs. clickable) avoids adding a merging click-semantics
            // node that would swallow the inner score/title test tags into the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag(rootTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 24.dp)
                .clip(FuseTheme.shapes.largeCard)
                .background(c.card)
                .border(1.dp, accent, FuseTheme.shapes.largeCard)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/** Final/current score + best, reusing the HUD's locale-safe [formatScore] grouping. */
@Composable
private fun ScoreSummary(score: Long, best: Long) {
    val c = FuseTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SCORE",
            style = FuseTheme.type.captionS.copy(color = c.sub),
        )
        Text(
            text = formatScore(score),
            style = FuseTheme.type.displayM.copy(color = c.text),
            modifier = Modifier.testTag(GameOverlayTags.SCORE_VALUE),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Best ${formatScore(best)}",
            style = FuseTheme.type.bodyS.copy(color = c.gold),
            modifier = Modifier.testTag(GameOverlayTags.BEST_VALUE),
        )
    }
}

/** Stable test tags so UI tests target overlay nodes without depending on copy. */
object GameOverlayTags {
    const val WIN_ROOT: String = "overlay_win"
    const val WIN_TITLE: String = "overlay_win_title"
    const val WIN_KEEP_GOING: String = "overlay_win_keep_going"
    const val WIN_RESTART: String = "overlay_win_restart"

    const val LOSE_ROOT: String = "overlay_lose"
    const val LOSE_TITLE: String = "overlay_lose_title"
    const val LOSE_RESTART: String = "overlay_lose_restart"

    /** ADS-2 — the optional rewarded "continue" action (rendered only when a revive is available). */
    const val LOSE_CONTINUE: String = "overlay_lose_continue"

    /** ADS-2 — the brief "No ad available" graceful-fallback note. */
    const val LOSE_NO_AD_NOTE: String = "overlay_lose_no_ad_note"

    /** Shared score/best summary tags (present in whichever overlay is shown). */
    const val SCORE_VALUE: String = "overlay_score_value"
    const val BEST_VALUE: String = "overlay_best_value"
}
