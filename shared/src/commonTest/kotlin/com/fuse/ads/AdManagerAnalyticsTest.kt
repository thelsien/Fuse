package com.fuse.ads

import com.fuse.analytics.AnalyticsEvents
import com.fuse.analytics.AnalyticsParams
import com.fuse.analytics.AnalyticsValues
import com.fuse.analytics.FakeAnalyticsLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ANL-2 — instrumentation at the single ad seam [AdManager]: a presented ad logs `ad_impression`
 * (format + placement) and a rewarded completion additionally logs `ad_reward_granted` (placement).
 * A no-fill/failed show (the ad never appeared) logs nothing. No PII.
 */
class AdManagerAnalyticsTest {

    @Test
    fun rewardedCompletionLogsImpressionAndRewardGranted() = runTest {
        val analytics = FakeAnalyticsLogger()
        val fake = FakeAdProvider().apply { scriptShow(AdFormat.REWARDED, AdResult.Rewarded) }
        val manager = AdManager(provider = fake, analytics = analytics)

        val result = manager.showRewarded(AnalyticsValues.PLACEMENT_REVIVE)

        assertEquals(AdResult.Rewarded, result)
        assertEquals(2, analytics.loggedEvents.size)
        val impression = analytics.loggedEvents.first { it.name == AnalyticsEvents.AD_IMPRESSION }
        assertEquals(AnalyticsValues.FORMAT_REWARDED, impression.params[AnalyticsParams.FORMAT])
        assertEquals(AnalyticsValues.PLACEMENT_REVIVE, impression.params[AnalyticsParams.PLACEMENT])
        val reward = analytics.loggedEvents.first { it.name == AnalyticsEvents.AD_REWARD_GRANTED }
        assertEquals(AnalyticsValues.FORMAT_REWARDED, reward.params[AnalyticsParams.FORMAT])
        assertEquals(AnalyticsValues.PLACEMENT_REVIVE, reward.params[AnalyticsParams.PLACEMENT])
    }

    @Test
    fun rewardedDismissedIsAnImpressionButNoReward() = runTest {
        val analytics = FakeAnalyticsLogger()
        val fake = FakeAdProvider().apply { scriptShow(AdFormat.REWARDED, AdResult.Dismissed) }
        val manager = AdManager(provider = fake, analytics = analytics)

        manager.showRewarded(AnalyticsValues.PLACEMENT_STREAK_SAVER)

        val names = analytics.loggedEventNames
        assertTrue(AnalyticsEvents.AD_IMPRESSION in names, "a shown-then-closed rewarded ad is an impression")
        assertTrue(AnalyticsEvents.AD_REWARD_GRANTED !in names, "no reward when closed early")
        val impression = analytics.loggedEvents.single { it.name == AnalyticsEvents.AD_IMPRESSION }
        assertEquals(AnalyticsValues.PLACEMENT_STREAK_SAVER, impression.params[AnalyticsParams.PLACEMENT])
    }

    @Test
    fun interstitialCompletedLogsImpressionOnly() = runTest {
        val analytics = FakeAnalyticsLogger()
        val fake = FakeAdProvider() // interstitial default is Completed
        val manager = AdManager(provider = fake, analytics = analytics)

        manager.showInterstitial(AnalyticsValues.PLACEMENT_GAME_OVER)

        val event = analytics.loggedEvents.single()
        assertEquals(AnalyticsEvents.AD_IMPRESSION, event.name)
        assertEquals(AnalyticsValues.FORMAT_INTERSTITIAL, event.params[AnalyticsParams.FORMAT])
        assertEquals(AnalyticsValues.PLACEMENT_GAME_OVER, event.params[AnalyticsParams.PLACEMENT])
    }

    @Test
    fun noFillLogsNothing() = runTest {
        val analytics = FakeAnalyticsLogger()
        val fake = FakeAdProvider(loadSucceeds = false)
        val manager = AdManager(provider = fake, analytics = analytics)

        val result = manager.showRewarded(AnalyticsValues.PLACEMENT_REVIVE)

        assertEquals(AdResult.NoFill, result)
        assertTrue(analytics.loggedEvents.isEmpty(), "an ad that never appeared logs no impression")
    }

    @Test
    fun isImpressionCoversShownOutcomesOnly() {
        assertTrue(AdResult.Rewarded.isImpression)
        assertTrue(AdResult.Completed.isImpression)
        assertTrue(AdResult.Shown.isImpression)
        assertTrue(AdResult.Dismissed.isImpression)
        assertTrue(!AdResult.NoFill.isImpression)
        assertTrue(!AdResult.NotReady.isImpression)
        assertTrue(!AdResult.Failed.isImpression)
    }
}
