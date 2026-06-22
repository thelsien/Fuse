package com.fuse.ui.collection

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.cosmetics.CosmeticType
import com.fuse.cosmetics.Cosmetics
import com.fuse.presentation.AchievementsStore
import com.fuse.presentation.CosmeticsIntent
import com.fuse.presentation.CosmeticsStore
import com.fuse.ui.theme.FuseTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * COS-3 — headless Compose UI tests for [CollectionScreen]. Same Robolectric harness as the other
 * UI suites (androidUnitTest, runs in `:shared:testDebugUnitTest`). The screen reads a real
 * [CosmeticsStore] (over a NoOp/Map repository — no Koin needed), so these drive the actual store
 * and assert the owned/locked/equipped states + the equip flow + auto-unlock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CollectionScreenUiTest {

    /** A store with no achievements earned → the 2048-gated skin is locked. */
    private fun lockedStore() = CosmeticsStore(achievementsStore = AchievementsStore())

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersBothSectionsAndTheCatalog() = runComposeUiTest {
        setContent {
            FuseTheme {
                CollectionScreen(cosmeticsStore = lockedStore(), onBack = {})
            }
        }

        onNodeWithTag(CollectionScreenTags.ROOT).assertExists()
        onNodeWithTag(CollectionScreenTags.TITLE).assertExists()

        // Both type sections render.
        onNodeWithTag(CollectionScreenTags.sectionTag(CosmeticType.TILE_SKIN)).assertExists()
        onNodeWithTag(CollectionScreenTags.sectionTag(CosmeticType.BOARD_THEME)).assertExists()

        // Every catalog entry has a card.
        Cosmetics.all.forEach { cosmetic ->
            onNodeWithTag(CollectionScreenTags.cardTag(cosmetic.id)).assertExists()
            onNodeWithTag(CollectionScreenTags.previewTag(cosmetic.id)).assertExists()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun lockedCosmeticShowsUnlockConditionAndIsNotEquippable() = runComposeUiTest {
        setContent {
            FuseTheme {
                CollectionScreen(cosmeticsStore = lockedStore(), onBack = {})
            }
        }

        val gated = Cosmetics.GoldRushTileSkin
        // Its locked label shows the unlock CONDITION (no currency).
        onNodeWithTag(CollectionScreenTags.lockedTag(gated.id))
            .assertTextEquals("Reach 2048 to unlock")
        // It has NO Equip action and is NOT equipped.
        onNodeWithTag(CollectionScreenTags.equipTag(gated.id)).assertDoesNotExist()
        onNodeWithTag(CollectionScreenTags.equippedTag(gated.id)).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun defaultCosmeticShowsEquippedIndicator() = runComposeUiTest {
        setContent {
            FuseTheme {
                CollectionScreen(cosmeticsStore = lockedStore(), onBack = {})
            }
        }

        // The default tile skin is equipped on a fresh store.
        onNodeWithTag(CollectionScreenTags.equippedTag(Cosmetics.DEFAULT_TILE_SKIN))
            .assertExists()
        // Its Equip action is absent (already equipped).
        onNodeWithTag(CollectionScreenTags.equipTag(Cosmetics.DEFAULT_TILE_SKIN))
            .assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun ownedCosmeticShowsEquip_andTappingEquipsItLive() = runComposeUiTest {
        val store = lockedStore()
        setContent {
            FuseTheme {
                CollectionScreen(cosmeticsStore = store, onBack = {})
            }
        }

        val owned = Cosmetics.MidnightTileSkin // Free → owned, not equipped initially.
        // Owned, not equipped → shows Equip, not the Equipped indicator.
        onNodeWithTag(CollectionScreenTags.equipTag(owned.id)).assertExists()
        onNodeWithTag(CollectionScreenTags.equippedTag(owned.id)).assertDoesNotExist()
        // The default still holds the Equipped indicator.
        onNodeWithTag(CollectionScreenTags.equippedTag(Cosmetics.DEFAULT_TILE_SKIN)).assertExists()

        // Tap Equip → the store equips it and the indicator moves.
        onNodeWithTag(CollectionScreenTags.equipTag(owned.id)).performClick()
        waitForIdle()

        // Store state changed.
        assert(store.state.value.equipped.tileSkinId == owned.id)
        // The newly-equipped card shows Equipped; the old default no longer does.
        onNodeWithTag(CollectionScreenTags.equippedTag(owned.id)).assertExists()
        onNodeWithTag(CollectionScreenTags.equipTag(owned.id)).assertDoesNotExist()
        onNodeWithTag(CollectionScreenTags.equippedTag(Cosmetics.DEFAULT_TILE_SKIN))
            .assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reaching2048AutoUnlocksTheGatedCosmeticLive() = runComposeUiTest {
        // Wire the store to OBSERVE achievements so a newly-met one flips the gated skin live.
        val achievements = AchievementsStore()
        lateinit var store: CosmeticsStore
        setContent {
            // The store needs a scope to observe; reuse the composition scope via a side store.
            store = CosmeticsStore(
                achievementsStore = achievements,
                scope = kotlinx.coroutines.MainScope(),
            )
            FuseTheme {
                CollectionScreen(cosmeticsStore = store, onBack = {})
            }
        }

        val gated = Cosmetics.GoldRushTileSkin
        // Initially locked: shows the unlock condition, no Equip action.
        onNodeWithTag(CollectionScreenTags.lockedTag(gated.id)).assertExists()
        onNodeWithTag(CollectionScreenTags.equipTag(gated.id)).assertDoesNotExist()

        // Reach 2048 (the achievement) — the store's unlocked set updates live.
        achievements.markReached2048()
        waitForIdle()

        // Now owned/equippable: the lock label is gone, an Equip action appears.
        onNodeWithTag(CollectionScreenTags.lockedTag(gated.id)).assertDoesNotExist()
        onNodeWithTag(CollectionScreenTags.equipTag(gated.id)).assertExists()

        // And it can now be equipped.
        onNodeWithTag(CollectionScreenTags.equipTag(gated.id)).performClick()
        waitForIdle()
        assert(store.state.value.equipped.tileSkinId == gated.id)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backInvokesCallback() = runComposeUiTest {
        var backs = 0
        setContent {
            FuseTheme {
                CollectionScreen(cosmeticsStore = lockedStore(), onBack = { backs++ })
            }
        }
        onNodeWithTag(CollectionScreenTags.BACK).performClick()
        waitForIdle()
        assert(backs == 1)
    }
}
