package com.fuse.engine

/**
 * Win / lose evaluation for a board (ENG-8).
 *
 * These are **pure predicates** over a [Board] (plus the win [target]): no RNG, no
 * id minting, no mutation, no platform dependencies. They report the *condition* of
 * a board; turning those conditions into Playing -> Won -> Lost transitions (and the
 * one-shot "first 2048" event) is the state machine's job (ENG-9), not these
 * functions'.
 */

/** The classic 2048 win target. A constant so nothing hardcodes 2048 inline. */
const val DEFAULT_WIN_TARGET: Int = 2048

/**
 * True iff any tile on the board has reached the win [target].
 *
 * Uses `>=` (not `==`): once a 2048 tile exists the won condition stays true even as
 * the player keeps merging past it (4096, 8192, ...). The *first* time this flips to
 * true is the "you won" event, but that one-shot is the state machine's concern
 * (ENG-9) — this predicate only reports the persistent board condition.
 *
 * [target] defaults to [DEFAULT_WIN_TARGET] (2048) but is a parameter so post-MVP
 * variants / Daily challenges can use a different goal without touching this code.
 */
fun Board.hasWon(target: Int = DEFAULT_WIN_TARGET): Boolean =
    tiles().any { it.value >= target }

/**
 * True iff **some** move (in any of the four directions) would change the board.
 *
 * Implemented directly on adjacency (Option A from the design): the board can move iff
 *  - it has at least one empty cell (a slide is possible), OR
 *  - some two horizontally- or vertically-adjacent tiles share a value (a merge is
 *    possible, even on a full board).
 *
 * This is pure and allocation-free: it needs no [TileIdSource] and never calls
 * [move], so probing "can the game continue?" cannot burn ids from the game's real id
 * source. A non-full board can always move, so we short-circuit on the first empty
 * cell before scanning for mergeable neighbours.
 */
fun Board.canMove(): Boolean {
    // A non-full board can always slide.
    if (!isFull) return true
    // Full board: it can still move iff some adjacent pair shares a value (a merge).
    for (row in 0 until size) {
        for (col in 0 until size) {
            val value = this[row, col]?.value ?: continue
            // Compare against the right and down neighbours only; every adjacent
            // pair is covered exactly once this way.
            if (col + 1 < size && this[row, col + 1]?.value == value) return true
            if (row + 1 < size && this[row + 1, col]?.value == value) return true
        }
    }
    return false
}

/**
 * True iff the game is lost: the board is **full** AND **no** move in any direction
 * would change it (`isFull && !canMove()`). A board with an empty cell, or a full
 * board with any mergeable adjacent pair, is NOT game over.
 */
fun Board.isGameOver(): Boolean = isFull && !canMove()
