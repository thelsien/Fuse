package com.fuse.ads

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * ADS-1 (Sprint 8) — tests for the FAKEABLE part of the ad seam: the [AdProvider] abstraction and its
 * [NoOpAdProvider] default. The native ad code itself (Android Mobile Ads, the iOS Swift bridge) is a
 * thin platform actual verified by LAUNCHING, not unit-tested — so these cover only the pure,
 * cross-platform surface (runs on JVM via :shared:testDebugUnitTest and Kotlin/Native via
 * :shared:iosSimulatorArm64Test). The scripted [FakeAdProvider] is exercised in [FakeAdProviderTest]
 * and [AdManagerTest].
 */
class AdProviderTest {

    @Test
    fun noOpInitializeDoesNotThrow() {
        // The default provider must be a safe no-op (used in previews and ads-disabled builds).
        NoOpAdProvider.initialize()
    }

    @Test
    fun noOpLoadFailsAndIsNeverReady() = runTest {
        // No SDK → load can't succeed and nothing is ever ready, for both formats.
        for (format in AdFormat.entries) {
            assertFalse(NoOpAdProvider.load(format), "NoOp load($format) is false")
            assertFalse(NoOpAdProvider.isReady(format), "NoOp isReady($format) is false")
        }
    }

    @Test
    fun noOpShowReturnsNotReady() = runTest {
        // Nothing loaded → the deterministic NotReady outcome, never a crash, for both formats.
        for (format in AdFormat.entries) {
            assertEquals(AdResult.NotReady, NoOpAdProvider.show(format))
        }
    }

    @Test
    fun noOpRewardedTestConvenienceMapsEmptyLoadToNoFill() = runTest {
        // The retained ADS-0 convenience (load+show REWARDED) collapses a failed load to NoFill.
        assertEquals(AdResult.NoFill, NoOpAdProvider.showRewardedTestAd())
    }

    @Test
    fun adFormatCoversRewardedAndInterstitial() {
        assertEquals(setOf(AdFormat.REWARDED, AdFormat.INTERSTITIAL), AdFormat.entries.toSet())
    }

    @Test
    fun adResultCoversBothFormatsOutcomes() {
        // Guards the documented result vocabulary for rewarded + interstitial.
        val expected = setOf(
            AdResult.Shown,
            AdResult.Rewarded,
            AdResult.Dismissed,
            AdResult.Completed,
            AdResult.NoFill,
            AdResult.NotReady,
            AdResult.Failed,
        )
        assertEquals(expected, AdResult.entries.toSet())
    }

    @Test
    fun adUnitIdsAreGooglePublicTestUnits() {
        // Every unit must be under Google's PUBLIC test publisher — no real IDs committed.
        val testPublisher = "ca-app-pub-3940256099942544/"
        for (format in AdFormat.entries) {
            assertEquals(true, AdUnitIds.android(format).startsWith(testPublisher))
            assertEquals(true, AdUnitIds.ios(format).startsWith(testPublisher))
        }
        // Rewarded and interstitial must be DISTINCT units per platform.
        assertEquals(false, AdUnitIds.android(AdFormat.REWARDED) == AdUnitIds.android(AdFormat.INTERSTITIAL))
        assertEquals(false, AdUnitIds.ios(AdFormat.REWARDED) == AdUnitIds.ios(AdFormat.INTERSTITIAL))
    }
}
