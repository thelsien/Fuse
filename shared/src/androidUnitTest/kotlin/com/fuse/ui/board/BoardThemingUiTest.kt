package com.fuse.ui.board

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.fuse.cosmetics.Cosmetics
import com.fuse.engine.Board
import com.fuse.presentation.AchievementsStore
import com.fuse.presentation.CosmeticsIntent
import com.fuse.presentation.CosmeticsStore
import com.fuse.ui.theme.CosmeticStyles
import com.fuse.ui.theme.FuseTheme
import com.fuse.ui.theme.LocalTileRamp
import com.fuse.ui.theme.TileRamp
import com.fuse.ui.theme.TileRampStyle
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * COS-2 — UI tests that prove the EQUIPPED TILE_SKIN restyles the board LIVE through the theme.
 *
 * The board reads its ramp from the composition local `LocalTileRamp` (via `FuseTheme.tiles`), so
 * these tests assert against the RAMP THE BOARD WOULD USE — captured by reading `LocalTileRamp.current`
 * inside the same `FuseTheme` content as the rendered board. (Pixel capture under Robolectric is
 * flaky; the resolved-ramp identity + the pure color-diff in `CosmeticStylesTest` together prove the
 * tiles actually recolor.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoardThemingUiTest {

    private val board = Board.fromValues(
        arrayOf(
            intArrayOf(2, 4, 8, 16),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 2048),
        ),
    )

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun defaultEquipped_boardUsesDefaultRamp_currentLook() = runComposeUiTest {
        var providedRamp: TileRampStyle? = null
        setContent {
            // Default FuseTheme (no tileRamp arg) — the board renders the current look.
            FuseTheme {
                providedRamp = LocalTileRamp.current
                BoardView(board)
            }
        }
        // Sanity: tiles render, and the ramp the board reads is the default (identity) ramp.
        onNodeWithText("2").assertExists()
        onNodeWithText("2048").assertExists()
        assertSame(TileRamp, providedRamp, "default theme must provide the current TileRamp")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nonDefaultTileSkinProvided_boardReadsTheVariantRamp() = runComposeUiTest {
        val goldRush = CosmeticStyles.tileRamp(CosmeticStyles.GOLD_RUSH)
        var providedRamp: TileRampStyle? = null
        setContent {
            FuseTheme(tileRamp = goldRush) {
                providedRamp = LocalTileRamp.current
                BoardView(board)
            }
        }
        onNodeWithText("8").assertExists()
        // The board reads the VARIANT ramp, not the default — and the variant differs in color.
        assertSame(goldRush, providedRamp)
        assertNotEquals(
            TileRamp.forValue(8).bg,
            providedRamp!!.forValue(8).bg,
            "the variant ramp must recolor the tile vs default",
        )
    }

    /**
     * LIVE equip: drive a real [CosmeticsStore], resolve its equipped tile-skin → ramp the way
     * `App()` does, and assert that equipping the gold-rush skin swaps the ramp the board reads
     * WITHOUT recreating the composition (the store flips, recomposition re-resolves). The skin is
     * achievement-gated (REACHED_2048), so we seed that achievement first.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun equippingSkin_swapsTheBoardRampLive() = runComposeUiTest {
        val achievements = AchievementsStore().apply { markReached2048() }
        val store = CosmeticsStore(achievementsStore = achievements)

        var providedRamp: TileRampStyle? = null
        setContent {
            // Mirror App(): collect the equipped, resolve tile skin → ramp, feed FuseTheme.
            val cosmetics by store.state.collectAsState()
            val tileSkin = Cosmetics.byId(cosmetics.equipped.tileSkinId)
            val ramp = CosmeticStyles.tileRamp(tileSkin?.styleId ?: Cosmetics.DEFAULT_STYLE)
            FuseTheme(tileRamp = ramp) {
                providedRamp = LocalTileRamp.current
                BoardView(board)
            }
        }

        // Starts on the default ramp (identity).
        onNodeWithText("8").assertExists()
        assertSame(TileRamp, providedRamp)
        val defaultBg = providedRamp!!.forValue(8).bg

        // Equip Gold Rush — the board recomposes to the new ramp (no restart).
        store.accept(CosmeticsIntent.Equip(Cosmetics.GoldRushTileSkin.id))
        waitForIdle()

        assertNotEquals(TileRamp, providedRamp, "equipping must swap the ramp the board reads")
        assertNotEquals(defaultBg, providedRamp!!.forValue(8).bg, "the tile must recolor live")
        assertEquals(CosmeticStyles.tileRamp(CosmeticStyles.GOLD_RUSH).forValue(8).bg, providedRamp!!.forValue(8).bg)
    }
}
