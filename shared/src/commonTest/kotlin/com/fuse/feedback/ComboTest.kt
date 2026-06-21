package com.fuse.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FEL-7 — the PURE combo decision + escalation curve driving the combo COUNTER and its visual
 * cue. Runs on JVM and iOS Native. A combo is a single move that produced >= 2 merges; the cue
 * escalates with the count via [comboIntensity].
 */
class ComboTest {

    @Test
    fun comboCountIsTheNumberOfMerges() {
        assertEquals(0, comboCount(emptyList()))
        assertEquals(1, comboCount(listOf(4)))
        assertEquals(2, comboCount(listOf(4, 8)))
        assertEquals(3, comboCount(listOf(4, 4, 8)))
        assertEquals(4, comboCount(listOf(4, 8, 16, 32)))
    }

    @Test
    fun aMoveIsAComboOnlyAtTwoOrMoreMerges() {
        assertFalse(isCombo(emptyList()))        // pure slide
        assertFalse(isCombo(listOf(4)))          // single merge — not a combo
        assertTrue(isCombo(listOf(4, 8)))        // x2
        assertTrue(isCombo(listOf(4, 8, 16)))    // x3
    }

    @Test
    fun minComboThresholdIsTwo() {
        assertEquals(2, MIN_COMBO)
    }

    @Test
    fun intensityIsZeroBelowTheComboThreshold() {
        assertEquals(0f, comboIntensity(0))
        assertEquals(0f, comboIntensity(1))
    }

    @Test
    fun intensityIsMonotonicNonDecreasingInCount() {
        var prev = comboIntensity(MIN_COMBO)
        for (n in (MIN_COMBO + 1)..12) {
            val cur = comboIntensity(n)
            assertTrue(cur >= prev, "intensity must not decrease: count=$n got $cur < $prev")
            prev = cur
        }
    }

    @Test
    fun intensityIsBoundedToUnitRange() {
        for (n in 0..50) {
            val v = comboIntensity(n)
            assertTrue(v in 0f..1f, "intensity out of [0,1] for count=$n: $v")
        }
    }

    @Test
    fun intensityRisesFromTheFirstComboAndCapsAtOne() {
        // The first real combo (x2) is gentle; it climbs and reaches full by MAX_COMBO_TIER.
        assertEquals(0f, comboIntensity(MIN_COMBO))
        assertTrue(comboIntensity(3) > 0f)
        assertEquals(1f, comboIntensity(MAX_COMBO_TIER))
        assertEquals(1f, comboIntensity(MAX_COMBO_TIER + 5)) // stays capped beyond the tier
    }
}
