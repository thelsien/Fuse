package com.fuse.cosmetics

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * COS-1 — pure unit tests for the cosmetics model: the catalog shape, the unlock rules
 * ([isUnlocked]/[unlockedCosmetics]), the achievements projection, the equip helper, and JSON
 * round-trips. Runs on JVM + iOS (commonTest, no Settings/coroutines).
 */
class CosmeticsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun catalogHasADefaultPerTypeThatIsFree() {
        val tile = Cosmetics.default(CosmeticType.TILE_SKIN)
        val board = Cosmetics.default(CosmeticType.BOARD_THEME)
        assertEquals(CosmeticType.TILE_SKIN, tile.type)
        assertEquals(CosmeticType.BOARD_THEME, board.type)
        assertEquals(UnlockRule.Free, tile.unlock)
        assertEquals(UnlockRule.Free, board.unlock)
        assertEquals(Cosmetics.DEFAULT_TILE_SKIN, tile.id)
        assertEquals(Cosmetics.DEFAULT_BOARD_THEME, board.id)
        // Both defaults are in the catalog.
        assertTrue(tile in Cosmetics.all)
        assertTrue(board in Cosmetics.all)
    }

    @Test
    fun catalogHasAtLeastOne2048GatedCosmetic() {
        val gated = Cosmetics.all.filter {
            it.unlock == UnlockRule.Achievement(AchievementId.REACHED_2048)
        }
        assertTrue(gated.isNotEmpty(), "expected at least one cosmetic gated by reaching 2048")
    }

    @Test
    fun byIdFindsCatalogEntriesAndNullsUnknown() {
        assertEquals(Cosmetics.GoldRushTileSkin, Cosmetics.byId(Cosmetics.GoldRushTileSkin.id))
        assertNull(Cosmetics.byId("nope.not.real"))
    }

    @Test
    fun freeCosmeticsAreAlwaysUnlocked() {
        val none = PlayerAchievements()
        assertTrue(isUnlocked(Cosmetics.DefaultTileSkin, none))
        assertTrue(isUnlocked(Cosmetics.DefaultBoardTheme, none))
        assertTrue(isUnlocked(Cosmetics.MidnightTileSkin, none))
    }

    @Test
    fun achievementGatedIsLockedUntilTheAchievementIsEarned() {
        val locked = PlayerAchievements(reached2048 = false)
        val unlocked = PlayerAchievements(reached2048 = true)
        assertFalse(isUnlocked(Cosmetics.GoldRushTileSkin, locked))
        assertTrue(isUnlocked(Cosmetics.GoldRushTileSkin, unlocked))
    }

    @Test
    fun unlockedCosmeticsExcludesTheGatedOneUntilEarnedThenIncludesIt() {
        val before = unlockedCosmetics(PlayerAchievements(reached2048 = false))
        val after = unlockedCosmetics(PlayerAchievements(reached2048 = true))
        assertFalse(Cosmetics.GoldRushTileSkin in before)
        assertTrue(Cosmetics.GoldRushTileSkin in after)
        // Free ones are present in both.
        assertTrue(Cosmetics.DefaultTileSkin in before)
        assertTrue(Cosmetics.DefaultTileSkin in after)
        // Reaching 2048 unlocks exactly the one extra (in this catalog).
        assertEquals(before.size + 1, after.size)
    }

    @Test
    fun achievementsHasProjectsTheRecord() {
        assertFalse(PlayerAchievements().has(AchievementId.REACHED_2048))
        assertTrue(PlayerAchievements(reached2048 = true).has(AchievementId.REACHED_2048))
    }

    @Test
    fun equipHelperSetsTheCorrectSlotLeavingTheOtherUnchanged() {
        val base = EquippedCosmetics()
        val withTile = base.equip(CosmeticType.TILE_SKIN, "tile.midnight")
        assertEquals("tile.midnight", withTile.tileSkinId)
        assertEquals(Cosmetics.DEFAULT_BOARD_THEME, withTile.boardThemeId)

        val withBoard = withTile.equip(CosmeticType.BOARD_THEME, "board.ocean")
        assertEquals("tile.midnight", withBoard.tileSkinId)
        assertEquals("board.ocean", withBoard.boardThemeId)

        assertEquals("tile.midnight", withBoard.idFor(CosmeticType.TILE_SKIN))
        assertEquals("board.ocean", withBoard.idFor(CosmeticType.BOARD_THEME))
    }

    @Test
    fun equippedDefaultsToTheDefaultIds() {
        val d = EquippedCosmetics()
        assertEquals(Cosmetics.DEFAULT_TILE_SKIN, d.tileSkinId)
        assertEquals(Cosmetics.DEFAULT_BOARD_THEME, d.boardThemeId)
    }

    @Test
    fun playerAchievementsJsonRoundTrips() {
        val original = PlayerAchievements(reached2048 = true)
        val blob = json.encodeToString(PlayerAchievements.serializer(), original)
        val decoded = json.decodeFromString(PlayerAchievements.serializer(), blob)
        assertEquals(original, decoded)
    }

    @Test
    fun playerAchievementsDecodesAnEmptyObjectToDefaults() {
        // An OLDER blob (no fields) must decode to the all-false default (extensibility).
        val decoded = json.decodeFromString(PlayerAchievements.serializer(), "{}")
        assertEquals(PlayerAchievements(), decoded)
    }

    @Test
    fun equippedCosmeticsJsonRoundTrips() {
        val original = EquippedCosmetics(tileSkinId = "tile.goldRush", boardThemeId = "board.ocean")
        val blob = json.encodeToString(EquippedCosmetics.serializer(), original)
        val decoded = json.decodeFromString(EquippedCosmetics.serializer(), blob)
        assertEquals(original, decoded)
    }
}
