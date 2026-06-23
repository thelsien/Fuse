package com.fuse.ui.daily

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.ads.AdFormat
import com.fuse.ads.AdManager
import com.fuse.ads.AdResult
import com.fuse.ads.FakeAdProvider
import com.fuse.daily.DailyClock
import com.fuse.daily.DailyPuzzle
import com.fuse.daily.Sharer
import com.fuse.daily.dailyDayNumber
import com.fuse.data.SettingsDailyStreakRepository
import com.fuse.engine.Board
import com.fuse.presentation.DailyStore
import com.fuse.presentation.DailyStreakStore
import com.fuse.ui.theme.FuseTheme
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ADS-3 (streak-saver) — headless Compose UI tests for the "Restore streak — Watch Ad" prompt on
 * [DailyScreen], driving the REAL [DailyStreakStore] (over a seeded broken streak in [MapSettings])
 * through [DailyScreen] with an injected [FakeAdProvider] (scripted per outcome). Same Robolectric
 * harness as the other UI tests (androidUnitTest, runs in `:shared:testDebugUnitTest`).
 *
 * Proves: the prompt shows only when the streak is broken-with-a-streak (canRestore); a scripted
 * `Rewarded` restores it (prompt gone, displayed streak = restored value, fake recorded a REWARDED
 * load+show); `NoFill`/`Dismissed`/`Failed` do NOT restore (prompt + "No ad available" note stay,
 * no crash); the prompt is absent when the streak is alive or never played.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StreakRestoreUiTest {

    private class FixedClock(private val date: LocalDate) : DailyClock {
        override fun todayUtc(): LocalDate = date
        override fun now(): Instant = date.atStartOfDayIn(TimeZone.UTC).plus(12.hours)
    }

    // Four consecutive UTC days.
    private val day1 = LocalDate(2026, 6, 21)
    private val day2 = LocalDate(2026, 6, 22)
    private val day3 = LocalDate(2026, 6, 23)
    private val day4 = LocalDate(2026, 6, 24)

    private fun streakRepo(settings: Settings) = SettingsDailyStreakRepository(settings)

    /** An UNSOLVED daily store for [date] over a trivial puzzle (board still playable). */
    private fun dailyStore(date: LocalDate) = DailyStore(
        clock = FixedClock(date),
        puzzle = DailyPuzzle(
            seed = 0L,
            startBoard = Board.fromValues(
                arrayOf(
                    intArrayOf(16, 16, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                    intArrayOf(0, 0, 0, 0),
                ),
            ),
            target = 32,
            par = 1,
        ),
    )

    /** Seeds a 2-day streak ending day2 into [settings] (broken when observed on day4). */
    private fun seedBrokenStreak(settings: Settings) {
        DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(settings))
            .recordSolved(dailyDayNumber(day1))
        DailyStreakStore(clock = FixedClock(day2), repository = streakRepo(settings))
            .recordSolved(dailyDayNumber(day2))
    }

    private class FakeSharer : Sharer {
        override fun share(text: String) { /* no OS sheet in tests */ }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun promptShownWhenBrokenAndRewardedAdRestores() = runComposeUiTest {
        val settings = MapSettings()
        seedBrokenStreak(settings)
        val streakStore = DailyStreakStore(clock = FixedClock(day4), repository = streakRepo(settings))
        val fake = FakeAdProvider().apply { scriptShow(AdFormat.REWARDED, AdResult.Rewarded) }
        setContent {
            FuseTheme {
                DailyScreen(
                    store = dailyStore(day4),
                    streakStore = streakStore,
                    sharer = FakeSharer(),
                    adManager = AdManager(fake),
                )
            }
        }

        // The streak is broken → the restore prompt is shown with the rescuable length.
        onNodeWithTag(DailyScreenTags.RESTORE_PROMPT).assertExists()
        onNodeWithTag(DailyScreenTags.RESTORE_MESSAGE).assertTextContains("2", substring = true)
        assertTrue("precondition: canRestore", streakStore.state.value.canRestore)

        // Tap Restore → rewarded ad watched to completion → streak restored.
        onNodeWithTag(DailyScreenTags.RESTORE_BUTTON).performClick()
        waitForIdle()

        // Prompt gone (canRestore=false), streak live again (2).
        onNodeWithTag(DailyScreenTags.RESTORE_PROMPT).assertDoesNotExist()
        assertFalse("restored → no longer restorable", streakStore.state.value.canRestore)
        assertTrue("restored streak shows 2", streakStore.state.value.current == 2)

        // The fake recorded a REWARDED load + show (the ad was actually requested).
        assertTrue("a rewarded ad was loaded", AdFormat.REWARDED in fake.loadCalls)
        assertTrue("a rewarded ad was shown", AdFormat.REWARDED in fake.showCalls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noFillDoesNotRestoreAndPromptStays() = runComposeUiTest {
        val settings = MapSettings()
        seedBrokenStreak(settings)
        val streakStore = DailyStreakStore(clock = FixedClock(day4), repository = streakRepo(settings))
        val fake = FakeAdProvider(loadSucceeds = false) // → NoFill
        setContent {
            FuseTheme {
                DailyScreen(
                    store = dailyStore(day4),
                    streakStore = streakStore,
                    sharer = FakeSharer(),
                    adManager = AdManager(fake),
                )
            }
        }

        onNodeWithTag(DailyScreenTags.RESTORE_BUTTON).performClick()
        waitForIdle()

        // No restore: prompt stays, the "No ad available" note appears, no crash.
        onNodeWithTag(DailyScreenTags.RESTORE_PROMPT).assertExists()
        onNodeWithTag(DailyScreenTags.RESTORE_NO_AD_NOTE).assertExists()
        assertTrue("still restorable after no-fill", streakStore.state.value.canRestore)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dismissedDoesNotRestore() = runComposeUiTest {
        val settings = MapSettings()
        seedBrokenStreak(settings)
        val streakStore = DailyStreakStore(clock = FixedClock(day4), repository = streakRepo(settings))
        val fake = FakeAdProvider().apply { scriptShow(AdFormat.REWARDED, AdResult.Dismissed) }
        setContent {
            FuseTheme {
                DailyScreen(
                    store = dailyStore(day4),
                    streakStore = streakStore,
                    sharer = FakeSharer(),
                    adManager = AdManager(fake),
                )
            }
        }

        onNodeWithTag(DailyScreenTags.RESTORE_BUTTON).performClick()
        waitForIdle()

        onNodeWithTag(DailyScreenTags.RESTORE_PROMPT).assertExists()
        assertTrue("dismissed (closed early) does not restore", streakStore.state.value.canRestore)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun failedDoesNotRestore() = runComposeUiTest {
        val settings = MapSettings()
        seedBrokenStreak(settings)
        val streakStore = DailyStreakStore(clock = FixedClock(day4), repository = streakRepo(settings))
        val fake = FakeAdProvider().apply { scriptShow(AdFormat.REWARDED, AdResult.Failed) }
        setContent {
            FuseTheme {
                DailyScreen(
                    store = dailyStore(day4),
                    streakStore = streakStore,
                    sharer = FakeSharer(),
                    adManager = AdManager(fake),
                )
            }
        }

        onNodeWithTag(DailyScreenTags.RESTORE_BUTTON).performClick()
        waitForIdle()

        onNodeWithTag(DailyScreenTags.RESTORE_PROMPT).assertExists()
        assertTrue("a failed show does not restore", streakStore.state.value.canRestore)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun promptAbsentWhenStreakAlive() = runComposeUiTest {
        // Solved-yesterday streak observed on day3 → alive → no prompt.
        val settings = MapSettings()
        seedBrokenStreak(settings) // 2-streak ending day2
        val streakStore = DailyStreakStore(clock = FixedClock(day3), repository = streakRepo(settings))
        setContent {
            FuseTheme {
                DailyScreen(
                    store = dailyStore(day3),
                    streakStore = streakStore,
                    sharer = FakeSharer(),
                    adManager = AdManager(FakeAdProvider(loadSucceeds = false)),
                )
            }
        }
        onNodeWithTag(DailyScreenTags.RESTORE_PROMPT).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun promptAbsentWhenNeverPlayed() = runComposeUiTest {
        val streakStore = DailyStreakStore(clock = FixedClock(day1), repository = streakRepo(MapSettings()))
        setContent {
            FuseTheme {
                DailyScreen(
                    store = dailyStore(day1),
                    streakStore = streakStore,
                    sharer = FakeSharer(),
                    adManager = AdManager(FakeAdProvider(loadSucceeds = false)),
                )
            }
        }
        onNodeWithTag(DailyScreenTags.RESTORE_PROMPT).assertDoesNotExist()
    }
}
