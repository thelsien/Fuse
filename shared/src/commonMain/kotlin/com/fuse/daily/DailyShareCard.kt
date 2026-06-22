package com.fuse.daily

import com.fuse.engine.Board

/**
 * DLY-7 — the PURE, deterministic builder for the Daily Challenge's shareable result card.
 *
 * After solving the day's Daily (a deterministic no-spawn puzzle — everyone gets the SAME
 * seed-derived start board and target), the player can share a Wordle-style result. Because
 * the board is identical for everyone that day, an emoji mini-grid of the START board is
 * directly comparable/copy-paste friendly between players.
 *
 * This file is the TESTABLE CORE: a single pure function that turns the daily result fields
 * into a deterministic string (golden-tested on JVM + iOS in `DailyShareCardTest`). The native
 * share sheet itself is the thin `Sharer` `expect`/`actual` (see `Sharer.kt`); this builds the
 * text it shares.
 *
 * ## No PII
 * The card contains ONLY the public daily result: the day number (a global counter, the same
 * for everyone), the target, the move count vs par, the day's shared start-board grid, and an
 * optional streak count. There is NO user identifier, device id, name, timestamp, location, or
 * any link — nothing that identifies the player.
 */

/**
 * The headline tag prefixing every card, plus the app/mode identity. Kept tight and stable so
 * the first line reads as a recognisable, comparable header (like "Wordle 1,234 4/6").
 */
private const val APP_TAG: String = "Fuse Daily"

/**
 * The emoji used for an EMPTY cell in the mini-grid — a neutral dark square. Distinct from any
 * tile color so blanks read clearly.
 */
private const val EMPTY_EMOJI: String = "⬛"

/**
 * The value→emoji mapping for occupied cells, by tile TIER. Mirrors the *idea* of [com.fuse.ui
 * .theme.TileRamp] (cool low values → warm/bright high values) using the small fixed set of
 * colored-square emoji that render identically across platforms. The mapping is intentionally
 * coarse (one emoji per power-of-two tier) so the grid stays legible as a comparable pattern,
 * not a literal recolor of the in-app palette.
 *
 * Documented mapping (occupied cell → emoji):
 *  - 2, 4        → 🟦 (blue — the low, cool tiles, like the ramp's light blues)
 *  - 8, 16       → 🟪 (purple)
 *  - 32, 64      → 🟩 (green — the teal/green mid band)
 *  - 128, 256    → 🟧 (orange — the "high target" band; daily targets top out at 256)
 *  - 512, 1024   → 🟥 (red — beyond the daily range, future-proofing)
 *  - 2048+       → 🟨 (yellow/gold — the apex)
 *
 * Any value not an exact key (non-power-of-two, or 1) falls back to the nearest sensible tier
 * via [emojiForValue]; the daily generator only ever places powers of two, so in practice every
 * cell hits an exact key.
 */
private val TILE_EMOJI: Map<Int, String> = mapOf(
    2 to "🟦", 4 to "🟦",
    8 to "🟪", 16 to "🟪",
    32 to "🟩", 64 to "🟩",
    128 to "🟧", 256 to "🟧",
    512 to "🟥", 1024 to "🟥",
    2048 to "🟨",
)

/** The emoji for a tile [value], by tier. Values above 2048 use the apex 🟨; misses clamp up. */
private fun emojiForValue(value: Int): String = when {
    value >= 2048 -> "🟨"
    else -> TILE_EMOJI[value]
        // Nearest-tier fallback for any non-exact value: round down to the largest known key
        // that is <= value (e.g. a stray 3 → the 2 tier), defaulting to the lowest tier.
        ?: TILE_EMOJI.keys.filter { it <= value }.maxOrNull()?.let { TILE_EMOJI[it] }
        ?: "🟦"
}

/**
 * Builds the deterministic share card text for a solved Daily.
 *
 * Layout (newline-separated, no trailing newline):
 * ```
 * Fuse Daily #142 · 🎯 256 · solved in 7 moves (par 5)
 * 🟦🟦⬛⬛
 * ⬛🟧🟧⬛
 * ⬛⬛⬛⬛
 * ⬛⬛⬛⬛
 * 🔥 Streak 3
 * ```
 *  - **Headline**: app tag + day number, the target (🎯), and the result (moves, and
 *    moves-vs-par). Self-identifying and comparable.
 *  - **Emoji mini-grid**: one line per board row of [startBoard]. Empty cells → [EMPTY_EMOJI];
 *    tiles → a tier emoji (see [TILE_EMOJI]). The grid is the day's SHARED start board (the
 *    puzzle is no-spawn, so it is the same for everyone), NOT the player's mid-solve board, so
 *    cards are comparable.
 *  - **Streak line** (optional): only when [currentStreak] != null && > 0.
 *
 * Pure + deterministic: the same inputs always produce the byte-for-byte same string on every
 * platform (golden-tested on JVM + iOS). No PII — only the public daily result appears.
 *
 * @param dayNumber the Daily #N (a global day counter; the same for everyone — not a user id).
 * @param target the day's target tile value.
 * @param moves the move count the player solved in.
 * @param par the day's optimal move count (the benchmark on the headline).
 * @param startBoard the day's SHARED start board (regenerable from the seed); its rows become
 *   the emoji grid. Use the START board, never the player's current board, so grids compare.
 * @param currentStreak optional current streak; renders "🔥 Streak N" when non-null and > 0.
 * @return the card text, ready to hand to [com.fuse.daily.Sharer.share].
 */
fun buildDailyShareCard(
    dayNumber: Long,
    target: Int,
    moves: Int,
    par: Int,
    startBoard: Board,
    currentStreak: Int? = null,
): String {
    val headline = "$APP_TAG #$dayNumber · 🎯 $target · solved in $moves " +
        "${moveWord(moves)} (par $par)"

    val grid = buildString {
        for (row in 0 until startBoard.size) {
            for (col in 0 until startBoard.size) {
                val tile = startBoard[row, col]
                append(if (tile == null) EMPTY_EMOJI else emojiForValue(tile.value))
            }
            if (row < startBoard.size - 1) append('\n')
        }
    }

    val lines = mutableListOf(headline, grid)
    if (currentStreak != null && currentStreak > 0) {
        lines.add("🔥 Streak $currentStreak")
    }
    return lines.joinToString("\n")
}

/** "move" vs "moves" so a 1-move solve reads naturally. */
private fun moveWord(moves: Int): String = if (moves == 1) "move" else "moves"
