package com.fuse.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuse.presentation.GameEffect
import com.fuse.presentation.GameIntent
import com.fuse.presentation.GameStore
import com.fuse.presentation.GameUiState
import com.fuse.ui.board.BoardTransition
import com.fuse.ui.board.BoardView
import com.fuse.ui.input.swipeable
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.koinInject

/**
 * UIB-3 — the playable game screen: the First-Playable binding of swipe input to the
 * pure engine through the [GameStore].
 *
 * It resolves the [GameStore] from Koin (overridable via [store] for tests), collects
 * its [GameStore.state], and renders:
 *  - the [ScoreHud] (UIB-4) showing live current + session-best score,
 *  - the [BoardView] wrapped in [Modifier.swipeable] so every resolved swipe becomes a
 *    [GameIntent.Move] (engine → new state → recomposition), disabled once the game is
 *    over,
 *  - a "New game" button submitting [GameIntent.NewGame].
 *
 * A blocked swipe is a visible no-op: the store leaves the board/score unchanged and
 * raises [GameUiState.lastMoveBlocked] (plus a one-shot effect); a small "blocked"
 * marker is shown here so the no-op is observable. Win/lose overlays consuming
 * [GameUiState.phase] / [GameUiState.justWon] / [GameUiState.gameOver] are UIB-5.
 *
 * @param store the MVI store; defaults to the Koin-provided singleton.
 * @param modifier outer modifier.
 */
@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    store: GameStore = koinInject(),
) {
    val state by store.state.collectAsState()

    // UIB-5 — win-once-then-keep-going.
    //
    // The persistent `Won` phase / `justWon` flag are the WRONG driver for the
    // celebration: deriving the overlay from them would re-show it (or, with
    // `LaunchedEffect(state.justWon)`, risk re-firing) on the moves the player makes
    // after "Keep going". Instead we flip a local UI flag from the store's one-shot
    // [GameEffect.Won], which is emitted exactly once on the move that first reaches the
    // target and never replays. "Keep going" / "Restart" simply clear the flag; play
    // continues because the engine phase stays `Won(canContinue = true)`.
    var showWin by remember(store) { mutableStateOf(false) }
    LaunchedEffect(store) {
        store.effects.filterIsInstance<GameEffect.Won>().collect { showWin = true }
    }

    GameScreenContent(
        state = state,
        onSwipe = { store.accept(GameIntent.Move(it)) },
        onNewGame = { store.accept(GameIntent.NewGame()) },
        showWin = showWin,
        onKeepGoing = { showWin = false },
        onRestart = {
            showWin = false
            store.accept(GameIntent.NewGame())
        },
        modifier = modifier,
    )
}

/**
 * Stateless content of [GameScreen] — a pure function of [state] + callbacks, so it
 * renders identically under preview/test without Koin or a real store.
 */
@Composable
fun GameScreenContent(
    state: GameUiState,
    onSwipe: (com.fuse.engine.Direction) -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
    showWin: Boolean = false,
    onKeepGoing: () -> Unit = {},
    onRestart: () -> Unit = onNewGame,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(GameScreenTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // UIB-4 — the score HUD (live current + session best), fed from store state.
            ScoreHud(
                current = state.currentScore,
                best = state.bestScore,
            )

            Spacer(Modifier.height(16.dp))

            BoardView(
                board = state.board,
                // FEL-2 — pop/glow the just-merged result tiles, and FEL-3 — play the
                // entrance for the tile that spawned this move (after the slide settles).
                // Empty merges + null spawn (blocked / new game) ⇒ BoardTransition.None.
                transition = BoardTransition.fromOutcome(
                    merges = state.lastMerges,
                    spawnedId = state.spawned?.id,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GameScreenTags.BOARD)
                    .semantics {
                        // Surface the blocked no-op for assertion / accessibility.
                        if (state.lastMoveBlocked) contentDescription = BLOCKED_DESCRIPTION
                    }
                    .swipeable(
                        onSwipe = onSwipe,
                        enabled = !state.isGameOver,
                    ),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onNewGame,
                modifier = Modifier.testTag(GameScreenTags.NEW_GAME),
            ) {
                Text("New game")
            }
        }

        // UIB-5 — end-of-game overlays, layered on top of the board.
        //
        // Lose is derived straight from state: `Lost` is terminal, so the overlay should
        // persist for the whole game-over condition (and swipe is already disabled when
        // isGameOver). Win is gated by the one-shot-driven [showWin] flag so it shows
        // ONCE on first-2048 and not again after "Keep going". Lose wins the layer if a
        // continued game is later lost while the win overlay was somehow still up.
        if (state.isGameOver) {
            LoseOverlay(
                score = state.currentScore,
                best = state.bestScore,
                onRestart = onRestart,
            )
        } else if (showWin) {
            WinOverlay(
                score = state.currentScore,
                best = state.bestScore,
                onKeepGoing = onKeepGoing,
                onRestart = onRestart,
            )
        }
    }
}

/** Stable test tags so UI tests target nodes without depending on copy. */
object GameScreenTags {
    const val ROOT: String = "game_screen"
    const val BOARD: String = "game_board"
    const val NEW_GAME: String = "game_new_game"
}

/** Semantic marker placed on the board when the last swipe was blocked. */
const val BLOCKED_DESCRIPTION: String = "Move blocked"
