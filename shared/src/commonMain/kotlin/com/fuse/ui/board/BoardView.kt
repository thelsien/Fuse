package com.fuse.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.fuse.engine.Board
import com.fuse.engine.Tile
import com.fuse.ui.theme.FuseMotion
import com.fuse.ui.theme.FuseTheme
import com.fuse.ui.theme.TileRamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * UIB-1 — the Board renderer.
 *
 * A reusable, size-agnostic Compose Multiplatform composable that renders ANY
 * engine [Board] state as a square grid (4x4 by default, but it iterates
 * `0 until board.size` so 5x5 / 6x6 variants render unchanged).
 *
 * ## State / recomposition
 * [board] is a plain composable parameter — state is hoisted by the caller. Because
 * [Board] is an immutable value type with structural [Board.equals], passing a new
 * `Board` instance recomposes this function and re-lays the grid: the AC's
 * "recomposes on state change". There is no internal mutable state here.
 *
 * ## Geometry (scales from the ratio tokens, nothing hardcoded)
 * The board is laid out from the `pad : cell : gap` PROPORTIONS in [Dimens]
 * (12 : 72 : 11) rather than fixed dp. A [BoxWithConstraints] with a 1:1
 * [aspectRatio] gives a square box of the available width; cell + gap + pad sizes
 * are derived from that width via the ratios so the board scales to any device
 * width and stays square. For an `n x n` board the reference side is
 * `pad*2 + n*cell + (n-1)*gap`, and each token's fraction of that side is multiplied
 * by the measured width.
 *
 * ## Colors / numerals (from tokens only — no literal hex)
 * The board background uses [FuseTheme.colors] `boardBg`; empty cells use `card2`.
 * Each occupied cell is filled with [TileRamp.forValue]`(value).bg` and its numeral
 * is drawn in `.fg`, with corner radius from the shape tokens. The numeral is scaled
 * down for longer numbers ([tileFontSizeSp]) so 4-digit tiles stay readable.
 *
 * ## Slide animation (FEL-1)
 * Tiles are keyed by [Tile.id] and positioned by an absolute `offset` over a single
 * [Box]. Each keyed tile owns an [Animatable] of its top-left offset; because the
 * `key(tile.id)` block is stable across recompositions, a SURVIVING tile (same id,
 * new position) keeps its animatable and **slides** from its old offset to the new
 * one — `tile.id` is what gives the slide its continuity. A tile that has no prior
 * state (a freshly **spawned** tile or a **merged** result tile — both minted with
 * NEW ids) initializes its animatable AT its target offset, so it simply appears in
 * place with **no flicker / no teleport from the origin**. Slide duration & easing are
 * read from [FuseTheme.motion] (`tileSlideMs` / `tileSlideEasing`), so FEL-8 can flip
 * the whole board to reduced motion (durations -> ~1ms, an effective snap) by swapping
 * the provided [FuseMotion] — nothing here hardcodes a duration or curve.
 *
 * The position->offset math lives in [BoardGeometry] (pure, unit-tested in commonTest).
 *
 * ## Merge pop (FEL-2)
 * When supplied a [transition] (built by `GameScreen` from the store's `lastMerges`), each
 * tile whose id is a merge RESULT plays a one-shot scale-bounce + transient glow as it
 * appears. The pop's peak scale and the glow's alpha both scale with the resulting value's
 * TIER via [mergeIntensity] — a 1024 reads bigger than a 4. Pop/glow durations + easing come
 * from [FuseTheme.motion] (`mergePopMs` / `mergeGlowMs` / `mergePopEasing`), so FEL-8's
 * reduced-motion branch collapses them to an effective snap. `transition == null` (or
 * [BoardTransition.None]) ⇒ no pop, identical to FEL-1 behavior, so plain `BoardView(board)`
 * callers are unchanged.
 *
 * ## Spawn entrance (FEL-3)
 * The [transition] also carries `spawnedId` — the new id of the single tile that spawned on
 * this move. That tile starts hidden (scaled-down + alpha 0, seeded at composition so there
 * is no full-size flash) and, AFTER a delay equal to the slide duration (`tileSlideMs`, so it
 * enters once the board's movement has settled), scales+fades up to its resting state. A
 * spawn is never also a merge result ([BoardTransition.isSpawn] enforces this), so a given new
 * id plays AT MOST one of {slide-in-place, FEL-2 pop, FEL-3 entrance}. Under reduced motion the
 * delay and entrance collapse to ~1ms (an effective snap-in). `transition == null` ⇒ no entrance.
 *
 * ## What this deliberately does NOT do
 * The two MERGING SOURCE tiles are not slid INTO the result (they vanish from the new board
 * and the result pops in place).
 */
@Composable
fun BoardView(
    board: Board,
    modifier: Modifier = Modifier,
    transition: BoardTransition? = null,
) {
    val colors = FuseTheme.colors
    val motion = FuseTheme.motion
    val n = board.size

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(FuseTheme.shapes.card)
            .background(colors.boardBg),
    ) {
        val geometry = BoardGeometry.forBoard(n, maxWidth.value)
        val cell = geometry.cell.dp

        // Empty cell slots (subtle card2 background) for every grid position.
        for (row in 0 until n) {
            for (col in 0 until n) {
                Box(
                    modifier = Modifier
                        .offset(x = geometry.offsetX(col).dp, y = geometry.offsetY(row).dp)
                        .size(cell)
                        .clip(FuseTheme.shapes.tile)
                        .background(colors.card2),
                )
            }
        }

        // Occupied tiles, each keyed by its id so its slide animatable survives
        // recomposition (a surviving tile slides; a new-id tile appears in place).
        for ((position, tile) in board.tilesWithPositions()) {
            // Non-null only when this tile is a merge RESULT on this transition — drives
            // the one-shot pop/glow. Null for slid/spawned tiles (no pop).
            val popIntensity: Float? =
                if (transition?.isMergeResult(tile.id) == true) mergeIntensity(tile.value) else null
            // FEL-3 — true only for the freshly SPAWNED tile (a new id that is NOT a merge
            // result). isSpawn already enforces the spawn/merge mutual exclusion, so a tile
            // is at most one of {merge result, spawn}; here they cannot both be set.
            val isSpawn: Boolean = transition?.isSpawn(tile.id) == true
            key(tile.id) {
                TileCell(
                    tile = tile,
                    targetX = geometry.offsetX(position.col).dp,
                    targetY = geometry.offsetY(position.row).dp,
                    size = cell,
                    motion = motion,
                    popIntensity = popIntensity,
                    isSpawn = isSpawn,
                )
            }
        }
    }
}

@Composable
private fun TileCell(
    tile: Tile,
    targetX: Dp,
    targetY: Dp,
    size: Dp,
    motion: FuseMotion,
    modifier: Modifier = Modifier,
    popIntensity: Float? = null,
    isSpawn: Boolean = false,
) {
    // One Offset animatable per tile id. This whole composable sits inside key(tile.id),
    // so the remember SURVIVES recomposition for a surviving tile (same id, new target ->
    // it slides) and is FRESH for a new id (spawn / merge result). Seeded AT the first
    // target so a new-id tile appears in place — never sliding in from (0,0): no flicker.
    val target = Offset(targetX.value, targetY.value)
    val anim = remember { Animatable(target, Offset.VectorConverter) }

    LaunchedEffect(target, motion) {
        anim.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = motion.tileSlideMs,
                easing = motion.tileSlideEasing,
            ),
        )
    }

    // FEL-2 — merge pop. A merge-result tile (popIntensity != null) is freshly composed
    // (new id), so this one-shot LaunchedEffect runs exactly once as it appears: scale
    // grows to an intensity-scaled peak then settles to 1, with a glow that fades from an
    // intensity-scaled alpha. Plain/surviving tiles keep scale 1 / glow 0 — no effect.
    val pop = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    if (popIntensity != null) {
        LaunchedEffect(Unit) {
            val peak = mergePopPeakScale(popIntensity)
            val glowPeak = mergeGlowPeakAlpha(popIntensity)
            launch {
                glow.snapTo(glowPeak)
                glow.animateTo(0f, tween(motion.mergeGlowMs, easing = motion.standardEasing))
            }
            pop.snapTo(1f)
            pop.animateTo(peak, tween(motion.mergePopMs / 2, easing = motion.standardEasing))
            pop.animateTo(1f, tween(motion.mergePopMs / 2, easing = motion.mergePopEasing))
        }
    }

    // FEL-3 — spawn entrance. The spawned tile is a brand-new id (so these animatables are
    // FRESH), and it is NOT a merge result (mutual exclusion is enforced upstream by
    // BoardTransition.isSpawn). It starts HIDDEN (scaled down + transparent) from its very
    // first frame — seeded at composition time so there is no full-size flash — then, AFTER
    // a delay equal to the slide duration (so it appears once the board's movement has
    // settled), scales+fades up to its resting 1.0 / 1.0. Non-spawn tiles seed at 1/1 and
    // never run this effect, so slid/surviving/merge tiles are unaffected.
    val entranceScale = remember { Animatable(if (isSpawn) SPAWN_START_SCALE else 1f) }
    val entranceAlpha = remember { Animatable(if (isSpawn) 0f else 1f) }
    if (isSpawn) {
        LaunchedEffect(Unit) {
            // Wait for the slide to settle, THEN play the entrance — this ordering is the
            // FEL-3 acceptance criterion ("fades/scales in AFTER the movement settles").
            delay(motion.tileSlideMs.toLong())
            launch {
                entranceAlpha.animateTo(1f, tween(motion.spawnEntranceMs, easing = motion.standardEasing))
            }
            entranceScale.animateTo(1f, tween(motion.spawnEntranceMs, easing = motion.standardEasing))
        }
    }

    val tileColors = TileRamp.forValue(tile.value)
    Box(
        modifier = modifier
            .offset(x = anim.value.x.dp, y = anim.value.y.dp)
            .graphicsLayer {
                // pop (FEL-2) and entrance (FEL-3) are mutually exclusive per tile, so at
                // most one of these is ever != its resting value — multiplying composes them
                // safely either way (the other stays 1.0).
                scaleX = pop.value * entranceScale.value
                scaleY = pop.value * entranceScale.value
                alpha = entranceAlpha.value
            }
            .size(size)
            .clip(FuseTheme.shapes.tile)
            .background(tileColors.bg)
            .then(
                // Transient glow ring, fixed width so it doesn't shift layout; alpha fades.
                if (glow.value > 0f) {
                    Modifier.border(2.dp, Color.White.copy(alpha = glow.value), FuseTheme.shapes.tile)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val text = tile.value.toString()
        Text(
            text = text,
            color = tileColors.fg,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontSize = tileFontSizeSp(text.length).sp,
        )
    }
}

/**
 * Small self-contained demo of [BoardView] over a known [Board], for manual
 * viewing / a future preview route. Owns its own [FuseTheme] (dark) and is
 * Koin-free, so it drops into any preview or test harness. Renders a board with a
 * spread of ramp values (including a 4-digit 2048) to eyeball geometry + numerals.
 *
 * This is NOT wired into `App()` (UIB-1 ships the reusable renderer, not a screen);
 * swipe input is UIB-2 and the MVI store binding is UIB-3.
 */
@Composable
fun BoardPreview(modifier: Modifier = Modifier) {
    FuseTheme(darkTheme = true) {
        val board = Board.fromValues(
            arrayOf(
                intArrayOf(2, 4, 8, 16),
                intArrayOf(32, 64, 128, 256),
                intArrayOf(512, 1024, 2048, 0),
                intArrayOf(0, 0, 2, 0),
            ),
        )
        BoardView(
            board = board,
            modifier = modifier
                .background(FuseTheme.colors.bg)
                .padding(16.dp),
        )
    }
}

/**
 * Numeral size (sp) chosen by digit count so longer numbers stay inside the cell.
 * Pure function of the display length; unit-tested in commonTest. Sizes track the
 * type scale's tile-numeral roles (28 down to 17 for 4+ digits).
 */
internal fun tileFontSizeSp(digits: Int): Int = when {
    digits <= 1 -> 32
    digits == 2 -> 28
    digits == 3 -> 22
    digits == 4 -> 18
    else -> 15
}
