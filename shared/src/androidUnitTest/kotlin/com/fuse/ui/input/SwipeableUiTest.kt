package com.fuse.ui.input

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.fuse.engine.Direction
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UIB-2 — Compose gesture test for [Modifier.swipeable], same Robolectric +
 * `runComposeUiTest` harness as [com.fuse.ui.board.BoardViewUiTest]. Verifies the
 * Compose plumbing end-to-end: a real touch swipe injected via `performTouchInput`
 * resolves to the expected [Direction], `enabled = false` makes input inert, and a
 * single gesture emits exactly once (resolve-once-per-gesture debounce).
 *
 * The pure geometry is covered exhaustively in commonTest [SwipeResolverTest]; this
 * suite only proves the gesture wiring.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SwipeableUiTest {

    private val tag = "swipeTarget"

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun swipeLeft_emitsLeft() = runComposeUiTest {
        val emitted = mutableListOf<Direction>()
        setContent {
            Box(Modifier.size(300.dp).testTag(tag).swipeable(onSwipe = { emitted += it }))
        }
        onNodeWithTag(tag).performTouchInput { swipeLeft() }

        assertEquals(listOf(Direction.LEFT), emitted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun swipeRight_emitsRight() = runComposeUiTest {
        val emitted = mutableListOf<Direction>()
        setContent {
            Box(Modifier.size(300.dp).testTag(tag).swipeable(onSwipe = { emitted += it }))
        }
        onNodeWithTag(tag).performTouchInput { swipeRight() }

        assertEquals(listOf(Direction.RIGHT), emitted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun swipeUp_emitsUp() = runComposeUiTest {
        val emitted = mutableListOf<Direction>()
        setContent {
            Box(Modifier.size(300.dp).testTag(tag).swipeable(onSwipe = { emitted += it }))
        }
        onNodeWithTag(tag).performTouchInput { swipeUp() }

        assertEquals(listOf(Direction.UP), emitted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun swipeDown_emitsDown() = runComposeUiTest {
        val emitted = mutableListOf<Direction>()
        setContent {
            Box(Modifier.size(300.dp).testTag(tag).swipeable(onSwipe = { emitted += it }))
        }
        onNodeWithTag(tag).performTouchInput { swipeDown() }

        assertEquals(listOf(Direction.DOWN), emitted)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun disabled_emitsNothing() = runComposeUiTest {
        val emitted = mutableListOf<Direction>()
        setContent {
            Box(
                Modifier.size(300.dp).testTag(tag)
                    .swipeable(onSwipe = { emitted += it }, enabled = false),
            )
        }
        onNodeWithTag(tag).performTouchInput { swipeLeft() }

        assertTrue(emitted.isEmpty(), "disabled swipeable must not emit")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun singleGesture_emitsExactlyOnce() = runComposeUiTest {
        val emitted = mutableListOf<Direction>()
        setContent {
            Box(Modifier.size(300.dp).testTag(tag).swipeable(onSwipe = { emitted += it }))
        }
        // One continuous drag -> exactly one emission despite many intermediate events.
        onNodeWithTag(tag).performTouchInput { swipeRight() }

        assertEquals(1, emitted.size, "one gesture must resolve once")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rapidDuplicateGestures_areTimeDebounced() = runComposeUiTest {
        val emitted = mutableListOf<Direction>()
        // Frozen clock: both gestures land inside the same debounce window, so the
        // second is swallowed by the time-based debounce.
        setContent {
            Box(
                Modifier.size(300.dp).testTag(tag)
                    .swipeable(onSwipe = { emitted += it }, nowMs = { 0L }),
            )
        }
        onNodeWithTag(tag).performTouchInput { swipeRight() }
        onNodeWithTag(tag).performTouchInput { swipeRight() }

        assertEquals(listOf(Direction.RIGHT), emitted)
    }
}
