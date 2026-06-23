package com.fuse.ads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADS-4 — unit tests for the pure [InterstitialPolicy] (no SDK, no Settings, no Compose).
 *
 * Pins the documented rules: every-Nth cap (N = [InterstitialPolicy.CAP_EVERY_NTH] = 3) fires only on
 * the Nth replay; Remove-Ads ownership suppresses; first-session suppresses; the counter advances
 * (and is returned for persistence) on every replay regardless of suppression; boundary cases.
 */
class InterstitialPolicyTest {

    private fun decide(
        replayCount: Int,
        removeAdsOwned: Boolean = false,
        isFirstSession: Boolean = false,
    ): InterstitialPolicy.Decision = InterstitialPolicy.decide(
        state = InterstitialState(replayCount = replayCount),
        removeAdsOwned = removeAdsOwned,
        isFirstSession = isFirstSession,
    )

    @Test
    fun capIsThree() {
        assertEquals(3, InterstitialPolicy.CAP_EVERY_NTH, "documented cap value")
    }

    @Test
    fun fitsCadence_showsOnlyOnEveryThirdReplay() {
        // Walk the counter from a fresh install across 6 replays; show on 3rd and 6th only.
        var state = InterstitialState()
        val shown = mutableListOf<Boolean>()
        repeat(6) {
            val d = InterstitialPolicy.decide(state, removeAdsOwned = false, isFirstSession = false)
            shown += d.shouldShow
            state = d.nextState
        }
        assertEquals(
            listOf(false, false, true, false, false, true),
            shown,
            "cadence over replays 1..6 is: no,no,SHOW,no,no,SHOW",
        )
        assertEquals(6, state.replayCount, "counter advanced once per replay")
    }

    @Test
    fun counterAlwaysAdvancesByOne() {
        assertEquals(1, decide(replayCount = 0).nextState.replayCount)
        assertEquals(43, decide(replayCount = 42).nextState.replayCount)
    }

    @Test
    fun showsExactlyWhenAdvancedCountIsMultipleOfN() {
        // The decision uses the ADVANCED count: replayCount 2 -> advanced 3 -> show.
        assertTrue(decide(replayCount = 2).shouldShow, "replay #3 shows")
        assertFalse(decide(replayCount = 0).shouldShow, "replay #1 does not")
        assertFalse(decide(replayCount = 1).shouldShow, "replay #2 does not")
        assertTrue(decide(replayCount = 5).shouldShow, "replay #6 shows")
        assertFalse(decide(replayCount = 3).shouldShow, "replay #4 does not")
    }

    @Test
    fun removeAdsOwnedNeverShows_butStillAdvances() {
        // Even on a would-be-show replay (#3), entitlement suppresses.
        val d = decide(replayCount = 2, removeAdsOwned = true)
        assertFalse(d.shouldShow, "entitled players never see an interstitial")
        assertEquals(3, d.nextState.replayCount, "counter still advances so cadence stays positional")
    }

    @Test
    fun firstSessionNeverShows_butStillAdvances() {
        val d = decide(replayCount = 2, isFirstSession = true)
        assertFalse(d.shouldShow, "first session is an ad-free grace window")
        assertEquals(3, d.nextState.replayCount, "counter still advances during the first session")
    }

    @Test
    fun bothSuppressorsTogetherNeverShow() {
        assertFalse(decide(replayCount = 2, removeAdsOwned = true, isFirstSession = true).shouldShow)
    }

    @Test
    fun suppressionAppliesAcrossAllReplays() {
        var state = InterstitialState()
        repeat(9) {
            val d = InterstitialPolicy.decide(state, removeAdsOwned = true, isFirstSession = false)
            assertFalse(d.shouldShow, "entitled: never show on any replay")
            state = d.nextState
        }
        assertEquals(9, state.replayCount)
    }
}
