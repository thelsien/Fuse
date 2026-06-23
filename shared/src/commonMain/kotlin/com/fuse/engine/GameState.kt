package com.fuse.engine

import kotlinx.serialization.Serializable

/**
 * The lifecycle phase of a game (ENG-9): Playing -> Won -> Lost.
 *
 *  - [Playing]: the normal in-progress state. No win tile yet, board not stuck.
 *  - [Won]: a tile has reached the win target at least once. This is a PERSISTENT
 *    condition (it stays `Won` even as the player keeps merging past 2048). The
 *    one-shot "you just won!" event is NOT this phase — it is the `justWon` flag on
 *    [MoveOutcome], which fires exactly once on the move that first reaches the
 *    target. [canContinue] reflects whether play may keep going after winning; in
 *    classic 2048 the player chooses to continue, so it is always `true` here and
 *    further [applyMove] calls keep applying. A `Won` game becomes [Lost] if it is
 *    later played into a stuck board.
 *  - [Lost]: terminal. The board is full and no move would change it. Once `Lost`,
 *    [applyMove] is a no-op (the game cannot be un-lost).
 *
 * `Won` carries [canContinue] as data so a future "challenge mode" (win = stop) can
 * set it `false` without changing the phase model; the engine itself never blocks a
 * continuable win.
 */
@Serializable
sealed interface GamePhase {
    /** Game in progress: no win tile yet, at least one move possible. */
    @Serializable
    data object Playing : GamePhase

    /**
     * The win target has been reached (persists past it). [canContinue] is `true`
     * in classic mode: the player may keep merging. The won *event* (one-shot) is
     * surfaced separately via [MoveOutcome.justWon].
     */
    @Serializable
    data class Won(val canContinue: Boolean = true) : GamePhase

    /** Terminal: board full and no move changes it. [applyMove] becomes a no-op. */
    @Serializable
    data object Lost : GamePhase

    /** True for [Won] in any state (one place to ask "has the target been reached?"). */
    val isWon: Boolean get() = this is Won

    /** True for the terminal [Lost] phase. */
    val isLost: Boolean get() = this is Lost
}

/**
 * A complete, serializable snapshot of a single game (ENG-9).
 *
 * This captures EVERYTHING needed to (a) render the current game and (b) continue
 * it deterministically, so a game can be persisted mid-run (Sprint 2) and a result
 * can be validated by replay (later anti-cheat). Two `GameState`s that are
 * structurally equal represent the exact same game at the exact same point.
 *
 * ## Determinism / why state is stored as plain values
 * The two stateful collaborators the engine threads through a turn — the [Rng] and
 * the [TileIdSource] — are stored here as their raw serializable state:
 *  - [rngState] is the [SeededRng.state] field; [applyMove]/[newGame] rebuild a
 *    generator with [SeededRng.fromState] (ENG-2), draw from it, and write the
 *    generator's advanced [SeededRng.state] back into the new snapshot.
 *  - [nextTileId] is the value [TileIdSource.peek] would return next; a transition
 *    builds `TileIdSource(nextTileId)`, mints ids through the move + spawn, and
 *    writes `idSource.peek()` back into the new snapshot.
 *
 * Storing them as values (not as the mutable collaborator objects) is what keeps
 * [applyMove] a PURE function of `(GameState, Direction)`: each transition rebuilds
 * fresh collaborators from the persisted state, so applying the same move to the
 * same state always yields the same result, with no shared mutable aliasing.
 *
 * ## The win latch
 * [phase] holds the persistent Won/Lost condition. The one-shot "just won" event is
 * derived per-move by comparing the phase before and after the move (see
 * [applyMove]); it is never stored, because it is an event, not state. `Won` persists
 * as the player merges past the target.
 *
 * @property board the current board.
 * @property score the running + best score ([Score]).
 * @property phase the lifecycle phase ([GamePhase]).
 * @property rngState the serialized [SeededRng] state for the next spawn draw.
 * @property nextTileId the id the next minted tile will receive ([TileIdSource] state).
 * @property moveCount number of ACCEPTED moves played (no-op moves never increment).
 * @property target the win target (default 2048); stored so a Daily Challenge with a
 *   different goal replays identically.
 * @property revivedThisGame ADS-2 — `true` once this game has been revived (see [revive]).
 *   A game may be revived AT MOST ONCE (prevents an infinite ad-revive loop); a second
 *   [revive] is a no-op. [newGame] starts it `false`. Defaulted so OLD persisted blobs
 *   that pre-date this field still decode (they decode to `false` = never revived).
 */
@Serializable
data class GameState(
    val board: Board,
    val score: Score,
    val phase: GamePhase,
    val rngState: Long,
    val nextTileId: Long,
    val moveCount: Int,
    val target: Int = DEFAULT_WIN_TARGET,
    val revivedThisGame: Boolean = false,
) {
    /** True iff the win target has been reached (persistent condition). */
    val hasWon: Boolean get() = phase.isWon

    /** True iff the game is over (terminal [GamePhase.Lost]). */
    val isGameOver: Boolean get() = phase.isLost

    /** Rebuilds a generator positioned at this snapshot's [rngState]. */
    private fun rng(): SeededRng = SeededRng.fromState(rngState)

    /** Rebuilds an id source positioned at this snapshot's [nextTileId]. */
    private fun idSource(): TileIdSource = TileIdSource(nextTileId)

    /**
     * Applies a swipe in [direction] as a PURE transition: returns a NEW [GameState]
     * (this one is never mutated) wrapped in a [MoveOutcome] describing what happened.
     *
     * Algorithm (deterministic; mirrors ENG-5/6/7/8):
     *  1. If the game is already [GamePhase.Lost], do nothing: return this state and a
     *     blocked outcome (a terminal game cannot move).
     *  2. Rebuild a [TileIdSource] at [nextTileId] and a [SeededRng] at [rngState].
     *  3. `board.move(direction, idSource)` (ENG-5). If `!changed` the move is INVALID:
     *     return this state unchanged and a blocked outcome — NO spawn, NO score, NO
     *     move-count increment, NO rng/id advance, phase unchanged. (We discard the
     *     rebuilt collaborators, so the persisted state is untouched.)
     *  4. The move changed the board: spawn exactly one tile (ENG-6) from the SAME rng
     *     + idSource, so spawn/merge ids never collide and the rng stream stays in sync.
     *  5. Add the move's `scoreDelta()` (ENG-7), increment the move count, and read back
     *     the advanced rng/id state.
     *  6. Evaluate WIN then LOSE on the post-spawn board (ENG-8): the first move that
     *     reaches the target sets [GamePhase.Won] and surfaces `justWon = true`
     *     (one-shot — derived from the phase having been non-Won before). If, after the
     *     spawn, the board is [Board.isGameOver], the phase becomes [GamePhase.Lost]
     *     (terminal; lose takes precedence over a continue-able win).
     *  7. Return the new snapshot + a [MoveOutcome] carrying merges, spawn, score delta,
     *     and the justWon / gameOver flags.
     */
    fun applyMove(direction: Direction): MoveOutcome {
        // A terminal game cannot move.
        if (phase.isLost) return MoveOutcome.blocked(this)

        val idSource = idSource()
        val move = board.move(direction, idSource)

        // Invalid move: a true no-op. Nothing advances; persisted state is untouched
        // because the rebuilt idSource/rng we just made are simply discarded.
        if (!move.changed) return MoveOutcome.blocked(this)

        // Accepted move: spawn one tile from the SAME id source + rng.
        val rng = rng()
        val spawn = move.board.spawnTile(rng, idSource)

        val newScore = score.add(move.scoreDelta())
        val newMoveCount = moveCount + 1

        // Win-then-lose evaluation on the post-spawn board.
        val wonNow = spawn.board.hasWon(target)
        val justWon = wonNow && !phase.isWon
        val lost = spawn.board.isGameOver()

        val newPhase: GamePhase = when {
            lost -> GamePhase.Lost
            wonNow -> GamePhase.Won(canContinue = true)
            else -> GamePhase.Playing
        }

        val newState = GameState(
            board = spawn.board,
            score = newScore,
            phase = newPhase,
            rngState = rng.state,
            nextTileId = idSource.peek(),
            moveCount = newMoveCount,
            target = target,
            // ADS-2 — carry the once-per-game revive latch forward across moves: a game
            // that was revived stays "already revived" no matter how it is played on.
            revivedThisGame = revivedThisGame,
        )

        return MoveOutcome(
            state = newState,
            accepted = true,
            merges = move.merges,
            spawned = spawn.spawned,
            spawnPosition = spawn.position,
            scoreDelta = move.scoreDelta(),
            justWon = justWon,
            gameOver = lost,
        )
    }

    /**
     * ADS-2 — whether this game may be revived right now: it must be terminal
     * ([GamePhase.Lost]) AND not already revived ([revivedThisGame] `false`). The UI uses
     * this to show/hide the optional "Continue — Watch Ad" action and the store guards
     * [revive] on it.
     */
    val canRevive: Boolean get() = phase.isLost && !revivedThisGame

    /**
     * ADS-2 — REVIVE a lost game by FREEING SPACE so play can resume. PURE: returns a new
     * [GameState]; `this` is never mutated.
     *
     * ## When it does nothing (rejected → returns `this` unchanged)
     * Revive only applies to a game that [canRevive]: it must be [GamePhase.Lost] AND not
     * already revived. On a non-lost game, or a second revive, this is a no-op (returns the
     * receiver), which is what prevents an infinite ad-revive loop.
     *
     * ## The space-freeing rule (deterministic, documented)
     * The board is freed by removing the LOWEST-valued tiles until at least
     * `ceil(cellCount / 3)` cells are empty (on a classic 4×4 that is `ceil(16/3) = 6`
     * free cells). Tiles are removed in ascending order of `(value, row-major position)`:
     * lowest value first, and among equal values the earliest row-major cell first — a
     * fully deterministic, stable tie-break (see [Board.clearLowestUntilFree]). A
     * board that already has enough free cells is left untouched (only the phase/flag
     * change). The player's big tiles are preserved as far as possible; only the cheapest
     * tiles are sacrificed.
     *
     * ## What is preserved
     * `score`, `moveCount`, `target`, and the rng/id state (`rngState`, `nextTileId`) all
     * carry over unchanged, so the game continues deterministically from where it stood.
     * [phase] is set back to [GamePhase.Playing] and [revivedThisGame] to `true`.
     */
    fun revive(): GameState {
        if (!canRevive) return this
        return copy(
            board = board.clearLowestUntilFree(reviveTargetEmptyCells(board)),
            phase = GamePhase.Playing,
            revivedThisGame = true,
        )
    }

    companion object
}

/**
 * ADS-2 — the documented revive target: the number of cells revive frees up, `ceil(cellCount / 3)`
 * (6 on a classic 4×4). Pulled out so the rule lives in one named place.
 */
internal fun reviveTargetEmptyCells(board: Board): Int = (board.cellCount + 2) / 3

/**
 * The result of a single [GameState.applyMove] (ENG-9): the resulting [state] plus a
 * description of what happened during the turn, for the UI/store to render.
 *
 * On an INVALID move ([accepted] == `false`) this is a "blocked" outcome: [state] is
 * the unchanged input state, [merges] is empty, nothing spawned, [scoreDelta] is 0,
 * and both event flags are `false`. The UI can use `!accepted` to give blocked
 * feedback (a shake/buzz) without changing anything.
 *
 * On an ACCEPTED move the fields describe the turn for animation, haptics, and HUD:
 *  - [merges]: the per-merge events (ENG-5 [BoardMergeEvent]s) to animate two tiles
 *    fusing and to drive merge haptics; their values also sum to [scoreDelta].
 *  - [spawned] / [spawnPosition]: the tile that appeared and where (ENG-6), to animate
 *    the new-tile pop. Always non-null on an accepted move (a changed move always has
 *    a free cell to spawn into).
 *  - [scoreDelta]: points gained this turn (ENG-7), for a score-increment animation.
 *  - [justWon]: a ONE-SHOT win event — `true` only on the move that first reaches the
 *    target, so the UI shows the "You win!" overlay exactly once even though the
 *    [GamePhase.Won] condition persists.
 *  - [gameOver]: `true` on the move that loses the game (phase is now [GamePhase.Lost]).
 *
 * @property state the resulting game state (the unchanged input state when blocked).
 * @property accepted `true` iff the move changed the board and was applied.
 * @property merges the merge events for this turn (empty when blocked).
 * @property spawned the tile spawned this turn, or `null` when blocked.
 * @property spawnPosition where [spawned] landed, or `null` when blocked.
 * @property scoreDelta points gained this turn (0 when blocked).
 * @property justWon one-shot: `true` only on the first move to reach the target.
 * @property gameOver `true` on the move that transitions the game to [GamePhase.Lost].
 */
@Serializable
data class MoveOutcome(
    val state: GameState,
    val accepted: Boolean,
    val merges: List<BoardMergeEvent>,
    val spawned: Tile?,
    val spawnPosition: Position?,
    val scoreDelta: Long,
    val justWon: Boolean,
    val gameOver: Boolean,
) {
    /** True iff the move did nothing (an invalid/no-op swipe). Inverse of [accepted]. */
    val blocked: Boolean get() = !accepted

    companion object {
        /**
         * Builds the "nothing happened" outcome for an invalid move: the unchanged
         * [state] and all event fields empty/false.
         */
        fun blocked(state: GameState): MoveOutcome = MoveOutcome(
            state = state,
            accepted = false,
            merges = emptyList(),
            spawned = null,
            spawnPosition = null,
            scoreDelta = 0L,
            justWon = false,
            gameOver = false,
        )
    }
}

/**
 * Starts a fresh, deterministic game from [seed] (ENG-9).
 *
 * The entire game is derived from [seed]: the spawn RNG and the tile-id source are
 * both seeded from it, so the same [seed] always produces the same opening position
 * (including the [startTiles] starting tiles) and the same continuation under the
 * same moves. This is what makes a Daily Challenge / replay possible.
 *
 * ## Exact start procedure (part of the deterministic, replayable sequence)
 * 1. Begin with an empty board, a [SeededRng] seeded with [seed], and a
 *    [TileIdSource] starting at `1`.
 * 2. Spawn [startTiles] tiles (default 2) by calling [Board.spawnTile] that many
 *    times in a row, threading the SAME rng + id source through every spawn. Each
 *    spawn consumes one `nextInt` (cell pick) and one `nextDouble` (2-vs-4 roll) from
 *    the rng and one id from the id source, in that fixed order. (With 2 starting
 *    tiles on a 4x4 board there is always room for both.)
 * 3. The resulting rng state and next id are persisted into the returned [GameState];
 *    the very first [applyMove] continues the exact same rng/id streams.
 *
 * Replaying this same procedure (same [seed], [target], [startTiles]) reproduces the
 * identical opening — see [replay].
 *
 * @param seed the game seed; any [Long] is valid (used for the rng AND, implicitly,
 *   the whole deterministic sequence).
 * @param target the win target (default 2048).
 * @param best a starting best score to carry in (e.g. a persisted best); default 0.
 * @param startTiles how many tiles to place at the start (classic 2048 = 2).
 * @return a [GameState] in [GamePhase.Playing] with [startTiles] tiles placed.
 */
fun newGame(
    seed: Long,
    target: Int = DEFAULT_WIN_TARGET,
    best: Long = 0L,
    startTiles: Int = DEFAULT_START_TILES,
): GameState {
    require(startTiles >= 0) { "startTiles must be non-negative, was $startTiles" }

    val rng = SeededRng(seed)
    val idSource = TileIdSource()
    var board = Board.empty()

    repeat(startTiles) {
        val spawn = board.spawnTile(rng, idSource)
        board = spawn.board
    }

    return GameState(
        board = board,
        score = Score(current = 0L, best = best),
        phase = GamePhase.Playing,
        rngState = rng.state,
        nextTileId = idSource.peek(),
        moveCount = 0,
        target = target,
    )
}

/**
 * Replays a game from [seed] + a [moves] list (ENG-9 — the core replayability AC).
 *
 * Starts from `newGame(seed, target, best, startTiles)` and folds [GameState.applyMove]
 * over [moves]. Because both the rng and the id source are derived purely from the
 * persisted state (and [applyMove] is a pure transition), the resulting [GameState] is
 * byte-identical to having played the same [moves] live with the same [seed] — same
 * board, score, rng state, id state, move count, and phase. Invalid moves in [moves]
 * are no-ops (as in live play), so a recorded "live" move list replays exactly.
 *
 * This is what lets a Daily Challenge be defined by (seed + target) and a finished
 * game be validated later by replaying its recorded move list and comparing the
 * outcome.
 *
 * @return the final [GameState] after applying every move in [moves].
 */
fun replay(
    seed: Long,
    moves: List<Direction>,
    target: Int = DEFAULT_WIN_TARGET,
    best: Long = 0L,
    startTiles: Int = DEFAULT_START_TILES,
): GameState =
    moves.fold(newGame(seed, target, best, startTiles)) { state, direction ->
        state.applyMove(direction).state
    }

/** Classic 2048 number of tiles placed at the start of a game. */
const val DEFAULT_START_TILES: Int = 2
