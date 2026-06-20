package com.fuse.data

import com.fuse.engine.Direction
import com.fuse.engine.GamePhase
import com.fuse.engine.newGame
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UIB-6 — round-trip tests for [SettingsGameRepository] over an in-memory [MapSettings]
 * (the `multiplatform-settings-test` artifact), so they run deterministically on every
 * target (JVM + iOS Native) with no real SharedPreferences/NSUserDefaults.
 */
class GameRepositoryTest {

    private fun repo(settings: MapSettings = MapSettings()): SettingsGameRepository =
        SettingsGameRepository(settings)

    @Test
    fun saveThenLoadRoundTripsTheWholeGameStateExactly() {
        val repo = repo()
        // A game with a few moves played so board, score, rng/id state, move count and
        // phase are all non-trivial.
        var state = newGame(seed = 1234L, best = 50L)
        listOf(Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.DOWN).forEach {
            state = state.applyMove(it).state
        }

        repo.saveGame(state)
        val loaded = repo.loadGame()

        assertNotNull(loaded, "a saved game must load back")
        // Full structural equality covers board (incl. tile ids), score, phase,
        // rngState, nextTileId, moveCount and target.
        assertEquals(state, loaded, "round-trip must reproduce the exact GameState")
        assertEquals(state.board.tiles(), loaded.board.tiles(), "tile ids preserved")
        assertEquals(state.rngState, loaded.rngState, "rng state preserved")
        assertEquals(state.nextTileId, loaded.nextTileId, "next tile id preserved")
    }

    @Test
    fun loadGameReturnsNullWhenNothingSaved() {
        assertNull(repo().loadGame(), "empty store -> no game")
    }

    @Test
    fun loadGameReturnsNullOnCorruptBlobWithoutCrashing() {
        val settings = MapSettings()
        settings.putString(SettingsGameRepository.KEY_GAME, "{ this is not valid game json !!")
        val loaded = repo(settings).loadGame()
        assertNull(loaded, "corrupt blob is tolerated -> null (no crash)")
    }

    @Test
    fun clearGameRemovesTheSavedGameButNotTheBest() {
        val repo = repo()
        repo.saveGame(newGame(seed = 1L))
        repo.saveBest(999L)
        assertNotNull(repo.loadGame())

        repo.clearGame()

        assertNull(repo.loadGame(), "clearGame removes the in-progress blob")
        assertEquals(999L, repo.loadBest(), "best survives clearGame (stored separately)")
    }

    @Test
    fun bestScoreSavesAndLoads() {
        val repo = repo()
        assertEquals(0L, repo.loadBest(), "default best is 0")
        repo.saveBest(2048L)
        assertEquals(2048L, repo.loadBest())
    }

    @Test
    fun bestPersistsAcrossARepositoryReconstructionOverTheSameStore() {
        val settings = MapSettings()
        repo(settings).saveBest(4096L)
        // A brand-new repository over the SAME settings (simulates a relaunch) sees it.
        assertEquals(4096L, repo(settings).loadBest(), "best persists across launches")
    }

    @Test
    fun savedGameSurvivesARepositoryReconstruction() {
        val settings = MapSettings()
        val state = newGame(seed = 77L)
        repo(settings).saveGame(state)

        val loaded = repo(settings).loadGame()
        assertNotNull(loaded)
        assertEquals(state, loaded, "in-progress game persists across a relaunch")
        assertTrue(loaded.phase is GamePhase.Playing)
    }
}
