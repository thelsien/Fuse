package com.fuse.presentation

import com.fuse.engine.Board
import com.fuse.engine.BoardMergeEvent
import com.fuse.engine.Direction
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.MoveOutcome
import com.fuse.engine.Score
import com.fuse.engine.Tile
import com.fuse.engine.newGame
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UIB-3 — the MVI store that binds the pure engine ([GameState.applyMove]/[newGame])
 * to the UI.
 *
 * ## The MVI triangle
 *  - **Intent** ([GameIntent]) — what the UI asks for: a [GameIntent.Move] (driven by
 *    a swipe) or a [GameIntent.NewGame] (restart). Submitted via [accept].
 *  - **State** ([GameUiState]) — an immutable projection of the current [GameState]
 *    plus the last move's events, exposed as a [StateFlow] ([state]) the UI collects.
 *  - **Reduce** — [accept] runs the engine transition and pushes a new [GameUiState].
 *
 * ## Why the reduce is synchronous (no coroutine scope here)
 * The engine ([GameState.applyMove]) is a PURE, in-memory, microsecond-scale function:
 * there is no IO and nothing to suspend on. So [accept] computes the next state inline
 * and writes it to a [MutableStateFlow] — no dispatcher, no scope, no race. This keeps
 * the store trivially testable (no `runTest` needed for reduce, though the tests still
 * collect the `StateFlow`/effects through coroutines) and free of lifecycle concerns.
 * If a later story needs async work (e.g. UIB-6 loading a persisted game off the main
 * thread), a scope can be added then without changing this story's contract.
 *
 * ## How a BLOCKED move is surfaced — flag AND one-shot effect (both, on purpose)
 * The engine guarantees a blocked move mutates nothing (no spawn, no score, no
 * move-count change). The store does NOT replace [GameState] on a blocked move, so the
 * board/score/phase in [state] are untouched — a blocked swipe is a true no-op for
 * rendering. To let UIB-5 / FEL react, "blocked" is surfaced two complementary ways:
 *  - **State flag** [GameUiState.lastMoveBlocked] — `true` after a blocked move, reset
 *    to `false` after the next ACCEPTED move (or a new game). A flag is convenient for
 *    a "blocked" decoration that should track the latest move.
 *  - **One-shot effect** [GameEffect.Blocked] — emitted exactly once per blocked move
 *    on [effects]. A one-shot is the right shape for a transient nudge/shake/haptic
 *    that must fire once and never replay on recomposition or config change.
 * UIB-5/FEL can consume whichever fits; both are wired and documented here.
 *
 * ## Last-outcome fields (for UIB-4 / UIB-5 / FEL, exposed now, unused by this story)
 * After an accepted move, [GameUiState] also carries [lastMerges], [justWon] and
 * [gameOver] straight from the [MoveOutcome], so overlays (UIB-5) and merge animation
 * (FEL) have the data without re-deriving it. UIB-3 itself only needs board/score/phase.
 *
 * ## Determinism / the seed seam
 * One game's RNG + tile-id streams live INSIDE [GameState] (the engine threads them
 * purely), so the store only ever holds the current [GameState]; replaying the same
 * intents from the same seed reproduces the game exactly. A restart draws a seed from
 * [seedSource] — defaulted here to a simple incrementing counter so a fresh game is
 * reproducible in tests and never repeats the previous board. Real entropy/wall-clock
 * time as a seed is a later concern (a platform clock can be injected via [seedSource]).
 *
 * @param initialSeed the seed for the very first game (the board shown on launch).
 * @param initialBest a best score to carry into the first game — the seam for UIB-6 to
 *   inject a persisted best; defaults to 0 (no persistence yet).
 * @param target the win target threaded into every game (default classic 2048).
 * @param seedSource supplies the seed for each restart ([GameIntent.NewGame] with no
 *   explicit seed). Defaults to an incrementing counter starting after [initialSeed].
 */
class GameStore(
    initialSeed: Long = DEFAULT_INITIAL_SEED,
    private val initialBest: Long = 0L,
    private val target: Int = com.fuse.engine.DEFAULT_WIN_TARGET,
    private val seedSource: () -> Long = incrementingSeedSource(initialSeed),
    startState: GameState? = null,
) {
    /** The single source of truth: the current engine snapshot. */
    private var gameState: GameState =
        startState ?: newGame(seed = initialSeed, target = target, best = initialBest)

    private val _state = MutableStateFlow(GameUiState.playing(gameState))

    /** The UI state the screen collects. Always reflects the latest reduce. */
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<GameEffect>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** One-shot effects (e.g. [GameEffect.Blocked]). Hot, non-replaying. */
    val effects: Flow<GameEffect> = _effects.asSharedFlow()

    /** Submits an [intent] and synchronously reduces it into [state]/[effects]. */
    fun accept(intent: GameIntent) {
        when (intent) {
            is GameIntent.Move -> reduceMove(intent.direction)
            is GameIntent.NewGame -> reduceNewGame(intent.seed)
        }
    }

    private fun reduceMove(direction: Direction) {
        val outcome: MoveOutcome = gameState.applyMove(direction)
        if (outcome.accepted) {
            gameState = outcome.state
            _state.value = GameUiState.fromAccepted(gameState, outcome)
        } else {
            // True no-op: keep gameState; only raise the blocked signals.
            _state.value = _state.value.copy(
                lastMoveBlocked = true,
                lastMerges = emptyList(),
            )
            _effects.tryEmit(GameEffect.Blocked)
        }
    }

    private fun reduceNewGame(seed: Long?) {
        val chosenSeed = seed ?: seedSource()
        // A restart preserves the player's best across the session.
        val best = maxOf(initialBest, gameState.score.best)
        gameState = newGame(seed = chosenSeed, target = target, best = best)
        _state.value = GameUiState.playing(gameState)
    }

    companion object {
        /** Seed for the launch board when none is supplied. Deterministic by default. */
        const val DEFAULT_INITIAL_SEED: Long = 0L

        /**
         * Test/preview seam: build a store positioned at an arbitrary [state] (e.g. a
         * board where a specific direction is blocked, or a near-win board), instead of
         * a fresh `newGame`. Restarts ([GameIntent.NewGame] without a seed) still draw
         * from [seedSource].
         */
        fun forState(
            state: GameState,
            seedSource: () -> Long = incrementingSeedSource(DEFAULT_INITIAL_SEED),
        ): GameStore = GameStore(seedSource = seedSource, startState = state)

        /** A restart seed source that never repeats the previous game's board. */
        private fun incrementingSeedSource(start: Long): () -> Long {
            var next = start + 1
            return { next++ }
        }
    }
}

/** UIB-3 — the intents the UI can submit to [GameStore.accept]. */
sealed interface GameIntent {
    /** A swipe in [direction]; drives [GameState.applyMove]. */
    data class Move(val direction: Direction) : GameIntent

    /**
     * Restart the game. [seed] is optional: `null` draws a fresh seed from the
     * store's seed source (so each restart differs); a non-null [seed] forces a
     * specific, reproducible game (used by tests / a future "share this board").
     */
    data class NewGame(val seed: Long? = null) : GameIntent
}

/** UIB-3 — transient one-shot effects emitted by the store (consume once). */
sealed interface GameEffect {
    /** A swipe that didn't change the board. Fire a nudge/shake/haptic exactly once. */
    data object Blocked : GameEffect
}

/**
 * UIB-3 — the immutable UI projection of the game.
 *
 * Holds exactly what the screen needs now (board/score/phase) plus fields the next
 * stories will read (merges, win/lose events, the blocked flag). It is a pure value
 * derived from a [GameState] + the last [MoveOutcome]; the store never mutates it.
 *
 * @property board the board to render ([com.fuse.ui.board.BoardView]).
 * @property score current + best (UIB-4 will surface both fully).
 * @property phase lifecycle phase; UIB-5 overlays read this.
 * @property lastMoveBlocked `true` iff the most recent move was a no-op (blocked).
 *   Reset to `false` on the next accepted move / new game. See the store KDoc.
 * @property lastMerges merge events from the last accepted move (empty otherwise) —
 *   for FEL merge animation.
 * @property justWon one-shot win flag from the last accepted move — for UIB-5.
 * @property gameOver `true` once the game is lost — for UIB-5 and to disable input.
 */
data class GameUiState(
    val board: Board,
    val score: Score,
    val phase: GamePhase,
    val lastMoveBlocked: Boolean = false,
    val lastMerges: List<BoardMergeEvent> = emptyList(),
    val justWon: Boolean = false,
    val gameOver: Boolean = false,
) {
    /** Convenience for the swipe `enabled` flag: input is live unless the game is lost. */
    val isGameOver: Boolean get() = gameOver

    /** Current running score (UIB-4 reads this + [Score.best]). */
    val currentScore: Long get() = score.current

    /** Best score reached this session (seam for UIB-6 persistence). */
    val bestScore: Long get() = score.best

    /** All tiles currently on the board (handy for tests / overlays). */
    fun tiles(): List<Tile> = board.tiles()

    companion object {
        /** A fresh, in-progress projection of [state] (no last-move events). */
        fun playing(state: GameState): GameUiState = GameUiState(
            board = state.board,
            score = state.score,
            phase = state.phase,
        )

        /** Projection after an ACCEPTED move, carrying that move's events. */
        fun fromAccepted(state: GameState, outcome: MoveOutcome): GameUiState = GameUiState(
            board = state.board,
            score = state.score,
            phase = state.phase,
            lastMoveBlocked = false,
            lastMerges = outcome.merges,
            justWon = outcome.justWon,
            gameOver = outcome.gameOver,
        )
    }
}
