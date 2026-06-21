package com.fuse.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FEL-4 — pure-decision tests for [HapticsCoordinator], driven by a recording
 * [FakeHaptics]. These run on every target (JVM + iOS Native) and pin the tick/thunk/buzz
 * mapping, the milestone threshold set, and the enable/disable gate.
 */
class HapticsCoordinatorTest {

    private fun coordinator(enabled: Boolean = true): Pair<HapticsCoordinator, FakeHaptics> {
        val fake = FakeHaptics()
        val settings = HapticsSettings(hapticsEnabled = enabled)
        return HapticsCoordinator(fake, settings) to fake
    }

    @Test
    fun moveWithAMergeTicks() {
        val (c, fake) = coordinator()
        c.onMove(mergedValues = listOf(4), justWon = false)
        assertEquals(listOf("tick"), fake.calls)
    }

    @Test
    fun moveWithMultipleMergesStillTicksExactlyOnce() {
        val (c, fake) = coordinator()
        c.onMove(mergedValues = listOf(4, 8, 16), justWon = false)
        assertEquals(listOf("tick"), fake.calls, "feedback is per-move, not per-merge")
    }

    @Test
    fun moveWithNoMergeIsSilent() {
        val (c, fake) = coordinator()
        c.onMove(mergedValues = emptyList(), justWon = false)
        assertTrue(fake.calls.isEmpty(), "a pure slide must produce no haptic")
    }

    @Test
    fun reachingAMilestoneThunksInsteadOfTicking() {
        for (milestone in HapticsCoordinator.MILESTONES) {
            val (c, fake) = coordinator()
            // The move also produced a smaller merge; the milestone still wins (thunk only).
            c.onMove(mergedValues = listOf(8, milestone), justWon = false)
            assertEquals(
                listOf("thunk"),
                fake.calls,
                "reaching $milestone should thunk (and not also tick)",
            )
        }
    }

    @Test
    fun nonMilestoneMergeOnlyTicks() {
        val (c, fake) = coordinator()
        // 256 is a merge but below the milestone set, so it is a plain tick.
        c.onMove(mergedValues = listOf(256), justWon = false)
        assertEquals(listOf("tick"), fake.calls)
    }

    @Test
    fun justWonThunks() {
        val (c, fake) = coordinator()
        c.onMove(mergedValues = listOf(2048), justWon = true)
        assertEquals(listOf("thunk"), fake.calls)
    }

    @Test
    fun blockedMoveBuzzes() {
        val (c, fake) = coordinator()
        c.onBlocked()
        assertEquals(listOf("buzz"), fake.calls)
    }

    @Test
    fun disabledSilencesTick() {
        val (c, fake) = coordinator(enabled = false)
        c.onMove(mergedValues = listOf(4), justWon = false)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun disabledSilencesThunk() {
        val (c, fake) = coordinator(enabled = false)
        c.onMove(mergedValues = listOf(1024), justWon = false)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun disabledSilencesBuzz() {
        val (c, fake) = coordinator(enabled = false)
        c.onBlocked()
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun milestoneSetIsTheDocumentedThresholds() {
        assertEquals(setOf(512, 1024, 2048), HapticsCoordinator.MILESTONES)
    }
}
