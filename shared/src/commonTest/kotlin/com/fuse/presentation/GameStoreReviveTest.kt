package com.fuse.presentation

import com.fuse.data.SettingsGameRepository
import com.fuse.engine.Board
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.Score
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ADS-2 — store-level tests for the [GameIntent.Revive] intent and the [GameUiState.canRevive]
 * projection: revive applies only at game-over before the one allowed revive, re-projects a
 * playing state with free cells, persists write-through, and toggles `canRevive` correctly.
 *
 * Pure/synchronous reduce (no coroutines needed); runs on JVM + iOS Native (commonTest).
 */
class GameStoreReviveTest {

    /** A full, stuck 4x4 (no adjacent equals) -> Lost. */
    private fun stuckBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
        ),
    )

    private fun lostState(
        score: Score = Score(current = 100L, best = 100L),
        revivedThisGame: Boolean = false,
    ): GameState = GameState(
        board = stuckBoard(),
        score = score,
        phase = GamePhase.Lost,
        rngState = 1L,
        nextTileId = 100L,
        moveCount = 12,
        revivedThisGame = revivedThisGame,
    )

    @Test
    fun reviveAtGameOverRevivesTheGame() {
        val store = GameStore.forState(lostState())
        assertTrue(store.state.value.isGameOver)
        assertTrue(store.state.value.canRevive, "canRevive is true at game-over before revive")

        store.accept(GameIntent.Revive)

        val s = store.state.value
        assertFalse(s.isGameOver, "revive clears game-over")
        assertTrue(s.phase is GamePhase.Playing, "revive returns to Playing")
        assertTrue(s.board.emptyCells().isNotEmpty(), "revive opens up board space")
        assertEquals(100L, s.currentScore, "score is preserved across revive")
        assertFalse(s.canRevive, "canRevive is false after the one allowed revive")
    }

    @Test
    fun reviveIsANoOpWhenNotGameOver() {
        val playing = GameState(
            board = Board.fromValues(
                arrayOf(
                    intArrayOf(2, 4, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                ),
            ),
            score = Score.zero,
            phase = GamePhase.Playing,
            rngState = 1L,
            nextTileId = 100L,
            moveCount = 1,
        )
        val store = GameStore.forState(playing)
        assertFalse(store.state.value.canRevive)
        val before = store.state.value

        store.accept(GameIntent.Revive)

        assertEquals(before.board, store.state.value.board, "revive on a playing game changes nothing")
        assertTrue(store.state.value.phase is GamePhase.Playing)
    }

    @Test
    fun reviveIsANoOpWhenAlreadyRevived() {
        // A game-over state that has already been revived once must not revive again.
        val store = GameStore.forState(lostState(revivedThisGame = true))
        assertFalse(store.state.value.canRevive, "already-revived game cannot revive again")
        val before = store.state.value

        store.accept(GameIntent.Revive)

        assertEquals(before.board, store.state.value.board, "second revive is a no-op")
        assertTrue(store.state.value.isGameOver, "still game-over (no revive applied)")
    }

    @Test
    fun revivePersistsTheNewStateWriteThrough() {
        val settings = MapSettings()
        val store = GameStore.forState(lostState(), repository = SettingsGameRepository(settings))

        store.accept(GameIntent.Revive)

        val saved = SettingsGameRepository(settings).loadGame()
        assertNotNull(saved, "revive persists the new game state")
        assertTrue(saved.phase is GamePhase.Playing, "persisted state is playing again")
        assertTrue(saved.revivedThisGame, "persisted state carries the once-per-game latch")
        assertEquals(store.state.value.board, saved.board, "saved blob tracks the revived board")
    }

    @Test
    fun canReviveIsFalseForAFreshPlayingGame() {
        val store = GameStore(initialSeed = 1L)
        assertFalse(store.state.value.canRevive, "a fresh playing game offers no revive")
    }
}
