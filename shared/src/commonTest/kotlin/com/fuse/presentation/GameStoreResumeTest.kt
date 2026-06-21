package com.fuse.presentation

import com.fuse.data.SettingsGameRepository
import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.Score
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SHL-4 — the store's reactive `canResume` projection: the [GameUiState.canResume] flag
 * Home reads to decide between **Continue + New game** and the single **Play Classic**.
 *
 * It mirrors [com.fuse.engine.isResumable] over whatever game the store currently holds,
 * recomputed on every reduce so it stays correct as the game changes (resume on init →
 * true; after [GameIntent.NewGame] → false; once a game is lost → false). Pure, so it
 * runs on JVM + iOS in commonTest.
 */
class GameStoreResumeTest {

    private fun repoOver(settings: Settings) = SettingsGameRepository(settings)

    private fun boardState(
        board: Board,
        phase: GamePhase = GamePhase.Playing,
        moveCount: Int = 0,
    ): GameState = GameState(
        board = board,
        score = Score.zero,
        phase = phase,
        rngState = 1L,
        nextTileId = 100L,
        moveCount = moveCount,
    )

    private fun twoTwoRow(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(2, 2, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        ),
    )

    @Test
    fun freshGameOnInitIsNotResumable() {
        // A brand-new launch with no save: the store holds a fresh newGame (moveCount 0),
        // so Home shows Play Classic, not Continue.
        val store = GameStore(initialSeed = 42L)
        assertFalse(store.state.value.canResume, "fresh game (moveCount 0) → canResume false")
    }

    @Test
    fun resumedSavedGameIsResumable() {
        // Simulate a prior session that actually moved, then "relaunch" over the same
        // settings: the store resumes the saved (moveCount > 0) game and projects canResume.
        val settings = MapSettings()
        val first = GameStore(initialSeed = 7L, repository = repoOver(settings))
        first.accept(GameIntent.Move(Direction.LEFT))
        assertTrue(first.state.value.canResume, "after a move the live game is resumable")

        val relaunched = GameStore(initialSeed = 999L, repository = repoOver(settings))
        assertTrue(
            relaunched.state.value.canResume,
            "a resumed saved in-progress game projects canResume = true",
        )
    }

    @Test
    fun firstAcceptedMoveTurnsCanResumeOn() {
        val store = GameStore.forState(boardState(twoTwoRow()))
        assertFalse(store.state.value.canResume, "moveCount 0 board → not yet resumable")

        store.accept(GameIntent.Move(Direction.LEFT)) // moveCount becomes 1
        assertTrue(store.state.value.canResume, "first accepted move makes the game resumable")
    }

    @Test
    fun newGameTurnsCanResumeBackOff() {
        // Drive a resumable game, then New game must flip canResume back to false (the fresh
        // board has moveCount 0), so Home returns to the single Play Classic CTA.
        val store = GameStore.forState(boardState(twoTwoRow()))
        store.accept(GameIntent.Move(Direction.LEFT))
        assertTrue(store.state.value.canResume, "precondition: resumable after a move")

        store.accept(GameIntent.NewGame(seed = 5L))
        assertFalse(store.state.value.canResume, "a fresh New game is not resumable")
    }

    @Test
    fun lostGameIsNotResumable() {
        // A store positioned directly at a terminal Lost phase (with moves played) must NOT
        // be offered as a resume — the game is finished.
        val store = GameStore.forState(
            boardState(twoTwoRow(), phase = GamePhase.Lost, moveCount = 50),
        )
        assertFalse(store.state.value.canResume, "a lost game → not resumable")
    }
}
