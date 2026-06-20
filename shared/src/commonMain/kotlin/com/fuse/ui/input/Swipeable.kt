package com.fuse.ui.input

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.fuse.engine.Direction
import kotlin.time.TimeSource

/**
 * UIB-2 — Compose gesture plumbing for swipe input.
 *
 * Wraps a single `pointerInput { detectDragGestures(...) }` that accumulates the
 * drag deltas of ONE gesture and, on drag END, resolves them once via
 * [resolveSwipe]. If the result is non-null, exactly ONE [Direction] is emitted to
 * [onSwipe]. All the geometry (threshold, dominant-axis snap, diagonal rejection,
 * coordinate convention) lives in the pure [resolveSwipe] so it is unit-testable on
 * every platform; this layer only owns Compose pointer mechanics.
 *
 * ## Debounce
 * Two layers, both documented as the AC's "prevents double-moves":
 *  1. **Resolve-once-per-gesture (primary).** The accumulator is reset on each
 *     `onDragStart` and read only on `onDragEnd`, so a single continuous drag can
 *     emit at most one [Direction] no matter how many intermediate drag events fire.
 *  2. **Time window (secondary).** Successful emissions closer together than
 *     [debounceWindowMs] are swallowed, guarding against rapid duplicate flicks
 *     (e.g. a bounce / double-tap-drag) collapsing into two engine moves. Kept
 *     small and simple; tunable in Sprint 3.
 *
 * ## enabled
 * When [enabled] is `false` the modifier installs no pointer input at all (returns
 * the receiver unchanged), so UIB-3 / UIB-5 can cheaply disable board input while a
 * game-over or settings overlay is shown. [enabled] is a key of the `pointerInput`,
 * so toggling it restarts the gesture detector cleanly.
 *
 * @param onSwipe invoked with exactly one [Direction] per resolved gesture.
 * @param enabled when false, no gesture is detected (input is inert).
 * @param minDistance forwarded to [resolveSwipe]; see [DEFAULT_MIN_DISTANCE].
 * @param diagonalToleranceRatio forwarded to [resolveSwipe]; see
 *   [DEFAULT_DIAGONAL_TOLERANCE_RATIO].
 * @param debounceWindowMs minimum gap between two emitted directions (ms).
 * @param nowMs time source (ms); injectable so tests can drive the debounce clock.
 */
fun Modifier.swipeable(
    onSwipe: (Direction) -> Unit,
    enabled: Boolean = true,
    minDistance: Float = DEFAULT_MIN_DISTANCE,
    diagonalToleranceRatio: Float = DEFAULT_DIAGONAL_TOLERANCE_RATIO,
    debounceWindowMs: Long = DEFAULT_DEBOUNCE_WINDOW_MS,
    nowMs: () -> Long = { currentTimeMs() },
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, minDistance, diagonalToleranceRatio, debounceWindowMs) {
        var accumulated = Offset.Zero
        // null until the first emission, so the first swipe is never debounced.
        // (Avoids overflow from subtracting a sentinel like Long.MIN_VALUE.)
        var lastEmitMs: Long? = null
        detectDragGestures(
            onDragStart = { accumulated = Offset.Zero },
            onDragCancel = { accumulated = Offset.Zero },
            onDragEnd = {
                val direction = resolveSwipe(
                    dx = accumulated.x,
                    dy = accumulated.y,
                    minDistance = minDistance,
                    diagonalToleranceRatio = diagonalToleranceRatio,
                )
                if (direction != null) {
                    val now = nowMs()
                    val last = lastEmitMs
                    if (last == null || now - last >= debounceWindowMs) {
                        lastEmitMs = now
                        onSwipe(direction)
                    }
                }
                accumulated = Offset.Zero
            },
        ) { change, dragAmount ->
            change.consume()
            accumulated += dragAmount
        }
    }
}

/**
 * Default secondary debounce window (ms). Two resolved swipes closer than this are
 * collapsed to one. Small enough not to drop intentional rapid play; tunable.
 */
const val DEFAULT_DEBOUNCE_WINDOW_MS: Long = 80L

/**
 * Multiplatform monotonic millisecond clock for the secondary debounce window.
 * Uses [kotlin.time.TimeSource.Monotonic] (pure Kotlin, no platform actuals, no
 * datetime dependency). Only relative gaps matter for debounce, so a monotonic
 * source is exactly right.
 */
private val monotonicStart = TimeSource.Monotonic.markNow()

internal fun currentTimeMs(): Long = monotonicStart.elapsedNow().inWholeMilliseconds
