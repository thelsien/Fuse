package com.fuse.ui.game

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.ads.AdFormat
import com.fuse.ads.AdManager
import com.fuse.ads.AdResult
import com.fuse.ads.FakeAdProvider
import com.fuse.ads.FakeEntitlements
import com.fuse.ads.InterstitialController
import com.fuse.data.SettingsAdsRepository
import com.fuse.engine.Board
import com.fuse.engine.GamePhase
import com.fuse.engine.GameState
import com.fuse.engine.Score
import com.fuse.feedback.HapticsCoordinator
import com.fuse.feedback.HapticsSettings
import com.fuse.feedback.NoOpHaptics
import com.fuse.feedback.NoOpSound
import com.fuse.feedback.SoundCoordinator
import com.fuse.feedback.SoundSettings
import com.fuse.presentation.AchievementsStore
import com.fuse.presentation.GameStore
import com.fuse.ui.theme.FuseTheme
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ADS-4 — headless Compose UI tests for the capped game-over → replay interstitial on the lose
 * overlay, driving the REAL [GameStore] through [GameScreen] with an injected [FakeAdProvider] +
 * [InterstitialController]. Same Robolectric harness as [ReviveOverlayUiTest].
 *
 * Proves: when the controller (policy) says SHOW, tapping Restart presents an INTERSTITIAL (fake
 * recorded it) AND the game still restarts; when it says DON'T (first session / entitled / not Nth)
 * no interstitial is shown but the game still restarts; a NoFill/Failed interstitial still restarts
 * the game (graceful); the rewarded Continue/revive path shows NO interstitial.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InterstitialOverlayUiTest {

    private fun testHaptics(): HapticsCoordinator =
        HapticsCoordinator(NoOpHaptics, HapticsSettings())

    private fun testSound(): SoundCoordinator =
        SoundCoordinator(NoOpSound, SoundSettings())

    private fun stuckBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
            intArrayOf(2, 4, 2, 4),
            intArrayOf(4, 2, 4, 2),
        ),
    )

    private fun lostStore(): GameStore = GameStore.forState(
        GameState(
            board = stuckBoard(),
            score = Score(current = 1234L, best = 1234L),
            phase = GamePhase.Lost,
            rngState = 1L,
            nextTileId = 100L,
            moveCount = 20,
        ),
    )

    /**
     * A controller whose persisted replay counter is primed so the NEXT onReplay() lands on the
     * every-3rd show (replayCount = 2 ⇒ advanced 3 ⇒ show), in a non-first session, not entitled.
     */
    private fun controllerThatShows(): InterstitialController {
        val settings = MapSettings()
        val repo = SettingsAdsRepository(settings)
        repo.recordLaunch(); repo.recordLaunch() // count 2 -> not first session
        repo.saveInterstitialState(com.fuse.ads.InterstitialState(replayCount = 2))
        return InterstitialController(repository = repo, entitlements = FakeEntitlements(false))
    }

    /** A controller that suppresses: first session (launch count 1). */
    private fun controllerFirstSession(): InterstitialController {
        val settings = MapSettings()
        val repo = SettingsAdsRepository(settings)
        repo.recordLaunch() // count 1 -> first session
        repo.saveInterstitialState(com.fuse.ads.InterstitialState(replayCount = 2)) // would-be Nth
        return InterstitialController(repository = repo, entitlements = FakeEntitlements(false))
    }

    /** A controller that suppresses: Remove-Ads entitled (non-first session, would-be Nth). */
    private fun controllerEntitled(): InterstitialController {
        val settings = MapSettings()
        val repo = SettingsAdsRepository(settings)
        repo.recordLaunch(); repo.recordLaunch()
        repo.saveInterstitialState(com.fuse.ads.InterstitialState(replayCount = 2))
        return InterstitialController(repository = repo, entitlements = FakeEntitlements(true))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restartShowsInterstitialWhenPolicySaysShowAndGameRestarts() = runComposeUiTest {
        val store = lostStore()
        val fake = FakeAdProvider().apply { scriptShow(AdFormat.INTERSTITIAL, AdResult.Completed) }
        setContent {
            FuseTheme {
                GameScreen(
                    store = store,
                    haptics = testHaptics(),
                    sound = testSound(),
                    achievements = AchievementsStore(),
                    adManager = AdManager(fake),
                    interstitialController = controllerThatShows(),
                )
            }
        }

        onNodeWithTag(GameOverlayTags.LOSE_ROOT).assertExists()
        onNodeWithTag(GameOverlayTags.LOSE_RESTART).performClick()
        waitForIdle()

        // An interstitial was actually requested + shown.
        assertTrue("an interstitial was loaded", AdFormat.INTERSTITIAL in fake.loadCalls)
        assertTrue("an interstitial was shown", AdFormat.INTERSTITIAL in fake.showCalls)
        // The game still restarted: back to Playing, overlay gone.
        assertTrue("restarted -> Playing", store.state.value.phase is GamePhase.Playing)
        onNodeWithTag(GameOverlayTags.LOSE_ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restartShowsNoInterstitialDuringFirstSessionButStillRestarts() = runComposeUiTest {
        val store = lostStore()
        val fake = FakeAdProvider()
        setContent {
            FuseTheme {
                GameScreen(
                    store = store,
                    haptics = testHaptics(),
                    sound = testSound(),
                    achievements = AchievementsStore(),
                    adManager = AdManager(fake),
                    interstitialController = controllerFirstSession(),
                )
            }
        }

        onNodeWithTag(GameOverlayTags.LOSE_RESTART).performClick()
        waitForIdle()

        assertFalse("no interstitial in the first session", AdFormat.INTERSTITIAL in fake.showCalls)
        assertTrue("game still restarts", store.state.value.phase is GamePhase.Playing)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun restartShowsNoInterstitialForEntitledUserButStillRestarts() = runComposeUiTest {
        val store = lostStore()
        val fake = FakeAdProvider()
        setContent {
            FuseTheme {
                GameScreen(
                    store = store,
                    haptics = testHaptics(),
                    sound = testSound(),
                    achievements = AchievementsStore(),
                    adManager = AdManager(fake),
                    interstitialController = controllerEntitled(),
                )
            }
        }

        onNodeWithTag(GameOverlayTags.LOSE_RESTART).performClick()
        waitForIdle()

        assertFalse("Remove-Ads suppresses the interstitial", AdFormat.INTERSTITIAL in fake.showCalls)
        assertTrue("game still restarts", store.state.value.phase is GamePhase.Playing)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noFillInterstitialStillRestartsGame() = runComposeUiTest {
        val store = lostStore()
        // loadSucceeds=false -> showInterstitial() returns NoFill (no ad to show).
        val fake = FakeAdProvider(loadSucceeds = false)
        setContent {
            FuseTheme {
                GameScreen(
                    store = store,
                    haptics = testHaptics(),
                    sound = testSound(),
                    achievements = AchievementsStore(),
                    adManager = AdManager(fake),
                    interstitialController = controllerThatShows(),
                )
            }
        }

        onNodeWithTag(GameOverlayTags.LOSE_RESTART).performClick()
        waitForIdle()

        // The show was attempted but no-filled; the replay must NOT be lost.
        assertTrue("an interstitial show was attempted", AdFormat.INTERSTITIAL in fake.loadCalls)
        assertTrue("game restarts despite no-fill", store.state.value.phase is GamePhase.Playing)
        onNodeWithTag(GameOverlayTags.LOSE_ROOT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun reviveDoesNotShowAnInterstitial() = runComposeUiTest {
        val store = lostStore()
        // Rewarded scripted to revive; the same fake records interstitial calls (there must be none).
        val fake = FakeAdProvider().apply { scriptShow(AdFormat.REWARDED, AdResult.Rewarded) }
        setContent {
            FuseTheme {
                GameScreen(
                    store = store,
                    haptics = testHaptics(),
                    sound = testSound(),
                    achievements = AchievementsStore(),
                    adManager = AdManager(fake),
                    interstitialController = controllerThatShows(),
                )
            }
        }

        onNodeWithTag(GameOverlayTags.LOSE_CONTINUE).performClick()
        waitForIdle()

        // Revived via rewarded — and NO interstitial was shown on the revive path.
        assertTrue("revived -> Playing", store.state.value.phase is GamePhase.Playing)
        assertFalse("revive must NOT show an interstitial", AdFormat.INTERSTITIAL in fake.showCalls)
        assertTrue("the rewarded ad was the one shown", AdFormat.REWARDED in fake.showCalls)
    }
}
