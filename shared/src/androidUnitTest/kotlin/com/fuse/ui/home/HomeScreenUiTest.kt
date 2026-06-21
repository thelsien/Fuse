package com.fuse.ui.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.ui.theme.FuseTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * SHL-1 headless Compose UI tests for [HomeScreen] — the app-shell launch surface.
 * Same Robolectric harness as the other UI tests (androidUnitTest, runs in
 * `:shared:testDebugUnitTest`). Home is presentational + value-driven, so no store/Koin needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class HomeScreenUiTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun showsBestScoreAndAllEntryPoints() = runComposeUiTest {
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 2048L,
                    onPlayClassic = {},
                    onOpenDaily = {},
                    onOpenSettings = {},
                )
            }
        }

        // Best score shown prominently.
        onNodeWithTag(HomeScreenTags.BEST_VALUE).assertTextEquals("2 048")

        // All three entry points present.
        onNodeWithTag(HomeScreenTags.CLASSIC).assertExists()
        onNodeWithTag(HomeScreenTags.DAILY).assertExists()
        onNodeWithTag(HomeScreenTags.SETTINGS).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingClassicInvokesCallback() = runComposeUiTest {
        var played = 0
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 0L,
                    onPlayClassic = { played++ },
                    onOpenDaily = {},
                    onOpenSettings = {},
                )
            }
        }

        onNodeWithTag(HomeScreenTags.CLASSIC).assertHasClickAction().performClick()
        assertEquals(1, played)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tappingSettingsInvokesCallback() = runComposeUiTest {
        var opened = false
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 0L,
                    onPlayClassic = {},
                    onOpenDaily = {},
                    onOpenSettings = { opened = true },
                )
            }
        }

        onNodeWithTag(HomeScreenTags.SETTINGS).performClick()
        assertTrue(opened)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dailyIsDisabledPlaceholderByDefault() = runComposeUiTest {
        var dailyTaps = 0
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 0L,
                    onPlayClassic = {},
                    onOpenDaily = { dailyTaps++ },
                    onOpenSettings = {},
                )
            }
        }

        // Placeholder: rendered but not clickable, so tapping does nothing.
        onNodeWithTag(HomeScreenTags.DAILY).assertExists().performClick()
        assertEquals(0, dailyTaps)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dailyFiresWhenEnabled() = runComposeUiTest {
        var dailyTaps = 0
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 0L,
                    onPlayClassic = {},
                    onOpenDaily = { dailyTaps++ },
                    onOpenSettings = {},
                    dailyEnabled = true,
                )
            }
        }

        onNodeWithTag(HomeScreenTags.DAILY).assertHasClickAction().performClick()
        assertEquals(1, dailyTaps)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dailyEntryShowsTheStreakWhenRunning() = runComposeUiTest {
        // DLY-5 — when Daily is enabled and a streak is running, the entry surfaces it.
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 0L,
                    onPlayClassic = {},
                    onOpenDaily = {},
                    onOpenSettings = {},
                    dailyEnabled = true,
                    dailyStreak = 5,
                )
            }
        }
        onNodeWithTag(HomeScreenTags.DAILY).assertTextContains("5", substring = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dailyEntryShowsPlainLabelWhenNoStreak() = runComposeUiTest {
        // DLY-5 — a broken/absent streak (0) shows the plain "Daily" label (no "🔥").
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 0L,
                    onPlayClassic = {},
                    onOpenDaily = {},
                    onOpenSettings = {},
                    dailyEnabled = true,
                    dailyStreak = 0,
                )
            }
        }
        onNodeWithTag(HomeScreenTags.DAILY).assertTextEquals("Daily")
    }

    @Test
    fun formatGroupsLargeBestScores() {
        assertEquals("0", formatHomeScore(0L))
        assertEquals("999", formatHomeScore(999L))
        assertEquals("1 048 576", formatHomeScore(1_048_576L))
    }

    // --- SHL-4: Resume-on-launch choice ----------------------------------------

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun whenNotResumableShowsPlayClassicOnly() = runComposeUiTest {
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 0L,
                    onPlayClassic = {},
                    onOpenDaily = {},
                    onOpenSettings = {},
                    canResume = false,
                )
            }
        }

        // Default (no resumable game): the single Play Classic CTA, no Continue / New game.
        onNodeWithTag(HomeScreenTags.CLASSIC).assertExists()
        onNodeWithTag(HomeScreenTags.CONTINUE).assertDoesNotExist()
        onNodeWithTag(HomeScreenTags.NEW_GAME).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun whenResumableShowsContinueAndNewGameInsteadOfClassic() = runComposeUiTest {
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 4096L,
                    onPlayClassic = {},
                    onOpenDaily = {},
                    onOpenSettings = {},
                    canResume = true,
                    savedScore = 1234L,
                )
            }
        }

        // Resumable: Continue (primary) + New game replace the single Play Classic CTA.
        onNodeWithTag(HomeScreenTags.CONTINUE).assertExists()
        onNodeWithTag(HomeScreenTags.NEW_GAME).assertExists()
        onNodeWithTag(HomeScreenTags.CLASSIC).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun continueInvokesOnContinueAndNewGameInvokesOnNewGame() = runComposeUiTest {
        var continued = 0
        var started = 0
        setContent {
            FuseTheme {
                HomeScreen(
                    best = 0L,
                    onPlayClassic = {},
                    onOpenDaily = {},
                    onOpenSettings = {},
                    canResume = true,
                    savedScore = 64L,
                    onContinue = { continued++ },
                    onNewGame = { started++ },
                )
            }
        }

        onNodeWithTag(HomeScreenTags.CONTINUE).assertHasClickAction().performClick()
        assertEquals(1, continued)
        assertEquals(0, started)

        onNodeWithTag(HomeScreenTags.NEW_GAME).assertHasClickAction().performClick()
        assertEquals(1, started)
        assertEquals(1, continued)
    }
}
