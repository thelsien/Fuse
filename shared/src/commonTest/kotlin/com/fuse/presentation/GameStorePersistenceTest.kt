package com.fuse.presentation

import com.fuse.data.SettingsGameRepository
import com.fuse.engine.Direction
import com.fuse.engine.newGame
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * UIB-6 — store-level persistence: the [GameStore] resumes a saved game on init,
 * persists after every change, carries the best across a simulated relaunch, and a
 * resume + the same moves replays identically to uninterrupted play.
 *
 * All over an in-memory [MapSettings] so it runs on JVM + iOS Native in commonTest.
 */
class GameStorePersistenceTest {

    private fun repoOver(settings: Settings) = SettingsGameRepository(settings)

    @Test
    fun storeOverEmptySettingsStartsAFreshGameAndPersistsIt() {
        val settings = MapSettings()
        val store = GameStore(initialSeed = 42L, repository = repoOver(settings))

        // Init writes the starting game so it is resumable before any move.
        val saved = repoOver(settings).loadGame()
        assertNotNull(saved, "fresh game is persisted on init")
        assertEquals(store.state.value.board, saved.board)
        assertEquals(newGame(seed = 42L).board, saved.board, "fresh game from initialSeed")
    }

    @Test
    fun storeResumesAPreviouslySavedGameInsteadOfStartingFresh() {
        val settings = MapSettings()
        // Simulate a prior session: play a few moves through a first store.
        val first = GameStore(initialSeed = 7L, repository = repoOver(settings))
        first.accept(GameIntent.Move(Direction.LEFT))
        first.accept(GameIntent.Move(Direction.UP))
        first.accept(GameIntent.Move(Direction.RIGHT))
        val savedBoard = first.state.value.board
        val savedScore = first.state.value.currentScore

        // "Relaunch": a brand-new store over the SAME settings, with a DIFFERENT seed.
        val resumed = GameStore(initialSeed = 999L, repository = repoOver(settings))
        val s = resumed.state.value

        assertEquals(savedBoard, s.board, "resume restores the exact saved board (not seed 999)")
        assertEquals(savedScore, s.currentScore, "resume restores the saved score")
        assertTrue(
            s.board != newGame(seed = 999L).board,
            "resumed store ignores its own initialSeed when a save exists",
        )
    }

    @Test
    fun afterAMoveThePersistedBlobUpdates() {
        val settings = MapSettings()
        val store = GameStore(initialSeed = 3L, repository = repoOver(settings))
        val beforeBlob = repoOver(settings).loadGame()
        assertNotNull(beforeBlob)

        store.accept(GameIntent.Move(Direction.LEFT))
        val afterBlob = repoOver(settings).loadGame()
        assertNotNull(afterBlob)

        assertEquals(store.state.value.board, afterBlob.board, "saved blob tracks the live state")
        assertEquals(1, afterBlob.moveCount, "the persisted blob reflects the accepted move")
    }

    @Test
    fun bestPersistsAcrossASimulatedRelaunch() {
        val settings = MapSettings()
        // Build a near-win-ish scoring game and play to accrue some score/best.
        val store = GameStore.forState(
            com.fuse.engine.GameState(
                board = com.fuse.engine.Board.fromValues(
                    arrayOf(
                        intArrayOf(2, 2, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                    ),
                ),
                score = com.fuse.engine.Score.zero,
                phase = com.fuse.engine.GamePhase.Playing,
                rngState = 1L,
                nextTileId = 100L,
                moveCount = 0,
            ),
            repository = repoOver(settings),
        )
        store.accept(GameIntent.Move(Direction.LEFT)) // 2+2 -> +4, best becomes 4
        assertEquals(4L, store.state.value.bestScore)

        // Relaunch: new store over the same settings sees the prior best as its floor,
        // even on a fresh board.
        val relaunched = GameStore(initialSeed = 555L, repository = repoOver(settings))
        assertTrue(
            relaunched.state.value.bestScore >= 4L,
            "best (>=4) persists across a relaunch",
        )
    }

    @Test
    fun newGameOverwritesTheSavedBlobButKeepsBest() {
        val settings = MapSettings()
        val store = GameStore.forState(
            com.fuse.engine.GameState(
                board = com.fuse.engine.Board.fromValues(
                    arrayOf(
                        intArrayOf(2, 2, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                        intArrayOf(0, 0, 0, 0),
                    ),
                ),
                score = com.fuse.engine.Score.zero,
                phase = com.fuse.engine.GamePhase.Playing,
                rngState = 1L,
                nextTileId = 100L,
                moveCount = 0,
            ),
            repository = repoOver(settings),
        )
        store.accept(GameIntent.Move(Direction.LEFT)) // best -> 4
        store.accept(GameIntent.NewGame(seed = 12345L))

        val saved = repoOver(settings).loadGame()
        assertNotNull(saved)
        assertEquals(newGame(seed = 12345L).board, saved.board, "new game overwrites the blob")
        assertEquals(4L, repoOver(settings).loadBest(), "best survives a new game")
    }

    @Test
    fun resumePlusSameMovesEqualsUninterruptedPlay() {
        // Determinism: a game closed mid-run and resumed continues the exact rng/id
        // stream, so resume + remaining moves == playing all moves uninterrupted.
        val moves = listOf(
            Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN,
            Direction.LEFT, Direction.DOWN, Direction.UP, Direction.RIGHT,
        )

        // (a) Uninterrupted: one store plays every move.
        val settingsA = MapSettings()
        val uninterrupted = GameStore(initialSeed = 2024L, repository = repoOver(settingsA))
        moves.forEach { uninterrupted.accept(GameIntent.Move(it)) }
        val uninterruptedBlob = repoOver(settingsA).loadGame()
        assertNotNull(uninterruptedBlob)

        // (b) Interrupted: play half, "kill", relaunch over the saved settings, play rest.
        val settingsB = MapSettings()
        val firstHalf = moves.take(moves.size / 2)
        val secondHalf = moves.drop(moves.size / 2)
        val session1 = GameStore(initialSeed = 2024L, repository = repoOver(settingsB))
        firstHalf.forEach { session1.accept(GameIntent.Move(it)) }
        // relaunch
        val session2 = GameStore(initialSeed = 2024L, repository = repoOver(settingsB))
        secondHalf.forEach { session2.accept(GameIntent.Move(it)) }
        val interruptedBlob = repoOver(settingsB).loadGame()
        assertNotNull(interruptedBlob)

        assertEquals(
            uninterruptedBlob,
            interruptedBlob,
            "resume + same moves reproduces uninterrupted play exactly (board+ids+rng+score+phase)",
        )
    }
}
