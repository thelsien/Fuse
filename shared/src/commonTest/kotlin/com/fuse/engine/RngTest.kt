package com.fuse.engine

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RngTest {

    // ---- Cross-platform determinism: EXACT expected sequence ---------------
    //
    // These literals are the SplitMix64 output for the given seeds, computed
    // independently of this code. They run identically on the JVM target
    // (:shared:testDebugUnitTest) and the iOS/Native target
    // (:shared:iosSimulatorArm64Test). If either platform's bit math diverged,
    // these assertions would fail — that is the whole point of the story.

    @Test
    fun nextLongMatchesKnownSplitMix64SequenceForSeed42() {
        val rng = SeededRng(42L)
        val expected = longArrayOf(
            -4767286540954276203L,
            2949826092126892291L,
            5139283748462763858L,
            6349198060258255764L,
            701532786141963250L,
        )
        for (e in expected) {
            assertEquals(e, rng.nextLong())
        }
    }

    @Test
    fun nextIntMatchesKnownSequenceForSeed7() {
        // Bound 4 == an emptyCells-style pick on a tiny board.
        val rng = SeededRng(7L)
        val expected = intArrayOf(2, 3, 0, 0, 3, 3, 0, 0)
        for (e in expected) {
            assertEquals(e, rng.nextInt(4))
        }
    }

    @Test
    fun nextIntBound16MatchesKnownSequenceForSeed7() {
        // Bound 16 == picking among all cells of a full 4x4 board.
        val rng = SeededRng(7L)
        val expected = intArrayOf(2, 11, 0, 8)
        for (e in expected) {
            assertEquals(e, rng.nextInt(16))
        }
    }

    @Test
    fun nextDoubleMatchesKnownSequenceForSeed123() {
        val rng = SeededRng(123L)
        val expected = doubleArrayOf(
            0.70649122176370671,
            0.97659664832502702,
            0.85966223893360116,
        )
        for (e in expected) {
            assertEquals(e, rng.nextDouble(), absoluteTolerance = 0.0)
        }
    }

    // ---- Reproducibility ---------------------------------------------------

    @Test
    fun sameSeedProducesSameSequence() {
        val a = SeededRng(2026L)
        val b = SeededRng(2026L)
        repeat(1_000) {
            assertEquals(a.nextLong(), b.nextLong())
        }
    }

    @Test
    fun differentSeedsProduceDifferentSequences() {
        val a = SeededRng(1L)
        val b = SeededRng(2L)
        // Practically certain to differ within the first few draws.
        val differ = (0 until 16).any { a.nextLong() != b.nextLong() }
        assertTrue(differ, "different seeds should not yield identical sequences")
    }

    // ---- nextInt contract --------------------------------------------------

    @Test
    fun nextIntStaysInRange() {
        val rng = SeededRng(99L)
        repeat(50_000) {
            val bound = 1 + (it % 17) // bounds 1..17, covers pow2 and non-pow2
            val v = rng.nextInt(bound)
            assertTrue(v in 0 until bound, "nextInt($bound) returned $v")
        }
    }

    @Test
    fun nextIntBoundOneAlwaysReturnsZero() {
        val rng = SeededRng(5L)
        repeat(100) { assertEquals(0, rng.nextInt(1)) }
    }

    @Test
    fun nextIntRejectsNonPositiveBound() {
        val rng = SeededRng(5L)
        assertFailsWith<IllegalArgumentException> { rng.nextInt(0) }
        assertFailsWith<IllegalArgumentException> { rng.nextInt(-3) }
    }

    @Test
    fun nextIntIsReasonablyUniform() {
        // Chi-square-ish smell test: 16 buckets over a large sample should each
        // land within +/-20% of the expected count. Guards against a badly
        // biased generator without being flaky for a fixed seed.
        val rng = SeededRng(31337L)
        val buckets = IntArray(16)
        val n = 160_000
        repeat(n) { buckets[rng.nextInt(16)]++ }
        val expected = n / 16
        for ((i, count) in buckets.withIndex()) {
            assertTrue(
                count > expected * 0.8 && count < expected * 1.2,
                "bucket $i count $count too far from expected $expected",
            )
        }
    }

    // ---- nextDouble contract ----------------------------------------------

    @Test
    fun nextDoubleStaysInUnitInterval() {
        val rng = SeededRng(7L)
        repeat(50_000) {
            val d = rng.nextDouble()
            assertTrue(d >= 0.0 && d < 1.0, "nextDouble returned $d")
        }
    }

    @Test
    fun nextDoubleSplitsRoughlyNinetyTenAtThreshold() {
        // Models the ENG-6 spawn roll: < 0.9 -> spawn a 2, else spawn a 4.
        val rng = SeededRng(2048L)
        var twos = 0
        val n = 100_000
        repeat(n) { if (rng.nextDouble() < 0.9) twos++ }
        val ratio = twos.toDouble() / n
        assertTrue(ratio > 0.88 && ratio < 0.92, "2-spawn ratio was $ratio")
    }

    // ---- State save / restore (ENG-9 replay) ------------------------------

    @Test
    fun fromStateReproducesExactContinuation() {
        val rng = SeededRng(555L)
        repeat(10) { rng.nextLong() } // advance partway through the stream
        val saved = rng.state

        val expectedTail = LongArray(20) { rng.nextLong() }

        // A fresh generator restored from the saved state continues identically.
        val resumed = SeededRng.fromState(saved)
        val actualTail = LongArray(20) { resumed.nextLong() }

        assertTrue(expectedTail.contentEquals(actualTail))
    }

    @Test
    fun serializesAndRestoresMidStream() {
        val rng = SeededRng(777L)
        repeat(5) { rng.nextLong() }

        val encoded = Json.encodeToString(SeededRng.serializer(), rng)
        val decoded = Json.decodeFromString(SeededRng.serializer(), encoded)

        // The decoded generator must produce the exact same continuation.
        repeat(50) {
            assertEquals(rng.nextLong(), decoded.nextLong())
        }
    }

    @Test
    fun stateFieldRoundTripsThroughFromState() {
        val rng = SeededRng(-9999L)
        repeat(3) { rng.nextLong() }
        val clone = SeededRng.fromState(rng.state)
        assertEquals(rng.state, clone.state)
        assertEquals(rng.nextLong(), clone.nextLong())
    }

    // ---- Injectability sanity: usable through the interface ----------------

    @Test
    fun usableThroughRngInterface() {
        val rng: Rng = SeededRng(1L)
        // Picks a random empty-cell index the way ENG-6 will.
        val emptyCount = 9
        val idx = rng.nextInt(emptyCount)
        assertTrue(idx in 0 until emptyCount)
        val spawnValue = if (rng.nextDouble() < 0.9) 2 else 4
        assertTrue(spawnValue == 2 || spawnValue == 4)
    }
}
