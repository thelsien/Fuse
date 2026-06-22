package com.fuse.presentation

import com.fuse.cosmetics.AchievementId
import com.fuse.cosmetics.Cosmetics
import com.fuse.cosmetics.CosmeticType
import com.fuse.cosmetics.PlayerAchievements
import com.fuse.cosmetics.UnlockRule
import com.fuse.data.SettingsAchievementsRepository
import com.fuse.data.SettingsCosmeticsRepository
import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.Score
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * COS-1 — integration tests for the achievements + cosmetics stores over an in-memory
 * [MapSettings] (so the suite runs on JVM + iOS). Covers:
 *  - achievements persist and a NEW store over the same `Settings` restores them;
 *  - marking reached2048 is idempotent (no re-persist/re-emit) and unlocks the gated cosmetic;
 *  - equipped persists and a new store restores it;
 *  - `CosmeticsStore.Equip` persists, rejects a locked id, and reflects newly-met achievements;
 *  - end-to-end: driving a real [GameStore] to emit `GameEffect.Won` → the achievements
 *    recorder sets reached2048 → the gated cosmetic becomes unlocked (the GameScreen wiring).
 */
class CosmeticsStoreTest {

    // --- achievements persistence ------------------------------------------------

    @Test
    fun achievementsPersistAndANewStoreRestoresThem() {
        val settings = MapSettings()
        val repo = SettingsAchievementsRepository(settings)

        val store = AchievementsStore(repository = repo)
        assertFalse(store.state.value.reached2048)
        store.markReached2048()
        assertTrue(store.state.value.reached2048)

        // A NEW store over the same Settings sees the persisted achievement ("relaunch").
        val restored = AchievementsStore(repository = repo)
        assertTrue(restored.state.value.reached2048)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun markReached2048IsIdempotent() = runTest {
        val settings = MapSettings()
        val repo = SettingsAchievementsRepository(settings)
        val store = AchievementsStore(repository = repo)

        val emissions = mutableListOf<PlayerAchievements>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            store.state.toList(emissions)
        }
        yield()

        store.markReached2048()
        store.markReached2048() // second call must be a no-op
        store.markReached2048()
        yield()

        // Initial(false) + exactly one true emission; no churn from the repeats.
        assertEquals(
            listOf(PlayerAchievements(false), PlayerAchievements(true)),
            emissions,
        )
        job.cancel()
    }

    // --- equipped persistence ----------------------------------------------------

    @Test
    fun equippedPersistsAndANewStoreRestoresIt() {
        val settings = MapSettings()
        val cosmeticsRepo = SettingsCosmeticsRepository(settings)
        val achievements = AchievementsStore() // NoOp; only Free cosmetics here

        val store = CosmeticsStore(achievementsStore = achievements, repository = cosmeticsRepo)
        // Midnight is a FREE tile skin → always unlocked → equippable.
        store.accept(CosmeticsIntent.Equip(Cosmetics.MidnightTileSkin.id))
        assertEquals(Cosmetics.MidnightTileSkin.id, store.state.value.equipped.tileSkinId)

        val restored = CosmeticsStore(achievementsStore = AchievementsStore(), repository = cosmeticsRepo)
        assertEquals(Cosmetics.MidnightTileSkin.id, restored.state.value.equipped.tileSkinId)
    }

    // --- equip guard -------------------------------------------------------------

    @Test
    fun equippingALockedCosmeticIsRejected() {
        val achievements = AchievementsStore() // reached2048 = false
        val store = CosmeticsStore(achievementsStore = achievements)

        // GoldRush is gated by reaching 2048 (not yet earned) → equip is a no-op.
        assertFalse(store.state.value.isUnlocked(Cosmetics.GoldRushTileSkin.id))
        store.accept(CosmeticsIntent.Equip(Cosmetics.GoldRushTileSkin.id))
        assertEquals(Cosmetics.DEFAULT_TILE_SKIN, store.state.value.equipped.tileSkinId)
    }

    @Test
    fun equippingAnUnknownIdIsRejected() {
        val store = CosmeticsStore(achievementsStore = AchievementsStore())
        store.accept(CosmeticsIntent.Equip("totally.unknown"))
        assertEquals(Cosmetics.DEFAULT_TILE_SKIN, store.state.value.equipped.tileSkinId)
    }

    @Test
    fun theDefaultIsAlwaysEquippable() {
        val store = CosmeticsStore(achievementsStore = AchievementsStore())
        store.accept(CosmeticsIntent.Equip(Cosmetics.MidnightTileSkin.id))
        store.accept(CosmeticsIntent.Equip(Cosmetics.DEFAULT_TILE_SKIN))
        assertEquals(Cosmetics.DEFAULT_TILE_SKIN, store.state.value.equipped.tileSkinId)
    }

    // --- newly-met achievement reflects live + then equippable -------------------

    @Test
    fun reaching2048UnlocksTheGatedCosmeticLiveThenItCanBeEquipped() = runTest {
        val settings = MapSettings()
        val achievements = AchievementsStore(repository = SettingsAchievementsRepository(settings))
        val cosmeticsRepo = SettingsCosmeticsRepository(settings)
        val store = CosmeticsStore(
            achievementsStore = achievements,
            repository = cosmeticsRepo,
            // observe the achievements flow so unlocks reflect live; backgroundScope is
            // auto-cancelled by runTest so the long-lived collector doesn't hang the test.
            scope = backgroundScope,
        )

        assertFalse(store.state.value.isUnlocked(Cosmetics.GoldRushTileSkin.id))

        achievements.markReached2048()
        yield()

        assertTrue(
            store.state.first().isUnlocked(Cosmetics.GoldRushTileSkin.id),
            "reaching 2048 must unlock the gated cosmetic live",
        )

        // Now that it is unlocked, equipping it succeeds and persists.
        store.accept(CosmeticsIntent.Equip(Cosmetics.GoldRushTileSkin.id))
        assertEquals(Cosmetics.GoldRushTileSkin.id, store.state.value.equipped.tileSkinId)

        val restored = CosmeticsStore(
            achievementsStore = AchievementsStore(repository = SettingsAchievementsRepository(settings)),
            repository = cosmeticsRepo,
        )
        assertEquals(Cosmetics.GoldRushTileSkin.id, restored.state.value.equipped.tileSkinId)
    }

    // --- end-to-end: GameEffect.Won → reached2048 → gated cosmetic unlocked ------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun firstWinRecordsReached2048AndUnlocksTheGatedCosmetic() = runTest {
        val settings = MapSettings()
        val achievements = AchievementsStore(repository = SettingsAchievementsRepository(settings))
        val cosmetics = CosmeticsStore(
            achievementsStore = achievements,
            repository = SettingsCosmeticsRepository(settings),
            scope = backgroundScope,
        )

        // Drive a real GameStore to the win, mirroring the GameScreen wiring: collect effects
        // and call markReached2048() on GameEffect.Won (the same branch GameScreen runs).
        val store = GameStore.forState(nearWinLeftState())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.effects.collect { effect ->
                if (effect is GameEffect.Won) achievements.markReached2048()
            }
        }
        yield()

        // [1024,1024] LEFT → 2048 → GameEffect.Won.
        store.accept(GameIntent.Move(Direction.LEFT))
        yield()

        assertTrue(store.state.value.phase is GamePhase.Won, "the move reaches 2048")
        assertTrue(achievements.state.value.reached2048, "Won recorded reached2048")
        assertTrue(
            cosmetics.state.first().isUnlocked(Cosmetics.GoldRushTileSkin.id),
            "the gated cosmetic is now unlocked",
        )
    }

    // --- sanity on the gate definition -------------------------------------------

    @Test
    fun theGatedCosmeticIsActuallyGatedByReaching2048() {
        assertEquals(
            UnlockRule.Achievement(AchievementId.REACHED_2048),
            Cosmetics.GoldRushTileSkin.unlock,
        )
        assertEquals(CosmeticType.TILE_SKIN, Cosmetics.GoldRushTileSkin.type)
    }

    // --- fixtures ----------------------------------------------------------------

    /** A row [1024,1024] (rest empty): LEFT merges to 2048 → first win. */
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

    private fun stateFromBoard(board: Board): GameState = GameState(
        board = board,
        score = Score.zero,
        phase = GamePhase.Playing,
        rngState = 1L,
        nextTileId = 100L,
        moveCount = 0,
    )
}
