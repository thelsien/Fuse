package com.fuse.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fuse.ads.AdManager
import com.fuse.ads.NoOpAdProvider
import com.fuse.daily.DailyClock
import com.fuse.daily.DailyPuzzle
import com.fuse.daily.NoOpSharer
import com.fuse.engine.Board
import com.fuse.presentation.DailyStore
import com.fuse.presentation.DailyStreakStore
import com.fuse.ui.daily.DailyScreen
import com.fuse.ui.daily.DailyScreenTags
import com.fuse.ui.home.HomeScreen
import com.fuse.ui.home.HomeScreenTags
import com.fuse.ui.theme.FuseTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DLY-4 — verifies the Home → Daily → back navigation, mirroring the topology `App()`
 * builds (Home with the now-ENABLED Daily entry, the DAILY route rendering [DailyScreen]),
 * with a directly-injected [DailyStore] (no Koin).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DailyNavigationUiTest {

    private class FixedClock(private val date: LocalDate) : DailyClock {
        override fun todayUtc(): LocalDate = date
        override fun now(): Instant = date.atStartOfDayIn(TimeZone.UTC).plus(12.hours)
    }

    private fun dailyStore() = DailyStore(
        clock = FixedClock(LocalDate(2026, 6, 21)),
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

    @Composable
    private fun NavGraphUnderTest(store: DailyStore) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = FuseDestinations.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(FuseDestinations.HOME) {
                HomeScreen(
                    best = 0L,
                    dailyEnabled = true,
                    onPlayClassic = {},
                    onOpenDaily = { navController.navigate(FuseDestinations.DAILY) },
                    onOpenSettings = {},
                )
            }
            composable(FuseDestinations.DAILY) {
                DailyScreen(
                    store = store,
                    streakStore = DailyStreakStore(clock = FixedClock(LocalDate(2026, 6, 21))),
                    sharer = NoOpSharer,
                    adManager = AdManager(NoOpAdProvider),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun homeDailyEntryOpensDailyScreen_thenBackReturnsHome() = runComposeUiTest {
        setContent { FuseTheme { NavGraphUnderTest(dailyStore()) } }

        onNodeWithTag(HomeScreenTags.ROOT).assertExists()
        onNodeWithTag(DailyScreenTags.ROOT).assertDoesNotExist()

        // The Daily entry is enabled and navigates to the Daily screen.
        onNodeWithTag(HomeScreenTags.DAILY).performClick()
        waitForIdle()
        onNodeWithTag(DailyScreenTags.ROOT).assertExists()
        onNodeWithTag(HomeScreenTags.ROOT).assertDoesNotExist()

        // Back (in-screen "‹ Home") returns to Home.
        onNodeWithTag(DailyScreenTags.BACK).performClick()
        waitForIdle()
        onNodeWithTag(HomeScreenTags.ROOT).assertExists()
        onNodeWithTag(DailyScreenTags.ROOT).assertDoesNotExist()
    }
}
