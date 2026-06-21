package com.fuse.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FEL-5 — pure-decision tests for [SoundCoordinator] + the pure [SoundCoordinator.mergePitch]
 * mapping, driven by a recording [FakeSound]. These run on every target (JVM + iOS Native)
 * and pin: the climbing merge tone, the milestone/win stings, the layering rules, the mute
 * gate, and the monotone-pitch property. Mirrors [HapticsCoordinatorTest].
 */
class SoundCoordinatorTest {

    private fun coordinator(enabled: Boolean = true): Pair<SoundCoordinator, FakeSound> {
        val fake = FakeSound()
        val settings = SoundSettings(soundEnabled = enabled)
        return SoundCoordinator(fake, settings) to fake
    }

    // ---- merge tone ----------------------------------------------------------------

    @Test
    fun moveWithAMergePlaysOneTone() {
        val (c, fake) = coordinator()
        c.onMove(mergedValues = listOf(4), justWon = false)
        assertEquals(listOf("mergeTone"), fake.calls)
    }

    @Test
    fun moveWithMultipleMergesPlaysExactlyOneTone() {
        val (c, fake) = coordinator()
        c.onMove(mergedValues = listOf(4, 8, 16), justWon = false)
        assertEquals(listOf("mergeTone"), fake.calls, "audio is per-move, not per-merge")
    }

    @Test
    fun moveWithNoMergeIsSilent() {
        val (c, fake) = coordinator()
        c.onMove(mergedValues = emptyList(), justWon = false)
        assertTrue(fake.calls.isEmpty(), "a pure slide must produce no sound")
    }

    @Test
    fun tonePitchUsesTheHighestMergedValueOfTheMove() {
        val (c, fake) = coordinator()
        c.onMove(mergedValues = listOf(4, 64, 16), justWon = false)
        assertEquals(1, fake.tonePitches.size)
        assertEquals(SoundCoordinator.mergePitch(64), fake.tonePitches.single())
    }

    @Test
    fun higherMergeProducesAHigherTonePitch() {
        val (lo, loFake) = coordinator()
        val (hi, hiFake) = coordinator()
        lo.onMove(mergedValues = listOf(4), justWon = false)
        hi.onMove(mergedValues = listOf(256), justWon = false)
        assertTrue(
            hiFake.tonePitches.single() > loFake.tonePitches.single(),
            "a bigger merged tile must sound higher (the climbing feel)",
        )
    }

    // ---- stings: layering & precedence ---------------------------------------------

    @Test
    fun milestoneMovePlaysToneThenSting() {
        for (milestone in SoundCoordinator.MILESTONES.filter { it != 2048 }) {
            val (c, fake) = coordinator()
            c.onMove(mergedValues = listOf(8, milestone), justWon = false)
            assertEquals(
                listOf("mergeTone", "milestoneSting"),
                fake.calls,
                "reaching $milestone should play the tone AND ring the milestone sting",
            )
            // The tone is pitched to the milestone (the highest value this move).
            assertEquals(SoundCoordinator.mergePitch(milestone), fake.tonePitches.single())
        }
    }

    @Test
    fun nonMilestoneMergeOnlyPlaysTone() {
        val (c, fake) = coordinator()
        // 256 is a merge but below the milestone set: tone only, no sting.
        c.onMove(mergedValues = listOf(256), justWon = false)
        assertEquals(listOf("mergeTone"), fake.calls)
    }

    @Test
    fun justWonPlaysToneThenWinStingNotMilestoneSting() {
        val (c, fake) = coordinator()
        // First 2048 is also a 2048 milestone, but justWon must win: win sting, not milestone.
        c.onMove(mergedValues = listOf(2048), justWon = true)
        assertEquals(listOf("mergeTone", "winSting"), fake.calls)
        assertEquals(SoundCoordinator.mergePitch(2048), fake.tonePitches.single())
    }

    // ---- mute gate ------------------------------------------------------------------

    @Test
    fun mutedSilencesTheMergeTone() {
        val (c, fake) = coordinator(enabled = false)
        c.onMove(mergedValues = listOf(4), justWon = false)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun mutedSilencesTheMilestoneSting() {
        val (c, fake) = coordinator(enabled = false)
        c.onMove(mergedValues = listOf(1024), justWon = false)
        assertTrue(fake.calls.isEmpty())
    }

    @Test
    fun mutedSilencesTheWinSting() {
        val (c, fake) = coordinator(enabled = false)
        c.onMove(mergedValues = listOf(2048), justWon = true)
        assertTrue(fake.calls.isEmpty())
    }

    // ---- thresholds -----------------------------------------------------------------

    @Test
    fun milestoneSetIsTheDocumentedThresholds() {
        assertEquals(setOf(512, 1024, 2048), SoundCoordinator.MILESTONES)
    }

    // ---- pure mergePitch mapping ----------------------------------------------------

    @Test
    fun mergePitchIsStrictlyMonotonicAcrossTheValueLadder() {
        val ladder = listOf(4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)
        val pitches = ladder.map { SoundCoordinator.mergePitch(it) }
        for (i in 1 until pitches.size) {
            assertTrue(
                pitches[i] > pitches[i - 1],
                "pitch must strictly increase: ${ladder[i - 1]}→${ladder[i]} " +
                    "gave ${pitches[i - 1]}→${pitches[i]}",
            )
        }
    }

    @Test
    fun mergePitchIsBoundedToZeroOneAndClampsTheExtremes() {
        assertEquals(0f, SoundCoordinator.mergePitch(4), "floor (4) maps to 0f")
        assertEquals(1f, SoundCoordinator.mergePitch(2048), "ceiling (2048) maps to 1f")
        // Clamp below/above the modelled range rather than running away.
        assertEquals(0f, SoundCoordinator.mergePitch(2))
        assertEquals(1f, SoundCoordinator.mergePitch(4096))
        for (v in listOf(4, 64, 512, 2048)) {
            val p = SoundCoordinator.mergePitch(v)
            assertTrue(p in 0f..1f, "pitch($v)=$p out of [0,1]")
        }
    }

    @Test
    fun mergePitchIsDeterministic() {
        assertEquals(SoundCoordinator.mergePitch(128), SoundCoordinator.mergePitch(128))
    }
}
