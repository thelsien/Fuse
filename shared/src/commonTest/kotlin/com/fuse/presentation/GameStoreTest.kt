package com.fuse.presentation

import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.Score
import com.fuse.engine.newGame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UIB-3 — store-level tests for [GameStore]: the MVI binding between swipe intents
 * and the pure engine. All logic is pure/JVM+iOS, so these live in commonTest and run
 * on every target; `runTest` + `kotlinx-coroutines-test` drive the StateFlow/effects.
 */
class GameStoreTest {

    @Test
    fun initialStateComesFromNewGame() {
        val store = GameStore(initialSeed = 42L)
        val expected = newGame(seed = 42L)

        val s = store.state.value
        assertEquals(expected.board, s.board, "initial board is newGame(seed)")
        assertEquals(2, s.board.tiles().size, "classic 2048 starts with 2 tiles")
        assertEquals(GamePhase.Playing, s.phase)
        assertEquals(0L, s.currentScore)
        assertFalse(s.lastMoveBlocked, "fresh game is not 'blocked'")
        assertFalse(s.isGameOver)
    }

    @Test
    fun sameSeedProducesSameInitialBoard() {
        val a = GameStore(initialSeed = 7L).state.value.board
        val b = GameStore(initialSeed = 7L).state.value.board
        assertEquals(a, b, "determinism: same seed -> identical opening board")
    }

    @Test
    fun acceptedMoveUpdatesState() {
        // Construct a state where LEFT is a productive merge: a single row of [2,2].
        val store = GameStore.forState(twoTwoRowState())
        val before = store.state.value

        store.accept(GameIntent.Move(Direction.LEFT))
        val after = store.state.value

        assertTrue(after.board != before.board, "accepted move changes the board")
        assertTrue(after.currentScore > before.currentScore, "merge scored")
        assertEquals(4L, after.currentScore, "2+2 merge -> +4")
        assertFalse(after.lastMoveBlocked, "an accepted move is not blocked")
    }

    @Test
    fun acceptedMoveProjectsSpawnedTileOntoState() {
        // FEL-3 — an accepted move always spawns one tile; the store must surface it (id +
        // position) so the renderer can target the spawn entrance. The spawned tile is the
        // ONE tile on the new board that is not the merge result.
        val store = GameStore.forState(twoTwoRowState())
        store.accept(GameIntent.Move(Direction.LEFT))
        val after = store.state.value

        val spawned = after.spawned
        val pos = after.spawnPosition
        assertTrue(spawned != null, "an accepted move carries the spawned tile")
        assertTrue(pos != null, "an accepted move carries the spawn position")
        // It is genuinely on the board and is not the merge result (the merged 4 has a
        // distinct id surfaced in lastMerges).
        assertTrue(after.board.tiles().any { it.id == spawned!!.id }, "spawn is on the board")
        val mergeResultIds = after.lastMerges.map { it.resultId }.toSet()
        assertFalse(spawned!!.id in mergeResultIds, "the spawn id is never a merge result id")
    }

    @Test
    fun blockedMoveCarriesNoSpawn() {
        // First produce a spawn, then a blocked move must clear it (no stale entrance).
        val store = GameStore.forState(twoTwoRowState())
        store.accept(GameIntent.Move(Direction.LEFT))
        assertTrue(store.state.value.spawned != null, "precondition: a spawn occurred")

        // UP on the now single-row-ish board: force a blocked no-op.
        val blocked = GameStore.forState(blockedLeftState())
        blocked.accept(GameIntent.Move(Direction.LEFT))
        val after = blocked.state.value
        assertTrue(after.lastMoveBlocked, "precondition: the move was blocked")
        assertEquals(null, after.spawned, "a blocked move spawns nothing")
        assertEquals(null, after.spawnPosition, "a blocked move has no spawn position")
    }

    @Test
    fun newGameAndInitialStateCarryNoSpawn() {
        // FEL-3 — the initial board and a fresh game are a whole-board appearance, not a
        // single post-move spawn, so no per-tile entrance should be targeted.
        val store = GameStore(initialSeed = 42L)
        assertEquals(null, store.state.value.spawned, "initial state has no spawn")
        assertEquals(null, store.state.value.spawnPosition)

        store.accept(GameIntent.NewGame(seed = 7L))
        assertEquals(null, store.state.value.spawned, "new game has no spawn")
        assertEquals(null, store.state.value.spawnPosition)
    }

    @Test
    fun blockedMoveIsNoOpAndSetsBlockedFlag() {
        // A row [2,4,8,16] flush-left: LEFT is a no-op (nothing slides or merges).
        val store = GameStore.forState(blockedLeftState())
        val before = store.state.value

        store.accept(GameIntent.Move(Direction.LEFT))
        val after = store.state.value

        assertEquals(before.board, after.board, "blocked move leaves the board unchanged")
        assertEquals(before.currentScore, after.currentScore, "blocked move scores nothing")
        assertTrue(after.lastMoveBlocked, "blocked move raises lastMoveBlocked")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun blockedMoveEmitsOneShotEffect() = runTest {
        val store = GameStore.forState(blockedLeftState())
        val received = mutableListOf<GameEffect>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.effects.toList(received)
        }

        store.accept(GameIntent.Move(Direction.LEFT))

        assertEquals(listOf<GameEffect>(GameEffect.Blocked), received)
        job.cancel()
    }

    @Test
    fun acceptedMoveClearsAPriorBlockedFlag() {
        val store = GameStore.forState(twoTwoRowState())

        // First force a blocked move (UP on a single row at the top is a no-op).
        store.accept(GameIntent.Move(Direction.UP))
        assertTrue(store.state.value.lastMoveBlocked, "precondition: UP was blocked")

        // A productive LEFT merge must clear the flag.
        store.accept(GameIntent.Move(Direction.LEFT))
        assertFalse(store.state.value.lastMoveBlocked, "accepted move clears blocked flag")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun firstWinEmitsOneShotWonEffectExactlyOnce() = runTest {
        // A row [1024,1024] (rest empty): LEFT merges to 2048 -> first win.
        val store = GameStore.forState(nearWinLeftState())
        val received = mutableListOf<GameEffect>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.effects.toList(received)
        }

        store.accept(GameIntent.Move(Direction.LEFT))

        assertTrue(store.state.value.justWon, "the winning move sets justWon")
        assertTrue(store.state.value.phase is GamePhase.Won, "phase is Won after reaching target")
        // FEL-4 — accepted moves now also emit a per-move Moved effect, so filter to Won.
        assertEquals(
            listOf<GameEffect>(GameEffect.Won),
            received.filterIsInstance<GameEffect.Won>(),
            "exactly one Won effect",
        )

        // A subsequent accepted move must NOT re-emit Won (the win is a one-shot).
        store.accept(GameIntent.Move(Direction.RIGHT))
        assertEquals(
            listOf<GameEffect>(GameEffect.Won),
            received.filterIsInstance<GameEffect.Won>(),
            "Won fires once; later moves past 2048 do not re-emit it",
        )
        job.cancel()
    }

    @Test
    fun newGameResetsBoardAndScore() {
        val store = GameStore.forState(twoTwoRowState())
        store.accept(GameIntent.Move(Direction.LEFT)) // score becomes 4
        assertTrue(store.state.value.currentScore > 0)

        store.accept(GameIntent.NewGame(seed = 99L))
        val s = store.state.value
        assertEquals(newGame(seed = 99L).board, s.board, "new game = fresh deterministic board")
        assertEquals(0L, s.currentScore, "new game zeroes the running score")
        assertEquals(GamePhase.Playing, s.phase)
        assertFalse(s.lastMoveBlocked)
    }

    @Test
    fun newGameWithoutSeedDrawsAFreshNonRepeatingSeed() {
        val store = GameStore(initialSeed = 1L)
        val first = store.state.value.board
        store.accept(GameIntent.NewGame()) // no seed -> seedSource()
        val second = store.state.value.board
        // Default incrementing seed source must not reproduce the previous board.
        assertTrue(first != second, "restart draws a new seed (different board)")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun stateFlowEmitsOnChange() = runTest {
        val store = GameStore.forState(twoTwoRowState())
        val emissions = mutableListOf<GameUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.state.toList(emissions)
        }

        store.accept(GameIntent.Move(Direction.LEFT))

        assertTrue(emissions.size >= 2, "StateFlow emits initial + post-move state")
        assertEquals(4L, emissions.last().currentScore)
        job.cancel()
    }

    @Test
    fun bestScoreCarriesAcrossNewGame() {
        val store = GameStore.forState(twoTwoRowState())
        store.accept(GameIntent.Move(Direction.LEFT)) // best -> 4
        assertEquals(4L, store.state.value.bestScore)

        store.accept(GameIntent.NewGame(seed = 5L))
        assertEquals(4L, store.state.value.bestScore, "best survives a restart")
        assertEquals(0L, store.state.value.currentScore)
    }

    @Test
    fun initialBestSeedsTheScore() {
        val store = GameStore(initialSeed = 3L, initialBest = 1500L)
        assertEquals(1500L, store.state.value.bestScore, "persisted best (UIB-6 seam) carried in")
        assertEquals(0L, store.state.value.currentScore)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun acceptedMergeMoveEmitsMovedEffectWithMergeValues() = runTest {
        // [2,2] LEFT merges to a 4.
        val store = GameStore.forState(twoTwoRowState())
        val received = mutableListOf<GameEffect>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.effects.toList(received)
        }

        store.accept(GameIntent.Move(Direction.LEFT))

        val moved = received.filterIsInstance<GameEffect.Moved>()
        assertEquals(1, moved.size, "an accepted move emits exactly one Moved effect")
        assertEquals(listOf(4), moved.single().mergedValues, "carries the merge result value")
        assertFalse(moved.single().justWon, "a 4 is not a win")
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun winningMoveEmitsMovedWithJustWon() = runTest {
        val store = GameStore.forState(nearWinLeftState())
        val received = mutableListOf<GameEffect>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.effects.toList(received)
        }

        store.accept(GameIntent.Move(Direction.LEFT))

        val moved = received.filterIsInstance<GameEffect.Moved>().single()
        assertEquals(listOf(2048), moved.mergedValues)
        assertTrue(moved.justWon, "the move reaching 2048 marks justWon on the Moved effect")
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun blockedMoveEmitsNoMovedEffect() = runTest {
        val store = GameStore.forState(blockedLeftState())
        val received = mutableListOf<GameEffect>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.effects.toList(received)
        }

        store.accept(GameIntent.Move(Direction.LEFT))

        assertTrue(
            received.filterIsInstance<GameEffect.Moved>().isEmpty(),
            "a blocked no-op emits Blocked, never Moved",
        )
        job.cancel()
    }

    // --- fixtures ---------------------------------------------------------------

    /** A single row [2,2] (rest empty): LEFT merges to a 4. */
    private fun twoTwoRowState(): GameState = stateFromBoard(
        Board.fromValues(
            arrayOf(
                intArrayOf(2, 2, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        ),
    )

    /** A row [1024,1024] (rest empty): LEFT merges to 2048 -> first win. */
    private fun nearWinLeftState(): GameState = stateFromBoard(
        Board.fromValues(
            arrayOf(
                intArrayOf(1024, 1024, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        ),
    )

    /** A row [2,4,8,16] flush-left (rest empty): LEFT is a no-op. */
    private fun blockedLeftState(): GameState = stateFromBoard(
        Board.fromValues(
            arrayOf(
                intArrayOf(2, 4, 8, 16),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
            ),
        ),
    )

    private fun stateFromBoard(board: Board): GameState = GameState(
        board = board,
        score = Score.zero,
        phase = GamePhase.Playing,
        rngState = 1L,
        nextTileId = 100L,
        moveCount = 0,
    )
}
