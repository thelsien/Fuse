package com.fuse.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FEL-7 — pure scale + alpha envelopes for the combo "x{n}" badge. The Compose [ComboEffect] is a
 * thin renderer over these numbers, so the pop/fade shape is unit-tested here on JVM and iOS Native
 * (no Compose, no clock).
 */
class ComboEffectTest {

    // ---- comboScale ------------------------------------------------------

    @Test
    fun scaleStartsSmallAndReachesThePeakEarly() {
        val peak = 1.5f
        // Starts below the peak (a pop-in from small).
        assertTrue(comboScale(0f, peak) < peak, "should start below peak")
        // Reaches the peak around the early peak point.
        assertEquals(peak, comboScale(0.30f, peak), absoluteTolerance = 1e-3f)
    }

    @Test
    fun scaleSettlesBackTowardThePeakRestingValueAfterTheOvershoot() {
        val peak = 1.5f
        val atPeak = comboScale(0.30f, peak)
        val atEnd = comboScale(1f, peak)
        assertTrue(atEnd <= atPeak, "should settle down from the peak, not keep growing")
        assertTrue(atEnd > 0f, "stays positive")
    }

    @Test
    fun scaleClampsOutOfRangeProgress() {
        val peak = 1.4f
        assertEquals(comboScale(0f, peak), comboScale(-1f, peak))
        assertEquals(comboScale(1f, peak), comboScale(2f, peak))
    }

    @Test
    fun biggerPeakAlwaysScalesAtLeastAsLarge() {
        // Escalation: a higher intensity (bigger peak) is never smaller at the same progress.
        for (i in 0..10) {
            val p = i / 10f
            assertTrue(
                comboScale(p, 1.6f) >= comboScale(p, 1.0f),
                "bigger peak should not scale smaller at progress=$p",
            )
        }
    }

    // ---- comboAlpha ------------------------------------------------------

    @Test
    fun alphaFadesInHoldsThenFadesOut() {
        assertEquals(0f, comboAlpha(0f), absoluteTolerance = 1e-4f)
        assertEquals(1f, comboAlpha(0.4f), absoluteTolerance = 1e-4f)   // held visible
        assertEquals(0f, comboAlpha(1f), absoluteTolerance = 1e-4f)
        // Rising into the hold, falling out of it.
        assertTrue(comboAlpha(0.07f) < comboAlpha(0.4f), "should be rising in")
        assertTrue(comboAlpha(0.9f) < comboAlpha(0.4f), "should be fading out")
    }

    @Test
    fun alphaStaysBounded() {
        for (i in 0..20) {
            val a = comboAlpha(i / 20f)
            assertTrue(a in 0f..1f, "alpha out of [0,1]: $a at ${i / 20f}")
        }
    }

    @Test
    fun alphaClampsOutOfRangeProgress() {
        assertEquals(comboAlpha(0f), comboAlpha(-1f))
        assertEquals(comboAlpha(1f), comboAlpha(2f))
    }
}
