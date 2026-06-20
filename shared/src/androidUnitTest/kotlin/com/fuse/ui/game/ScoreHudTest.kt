package com.fuse.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.ui.theme.FuseTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UIB-4 headless Compose UI tests for [ScoreHud] — the presentational score/best HUD.
 * Same Robolectric harness as the other UI tests (androidUnitTest, runs in
 * `:shared:testDebugUnitTest`). The HUD takes plain values, so no store/Koin is needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScoreHudTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rendersGivenCurrentAndBest() = runComposeUiTest {
        setContent {
            FuseTheme { ScoreHud(current = 128L, best = 256L) }
        }

        onNodeWithText("SCORE").assertExists()
        onNodeWithText("BEST").assertExists()
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("128")
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("256")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun updatesWhenValuesChange() = runComposeUiTest {
        var current by mutableStateOf(0L)
        var best by mutableStateOf(0L)

        setContent {
            FuseTheme { ScoreHud(current = current, best = best) }
        }

        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("0")
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("0")

        // Score rises and best tracks it.
        current = 12
        best = 12
        waitForIdle()
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("12")
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("12")

        // A new game within the session resets current but best holds the session max.
        current = 0
        waitForIdle()
        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("0")
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("12")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun groupsLargeNumbersForReadability() = runComposeUiTest {
        setContent {
            FuseTheme { ScoreHud(current = 12345L, best = 1048576L) }
        }

        onNodeWithTag(ScoreHudTags.SCORE_VALUE).assertTextEquals("12 345")
        onNodeWithTag(ScoreHudTags.BEST_VALUE).assertTextEquals("1 048 576")
    }
}
