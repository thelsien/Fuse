package com.fuse.ui.input

import com.fuse.engine.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * UIB-2 — pure resolver tests. Runs on BOTH JVM (`:shared:testDebugUnitTest`) and
 * iOS Native (`:shared:iosSimulatorArm64Test`) from commonTest.
 *
 * Covers the coordinate convention for all four directions, the distance threshold,
 * the exact 45° tie, the near-diagonal tolerance band, clearly-dominant axis with a
 * small cross component, and boundary cases on both threshold and tolerance.
 */
class SwipeResolverTest {

    // Large displacement well over the default threshold, used as the dominant axis.
    private val far = 100f

    // --- Coordinate convention: pure axis swipes resolve correctly ----------

    @Test
    fun pureRight_resolvesRight() {
        assertEquals(Direction.RIGHT, resolveSwipe(dx = far, dy = 0f))
    }

    @Test
    fun pureLeft_resolvesLeft() {
        assertEquals(Direction.LEFT, resolveSwipe(dx = -far, dy = 0f))
    }

    @Test
    fun pureDown_resolvesDown() {
        // +y is DOWN in Compose pointer space.
        assertEquals(Direction.DOWN, resolveSwipe(dx = 0f, dy = far))
    }

    @Test
    fun pureUp_resolvesUp() {
        // -y is UP in Compose pointer space.
        assertEquals(Direction.UP, resolveSwipe(dx = 0f, dy = -far))
    }

    // --- Distance threshold --------------------------------------------------

    @Test
    fun belowThreshold_returnsNull() {
        // Length ~14 px < default 24 px.
        assertNull(resolveSwipe(dx = 10f, dy = 10f))
    }

    @Test
    fun justBelowThresholdOnAxis_returnsNull() {
        assertNull(resolveSwipe(dx = DEFAULT_MIN_DISTANCE - 0.1f, dy = 0f))
    }

    @Test
    fun atThresholdOnAxis_resolves() {
        // length == minDistance is NOT below threshold -> counts.
        assertEquals(Direction.RIGHT, resolveSwipe(dx = DEFAULT_MIN_DISTANCE, dy = 0f))
    }

    @Test
    fun lowThreshold_fastShortFlickCounts() {
        // A short but clearly horizontal flick (just past the low threshold) counts.
        assertEquals(Direction.LEFT, resolveSwipe(dx = -30f, dy = 3f))
    }

    // --- Diagonal ambiguity --------------------------------------------------

    @Test
    fun exactDiagonal_returnsNull() {
        // |dx| == |dy| -> ratio 1.0 > 0.7 tolerance -> ambiguous -> null.
        assertNull(resolveSwipe(dx = far, dy = far))
        assertNull(resolveSwipe(dx = -far, dy = far))
        assertNull(resolveSwipe(dx = far, dy = -far))
        assertNull(resolveSwipe(dx = -far, dy = -far))
    }

    @Test
    fun nearDiagonalWithinToleranceBand_returnsNull() {
        // smaller/larger = 80/100 = 0.8 > 0.7 -> still too diagonal.
        assertNull(resolveSwipe(dx = 100f, dy = 80f))
        assertNull(resolveSwipe(dx = 80f, dy = -100f))
    }

    @Test
    fun dominantAxisWithSmallCross_resolves() {
        // smaller/larger = 30/100 = 0.3 <= 0.7 -> resolves to dominant axis.
        assertEquals(Direction.RIGHT, resolveSwipe(dx = 100f, dy = 30f))
        assertEquals(Direction.UP, resolveSwipe(dx = 30f, dy = -100f))
    }

    @Test
    fun justInsideToleranceBand_resolves() {
        // smaller/larger = 0.69 (<= 0.7) -> resolves.
        assertEquals(Direction.RIGHT, resolveSwipe(dx = 100f, dy = 69f))
    }

    @Test
    fun justOutsideToleranceBand_returnsNull() {
        // smaller/larger = 0.71 (> 0.7) -> ambiguous.
        assertNull(resolveSwipe(dx = 100f, dy = 71f))
    }

    @Test
    fun toleranceBoundaryExactlyAtRatio_resolves() {
        // smaller == diagonalToleranceRatio * larger -> NOT greater than -> resolves.
        assertEquals(Direction.RIGHT, resolveSwipe(dx = 100f, dy = 70f))
    }

    // --- Zero / custom params ------------------------------------------------

    @Test
    fun zeroDisplacement_returnsNull() {
        assertNull(resolveSwipe(dx = 0f, dy = 0f))
    }

    @Test
    fun customMinDistance_isRespected() {
        // With a higher threshold, a previously-counting flick is rejected.
        assertNull(resolveSwipe(dx = 30f, dy = 0f, minDistance = 50f))
        assertEquals(Direction.RIGHT, resolveSwipe(dx = 60f, dy = 0f, minDistance = 50f))
    }

    @Test
    fun customTolerance_isRespected() {
        // Loosening tolerance to 0.9 lets an 0.8-ratio swipe resolve.
        assertEquals(
            Direction.RIGHT,
            resolveSwipe(dx = 100f, dy = 80f, diagonalToleranceRatio = 0.9f),
        )
        // Tightening to 0.2 rejects an 0.3-ratio swipe.
        assertNull(resolveSwipe(dx = 100f, dy = 30f, diagonalToleranceRatio = 0.2f))
    }

    @Test
    fun verticalDominantOverEqualSignedComponents_picksVerticalWhenLarger() {
        // ay > ax -> vertical wins; ratio 50/100 = 0.5 ok.
        assertEquals(Direction.DOWN, resolveSwipe(dx = 50f, dy = 100f))
    }
}
