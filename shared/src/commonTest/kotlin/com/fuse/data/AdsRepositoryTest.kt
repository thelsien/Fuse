package com.fuse.data

import com.fuse.ads.InterstitialState
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADS-4 — round-trip tests for [SettingsAdsRepository] over an in-memory [MapSettings], so they run
 * deterministically on every target (JVM + iOS Native) with no real SharedPreferences/NSUserDefaults.
 *
 * Covers: the replay cap state persists across a "relaunch" (a fresh repo over the same store); the
 * first-session launch marker behaves (first launch ⇒ isFirstSession true, later launches false);
 * missing/corrupt blobs tolerate to defaults.
 */
class AdsRepositoryTest {

    private fun repo(settings: MapSettings = MapSettings()): SettingsAdsRepository =
        SettingsAdsRepository(settings)

    @Test
    fun defaultInterstitialStateIsZeroed() {
        assertEquals(InterstitialState(replayCount = 0), repo().loadInterstitialState())
    }

    @Test
    fun interstitialStateRoundTrips() {
        val r = repo()
        r.saveInterstitialState(InterstitialState(replayCount = 7))
        assertEquals(7, r.loadInterstitialState().replayCount)
    }

    @Test
    fun interstitialStatePersistsAcrossARelaunch() {
        val settings = MapSettings()
        repo(settings).saveInterstitialState(InterstitialState(replayCount = 5))
        // A brand-new repository over the SAME settings (simulates a relaunch) sees it -> the cap
        // cadence is real, not reset per session.
        assertEquals(5, repo(settings).loadInterstitialState().replayCount, "cap counter survives relaunch")
    }

    @Test
    fun corruptInterstitialBlobTolerated() {
        val settings = MapSettings()
        settings.putString(SettingsAdsRepository.KEY_INTERSTITIAL, "{ not valid json !!")
        assertEquals(InterstitialState(), repo(settings).loadInterstitialState(), "corrupt -> default, no crash")
    }

    @Test
    fun launchCounterStartsAtZeroAndAdvances() {
        val r = repo()
        assertEquals(0, r.loadLaunchCount(), "no launches recorded yet")
        assertEquals(1, r.recordLaunch(), "first launch -> 1")
        assertEquals(2, r.recordLaunch(), "second launch -> 2")
    }

    @Test
    fun firstSessionTrueOnFirstLaunchFalseAfterwards() {
        val settings = MapSettings()
        val r = repo(settings)
        // Before any launch is recorded the default is also "first session" (count 0 <= 1).
        assertTrue(r.isFirstSession(), "fresh install is the first session")
        r.recordLaunch() // count -> 1, first launch
        assertTrue(r.isFirstSession(), "during the first session (count 1) still first session")
        // Relaunch: a new repo over the same store records the 2nd launch.
        val r2 = repo(settings)
        r2.recordLaunch() // count -> 2
        assertFalse(r2.isFirstSession(), "second launch is no longer the first session")
    }

    @Test
    fun launchCounterPersistsAcrossARelaunch() {
        val settings = MapSettings()
        repo(settings).recordLaunch()
        repo(settings).recordLaunch()
        assertEquals(2, repo(settings).loadLaunchCount(), "launch count survives relaunch")
    }

    @Test
    fun noOpRepositorySuppressesByAlwaysFirstSession() {
        assertTrue(NoOpAdsRepository.isFirstSession(), "NoOp default is always first session (ad-free)")
        assertEquals(0, NoOpAdsRepository.recordLaunch(), "NoOp never advances")
        assertEquals(InterstitialState(), NoOpAdsRepository.loadInterstitialState())
    }
}
