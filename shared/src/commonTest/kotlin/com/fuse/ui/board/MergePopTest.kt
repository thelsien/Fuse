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

    // ---- FEL-3: spawn id on the transition --------------------------------

    @Test
    fun fromOutcomeCarriesSpawnIdAndMergeResults() {
        val t = BoardTransition.fromOutcome(
            merges = listOf(merge(resultId = 7, value = 8)),
            spawnedId = 42L,
        )
        assertTrue(t.isMergeResult(7), "merge result preserved")
        assertTrue(t.isSpawn(42), "spawned id is recognised")
        assertEquals(42L, t.spawnedId)
        assertFalse(t.isSpawn(7), "a merge result is not a spawn")
        assertFalse(t.isSpawn(99), "unrelated id is not a spawn")
    }

    @Test
    fun spawnAndMergeAreMutuallyExclusivePerTile() {
        // Pathological input: the same id is BOTH a merge result and the claimed spawn.
        // The merge wins; the id must NOT be reported as a spawn (no double animation).
        val t = BoardTransition.fromOutcome(
            merges = listOf(merge(resultId = 5, value = 8)),
            spawnedId = 5L,
        )
        assertTrue(t.isMergeResult(5))
        assertFalse(t.isSpawn(5), "an id that is a merge result is never a spawn")
        assertNull(t.spawnedId, "the conflicting spawn id is dropped")
    }

    @Test
    fun fromOutcomeWithSpawnButNoMergesIsNotNone() {
        val t = BoardTransition.fromOutcome(merges = emptyList(), spawnedId = 3L)
        assertTrue(t != BoardTransition.None, "a spawn-only move still has something to animate")
        assertTrue(t.isSpawn(3))
        assertFalse(t.isMergeResult(3))
    }

    @Test
    fun fromOutcomeWithNothingIsNone() {
        assertEquals(BoardTransition.None, BoardTransition.fromOutcome(emptyList(), spawnedId = null))
    }

    @Test
    fun fromMergesHasNoSpawnId() {
        val t = BoardTransition.fromMerges(listOf(merge(resultId = 7, value = 8)))
        assertNull(t.spawnedId, "fromMerges carries no spawn (back-compat)")
        assertFalse(t.isSpawn(7))
    }

    @Test
    fun noneHasNoSpawn() {
        assertNull(BoardTransition.None.spawnedId)
        assertFalse(BoardTransition.None.isSpawn(1))
    }

    // ---- FEL-3: spawn entrance timing (the "after movement settles" gate) -

    @Test
    fun spawnEntranceIsZeroDuringTheSlide() {
        // Default timing: slide 110ms, entrance 140ms. Anywhere up to and including the end
        // of the slide, the entrance progress is exactly 0 — the tile is hidden while things
        // are still moving. This IS the FEL-3 acceptance criterion, stated numerically.
        for (t in listOf(0, 1, 50, 109, 110)) {
            assertEquals(
                0f,
                spawnEntranceProgress(elapsedMs = t, slideMs = 110, entranceMs = 140),
                "entrance must be 0 at t=$t (during/at end of the 110ms slide)",
            )
        }
    }

    @Test
    fun spawnEntranceRampsAfterTheSlideThenSaturates() {
        // Just after the slide it starts moving off zero.
        assertTrue(spawnEntranceProgress(140, slideMs = 110, entranceMs = 140) > 0f)
        // Halfway through the entrance window (~70ms past the slide) ≈ 0.5.
        assertEquals(0.5f, spawnEntranceProgress(180, slideMs = 110, entranceMs = 140), absoluteTolerance = 0.01f)
        // After slide + full entrance it is fully in, and clamps there.
        assertEquals(1f, spawnEntranceProgress(110 + 140, slideMs = 110, entranceMs = 140))
        assertEquals(1f, spawnEntranceProgress(10_000, slideMs = 110, entranceMs = 140))
    }

    @Test
    fun spawnEntranceSnapsInUnderReducedMotion() {
        // Reduced motion: slide & entrance are ~1ms, so the entrance is effectively immediate
        // (no long fade) — but still gated to AFTER the (1ms) slide.
        assertEquals(0f, spawnEntranceProgress(elapsedMs = 1, slideMs = 1, entranceMs = 1))
        assertEquals(1f, spawnEntranceProgress(elapsedMs = 2, slideMs = 1, entranceMs = 1))
    }

    @Test
    fun spawnEntranceScaleGrowsFromStartToOne() {
        assertEquals(SPAWN_START_SCALE, spawnEntranceScale(0f), absoluteTolerance = 1e-4f)
        assertEquals(1f, spawnEntranceScale(1f), absoluteTolerance = 1e-4f)
        assertTrue(spawnEntranceScale(0.5f) > spawnEntranceScale(0f))
        assertTrue(spawnEntranceScale(1f) > spawnEntranceScale(0.5f))
        // Clamps out-of-range progress.
        assertEquals(spawnEntranceScale(0f), spawnEntranceScale(-1f))
        assertEquals(spawnEntranceScale(1f), spawnEntranceScale(2f))
        // Starts visibly below resting scale so it reads as a "grow in".
        assertTrue(spawnEntranceScale(0f) < 1f)
    }
}
