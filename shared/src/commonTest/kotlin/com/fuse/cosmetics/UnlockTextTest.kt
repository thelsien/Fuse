package com.fuse.cosmetics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * COS-3 — pure tests for the unlock-condition → display-text helper and the catalog grouping the
 * Collection screen renders. No Compose, so this runs on JVM + iOS in commonTest.
 */
class UnlockTextTest {

    // --- unlock-condition text ---------------------------------------------------

    @Test
    fun freeRuleHasNoUnlockConditionText() {
        assertNull(unlockConditionText(UnlockRule.Free))
    }

    @Test
    fun achievementRuleMapsToReach2048Copy() {
        assertEquals(
            "Reach 2048 to unlock",
            unlockConditionText(UnlockRule.Achievement(AchievementId.REACHED_2048)),
        )
    }

    @Test
    fun reached2048AchievementCopyIsStable() {
        assertEquals("Reach 2048 to unlock", unlockText(AchievementId.REACHED_2048))
    }

    @Test
    fun cosmeticExtensionMirrorsItsRule() {
        // The gated cosmetic shows the achievement copy; a free one shows nothing.
        assertEquals("Reach 2048 to unlock", Cosmetics.GoldRushTileSkin.unlockConditionText())
        assertNull(Cosmetics.MidnightTileSkin.unlockConditionText())
        assertNull(Cosmetics.DefaultTileSkin.unlockConditionText())
    }

    // --- catalog grouping --------------------------------------------------------

    @Test
    fun groupedSplitsCatalogByTypeInDisplayOrder() {
        val grouped = Cosmetics.grouped()

        // Tile skins first, then board themes.
        assertEquals(
            listOf(CosmeticType.TILE_SKIN, CosmeticType.BOARD_THEME),
            grouped.map { it.first },
        )

        // Every group holds exactly the catalog entries of its type, in catalog order.
        grouped.forEach { (type, items) ->
            assertTrue(items.all { it.type == type }, "group $type has only $type entries")
            assertEquals(Cosmetics.all.filter { it.type == type }, items)
        }

        // The grouping is exhaustive: it covers the whole catalog with no duplicates.
        assertEquals(Cosmetics.all.toSet(), grouped.flatMap { it.second }.toSet())
        assertEquals(Cosmetics.all.size, grouped.sumOf { it.second.size })
    }

    @Test
    fun firstEntryOfEachGroupIsItsDefault() {
        Cosmetics.grouped().forEach { (type, items) ->
            assertEquals(Cosmetics.default(type), items.first())
        }
    }
}
