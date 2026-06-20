package com.fuse.ui.input

import com.fuse.engine.Direction
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * UIB-2 — pure swipe-to-[Direction] resolver.
 *
 * This is the platform-agnostic core of the swipe gesture, deliberately split from
 * the Compose gesture plumbing in [swipeable] so it can be unit-tested on BOTH the
 * JVM and iOS Native targets from `commonTest`. It contains no Compose / Android
 * types — just the geometry of "given a total drag displacement `(dx, dy)`, which
 * [Direction] (if any) did the user intend?".
 *
 * ## Coordinate convention (Compose pointer space, +y is DOWN)
 * `dx`/`dy` are the accumulated displacement of a drag in Compose pointer
 * coordinates, where the origin is top-left and +y points DOWN the screen:
 *  - dragging **right** → `dx > 0`  → [Direction.RIGHT]
 *  - dragging **left**  → `dx < 0`  → [Direction.LEFT]
 *  - dragging **down**  → `dy > 0`  → [Direction.DOWN]
 *  - dragging **up**    → `dy < 0`  → [Direction.UP]
 *
 * ## Resolution rules (matches the AC + PRD §4.2 input targets)
 *  1. **Distance threshold** — if the total displacement length is below
 *     [minDistance], return `null` (no move). Kept LOW so fast/short flicks count.
 *  2. **Dominant axis** — the larger of `|dx|` vs `|dy|` wins; horizontal →
 *     LEFT/RIGHT, vertical → UP/DOWN. This "snaps to the dominant axis", so a swipe
 *     that is mostly horizontal but slightly off still resolves cleanly.
 *  3. **Diagonal ambiguity** — if the SMALLER axis is more than
 *     [diagonalToleranceRatio] × the LARGER axis, the gesture is too diagonal to
 *     call and we return `null`. With the default ratio of 0.7 a perfect 45° swipe
 *     (`|dx| == |dy|`, ratio 1.0) is rejected, while a clearly-dominant swipe with a
 *     small cross-axis component resolves. Generous by design (PRD wants forgiving
 *     angle tolerance) — feel tuning continues in Sprint 3.
 *
 * The defaults are intentionally exposed as [DEFAULT_MIN_DISTANCE] /
 * [DEFAULT_DIAGONAL_TOLERANCE_RATIO] so the gesture modifier and any future tuning
 * screen reference one source of truth.
 *
 * @param dx accumulated horizontal displacement in Compose px (right positive).
 * @param dy accumulated vertical displacement in Compose px (down positive).
 * @param minDistance minimum total displacement length (px) for a swipe to count.
 * @param diagonalToleranceRatio max allowed ratio of (smaller axis / larger axis);
 *   above this the swipe is treated as an ambiguous diagonal and rejected. Must be
 *   in `(0f, 1f]`; 1.0 disables diagonal rejection except the exact 45° tie.
 * @return the dominant-axis [Direction], or `null` if below threshold or ambiguous.
 */
fun resolveSwipe(
    dx: Float,
    dy: Float,
    minDistance: Float = DEFAULT_MIN_DISTANCE,
    diagonalToleranceRatio: Float = DEFAULT_DIAGONAL_TOLERANCE_RATIO,
): Direction? {
    val ax = abs(dx)
    val ay = abs(dy)

    // Rule 1 — distance threshold (Euclidean length so a diagonal of two small
    // components is still measured fairly).
    val distance = sqrt(dx * dx + dy * dy)
    if (distance < minDistance) return null

    val larger = maxOf(ax, ay)
    if (larger == 0f) return null

    // Rule 3 — diagonal ambiguity. A perfect 45° tie (ax == ay) yields ratio 1.0
    // and is rejected for any tolerance <= 1.0, satisfying "exactly diagonal → null".
    val smaller = minOf(ax, ay)
    if (smaller > diagonalToleranceRatio * larger) return null

    // Rule 2 — dominant axis wins.
    return if (ax >= ay) {
        if (dx >= 0f) Direction.RIGHT else Direction.LEFT
    } else {
        if (dy >= 0f) Direction.DOWN else Direction.UP
    }
}

/**
 * Default minimum swipe length in Compose px. Kept low (PRD §4.2: fast flicks must
 * count). Tunable — feel tuning continues in Sprint 3.
 */
const val DEFAULT_MIN_DISTANCE: Float = 24f

/**
 * Default diagonal-ambiguity tolerance: the smaller axis may be up to 70% of the
 * larger axis before the swipe is rejected as too diagonal. Generous angle
 * tolerance per PRD §4.2. Tunable.
 */
const val DEFAULT_DIAGONAL_TOLERANCE_RATIO: Float = 0.7f
