package com.fuse.ui.board

import com.fuse.engine.BoardMergeEvent
import com.fuse.engine.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FEL-2 — pure merge-pop model + intensity curves. These run on JVM and iOS Native and
 * are the testable core of the "intensity scales with tile tier" acceptance criterion;
 * the Compose pop/glow itself is a thin renderer over these numbers.
 */
class MergePopTest {

    private fun merge(resultId: Long, value: Int): BoardMergeEvent =
        BoardMergeEvent(
            resultingValue = value,
            resultId = resultId,
            sourceIdA = resultId * 10,
            sourceIdB = resultId * 10 + 1,
            sourcePosA = Position(0, 0),
            sourcePosB = Position(0, 1),
            resultPos = Position(0, 0),
        )

    // ---- mergeIntensity --------------------------------------------------

    @Test
    fun intensityIsZeroForNonMergeAndSmallestTier() {
        assertEquals(0f, mergeIntensity(2)) // can't be a merge result
        assertEquals(0f, mergeIntensity(4)) // smallest real merge → floor
    }

    @Test
    fun intensityIsOneAtBrandTierAndCappedAbove() {
        assertEquals(1f, mergeIntensity(2048))
        assertEquals(1f, mergeIntensity(4096)) // above the brand tier stays capped
        assertEquals(1f, mergeIntensity(8192))
    }

    @Test
    fun intensityRisesMonotonicallyWithTier() {
        val ladder = listOf(4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)
        val intensities = ladder.map { mergeIntensity(it) }
        for (i in 1 until intensities.size) {
            assertTrue(
                intensities[i] > intensities[i - 1],
                "intensity must increase from ${ladder[i - 1]} to ${ladder[i]}: $intensities",
            )
        }
    }

    @Test
    fun intensityStaysInUnitInterval() {
        for (v in listOf(2, 4, 8, 64, 1024, 2048, 65536)) {
            val i = mergeIntensity(v)
            assertTrue(i in 0f..1f, "intensity for $v out of [0,1]: $i")
        }
    }

    // ---- peak scale / glow alpha curves ----------------------------------

    @Test
    fun popPeakScaleSpansTheConfiguredRangeAndIsMonotonic() {
        assertEquals(1.10f, mergePopPeakScale(0f), absoluteTolerance = 1e-4f)
        assertEquals(1.30f, mergePopPeakScale(1f), absoluteTolerance = 1e-4f)
        assertTrue(mergePopPeakScale(0.5f) > mergePopPeakScale(0f))
        assertTrue(mergePopPeakScale(1f) > mergePopPeakScale(0.5f))
        // every pop, even the gentlest, overshoots above resting scale 1.0
        assertTrue(mergePopPeakScale(0f) > 1f)
    }

    @Test
    fun glowAlphaSpansTheConfiguredRangeAndIsMonotonic() {
        assertEquals(0.25f, mergeGlowPeakAlpha(0f), absoluteTolerance = 1e-4f)
        assertEquals(0.85f, mergeGlowPeakAlpha(1f), absoluteTolerance = 1e-4f)
        assertTrue(mergeGlowPeakAlpha(1f) > mergeGlowPeakAlpha(0f))
    }

    @Test
    fun higherTierResultPopsAndGlowsHarderThanLowerTier() {
        val small = mergeIntensity(8)
        val big = mergeIntensity(1024)
        assertTrue(mergePopPeakScale(big) > mergePopPeakScale(small))
        assertTrue(mergeGlowPeakAlpha(big) > mergeGlowPeakAlpha(small))
    }

    @Test
    fun curvesClampOutOfRangeIntensity() {
        assertEquals(mergePopPeakScale(0f), mergePopPeakScale(-1f))
        assertEquals(mergePopPeakScale(1f), mergePopPeakScale(2f))
        assertEquals(mergeGlowPeakAlpha(0f), mergeGlowPeakAlpha(-1f))
        assertEquals(mergeGlowPeakAlpha(1f), mergeGlowPeakAlpha(2f))
    }

    // ---- BoardTransition -------------------------------------------------

    @Test
    fun fromMergesMapsResultIdsToValues() {
        val t = BoardTransition.fromMerges(
            listOf(merge(resultId = 7, value = 8), merge(resultId = 9, value = 2048)),
        )
        assertTrue(t.isMergeResult(7))
        assertTrue(t.isMergeResult(9))
        assertFalse(t.isMergeResult(123))
        assertEquals(8, t.resultValue(7))
        assertEquals(2048, t.resultValue(9))
        assertNull(t.resultValue(123))
    }

    @Test
    fun fromEmptyMergesIsNone() {
        assertEquals(BoardTransition.None, BoardTransition.fromMerges(emptyList()))
        assertFalse(BoardTransition.None.isMergeResult(1))
    }
}
