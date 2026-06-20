package com.fuse.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UIB-1 pure-logic tests for the numeral-sizing helper. No Compose / no device —
 * these live in commonTest and run on every platform's test target.
 */
class BoardViewLogicTest {

    @Test
    fun fontSizeShrinksAsDigitCountGrows() {
        val one = tileFontSizeSp(1)
        val two = tileFontSizeSp(2)
        val three = tileFontSizeSp(3)
        val four = tileFontSizeSp(4)
        assertTrue(one >= two, "1-digit should be >= 2-digit")
        assertTrue(two >= three, "2-digit should be >= 3-digit")
        assertTrue(three >= four, "3-digit should be >= 4-digit")
    }

    @Test
    fun knownDigitCountsMapToExpectedSizes() {
        assertEquals(32, tileFontSizeSp(1))
        assertEquals(28, tileFontSizeSp(2))
        assertEquals(22, tileFontSizeSp(3))
        assertEquals(18, tileFontSizeSp(4))
    }

    @Test
    fun veryLongNumbersStayPositiveAndReadable() {
        val five = tileFontSizeSp(5)
        assertTrue(five in 1..18, "5+ digit size should be small but positive, was $five")
    }
}
