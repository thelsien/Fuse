package com.fuse.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.daily.DailyClock
import com.fuse.daily.durationUntilNextUtcMidnight
import com.fuse.daily.formatCountdown
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DLY-6 — verifies the live-ticking countdown composable [rememberDailyCountdown] is wired
 * to the injectable [DailyClock] seam and renders the formatted HH:MM:SS until the next UTC
 * midnight. Driven by a fixed clock so the rendered string is deterministic.
 *
 * The pure boundary/formatting logic is covered exhaustively in `DailyCountdownTest`
 * (commonTest, JVM+iOS); here we prove the COMPOSE plumbing emits that computed string.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyCountdownStateTest {

    /** A clock pinned to a fixed instant (date is irrelevant for the countdown). */
    private class FixedInstantClock(private val instant: Instant) : DailyClock {
        override fun todayUtc(): LocalDate = LocalDate(2026, 6, 21)
        override fun now(): Instant = instant
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersTheComputedCountdownForTheInjectedClock() = runComposeUiTest {
        val now = Instant.parse("2026-06-21T16:56:56Z") // 7h 3m 4s before midnight.
        val expected = formatCountdown(durationUntilNextUtcMidnight(now))
        assertEquals("07:03:04", expected) // sanity on the fixture.

        setContent {
            val countdown by rememberDailyCountdown(FixedInstantClock(now))
            Text(text = countdown, modifier = Modifier.testTag("countdown"))
        }

        onNodeWithTag("countdown").assertTextEquals("07:03:04")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun initialFrameAlreadyShowsTheCorrectTime() = runComposeUiTest {
        // The produceState initial value is computed eagerly, so even before the first
        // 1s delay the first composed frame shows the right countdown.
        val now = Instant.parse("2026-06-21T23:59:59Z") // 1s to midnight.
        setContent {
            val countdown by rememberDailyCountdown(FixedInstantClock(now), tickMillis = 1000L)
            Text(text = countdown, modifier = Modifier.testTag("countdown"))
        }
        onNodeWithTag("countdown").assertTextEquals("00:00:01")
    }
}
