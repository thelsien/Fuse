package com.fuse.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuse.feedback.HapticsCoordinator
import com.fuse.feedback.SoundCoordinator
import com.fuse.feedback.comboCount
import com.fuse.feedback.isCombo
import com.fuse.feedback.milestoneReached
import com.fuse.presentation.AchievementsStore
import com.fuse.presentation.GameEffect
import com.fuse.presentation.GameIntent
import com.fuse.presentation.GameStore
import com.fuse.presentation.GameUiState
import com.fuse.ui.board.BoardTransition
import com.fuse.ui.board.BoardView
import com.fuse.ui.input.swipeable
import com.fuse.ui.theme.FuseTheme
import kotlinx.coroutines.flow.filterIsInstance
import org.koin.compose.koinInject

/**
 * UIB-3 — the playable game screen: the First-Playable binding of swipe input to the
 * pure engine through the [GameStore].
 *
 * It resolves the [GameStore] from Koin (overridable via [store] for tests), collects
 * its [GameStore.state], and renders:
 *  - the [ScoreHud] (UIB-4) showing live current + session-best score,
 *  - the [BoardView] wrapped in [Modifier.swipeable] so every resolved swipe becomes a
 *    [GameIntent.Move] (engine → new state → recomposition), disabled once the game is
 *    over,
 *  - a "New game" button submitting [GameIntent.NewGame].
 *
 * A blocked swipe is a visible no-op: the store leaves the board/score unchanged and
 * raises [GameUiState.lastMoveBlocked] (plus a one-shot effect); a small "blocked"
 * marker is shown here so the no-op is observable. Win/lose overlays consuming
 * [GameUiState.phase] / [GameUiState.justWon] / [GameUiState.gameOver] are UIB-5.
 *
 * @param store the MVI store; defaults to the Koin-provided singleton.
 * @param modifier outer modifier.
 * @param onBack back affordance to the Home screen — `null` by default so existing call
 *   sites/tests are unchanged; the button only renders when a handler is supplied. SHL-2 wires
 *   this to the nav back stack: `App()` passes `navController.popBackStack()`, so this in-screen
 *   "‹ Home" button is the single coherent back (it is the iOS back affordance, and on Android it
 *   sits alongside the NavHost's automatic system-back handling — both pop to Home).
 */
@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    store: GameStore = koinInject(),
    haptics: HapticsCoordinator = koinInject(),
    sound: SoundCoordinator = koinInject(),
    achievements: AchievementsStore = koinInject(),
    onBack: (() -> Unit)? = null,
) {
    val state by store.state.collectAsState()

    // FEL-6 — milestone VISUAL trigger. A one-shot token (value + nonce) raised below from a
    // milestone `Moved`; the layered [MilestoneEffect] is keyed on it and (re)plays the
    // flash + particle burst, then self-dismisses. Driven from the one-shot effect (not state)
    // so it fires ONCE when the milestone is reached and never re-shows on later moves. A
    // monotonic nonce makes two consecutive same-tier milestones distinct so the effect restarts.
    var milestoneTrigger by remember(store) { mutableStateOf<MilestoneTrigger?>(null) }
    var milestoneNonce by remember(store) { mutableStateOf(0L) }

    // FEL-7 — combo COUNTER + escalating VISUAL cue. A one-shot token (count + nonce) raised below
    // from a multi-merge `Moved`; the layered [ComboEffect] is keyed on it and (re)plays the
    // "x{n}" badge pop, then self-dismisses. Same one-shot discipline as the milestone trigger so
    // it fires ONCE per combo move and never re-shows on later moves/recomposition. The nonce makes
    // two consecutive same-size combos distinct so the effect restarts.
    var comboTrigger by remember(store) { mutableStateOf<ComboTrigger?>(null) }
    var comboNonce by remember(store) { mutableStateOf(0L) }

    // FEL-4/FEL-5 — feedback. A thin collector turns the store's one-shot effects into the
    // pure decisions for BOTH channels off the same [GameEffect.Moved] signal:
    //  - haptics: accepted Moved → tick (merge) / thunk (milestone); Blocked → buzz.
    //  - sound: accepted Moved → a climbing merge tone (pitch rises with the highest merged
    //    tile) plus a milestone/win sting; a Blocked move plays NO sound (an error sound on
    //    every failed swipe would grate — the haptic buzz already marks it).
    // Each channel is gated by its own toggle inside its coordinator. Driven off the
    // non-replaying `effects` flow so each cue fires exactly once per move and is never
    // re-triggered by recomposition. (Real audio/haptics only on a physical device;
    // emulators/simulators run this harmlessly — no crash, often no audible output.)
    LaunchedEffect(store, haptics, sound, achievements) {
        store.effects.collect { effect ->
            when (effect) {
                is GameEffect.Moved -> {
                    haptics.onMove(effect.mergedValues, effect.justWon)
                    sound.onMove(effect.mergedValues, effect.justWon)
                    // FEL-6 — VISUAL only (no haptics/sound here; FEL-4/5 already fired above).
                    // If this move produced a milestone tile, raise the one-shot visual on the
                    // HIGHEST milestone reached. The nonce makes a repeated same-tier milestone a
                    // distinct token so the keyed effect restarts.
                    milestoneReached(effect.mergedValues)?.let { value ->
                        milestoneNonce += 1
                        milestoneTrigger = MilestoneTrigger(value = value, nonce = milestoneNonce)
                    }
                    // FEL-7 — VISUAL only (no extra haptics/sound; FEL-4/5 already fired per merge).
                    // If this move was a combo (>= 2 merges), raise the one-shot "x{n}" badge on the
                    // merge count. The nonce makes a repeated same-size combo a distinct token so the
                    // keyed effect restarts. A move can be BOTH a combo and a milestone — both fire.
                    if (isCombo(effect.mergedValues)) {
                        comboNonce += 1
                        comboTrigger = ComboTrigger(
                            count = comboCount(effect.mergedValues),
                            nonce = comboNonce,
                        )
                    }
                }
                GameEffect.Blocked -> haptics.onBlocked()
                // COS-1 — record the "reached 2048" achievement on the FIRST win, exactly once.
                // Mirrors DailyScreen recording the streak on DailyEffect.Solved: the one-shot
                // GameEffect.Won fires only on the move that first reaches the target, and
                // markReached2048() is idempotent, so this can't double-set. Reaching 2048
                // auto-unlocks the gated cosmetic (no purchase) via AchievementsStore.state,
                // which CosmeticsStore observes.
                GameEffect.Won -> achievements.markReached2048()
            }
        }
    }

    // UIB-5 — win-once-then-keep-going.
    //
    // The persistent `Won` phase / `justWon` flag are the WRONG driver for the
    // celebration: deriving the overlay from them would re-show it (or, with
    // `LaunchedEffect(state.justWon)`, risk re-firing) on the moves the player makes
    // after "Keep going". Instead we flip a local UI flag from the store's one-shot
    // [GameEffect.Won], which is emitted exactly once on the move that first reaches the
    // target and never replays. "Keep going" / "Restart" simply clear the flag; play
    // continues because the engine phase stays `Won(canContinue = true)`.
    var showWin by remember(store) { mutableStateOf(false) }
    LaunchedEffect(store) {
        store.effects.filterIsInstance<GameEffect.Won>().collect { showWin = true }
    }

    GameScreenContent(
        state = state,
        onSwipe = { store.accept(GameIntent.Move(it)) },
        onNewGame = { store.accept(GameIntent.NewGame()) },
        milestoneTrigger = milestoneTrigger,
        onMilestoneDismissed = { milestoneTrigger = null },
        comboTrigger = comboTrigger,
        onComboDismissed = { comboTrigger = null },
        showWin = showWin,
        onKeepGoing = { showWin = false },
        onRestart = {
            showWin = false
            store.accept(GameIntent.NewGame())
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless content of [GameScreen] — a pure function of [state] + callbacks, so it
 * renders identically under preview/test without Koin or a real store.
 */
@Composable
fun GameScreenContent(
    state: GameUiState,
    onSwipe: (com.fuse.engine.Direction) -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
    milestoneTrigger: MilestoneTrigger? = null,
    onMilestoneDismissed: () -> Unit = {},
    comboTrigger: ComboTrigger? = null,
    onComboDismissed: () -> Unit = {},
    showWin: Boolean = false,
    onKeepGoing: () -> Unit = {},
    onRestart: () -> Unit = onNewGame,
    onBack: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(GameScreenTags.ROOT),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // SHL-2 — back affordance to Home (only when a handler is supplied by App()).
            // Nav-driven: App() wires this to navController.popBackStack(). Left-aligned, above HUD.
            if (onBack != null) {
                androidx.compose.material3.TextButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .testTag(GameScreenTags.BACK),
                ) {
                    Text("‹ Home")
                }
            }

            // UIB-4 — the score HUD (live current + session best), fed from store state.
            ScoreHud(
                current = state.currentScore,
                best = state.bestScore,
            )

            Spacer(Modifier.height(16.dp))

            BoardView(
                board = state.board,
                // FEL-2 — pop/glow the just-merged result tiles, and FEL-3 — play the
                // entrance for the tile that spawned this move (after the slide settles).
                // Empty merges + null spawn (blocked / new game) ⇒ BoardTransition.None.
                transition = BoardTransition.fromOutcome(
                    merges = state.lastMerges,
                    spawnedId = state.spawned?.id,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GameScreenTags.BOARD)
                    .semantics {
                        // Surface the blocked no-op for assertion / accessibility.
                        if (state.lastMoveBlocked) contentDescription = BLOCKED_DESCRIPTION
                    }
                    .swipeable(
                        onSwipe = onSwipe,
                        enabled = !state.isGameOver,
                    ),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onNewGame,
                modifier = Modifier.testTag(GameScreenTags.NEW_GAME),
            ) {
                Text("New game")
            }
        }

        // FEL-6 — the milestone celebration (flash + particle burst), layered over the board but
        // BELOW the end-game overlays (a win/lose card should dominate). Self-dismisses: once a
        // trigger is set, a LaunchedEffect waits out the effect's lifetime then clears it back to
        // null so it does not linger. The composable itself no-ops under reduced motion / null.
        if (milestoneTrigger != null) {
            MilestoneEffect(token = milestoneTrigger)
            val dismissMs = FuseTheme.motion.milestoneMs
            LaunchedEffect(milestoneTrigger) {
                kotlinx.coroutines.delay(dismissMs.toLong())
                onMilestoneDismissed()
            }
        }

        // FEL-7 — the combo "x{n}" badge, layered over the board. It sits in the top-third (the
        // [ComboEffect] aligns itself TopCenter) while the milestone burst is centred, so when a
        // move is BOTH a combo and a milestone the two read cleanly without clobbering each other.
        // Same self-dismiss discipline as the milestone block: a LaunchedEffect waits out the
        // effect's lifetime then clears the trigger. The composable no-ops under reduced motion / null.
        if (comboTrigger != null) {
            ComboEffect(token = comboTrigger)
            val comboDismissMs = FuseTheme.motion.comboMs
            LaunchedEffect(comboTrigger) {
                kotlinx.coroutines.delay(comboDismissMs.toLong())
                onComboDismissed()
            }
        }

        // UIB-5 — end-of-game overlays, layered on top of the board.
        //
        // Lose is derived straight from state: `Lost` is terminal, so the overlay should
        // persist for the whole game-over condition (and swipe is already disabled when
        // isGameOver). Win is gated by the one-shot-driven [showWin] flag so it shows
        // ONCE on first-2048 and not again after "Keep going". Lose wins the layer if a
        // continued game is later lost while the win overlay was somehow still up.
        if (state.isGameOver) {
            LoseOverlay(
                score = state.currentScore,
                best = state.bestScore,
                onRestart = onRestart,
            )
        } else if (showWin) {
            WinOverlay(
                score = state.currentScore,
                best = state.bestScore,
                onKeepGoing = onKeepGoing,
                onRestart = onRestart,
            )
        }
    }
}

/** Stable test tags so UI tests target nodes without depending on copy. */
object GameScreenTags {
    const val ROOT: String = "game_screen"
    const val BOARD: String = "game_board"
    const val NEW_GAME: String = "game_new_game"

    /** SHL-2 — the nav-driven back-to-Home affordance (rendered only when `onBack` is supplied). */
    const val BACK: String = "game_back"
}

/** Semantic marker placed on the board when the last swipe was blocked. */
const val BLOCKED_DESCRIPTION: String = "Move blocked"
