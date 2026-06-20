package com.fuse.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuse.engine.Board
import com.fuse.engine.Direction
import com.fuse.ui.board.BoardView
import com.fuse.ui.theme.FuseTheme

/**
 * UIB-2 manual-verification demo (NOT the game screen — that's UIB-3+).
 *
 * Applies [Modifier.swipeable] to a presentational [BoardView] and shows the last
 * resolved [Direction] as text. There is deliberately NO engine `move` call and NO
 * store here: the swipe is resolved to a [Direction] and handed to a callback that
 * only updates local demo state — proving the gesture layer in isolation.
 *
 * Self-contained (owns its own [FuseTheme], Koin-free) so it drops into a preview
 * route or the app root for eyeballing. UIB-3 will replace the `onSwipe` lambda with
 * a dispatch into the MVI GameStore.
 */
@Composable
fun SwipeDemo(modifier: Modifier = Modifier) {
    FuseTheme(darkTheme = true) {
        var last by remember { mutableStateOf<Direction?>(null) }
        val board = remember {
            Board.fromValues(
                arrayOf(
                    intArrayOf(2, 4, 0, 0),
                    intArrayOf(0, 8, 16, 0),
                    intArrayOf(0, 0, 32, 0),
                    intArrayOf(0, 0, 0, 64),
                ),
            )
        }
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(FuseTheme.colors.bg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Swipe the board — UIB-2",
                style = FuseTheme.type.headingXL.copy(color = FuseTheme.colors.text),
            )
            Text(
                "Last direction: ${last?.name ?: "—"}",
                style = FuseTheme.type.bodyL.copy(color = FuseTheme.colors.sub),
            )
            BoardView(
                board = board,
                modifier = Modifier.swipeable(onSwipe = { last = it }),
            )
        }
    }
}
