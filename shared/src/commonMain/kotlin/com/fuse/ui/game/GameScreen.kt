package com.fuse.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fuse.presentation.GameIntent
import com.fuse.presentation.GameStore
import com.fuse.presentation.GameUiState
import com.fuse.ui.board.BoardView
import com.fuse.ui.input.swipeable
import com.fuse.ui.theme.FuseTheme
import org.koin.compose.koinInject

/**
 * UIB-3 — the playable game screen: the First-Playable binding of swipe input to the
 * pure engine through the [GameStore].
 *
 * It resolves the [GameStore] from Koin (overridable via [store] for tests), collects
 * its [GameStore.state], and renders:
 *  - a minimal score line (just enough to see the binding work — the full HUD is UIB-4),
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
    GameScreenContent(
        state = state,
        onSwipe = { store.accept(GameIntent.Move(it)) },
        onNewGame = { store.accept(GameIntent.NewGame()) },
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
            // Minimal score line (full HUD = UIB-4).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Score ${state.currentScore}",
                    color = FuseTheme.colors.text,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag(GameScreenTags.SCORE),
                )
                Text(
                    text = "Best ${state.bestScore}",
                    color = FuseTheme.colors.sub,
                    modifier = Modifier.testTag(GameScreenTags.BEST),
                )
            }

            Spacer(Modifier.height(16.dp))

            BoardView(
                board = state.board,
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
    }
}

/** Stable test tags so UI tests target nodes without depending on copy. */
object GameScreenTags {
    const val ROOT: String = "game_screen"
    const val SCORE: String = "game_score"
    const val BEST: String = "game_best"
    const val BOARD: String = "game_board"
    const val NEW_GAME: String = "game_new_game"
}

/** Semantic marker placed on the board when the last swipe was blocked. */
const val BLOCKED_DESCRIPTION: String = "Move blocked"
