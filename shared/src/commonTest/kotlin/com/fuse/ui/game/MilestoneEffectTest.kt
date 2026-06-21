package com.fuse.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FEL-6 — pure particle-burst layout + flash envelope. The Compose [MilestoneEffect] is a thin
 * renderer over these numbers, so the geometry/intensity rules are unit-tested here on JVM and
 * iOS Native (no Compose, no clock).
 */
class MilestoneEffectTest {

    // ---- milestoneParticles ----------------------------------------------

    @Test
    fun particleCountStaysWithinBounds() {
        for (v in listOf(512, 1024, 2048)) {
            val n = milestoneParticles(v).size
            assertTrue(n in 12..24, "particle count for $v out of [12,24]: $n")
        }
    }

    @Test
    fun higherTierMilestoneHasAtLeastAsManyParticles() {
        // Intensity rises with tier, so 2048 bursts no smaller than 512 (bigger celebration).
        val small = milestoneParticles(512).size
        val big = milestoneParticles(2048).size
        assertTrue(big >= small, "2048 burst ($big) should be >= 512 burst ($small)")
        assertTrue(big > small, "2048 should be strictly bigger than 512: $big vs $small")
    }

    @Test
    fun particlesFanAroundTheWholeCircle() {
        val particles = milestoneParticles(1024)
        // Angles are evenly spread; the max angle should be near (but below) a full turn.
        val twoPi = 2f * 3.1415927f
        val maxAngle = particles.maxOf { it.angleRad }
        assertTrue(maxAngle < twoPi, "angles must stay below a full turn")
        assertTrue(maxAngle > twoPi * 0.5f, "particles should span well past a half circle")
        // Distinct directions: no two particles share the same angle.
        assertEquals(particles.size, particles.map { it.angleRad }.toSet().size)
    }

    @Test
    fun particleFractionsAndAlphasAreBounded() {
        for (p in milestoneParticles(2048)) {
            assertTrue(p.distanceFraction in 0f..1f, "fraction out of [0,1]: ${p.distanceFraction}")
            assertTrue(p.baseAlpha in 0f..1f, "alpha out of [0,1]: ${p.baseAlpha}")
            assertTrue(p.sizeScale > 0f, "size scale must be positive: ${p.sizeScale}")
        }
    }

    @Test
    fun particleLayoutIsDeterministic() {
        // No RNG — same input gives the exact same layout (so the burst is test-stable).
        assertEquals(milestoneParticles(1024), milestoneParticles(1024))
    }

    // ---- milestoneFlashAlpha ---------------------------------------------

    @Test
    fun flashStartsAndEndsAtZero() {
        assertEquals(0f, milestoneFlashAlpha(0f), absoluteTolerance = 1e-4f)
        assertEquals(0f, milestoneFlashAlpha(1f), absoluteTolerance = 1e-4f)
    }

    @Test
    fun flashPeaksInTheEarlyWindowThenFades() {
        val peak = milestoneFlashAlpha(0.18f)
        assertTrue(peak > 0f, "flash must reach a visible peak")
        // Rises into the peak, then falls after it.
        assertTrue(milestoneFlashAlpha(0.1f) < peak, "should still be rising before the peak")
        assertTrue(milestoneFlashAlpha(0.5f) < peak, "should be fading after the peak")
        assertTrue(milestoneFlashAlpha(0.9f) < milestoneFlashAlpha(0.5f), "keeps fading toward 0")
    }

    @Test
    fun flashAlphaStaysBoundedAndDoesNotWhiteOut() {
        for (i in 0..20) {
            val a = milestoneFlashAlpha(i / 20f)
            assertTrue(a in 0f..0.45f, "flash alpha out of [0,0.45]: $a at ${i / 20f}")
        }
    }

    @Test
    fun flashClampsOutOfRangeProgress() {
        assertEquals(milestoneFlashAlpha(0f), milestoneFlashAlpha(-1f))
        assertEquals(milestoneFlashAlpha(1f), milestoneFlashAlpha(2f))
    }
}
