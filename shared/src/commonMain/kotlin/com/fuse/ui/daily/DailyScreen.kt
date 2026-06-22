package com.fuse.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuse.daily.Sharer
import com.fuse.daily.buildDailyShareCard
import com.fuse.engine.Direction
import com.fuse.presentation.DailyEffect
import com.fuse.presentation.DailyIntent
import com.fuse.presentation.DailyStore
import com.fuse.presentation.DailyStreakState
import com.fuse.presentation.DailyStreakStore
import com.fuse.presentation.DailyUiState
import com.fuse.ui.board.BoardTransition
import com.fuse.ui.board.BoardView
import com.fuse.ui.input.swipeable
import com.fuse.ui.theme.FuseTheme
import org.koin.compose.koinInject

/**
 * DLY-4 — the playable **Daily Challenge** screen.
 *
 * The Daily is a deterministic, NO-SPAWN puzzle: today's seed-derived start board, reach
 * the target tile in the fewest moves. This screen mirrors `GameScreen` but binds to the
 * SEPARATE [DailyStore] (no score; a move counter vs par; free undo/restart; one-shot
 * until solved):
 *  - a [DailyHud] showing **Moves · Par · Target · Daily #N**,
 *  - the [BoardView] (slide + merge pop from the last move) wrapped in [Modifier.swipeable]
 *    so each swipe is a no-spawn [DailyIntent.Move]; disabled once solved (locked),
 *  - **Undo** + **Restart** controls (disabled when there are no moves, or once solved),
 *  - on solve, a **solved overlay** ("Solved in N moves! (Par P)") that locks the board,
 *  - a back affordance to Home (system back on Android; this in-screen "‹ Home" on iOS).
 *
 * Presentational concerns live in [DailyScreenContent] (a pure function of state +
 * callbacks) so the screen renders under preview/test without Koin.
 *
 * @param store the daily MVI store; defaults to the Koin singleton.
 * @param onBack back affordance to Home; `null` hides the button (e.g. previews). `App()`
 *   wires it to `navController.popBackStack()`.
 */
@Composable
fun DailyScreen(
    modifier: Modifier = Modifier,
    store: DailyStore = koinInject(),
    streakStore: DailyStreakStore = koinInject(),
    sharer: Sharer = koinInject(),
    onBack: (() -> Unit)? = null,
) {
    val state by store.state.collectAsState()
    val streak by streakStore.state.collectAsState()

    // DLY-5 — record the streak on a LIVE solve. The store's one-shot DailyEffect.Solved fires
    // exactly once on the winning move (not on resume of an already-solved run), mirroring how
    // GameScreen collects effects. recordSolved is idempotent for the same day, so even a
    // re-observed effect never double-counts. The streakStore re-reads + persists, then its
    // state flow updates the overlay's "Streak: X" live.
    LaunchedEffect(store, streakStore) {
        store.effects.collect { effect ->
            when (effect) {
                is DailyEffect.Solved -> streakStore.recordSolved(effect.dayNumber)
            }
        }
    }

    DailyScreenContent(
        state = state,
        streak = streak,
        onSwipe = { store.accept(DailyIntent.Move(it)) },
        onUndo = { store.accept(DailyIntent.Undo) },
        onRestart = { store.accept(DailyIntent.Restart) },
        // DLY-7 — Share builds the result card from the day's SHARED start board (so cards are
        // comparable; never the player's mid-solve board), the result fields, and the live
        // streak, then hands it to the platform [Sharer] (native share sheet). User-initiated.
        onShare = { sharer.share(dailyShareCardFor(state, streak.current)) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * DLY-7 — builds the share-card text for [state] using the day's SHARED start board.
 *
 * The grid source is [DailyUiState.startBoard] (the day's fixed no-spawn start board, the same
 * for every player), NOT [DailyUiState.board] (the player's solved board, which differs per
 * player) — so the emoji grids on shared cards compare directly. The streak comes from the live
 * [DailyStreakState.current].
 */
private fun dailyShareCardFor(state: DailyUiState, currentStreak: Int): String =
    buildDailyShareCard(
        dayNumber = state.dayNumber,
        target = state.target,
        moves = state.winningMoves ?: state.moveCount,
        par = state.par,
        startBoard = state.startBoard,
        currentStreak = currentStreak,
    )

/** Stateless content of [DailyScreen] — pure function of [state] + callbacks. */
@Composable
fun DailyScreenContent(
    state: DailyUiState,
    onSwipe: (Direction) -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    streak: DailyStreakState = DailyStreakState(),
    onShare: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val c = FuseTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .testTag(DailyScreenTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .testTag(DailyScreenTags.BACK),
                ) {
                    Text("‹ Home")
                }
            }

            DailyHud(state = state)

            Spacer(Modifier.height(16.dp))

            BoardView(
                board = state.board,
                // Slide (FEL-1) + merge pop (FEL-2) from the last accepted move. No spawn ⇒
                // no spawn entrance. Empty merges (undo/restart/resume) ⇒ BoardTransition.None.
                transition = BoardTransition.fromMerges(state.lastMerges),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DailyScreenTags.BOARD)
                    .swipeable(
                        onSwipe = onSwipe,
                        // Locked once solved (one-shot until tomorrow).
                        enabled = !state.solved,
                    ),
            )

            Spacer(Modifier.height(16.dp))

            // Undo + Restart controls. Disabled when there's nothing to undo/restart or
            // once the run is solved (locked).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControlButton(
                    label = "Undo",
                    enabled = state.canUndo,
                    onClick = onUndo,
                    tag = DailyScreenTags.UNDO,
                    modifier = Modifier.weight(1f),
                )
                ControlButton(
                    label = "Restart",
                    enabled = state.canRestart,
                    onClick = onRestart,
                    tag = DailyScreenTags.RESTART,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Solved overlay — locks the board (swipe already disabled). Placeholder for the
        // DLY-7 share button + a "come back tomorrow" note.
        if (state.solved) {
            SolvedOverlay(
                moves = state.winningMoves ?: state.moveCount,
                par = state.par,
                streak = streak,
                onShare = onShare,
            )
        }
    }
}

/** The Daily HUD: a single token-styled card — Moves · Par · Target · Daily #N. */
@Composable
private fun DailyHud(state: DailyUiState, modifier: Modifier = Modifier) {
    val c = FuseTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(DailyHudTags.ROOT),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HudStat("MOVES", state.moveCount.toString(), c.text, DailyHudTags.MOVES, Modifier.weight(1f))
        HudStat("PAR", state.par.toString(), c.sub, DailyHudTags.PAR, Modifier.weight(1f))
        HudStat("TARGET", state.target.toString(), c.good, DailyHudTags.TARGET, Modifier.weight(1f))
        HudStat("DAILY", "#${state.dayNumber}", c.gold, DailyHudTags.DAY, Modifier.weight(1f))
    }
}

@Composable
private fun HudStat(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    Column(
        modifier = modifier
            .clip(FuseTheme.shapes.card)
            .background(c.card)
            .border(1.dp, c.line, FuseTheme.shapes.card)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("${tag}_card")
            .semantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = FuseTheme.type.captionXS.copy(color = c.sub))
        Text(
            value,
            style = FuseTheme.type.titleS.copy(color = valueColor),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

/** A token-styled control button (Undo / Restart) that visibly dims when disabled. */
@Composable
private fun ControlButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.card
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.dp, c.line, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = FuseTheme.type.headingM.copy(color = if (enabled) c.text else c.sub),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The solved overlay: "Solved in N moves! (Par P)", the DLY-5 **streak** line
 * ("🔥 Streak: X · Best Y"), the DLY-7 **Share** button (builds + shares the result card), and a
 * "come back tomorrow" note. The board is locked while this shows.
 *
 * @param streak the displayable streak — `current` is the live value (0 when broken),
 *   `longest` the all-time best. Rendered as a token-styled line under the move summary.
 * @param onShare invoked when the Share button is tapped; builds the card and opens the native
 *   share sheet (user-initiated; only presents the OS chooser, never auto-sends).
 */
@Composable
private fun SolvedOverlay(
    moves: Int,
    par: Int,
    streak: DailyStreakState,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg.copy(alpha = 0.86f))
            .testTag(DailyScreenTags.SOLVED_OVERLAY),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp)
                .clip(FuseTheme.shapes.largeCard)
                .background(c.card)
                .border(1.dp, c.line, FuseTheme.shapes.largeCard)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Solved!",
                style = FuseTheme.type.titleL.copy(color = c.good),
            )
            Text(
                "Solved in $moves moves! (Par $par)",
                style = FuseTheme.type.headingM.copy(color = c.text),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(DailyScreenTags.SOLVED_SUMMARY),
            )
            // DLY-5 — the streak line. current = the live consecutive-day streak (1+ after this
            // solve; 0 only if somehow not yet recorded), longest = the all-time best.
            Text(
                "🔥 Streak: ${streak.current} · Best ${streak.longest}",
                style = FuseTheme.type.headingM.copy(color = c.gold),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .testTag(DailyScreenTags.SOLVED_STREAK)
                    .semantics {
                        contentDescription =
                            "Daily streak ${streak.current}, best ${streak.longest}"
                    },
            )
            // DLY-7 — the Share button. Tapping it builds the result card (day · target ·
            // moves-vs-par + the day's shared start-board emoji grid + streak) and opens the
            // native share sheet via the platform [Sharer]. Token-styled, tappable.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(FuseTheme.shapes.card)
                    .background(c.card2)
                    .border(1.dp, c.line, FuseTheme.shapes.card)
                    .clickable(onClick = onShare)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(DailyScreenTags.SHARE_BUTTON)
                    .semantics { contentDescription = "Share your Daily result" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Share result",
                    style = FuseTheme.type.headingM.copy(color = c.text),
                )
            }
            Text(
                "Come back tomorrow for a new puzzle.",
                style = FuseTheme.type.bodyS.copy(color = c.sub),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Stable test tags so UI tests target Daily nodes without depending on copy. */
object DailyScreenTags {
    const val ROOT: String = "daily_screen"
    const val BOARD: String = "daily_board"
    const val BACK: String = "daily_back"
    const val UNDO: String = "daily_undo"
    const val RESTART: String = "daily_restart"
    const val SOLVED_OVERLAY: String = "daily_solved_overlay"
    const val SOLVED_SUMMARY: String = "daily_solved_summary"

    /** DLY-5 — the streak line on the solved overlay ("🔥 Streak: X · Best Y"). */
    const val SOLVED_STREAK: String = "daily_solved_streak"

    /** DLY-7 — the Share button on the solved overlay (builds + shares the result card). */
    const val SHARE_BUTTON: String = "daily_share_button"
}

/** Stable test tags for the Daily HUD stat values. */
object DailyHudTags {
    const val ROOT: String = "daily_hud"
    const val MOVES: String = "daily_hud_moves"
    const val PAR: String = "daily_hud_par"
    const val TARGET: String = "daily_hud_target"
    const val DAY: String = "daily_hud_day"
}
