package com.fuse.presentation

import com.fuse.analytics.AnalyticsLogger
import com.fuse.analytics.NoOpAnalyticsLogger
import com.fuse.analytics.logGameOver
import com.fuse.analytics.logGameStart
import com.fuse.data.GameRepository
import com.fuse.data.NoOpGameRepository
import com.fuse.engine.Board
import com.fuse.engine.BoardMergeEvent
import com.fuse.engine.Direction
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.MoveOutcome
import com.fuse.engine.Position
import com.fuse.engine.Score
import com.fuse.engine.Tile
import com.fuse.engine.isResumable
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
 * ## UIB-6 — persist & resume
 * The store is the one place that touches persistence:
 *  - **On init** it asks the injected [repository] for a saved best (used as the floor
 *    for the score) and any saved in-progress [GameState]. If a saved game exists it is
 *    RESUMED verbatim (board, tile ids, score, phase, rng/id state) so play continues
 *    deterministically as if the app never closed; otherwise a fresh [newGame] starts.
 *  - **After every accepted [GameIntent.Move] and every [GameIntent.NewGame]** it writes
 *    the current [GameState] and best back through [repository]. Writes are synchronous
 *    to match the synchronous reduce (the `Settings` store is fast); a blocked move
 *    changes nothing, so it need not persist (the prior blob still describes the game).
 *
 * The default [repository] is [NoOpGameRepository] so existing tests / previews (and
 * [forState]) construct without a real `Settings`: it loads nothing and saves nothing.
 *
 * @param initialSeed the seed for the very first game (when nothing is persisted).
 * @param initialBest a best score floor carried into the first game; merged with the
 *   persisted best from [repository] (the persisted best wins when higher).
 * @param target the win target threaded into every game (default classic 2048).
 * @param seedSource supplies the seed for each restart ([GameIntent.NewGame] with no
 *   explicit seed). Defaults to an incrementing counter starting after [initialSeed].
 * @param repository UIB-6 local persistence; defaults to a no-op so tests need no
 *   `Settings`. The Koin-built store injects the real [com.fuse.data.GameRepository].
 */
class GameStore(
    initialSeed: Long = DEFAULT_INITIAL_SEED,
    initialBest: Long = 0L,
    private val target: Int = com.fuse.engine.DEFAULT_WIN_TARGET,
    private val seedSource: () -> Long = incrementingSeedSource(initialSeed),
    startState: GameState? = null,
    private val repository: GameRepository = NoOpGameRepository,
    /**
     * ANL-2 — analytics seam. The store fires `game_start` on a fresh/restarted game and `game_over`
     * (mode/score/best_tile/moves) on the move that loses the game, via the typed taxonomy helpers.
     * Logging is a pure side effect at the same point the equivalent state transition happens, so the
     * reduce stays synchronous. Defaults to [NoOpAnalyticsLogger] so existing tests/previews construct
     * unchanged; the Koin singleton injects the real (Debug, later Firebase) logger. Per-move/merge
     * events are intentionally NOT logged (see the taxonomy volume note); only these aggregates are.
     */
    private val analytics: AnalyticsLogger = NoOpAnalyticsLogger,
) {
    /**
     * The best score to carry into a fresh/restarted game: the higher of the
     * constructor [initialBest] and the best persisted by [repository]. Resolved once
     * at init (before any save) so a relaunch over the same store seeds the prior best.
     */
    private val initialBest: Long = maxOf(initialBest, repository.loadBest())

    /**
     * The single source of truth: the current engine snapshot.
     *
     * Resolution order on init: an explicit [startState] (test/preview seam) wins; else
     * a persisted in-progress game is RESUMED (UIB-6); else a fresh [newGame]. A resumed
     * game keeps its own embedded best, but is floored to [initialBest] so a higher
     * persisted/seeded best is never lost.
     */
    private var gameState: GameState =
        startState
            ?: repository.loadGame()?.flooredBest(this.initialBest)
            ?: newGame(seed = initialSeed, target = target, best = this.initialBest)

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

    init {
        // UIB-6: persist the starting state so a fresh launch is resumable even before
        // the first move, and so a freshly-floored best is written through immediately.
        persist()
    }

    /** Submits an [intent] and synchronously reduces it into [state]/[effects]. */
    fun accept(intent: GameIntent) {
        when (intent) {
            is GameIntent.Move -> reduceMove(intent.direction)
            is GameIntent.NewGame -> reduceNewGame(intent.seed)
            is GameIntent.Revive -> reduceRevive()
        }
    }

    /**
     * ADS-2 — revive the current game (game-over → playable) by freeing space, ONLY when the
     * game [GameState.canRevive] (terminal AND not already revived this game). The ad orchestration
     * lives in the UI ([com.fuse.ui.game.GameScreen]); this reduce is the pure, synchronous grant
     * the UI calls AFTER a verified rewarded completion. Anything else is a no-op (no state change,
     * no persist), so a stray/duplicate Revive can never loop the game. On success it sets the
     * pure-engine [GameState.revive] state (phase Playing, board freed, flag set), persists it
     * write-through, and projects a fresh playing [GameUiState] (`gameOver = false`, `canRevive = false`).
     */
    private fun reduceRevive() {
        if (!gameState.canRevive) return
        gameState = gameState.revive()
        _state.value = GameUiState.playing(gameState)
        persist()
    }

    private fun reduceMove(direction: Direction) {
        val outcome: MoveOutcome = gameState.applyMove(direction)
        if (outcome.accepted) {
            gameState = outcome.state
            _state.value = GameUiState.fromAccepted(gameState, outcome)
            // UIB-6: persist the new in-progress game + best after every accepted move,
            // so a kill-and-relaunch resumes the exact same board/score/rng state.
            persist()
            // FEL-4 — one-shot per-move feedback signal. A move's merge results +
            // justWon are carried on [GameUiState] too, but a one-shot effect is the
            // right shape to DRIVE a fire-once haptic: it fires exactly once per accepted
            // move and never replays on recomposition (unlike collecting state late). The
            // pure [com.fuse.feedback.HapticsCoordinator] decides tick vs thunk from this.
            _effects.tryEmit(
                GameEffect.Moved(
                    mergedValues = outcome.merges.map { it.resultingValue },
                    justWon = outcome.justWon,
                ),
            )
            // One-shot win event (UIB-5): fire exactly once on the move that first
            // reaches the target, alongside the persistent Won phase. See KDoc on
            // [GameEffect.Won] for why this is an effect, not derived from state.
            if (outcome.justWon) _effects.tryEmit(GameEffect.Won)
            // ANL-2 — log `game_over` exactly once, on the accepted move that LOST the game
            // (the Lost transition). Aggregate outcome only: final score, highest tile reached
            // (max over the board), and the game's move count. No PII; no per-move events.
            if (outcome.gameOver) {
                analytics.logGameOver(
                    score = gameState.score.current.toInt(),
                    bestTile = gameState.board.tiles().maxOfOrNull { it.value } ?: 0,
                    moves = gameState.moveCount,
                )
            }
        } else {
            // True no-op: keep gameState; only raise the blocked signals.
            _state.value = _state.value.copy(
                lastMoveBlocked = true,
                lastMerges = emptyList(),
                // FEL-3 — a blocked move spawns nothing; clear any prior spawn so the
                // renderer doesn't re-trigger an entrance for a tile that already settled.
                spawned = null,
                spawnPosition = null,
            )
            _effects.tryEmit(GameEffect.Blocked)
        }
    }

    private fun reduceNewGame(seed: Long?) {
        val chosenSeed = seed ?: seedSource()
        // A restart preserves the player's best across the session (and across launches).
        val best = maxOf(initialBest, gameState.score.best)
        gameState = newGame(seed = chosenSeed, target = target, best = best)
        _state.value = GameUiState.playing(gameState)
        // UIB-6: overwrite the saved game with the fresh board and persist the best.
        persist()
        // ANL-2 — a new Classic game began. Logged on the explicit NewGame intent (a player-started
        // game), NOT on construction/resume (relaunching into a saved game is not a new game).
        analytics.logGameStart()
    }

    /**
     * UIB-6 — write the current [gameState] (the whole in-progress game) and its best
     * back through [repository]. Best is saved separately so it survives even when the
     * game blob is later cleared/overwritten by a brand-new game.
     */
    private fun persist() {
        repository.saveGame(gameState)
        repository.saveBest(gameState.score.best)
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
            repository: GameRepository = NoOpGameRepository,
        ): GameStore = GameStore(
            seedSource = seedSource,
            startState = state,
            repository = repository,
        )

        /** A restart seed source that never repeats the previous game's board. */
        private fun incrementingSeedSource(start: Long): () -> Long {
            var next = start + 1
            return { next++ }
        }

        /**
         * UIB-6 — returns this state with its [Score.best] raised to at least [floor]
         * (and never lowered). Used when resuming a saved game so a higher persisted /
         * seeded best is preserved; if the floor is already covered the state is
         * returned unchanged (identity), so a plain resume is byte-for-byte the saved
         * game and determinism is untouched.
         */
        private fun GameState.flooredBest(floor: Long): GameState =
            if (score.best >= floor) this
            else copy(score = score.copy(best = maxOf(score.best, floor)))
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

    /**
     * ADS-2 — revive a lost game (continue playing) by freeing board space. Applied ONLY when the
     * current game is game-over AND has not already been revived ([GameState.canRevive]); otherwise
     * a no-op. The UI submits this AFTER a verified rewarded-ad completion (the ad flow lives in
     * [com.fuse.ui.game.GameScreen]); the store itself stays pure/synchronous and grants nothing
     * without this intent.
     */
    data object Revive : GameIntent
}

/** UIB-3 — transient one-shot effects emitted by the store (consume once). */
sealed interface GameEffect {
    /** A swipe that didn't change the board. Fire a nudge/shake/haptic exactly once. */
    data object Blocked : GameEffect

    /**
     * FEL-4 — an ACCEPTED move just reduced (one-shot, fired once per accepted move).
     *
     * Carries exactly the signals the haptic decision needs, so a collector can drive
     * feedback without re-reading state:
     *  - [mergedValues] — the `resultingValue` of each merge this move (empty for a pure
     *    slide). A non-empty list ⇒ a "tick"; a value in the milestone set ⇒ a "thunk".
     *  - [justWon] — `true` iff this move first reached the win target (also ⇒ "thunk").
     *
     * Modeled as a one-shot effect (not derived from [GameUiState.lastMerges]) so the
     * haptic fires once on the move and never re-fires on a later recomposition or a late
     * state collection. Sound (FEL-5) can ride the very same effect.
     *
     * @property mergedValues per-merge result values produced by this move.
     * @property justWon whether this move first reached the win target.
     */
    data class Moved(
        val mergedValues: List<Int>,
        val justWon: Boolean,
    ) : GameEffect

    /**
     * UIB-5 — the player JUST reached the win target (one-shot win event).
     *
     * Emitted exactly once, on the accepted move whose [MoveOutcome.justWon] is `true`
     * (the first move to reach 2048). It is modeled as a one-shot effect rather than
     * derived from the persistent [GamePhase.Won] / [GameUiState.justWon] flag so the
     * win celebration shows ONCE and never re-shows on the subsequent moves the player
     * makes after choosing "Keep going" — even though the `Won` condition (and the
     * `justWon` flag value from that move) persist in state. A non-replaying
     * [kotlinx.coroutines.flow.SharedFlow] is the correct shape for a fire-once event:
     * it cannot be re-triggered by recomposition or by collecting state late.
     */
    data object Won : GameEffect
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
 * @property spawned the tile that appeared on the last accepted move (FEL-3 entrance),
 *   or `null` on a blocked move / new game / resume (where the "appearance" of tiles is
 *   the whole board, not a single post-move spawn). Its [Tile.id] targets the entrance.
 * @property spawnPosition where [spawned] landed, or `null` when nothing spawned.
 * @property canResume SHL-4 — `true` iff the game the store currently holds is a
 *   resumable, in-progress game (see [com.fuse.engine.isResumable]: not lost,
 *   `moveCount > 0`). Drives Home's **Continue + New game** choice: the store resumes a
 *   save on init, so this projects whether that resumed game is worth offering as a
 *   Continue. It is recomputed on every reduce, so it stays reactive — `true` after
 *   resuming a real save, `false` after a [GameIntent.NewGame] (fresh board, moveCount 0)
 *   or once a move loses the game (phase Lost).
 * @property canRevive ADS-2 — `true` iff the current game is game-over AND has not yet been
 *   revived ([GameState.canRevive]). Drives the optional "Continue — Watch Ad" action in the
 *   lose overlay: shown only while `true`, hidden after the one allowed revive (a revived game
 *   is back to Playing, so this is `false` for the rest of that game).
 */
data class GameUiState(
    val board: Board,
    val score: Score,
    val phase: GamePhase,
    val lastMoveBlocked: Boolean = false,
    val lastMerges: List<BoardMergeEvent> = emptyList(),
    val justWon: Boolean = false,
    val gameOver: Boolean = false,
    val spawned: Tile? = null,
    val spawnPosition: Position? = null,
    val canResume: Boolean = false,
    val canRevive: Boolean = false,
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
        /**
         * A projection of [state] with no last-move events. Used for the initial board
         * and after a new game. [gameOver] is derived from the phase rather than hardcoded
         * to `false`, so a store positioned directly at a terminal [GamePhase.Lost] (via
         * [forState], or a future UIB-6 resume of a finished game) correctly reports
         * game-over to the overlay/swipe-enable logic.
         */
        fun playing(state: GameState): GameUiState = GameUiState(
            board = state.board,
            score = state.score,
            phase = state.phase,
            gameOver = state.phase.isLost,
            // SHL-4 — project resumability of the very state we're rendering. For a fresh
            // newGame (moveCount 0) this is false; for a resumed save (moveCount > 0) true.
            canResume = isResumable(state),
            // ADS-2 — true at game-over before the one allowed revive; false otherwise (incl.
            // a fresh/playing game, or a game-over already revived).
            canRevive = state.canRevive,
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
            // FEL-3 — carry the spawned tile + where it landed so the renderer can target
            // its entrance precisely (the engine already computed this on the outcome).
            spawned = outcome.spawned,
            spawnPosition = outcome.spawnPosition,
            // SHL-4 — after the first accepted move (moveCount ≥ 1) the game becomes
            // resumable; on the move that loses the game (phase Lost) it flips back to false.
            canResume = isResumable(state),
            // ADS-2 — the move that loses the game (phase Lost, not yet revived) makes the
            // revive offer available; on any non-lost move it is false.
            canRevive = state.canRevive,
        )
    }
}
