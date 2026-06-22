package com.fuse.ads

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADS-1 — the [AdManager] load-then-show orchestration + result mapping that ADS-2/3/4 build on.
 */
class AdManagerTest {

    @Test
    fun showRewardedLoadsThenShowsAndReturnsScriptedResult() = runTest {
        val fake = FakeAdProvider().apply { scriptShow(AdFormat.REWARDED, AdResult.Rewarded) }
        val manager = AdManager(fake)

        val result = manager.showRewarded()

        assertEquals(AdResult.Rewarded, result)
        // It loaded (an ad was requested) THEN showed — the order ADS-2 asserts on.
        assertEquals(
            listOf(
                FakeAdProvider.Call.Load(AdFormat.REWARDED),
                FakeAdProvider.Call.Show(AdFormat.REWARDED),
            ),
            fake.calls,
        )
    }

    @Test
    fun showRewardedReturnsNoFillWhenLoadFailsAndNeverShows() = runTest {
        val fake = FakeAdProvider(loadSucceeds = false)
        val manager = AdManager(fake)

        val result = manager.showRewarded()

        assertEquals(AdResult.NoFill, result)
        assertTrue(fake.showCalls.isEmpty(), "no show attempt when nothing loaded")
        assertEquals(listOf(AdFormat.REWARDED), fake.loadCalls)
    }

    @Test
    fun showInterstitialReturnsCompletedOnNormalShow() = runTest {
        val fake = FakeAdProvider() // interstitial default is Completed
        val manager = AdManager(fake)

        assertEquals(AdResult.Completed, manager.showInterstitial())
    }

    @Test
    fun alreadyReadyAdSkipsRedundantLoad() = runTest {
        val fake = FakeAdProvider()
        val manager = AdManager(fake)

        assertTrue(manager.preload(AdFormat.REWARDED))
        fake.scriptShow(AdFormat.REWARDED, AdResult.Rewarded)

        val result = manager.showRewarded()

        assertEquals(AdResult.Rewarded, result)
        // Only ONE load (from preload); showRewarded reused the ready ad.
        assertEquals(listOf(AdFormat.REWARDED), fake.loadCalls)
        assertEquals(listOf(AdFormat.REWARDED), fake.showCalls)
    }

    @Test
    fun isRewardEarnedOnlyForRewarded() {
        assertTrue(AdResult.Rewarded.isRewardEarned)
        assertFalse(AdResult.Dismissed.isRewardEarned)
        assertFalse(AdResult.Completed.isRewardEarned)
        assertFalse(AdResult.NoFill.isRewardEarned)
        assertFalse(AdResult.NotReady.isRewardEarned)
        assertFalse(AdResult.Failed.isRewardEarned)
        assertFalse(AdResult.Shown.isRewardEarned)
    }

    @Test
    fun preloadReportsReadiness() = runTest {
        val ok = AdManager(FakeAdProvider())
        assertTrue(ok.preload(AdFormat.INTERSTITIAL))
        assertTrue(ok.isReady(AdFormat.INTERSTITIAL))

        val bad = AdManager(FakeAdProvider(loadSucceeds = false))
        assertFalse(bad.preload(AdFormat.INTERSTITIAL))
        assertFalse(bad.isReady(AdFormat.INTERSTITIAL))
    }
}
