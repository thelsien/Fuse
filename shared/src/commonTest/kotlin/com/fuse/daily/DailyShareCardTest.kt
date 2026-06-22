package com.fuse.daily

import com.fuse.engine.Board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DLY-7 — pure, cross-platform tests for [buildDailyShareCard] (runs on JVM via
 * `:shared:testDebugUnitTest` AND on Kotlin/Native via `:shared:iosSimulatorArm64Test`).
 *
 * The builder is 100% pure and deterministic, so we pin the EXACT card string for known inputs
 * (golden), plus structure (line count = headline + board rows [+ streak]; headline carries the
 * day/target/moves/par) and the no-PII property (only the daily result fields appear).
 */
class DailyShareCardTest {

    /** A small known board: two 16s top-left, a 256 mid, a 2 corner — covers several tiers. */
    private fun sampleBoard(): Board = Board.fromValues(
        arrayOf(
            intArrayOf(16, 16, 0, 0),
            intArrayOf(0, 256, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 2),
        ),
    )

    @Test
    fun goldenCardWithoutStreak() {
        val card = buildDailyShareCard(
            dayNumber = 142,
            target = 256,
            moves = 7,
            par = 5,
            startBoard = sampleBoard(),
        )
        val expected = """
            Fuse Daily #142 · 🎯 256 · solved in 7 moves (par 5)
            🟪🟪⬛⬛
            ⬛🟧⬛⬛
            ⬛⬛⬛⬛
            ⬛⬛⬛🟦
        """.trimIndent()
        assertEquals(expected, card)
    }

    @Test
    fun goldenCardWithStreak() {
        val card = buildDailyShareCard(
            dayNumber = 142,
            target = 256,
            moves = 7,
            par = 5,
            startBoard = sampleBoard(),
            currentStreak = 3,
        )
        val expected = """
            Fuse Daily #142 · 🎯 256 · solved in 7 moves (par 5)
            🟪🟪⬛⬛
            ⬛🟧⬛⬛
            ⬛⬛⬛⬛
            ⬛⬛⬛🟦
            🔥 Streak 3
        """.trimIndent()
        assertEquals(expected, card)
    }

    @Test
    fun oneMoveSolveUsesSingularMove() {
        val card = buildDailyShareCard(
            dayNumber = 1,
            target = 32,
            moves = 1,
            par = 1,
            startBoard = Board.empty(),
        )
        assertTrue(card.contains("solved in 1 move (par 1)"), card)
        assertFalse(card.contains("1 moves"), "should read '1 move', not '1 moves': $card")
    }

    @Test
    fun zeroOrNegativeStreakOmitsTheStreakLine() {
        val noStreak = buildDailyShareCard(1, 32, 2, 1, Board.empty(), currentStreak = 0)
        assertFalse(noStreak.contains("🔥"), "streak 0 must omit the line: $noStreak")
        // The empty 4x4 board → headline + 4 grid rows = 5 lines.
        assertEquals(5, noStreak.lines().size, noStreak)
    }

    @Test
    fun gridHasOneLinePerBoardRowPlusHeadline() {
        val card = buildDailyShareCard(10, 64, 4, 3, sampleBoard())
        val lines = card.lines()
        // headline + 4 board rows = 5 lines (no streak).
        assertEquals(1 + sampleBoard().size, lines.size, card)
        // Each grid row has exactly `size` emoji cells (one code-point group per cell).
        for (i in 1 until lines.size) {
            assertEquals(sampleBoard().size, countCells(lines[i]), "row $i: ${lines[i]}")
        }
    }

    @Test
    fun headlineCarriesDayTargetMovesAndPar() {
        val card = buildDailyShareCard(99, 128, 6, 4, sampleBoard())
        val headline = card.lines().first()
        assertTrue(headline.contains("Fuse Daily #99"), headline)
        assertTrue(headline.contains("128"), headline)
        assertTrue(headline.contains("solved in 6 moves"), headline)
        assertTrue(headline.contains("par 4"), headline)
    }

    @Test
    fun isDeterministicForSameInputs() {
        val a = buildDailyShareCard(142, 256, 7, 5, sampleBoard(), currentStreak = 3)
        val b = buildDailyShareCard(142, 256, 7, 5, sampleBoard(), currentStreak = 3)
        assertEquals(a, b)
    }

    @Test
    fun noPiiOnlyTheDailyResultFieldsAppear() {
        // The card must carry NO user identifier / device id / link / @ handle / http(s) — only
        // the public daily result (day, target, moves, par, grid, streak). A coarse but strong
        // structural check on the assembled text.
        val card = buildDailyShareCard(142, 256, 7, 5, sampleBoard(), currentStreak = 3)
        assertFalse(card.contains("http"), card)
        assertFalse(card.contains("@"), card)
        // No raw digit runs beyond the four numeric fields we put there (142, 256, 7, 5, 3) — i.e.
        // nothing that looks like an id/timestamp leaked in. We assert the only number tokens are
        // exactly those expected.
        val numbers = Regex("\\d+").findAll(card).map { it.value }.toList()
        assertEquals(listOf("142", "256", "7", "5", "3"), numbers, "unexpected numbers: $numbers")
    }

    /**
     * Counts emoji "cells" in a grid [row]. Each cell is one of the fixed single-glyph squares
     * (⬛/🟦/🟪/🟩/🟧/🟥/🟨); they all encode as a single code point (plus, for the colored ones,
     * no combining marks), so counting Unicode code points equals the cell count here.
     */
    private fun countCells(row: String): Int = row.codePointSequence().count()
}

/** Code-point iterator over a [String] (surrogate-pair aware), for cross-platform cell counting. */
private fun String.codePointSequence(): Sequence<Int> = sequence {
    var i = 0
    while (i < length) {
        val c = this@codePointSequence[i]
        if (c.isHighSurrogate() && i + 1 < length && this@codePointSequence[i + 1].isLowSurrogate()) {
            yield(((c.code - 0xD800) shl 10) + (this@codePointSequence[i + 1].code - 0xDC00) + 0x10000)
            i += 2
        } else {
            yield(c.code)
            i += 1
        }
    }
}
