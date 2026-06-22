package com.fuse.presentation

import com.fuse.daily.DailyClock
import com.fuse.daily.DailyPuzzle
import com.fuse.daily.dailyDayNumber
import com.fuse.daily.dailyPuzzleFor
import com.fuse.daily.puzzleStep
import com.fuse.data.DailyProgress
import com.fuse.data.DailyRepository
import com.fuse.data.NoOpDailyRepository
import com.fuse.engine.Board
import com.fuse.engine.BoardMergeEvent
import com.fuse.engine.Direction
import com.fuse.engine.TileIdSource
import com.fuse.engine.hasWon
import com.fuse.engine.move
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DLY-4 — the MVI store for the **Daily Challenge** mode: a deterministic, NO-SPAWN
 * puzzle (the same seed-derived start board for everyone each UTC day; reach a
 * seed-derived target tile in the fewest moves; no random spawns; free undo/restart;
 * one-shot until solved).
 *
 * It is a SEPARATE store from [GameStore] on purpose — the rules differ (no spawn, no
 * score, a move counter vs par, a single one-shot run) so overloading the classic store
 * would muddy both. The shape mirrors [GameStore]: a [state] `StateFlow`, an [accept]
 * intent sink, and a one-shot [effects] flow.
 *
 * ## The move-list model (board = replay)
 * The whole run is captured by a [DailyProgress] = today's day number + the ordered list
 * of accepted moves + a solved flag. Because the puzzle is regenerable from the date and
 * every move is replayable via [puzzleStep], the **board is `startBoard` replayed through
 * the moves**, `moveCount = moves.size`, **undo = drop the last move + replay**, and
 * **restart = clear the moves**. This keeps the store's persisted slot tiny and crash-safe
 * and gives undo for free. The store holds the move list as its single mutable state and
 * recomputes the board on every change.
 *
 * ## Lifecycle / the single slot + NEW-DAY RESET (on init)
 * On construction the store resolves TODAY's UTC day from the injected [clock], generates
 * today's [DailyPuzzle], then reads the [repository]'s single slot:
 *  - **Resume** — the slot's `dayNumber == today` → replay its moves (and if it was
 *    `solved`, show the solved/locked run). Re-opening resumes exactly where the player
 *    left off.
 *  - **New-day reset** — the slot's `dayNumber != today` (a new UTC day began) OR there is
 *    no slot → discard it and start today's fresh puzzle, writing a fresh empty slot.
 *
 * ## Move semantics (no spawn; count only changed moves; lock on solve)
 *  - [DailyIntent.Move] applies [puzzleStep] (slide+merge, NO new tile). A BLOCKED
 *    (unchanged) move is a NO-OP — not counted, nothing persisted. A CHANGED move is
 *    appended to the move list (so `moveCount` increments) and written through.
 *  - **Win** is `board.hasWon(target)`. The first changed move that wins flips
 *    [DailyUiState.solved] `true`, records [DailyUiState.winningMoves], LOCKS the run
 *    (further moves ignored), and emits the one-shot [DailyEffect.Solved] signal.
 *  - Once solved the run is one-shot until tomorrow: [DailyIntent.Move]/[DailyIntent.Undo]/
 *    [DailyIntent.Restart] are all ignored.
 *  - [DailyIntent.Undo] drops the last move and replays (no-op when there are no moves or
 *    when solved). [DailyIntent.Restart] clears all moves back to the start board (no-op
 *    when already at the start or when solved).
 *
 * ## The DLY-5 seam (streak)
 * Streak is OUT of scope here, but the win is surfaced as a clean one-shot
 * [DailyEffect.Solved] `(dayNumber, moves)` so DLY-5 can just record the streak by
 * collecting it — this store builds NO streak counters.
 *
 * The reduce is synchronous (like [GameStore]): [puzzleStep] is a pure microsecond
 * function and the `Settings`-backed slot is fast, so [accept] computes the next state
 * inline and writes the slot — no scope, no dispatcher, trivially testable.
 *
 * @param clock the daily clock seam (Koin-bound [com.fuse.daily.SystemDailyClock]); the
 *   ONLY source of "today". Injected so tests can pin a date (and a yesterday-dated slot)
 *   to exercise the new-day reset.
 * @param repository the single daily slot's persistence; defaults to [NoOpDailyRepository]
 *   so tests/previews need no `Settings`.
 * @param puzzle test seam: today's puzzle, defaulting to `dailyPuzzleFor(clock.todayUtc())`.
 *   Tests can inject a tiny known puzzle so the win sequence is short and deterministic.
 */
class DailyStore(
    private val clock: DailyClock,
    private val repository: DailyRepository = NoOpDailyRepository,
    private val puzzle: DailyPuzzle = dailyPuzzleFor(clock.todayUtc()),
) {
    /** The Daily #N for today — the new-day-reset key and the header label. */
    private val dayNumber: Long = dailyDayNumber(clock.todayUtc())

    /**
     * A stable id source for replay so a slide produces consistent tile ids across
     * recomputations (animation continuity in [BoardMergeEvent]); ids never affect the
     * puzzle outcome. Reset before each full replay so a given run is reproducible.
     */
    private var ids = TileIdSource()

    /** The single mutable state: the ordered accepted moves. The board is derived. */
    private var moves: MutableList<Direction> = mutableListOf()

    /** `true` once today's puzzle was solved — the run is then locked until tomorrow. */
    private var solved: Boolean = false

    private val _state: MutableStateFlow<DailyUiState>
    val state: StateFlow<DailyUiState>

    private val _effects = MutableSharedFlow<DailyEffect>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** One-shot effects (e.g. [DailyEffect.Solved]). Hot, non-replaying. */
    val effects: Flow<DailyEffect> = _effects.asSharedFlow()

    init {
        // NEW-DAY RESET / resume on init: read the single slot and decide.
        val slot = repository.load()
        if (slot != null && slot.dayNumber == dayNumber) {
            // Resume today's run: replay its moves and its solved flag.
            moves = slot.moves.toMutableList()
            solved = slot.solved
        } else {
            // No slot OR a stale slot from a previous day → discard and start fresh.
            moves = mutableListOf()
            solved = false
            persist()
        }
        _state = MutableStateFlow(project(lastMerges = emptyList()))
        state = _state.asStateFlow()
    }

    /** Submits an [intent] and synchronously reduces it into [state]/[effects]. */
    fun accept(intent: DailyIntent) {
        when (intent) {
            is DailyIntent.Move -> reduceMove(intent.direction)
            DailyIntent.Undo -> reduceUndo()
            DailyIntent.Restart -> reduceRestart()
        }
    }

    private fun reduceMove(direction: Direction) {
        if (solved) return // locked: one-shot until tomorrow.

        // Apply the no-spawn step to the CURRENT board, minting result ids from `ids` so
        // the merge events drive BoardView's pop animation.
        val current = currentBoard()
        val result = current.move(direction, ids)
        if (!result.changed) return // blocked move: true no-op (not counted, not saved).

        moves.add(direction)
        val won = result.board.hasWon(puzzle.target)
        if (won) solved = true
        persist()

        _state.value = project(lastMerges = result.merges, board = result.board)

        if (won) _effects.tryEmit(DailyEffect.Solved(dayNumber = dayNumber, moves = moves.size))
    }

    private fun reduceUndo() {
        if (solved) return // can't undo a finished run.
        if (moves.isEmpty()) return // nothing to undo.
        moves.removeAt(moves.lastIndex)
        persist()
        _state.value = project(lastMerges = emptyList())
    }

    private fun reduceRestart() {
        if (solved) return // a solved run is locked.
        if (moves.isEmpty()) return // already at the start board.
        moves.clear()
        persist()
        _state.value = project(lastMerges = emptyList())
    }

    /**
     * The board for the current move list: `startBoard` replayed through every accepted
     * move via [puzzleStep]. Mints ids from a FRESH source per replay so the layout is
     * reproducible; the in-place `ids` field is only used for the live last-move merges.
     */
    private fun currentBoard(): Board {
        ids = TileIdSource()
        var board = puzzle.startBoard
        for (d in moves) {
            board = board.puzzleStep(d, ids).board
        }
        return board
    }

    /**
     * Projects the current run into [DailyUiState]. [board] may be supplied (the freshly
     * stepped board from a move, carrying the right merge ids) or recomputed via replay.
     */
    private fun project(
        lastMerges: List<BoardMergeEvent>,
        board: Board = currentBoard(),
    ): DailyUiState = DailyUiState(
        board = board,
        // DLY-7 — the day's SHARED start board (no-spawn, fixed for the day; the same for every
        // player). The share card's emoji grid is built from THIS, not [board] (the player's
        // mid/solved board), so cards are comparable.
        startBoard = puzzle.startBoard,
        moveCount = moves.size,
        target = puzzle.target,
        par = puzzle.par,
        dayNumber = dayNumber,
        solved = solved,
        winningMoves = if (solved) moves.size else null,
        canUndo = !solved && moves.isNotEmpty(),
        canRestart = !solved && moves.isNotEmpty(),
        lastMerges = lastMerges,
    )

    /** Write-through the single slot: today's day number + the move list + solved. */
    private fun persist() {
        repository.save(DailyProgress(dayNumber = dayNumber, moves = moves.toList(), solved = solved))
    }
}

/** DLY-4 — the intents the Daily UI can submit to [DailyStore.accept]. */
sealed interface DailyIntent {
    /** A swipe in [direction]; drives a NO-SPAWN [puzzleStep]. */
    data class Move(val direction: Direction) : DailyIntent

    /** Undo the last accepted move (drop + replay). No-op when no moves / solved. */
    data object Undo : DailyIntent

    /** Restart to the start board (clear all moves). No-op when no moves / solved. */
    data object Restart : DailyIntent
}

/**
 * DLY-4 — transient one-shot effects from the daily store (consume once).
 *
 * The single member is the **DLY-5 seam**: a fire-once Solved signal.
 */
sealed interface DailyEffect {
    /**
     * The player JUST solved today's Daily (one-shot, emitted once on the winning move).
     *
     * Modeled as a one-shot effect (not derived from [DailyUiState.solved]) so DLY-5 can
     * record the streak exactly once without re-firing on recomposition or resume. Carries
     * the [dayNumber] (which day was solved) and [moves] (the player's move count vs
     * [DailyUiState.par]) — everything DLY-5 (streak) and DLY-7 (share card) need.
     *
     * @property dayNumber the Daily #N that was solved.
     * @property moves the number of moves the solve took.
     */
    data class Solved(val dayNumber: Long, val moves: Int) : DailyEffect
}

/**
 * DLY-4 — the immutable UI projection of the daily run.
 *
 * @property board the board to render ([com.fuse.ui.board.BoardView]); `startBoard`
 *   replayed through the accepted moves.
 * @property startBoard the day's SHARED start board (no-spawn ⇒ fixed for the day, identical
 *   for every player). DLY-7's share-card emoji grid is built from this (NOT [board]) so cards
 *   are comparable between players.
 * @property moveCount the number of accepted moves so far (the move counter).
 * @property target the tile value to form to win (shown in the HUD).
 * @property par the optimal move count for today's puzzle (the player's benchmark).
 * @property dayNumber the Daily #N (header label / share key).
 * @property solved `true` once a tile reached [target]; the run is then locked (one-shot).
 * @property winningMoves the move count of the solve once [solved], else `null`.
 * @property canUndo `true` iff Undo is available (has moves AND not solved).
 * @property canRestart `true` iff Restart is available (has moves AND not solved).
 * @property lastMerges merge events of the last accepted move (for the BoardView pop);
 *   empty after undo/restart/resume.
 */
data class DailyUiState(
    val board: Board,
    val startBoard: Board = board,
    val moveCount: Int,
    val target: Int,
    val par: Int,
    val dayNumber: Long,
    val solved: Boolean,
    val winningMoves: Int?,
    val canUndo: Boolean,
    val canRestart: Boolean,
    val lastMerges: List<BoardMergeEvent> = emptyList(),
)
