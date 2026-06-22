package com.fuse.ads

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADS-1 — the contract the FAKE must honour so ADS-2/3/4 can drive it deterministically:
 * load/isReady reflect loaded state, show returns the scripted result per format, the rewarded vs
 * interstitial defaults are correct, no-fill/failure map cleanly, and every call is recorded.
 */
class FakeAdProviderTest {

    @Test
    fun loadMarksReadyAndShowConsumesIt() = runTest {
        val ads = FakeAdProvider()
        assertFalse(ads.isReady(AdFormat.REWARDED), "not ready before load")

        assertTrue(ads.load(AdFormat.REWARDED), "load succeeds by default")
        assertTrue(ads.isReady(AdFormat.REWARDED), "ready after load")

        ads.show(AdFormat.REWARDED)
        assertFalse(ads.isReady(AdFormat.REWARDED), "show consumes the loaded ad")
    }

    @Test
    fun loadCanBeScriptedToFail() = runTest {
        val ads = FakeAdProvider(loadSucceeds = false)
        assertFalse(ads.load(AdFormat.INTERSTITIAL), "load fails when scripted")
        assertFalse(ads.isReady(AdFormat.INTERSTITIAL), "not ready after a failed load")
        // show without a ready ad → NotReady (it never implicitly loads).
        assertEquals(AdResult.NotReady, ads.show(AdFormat.INTERSTITIAL))
    }

    @Test
    fun showReturnsScriptedResultsInOrderThenDefault() = runTest {
        val ads = FakeAdProvider()
        ads.scriptShow(AdFormat.REWARDED, AdResult.Dismissed, AdResult.Rewarded)

        ads.load(AdFormat.REWARDED)
        assertEquals(AdResult.Dismissed, ads.show(AdFormat.REWARDED))
        ads.load(AdFormat.REWARDED)
        assertEquals(AdResult.Rewarded, ads.show(AdFormat.REWARDED))
        // Queue drained → per-format default (Rewarded for the rewarded format).
        ads.load(AdFormat.REWARDED)
        assertEquals(AdResult.Rewarded, ads.show(AdFormat.REWARDED))
    }

    @Test
    fun perFormatDefaultsAreRewardedAndCompleted() = runTest {
        val ads = FakeAdProvider()
        ads.load(AdFormat.REWARDED)
        assertEquals(AdResult.Rewarded, ads.show(AdFormat.REWARDED), "rewarded default")
        ads.load(AdFormat.INTERSTITIAL)
        assertEquals(AdResult.Completed, ads.show(AdFormat.INTERSTITIAL), "interstitial default")
    }

    @Test
    fun defaultResultIsOverridable() = runTest {
        val ads = FakeAdProvider()
        ads.setDefaultResult(AdFormat.REWARDED, AdResult.NoFill)
        ads.load(AdFormat.REWARDED)
        assertEquals(AdResult.NoFill, ads.show(AdFormat.REWARDED))
    }

    @Test
    fun recordsInitializeLoadAndShowCalls() = runTest {
        val ads = FakeAdProvider()
        ads.initialize()
        ads.load(AdFormat.REWARDED)
        ads.show(AdFormat.REWARDED)
        ads.load(AdFormat.INTERSTITIAL)

        assertEquals(1, ads.initializeCount)
        assertEquals(listOf(AdFormat.REWARDED, AdFormat.INTERSTITIAL), ads.loadCalls)
        assertEquals(listOf(AdFormat.REWARDED), ads.showCalls)
        assertEquals(
            listOf(
                FakeAdProvider.Call.Initialize,
                FakeAdProvider.Call.Load(AdFormat.REWARDED),
                FakeAdProvider.Call.Show(AdFormat.REWARDED),
                FakeAdProvider.Call.Load(AdFormat.INTERSTITIAL),
            ),
            ads.calls,
        )
    }

    @Test
    fun scriptsPerFormatAreIndependent() = runTest {
        val ads = FakeAdProvider()
        ads.scriptShow(AdFormat.REWARDED, AdResult.NoFill)
        ads.scriptShow(AdFormat.INTERSTITIAL, AdResult.Failed)

        ads.load(AdFormat.INTERSTITIAL)
        assertEquals(AdResult.Failed, ads.show(AdFormat.INTERSTITIAL))
        ads.load(AdFormat.REWARDED)
        assertEquals(AdResult.NoFill, ads.show(AdFormat.REWARDED))
    }
}
