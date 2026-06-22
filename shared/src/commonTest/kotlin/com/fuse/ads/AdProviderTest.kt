package com.fuse.ads

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ADS-0 (Sprint 8 spike) — tests for the FAKEABLE part of the ad seam: the [AdProvider]
 * abstraction and its [NoOpAdProvider] default. The native ad code itself (Android Mobile Ads,
 * the iOS Swift bridge) is a thin platform actual verified by LAUNCHING on a device/simulator, not
 * unit-tested — so these tests cover only the pure, cross-platform surface (runs on JVM via
 * :shared:testDebugUnitTest and Kotlin/Native via :shared:iosSimulatorArm64Test).
 */
class AdProviderTest {

    @Test
    fun noOpInitializeDoesNotThrow() {
        // The default provider must be a safe no-op (used in tests/previews and ads-disabled builds).
        NoOpAdProvider.initialize()
    }

    @Test
    fun noOpShowReturnsFailed() = runTest {
        // No real ad to show → the deterministic Failed outcome, never a crash.
        assertEquals(AdResult.Failed, NoOpAdProvider.showRewardedTestAd())
    }

    @Test
    fun adResultCoversTheCoarseOutcomes() {
        // Guards the spike's documented result vocabulary; ADS-1 may refine these.
        val expected = setOf(
            AdResult.Shown,
            AdResult.Rewarded,
            AdResult.Dismissed,
            AdResult.NoFill,
            AdResult.Failed,
        )
        assertEquals(expected, AdResult.entries.toSet())
    }
}
