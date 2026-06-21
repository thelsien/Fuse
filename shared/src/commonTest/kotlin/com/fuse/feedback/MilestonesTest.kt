package com.fuse.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * FEL-6 — the PURE milestone-detection decision driving the celebration VISUAL. Runs on JVM
 * and iOS Native. Also pins that the haptic/sound/visual all read the SAME shared [MILESTONES]
 * set, so the three channels can never disagree on what a milestone is.
 */
class MilestonesTest {

    @Test
    fun milestoneSetIsTheNotablePowersOfTwo() {
        assertEquals(setOf(512, 1024, 2048), MILESTONES)
    }

    @Test
    fun coordinatorsShareTheOneMilestoneSet() {
        // The same instance is reused, not a copy — one definition, three consumers.
        assertSame(MILESTONES, HapticsCoordinator.MILESTONES)
        assertSame(MILESTONES, SoundCoordinator.MILESTONES)
    }

    @Test
    fun reachesMilestoneForEachThreshold() {
        assertEquals(512, milestoneReached(listOf(512)))
        assertEquals(1024, milestoneReached(listOf(1024)))
        assertEquals(2048, milestoneReached(listOf(2048)))
    }

    @Test
    fun noMilestoneForNonMilestoneMerges() {
        assertNull(milestoneReached(listOf(4, 8, 16, 32, 64, 128, 256)))
    }

    @Test
    fun noMilestoneForAnEmptyMove() {
        assertNull(milestoneReached(emptyList()))
    }

    @Test
    fun returnsHighestWhenMultipleMilestonesInOneMove() {
        // A single move that produces both a 512 and a 1024 celebrates the bigger one.
        assertEquals(1024, milestoneReached(listOf(512, 1024)))
        assertEquals(2048, milestoneReached(listOf(2048, 512, 1024)))
    }

    @Test
    fun ignoresNonMilestonesAlongsideAMilestone() {
        // Non-milestone merges in the same move don't suppress a real milestone.
        assertEquals(512, milestoneReached(listOf(8, 512, 256)))
    }

    @Test
    fun aboveTopMilestoneIsNotAMilestone() {
        // 4096 is past the win target; only the named thresholds celebrate.
        assertNull(milestoneReached(listOf(4096)))
        assertEquals(2048, milestoneReached(listOf(4096, 2048)))
    }

    @Test
    fun everyMilestoneValueIsDetected() {
        for (m in MILESTONES) {
            assertTrue(milestoneReached(listOf(m)) == m, "milestone $m must be detected")
        }
    }
}
