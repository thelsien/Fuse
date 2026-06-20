package com.fuse.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UIB-4 — pure tests for [formatScore], the locale-safe thousands-grouping helper used
 * by [ScoreHud]. Pure logic, so it lives in commonTest and runs on JVM + iOS (the format
 * is dependency-free precisely so it can't diverge per platform/locale).
 */
class ScoreFormatTest {

    @Test
    fun smallNumbersAreUnchanged() {
        assertEquals("0", formatScore(0))
        assertEquals("4", formatScore(4))
        assertEquals("999", formatScore(999))
    }

    @Test
    fun groupsThousands() {
        assertEquals("1 000", formatScore(1_000))
        assertEquals("12 345", formatScore(12_345))
        assertEquals("999 999", formatScore(999_999))
    }

    @Test
    fun groupsMillionsAndBeyond() {
        assertEquals("1 000 000", formatScore(1_000_000))
        assertEquals("1 048 576", formatScore(1_048_576))
    }

    @Test
    fun handlesNegativeGracefully() {
        assertEquals("-1 234", formatScore(-1_234))
    }
}
