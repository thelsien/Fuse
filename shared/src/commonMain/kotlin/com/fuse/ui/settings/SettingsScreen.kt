package com.fuse.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuse.ads.AdProvider
import com.fuse.ads.AdsDebug
import com.fuse.presentation.RemoveAdsStore
import com.fuse.feedback.ColorblindSettings
import com.fuse.feedback.HapticsSettings
import com.fuse.feedback.ReducedMotionSettings
import com.fuse.feedback.SoundSettings
import com.fuse.ui.theme.FuseTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * SHL-3 — the real **Settings** screen: four persisted, live-applied toggles.
 *
 * ## Two layers (testability)
 * This file exposes the screen in two pieces, the same split as [com.fuse.ui.home.HomeScreen]:
 *  - A **presentational** [SettingsScreen] overload that takes the four current values plus four
 *    `onToggle` callbacks and an [onBack]. It owns NO state and needs no Koin, so UI tests drive it
 *    with plain booleans/lambdas.
 *  - A thin **stateful wrapper** [SettingsScreen] (the one `App()`/the nav graph calls) that
 *    resolves the four settings holders from Koin, reads their Compose-state-backed flags (so the
 *    switches reflect live state), and binds each `onToggle` to the holder's `setEnabled` (which
 *    flips the flag live AND persists it).
 *
 * ## The four toggles (acceptance criteria)
 *  - **Sound** → [SoundSettings.setEnabled]; the [com.fuse.feedback.SoundCoordinator] reads the
 *    flag at dispatch, so the next merge is muted/unmuted immediately.
 *  - **Haptics** → [HapticsSettings.setEnabled]; same live read by
 *    [com.fuse.feedback.HapticsCoordinator].
 *  - **Reduced motion** → [ReducedMotionSettings.setEnabled]; `App()` reads it into
 *    `FuseTheme(reducedMotion = …)`, so flipping it RE-THEMES live — slides/particles/combo
 *    collapse immediately (FEL-8).
 *  - **Colorblind mode** → [ColorblindSettings.setEnabled]; `App()` reads it into
 *    `FuseTheme(colorblind = …)`. The toggle/persistence/seam ship here; the colorblind-safe
 *    PALETTE behind the flag is ACC-1 (Sprint 10).
 *
 * All four are persisted via `multiplatform-settings` (the holders write through on `setEnabled`)
 * and seeded on launch, so they survive a relaunch.
 *
 * ## Styling — tokens only
 * Token-styled list of labeled-`Switch` rows (each a `card`-clipped, hair-lined surface) plus the
 * existing "‹ Home" back affordance wired to [onBack] (nav `popBackStack`). Stable per-row tags
 * ([SettingsScreenTags]) let tests target switches without depending on copy.
 *
 * @param onBack navigate back to Home (nav `popBackStack`).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRemoveAds: () -> Unit,
    modifier: Modifier = Modifier,
    soundSettings: SoundSettings = koinInject(),
    hapticsSettings: HapticsSettings = koinInject(),
    reducedMotionSettings: ReducedMotionSettings = koinInject(),
    colorblindSettings: ColorblindSettings = koinInject(),
    adProvider: AdProvider = koinInject(),
    removeAdsStore: RemoveAdsStore = koinInject(),
) {
    // IAP-4 — the live ownership, collected so the "Remove Ads" entry flips to "✓ Owned" with no
    // restart once the paywall purchase/restore grants the entitlement.
    val removeAds by removeAdsStore.state.collectAsState()
    SettingsScreen(
        sound = soundSettings.soundEnabled,
        haptics = hapticsSettings.hapticsEnabled,
        reducedMotion = reducedMotionSettings.reducedMotionEnabled,
        colorblind = colorblindSettings.colorblindEnabled,
        onToggleSound = soundSettings::setEnabled,
        onToggleHaptics = hapticsSettings::setEnabled,
        onToggleReducedMotion = reducedMotionSettings::setEnabled,
        onToggleColorblind = colorblindSettings::setEnabled,
        onBack = onBack,
        // IAP-4 — the real "Remove Ads" entry to the paywall (REMOVE_ADS route), replacing IAP-0's
        // debug spike row. Shows "✓ Owned" when already entitled.
        onOpenRemoveAds = onOpenRemoveAds,
        removeAdsOwned = removeAds.owned,
        modifier = modifier,
        // ADS-0 (Sprint 8 spike) — the debug-only "Show test ad" trigger, gated by AdsDebug.enabled
        // and behind the spike branch. Resolves the AdProvider (Google-TEST rewarded ad on Android;
        // Swift-bridged on iOS) and loads+shows ONE test ad, surfacing the coarse AdResult. NOT a
        // real placement (that's ADS-2/4) — purely to verify the native seam end to end.
        debugAdSection = if (AdsDebug.enabled) {
            { DebugAdTrigger(adProvider) }
        } else {
            null
        },
    )
}

/**
 * ADS-0 — the debug spike trigger: a button that loads + shows one Google-TEST ad through the
 * injected [adProvider] and shows the resulting [com.fuse.ads.AdResult]. Behind a flag, never wired
 * into the game. Kept here (not in the presentational screen) so UI tests stay Koin-free.
 */
@Composable
private fun DebugAdTrigger(adProvider: AdProvider) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.card
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("idle") }
    var inFlight by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(SettingsScreenTags.DEBUG_AD_ROW),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Debug · Ads spike (ADS-0)",
            style = FuseTheme.type.headingM.copy(color = c.text),
        )
        Text(
            text = "Result: $status",
            style = FuseTheme.type.bodyM.copy(color = c.sub),
            modifier = Modifier.testTag(SettingsScreenTags.DEBUG_AD_RESULT),
        )
        TextButton(
            onClick = {
                if (inFlight) return@TextButton
                inFlight = true
                status = "loading…"
                scope.launch {
                    val result = adProvider.showRewardedTestAd()
                    status = result.name
                    inFlight = false
                }
            },
            modifier = Modifier.testTag(SettingsScreenTags.DEBUG_AD_BUTTON),
        ) {
            Text("Show test ad", style = FuseTheme.type.headingM.copy(color = c.accent))
        }
    }
}

/**
 * SHL-3 — the **presentational** Settings screen (value-driven; no Koin, no state).
 *
 * @param sound / [haptics] / [reducedMotion] / [colorblind] the current toggle values.
 * @param onToggleSound / [onToggleHaptics] / [onToggleReducedMotion] / [onToggleColorblind] called
 *   with the new value when the matching switch is flipped.
 * @param onBack navigate back to Home.
 */
@Composable
fun SettingsScreen(
    sound: Boolean,
    haptics: Boolean,
    reducedMotion: Boolean,
    colorblind: Boolean,
    onToggleSound: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onToggleColorblind: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenRemoveAds: () -> Unit,
    modifier: Modifier = Modifier,
    removeAdsOwned: Boolean = false,
    debugAdSection: (@Composable () -> Unit)? = null,
) {
    val c = FuseTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag(SettingsScreenTags.ROOT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: a single back affordance (left-aligned), coherent with the nav back stack.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(SettingsScreenTags.BACK),
            ) {
                Text("‹ Home", style = FuseTheme.type.headingM.copy(color = c.text))
            }
        }

        Spacer(Modifier.heightIn(min = 8.dp))

        Text(
            text = "Settings",
            style = FuseTheme.type.titleL.copy(color = c.text),
            modifier = Modifier.testTag(SettingsScreenTags.TITLE),
        )

        Spacer(Modifier.heightIn(min = 24.dp))

        // The four toggle rows + Remove-Ads entry. Scrollable so content (which grew with the
        // Remove-Ads row) never gets cut off on shorter screens.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .widthIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToggleRow(
                label = "Sound",
                checked = sound,
                onToggle = onToggleSound,
                rowTag = SettingsScreenTags.SOUND_ROW,
                switchTag = SettingsScreenTags.SOUND_SWITCH,
            )
            ToggleRow(
                label = "Haptics",
                checked = haptics,
                onToggle = onToggleHaptics,
                rowTag = SettingsScreenTags.HAPTICS_ROW,
                switchTag = SettingsScreenTags.HAPTICS_SWITCH,
            )
            ToggleRow(
                label = "Reduced motion",
                checked = reducedMotion,
                onToggle = onToggleReducedMotion,
                rowTag = SettingsScreenTags.REDUCED_MOTION_ROW,
                switchTag = SettingsScreenTags.REDUCED_MOTION_SWITCH,
            )
            ToggleRow(
                label = "Colorblind mode",
                checked = colorblind,
                onToggle = onToggleColorblind,
                rowTag = SettingsScreenTags.COLORBLIND_ROW,
                switchTag = SettingsScreenTags.COLORBLIND_SWITCH,
            )

            // IAP-4 — the "Remove Ads" navigation entry to the real paywall (replaces IAP-0's debug
            // spike row). Shows "✓ Owned" once the entitlement is granted.
            NavRow(
                label = "Remove Ads",
                trailing = if (removeAdsOwned) "✓ Owned" else "›",
                onClick = onOpenRemoveAds,
                rowTag = SettingsScreenTags.REMOVE_ADS_ROW,
            )

            // ADS-0 (Sprint 8 spike) — optional debug ad trigger, supplied only by the stateful
            // wrapper when AdsDebug.enabled. Null in tests/previews, so the screen stays Koin-free.
            debugAdSection?.invoke()
        }
    }
}

/** One token-styled settings row: a label on the left, a [Switch] on the right. */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    rowTag: String,
    switchTag: String,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(rowTag)
            .semantics { contentDescription = "$label ${if (checked) "on" else "off"}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = FuseTheme.type.headingM.copy(color = c.text),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            modifier = Modifier.testTag(switchTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.bg,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.sub,
                uncheckedTrackColor = c.card2,
                uncheckedBorderColor = c.line,
            ),
        )
    }
}

/**
 * IAP-4 — one token-styled tappable navigation row: a label on the left, a trailing affordance on
 * the right (a chevron, or "✓ Owned"). Used for the "Remove Ads" entry to the paywall.
 */
@Composable
private fun NavRow(
    label: String,
    trailing: String,
    onClick: () -> Unit,
    rowTag: String,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(rowTag)
            .semantics { contentDescription = "$label, $trailing" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = FuseTheme.type.headingM.copy(color = c.text),
        )
        Text(
            text = trailing,
            style = FuseTheme.type.headingM.copy(color = c.sub),
        )
    }
}

/** Stable test tags so UI tests target Settings nodes without depending on copy. */
object SettingsScreenTags {
    const val ROOT: String = "settings_screen"
    const val TITLE: String = "settings_title"
    const val BACK: String = "settings_back"

    const val SOUND_ROW: String = "settings_sound_row"
    const val SOUND_SWITCH: String = "settings_sound_switch"
    const val HAPTICS_ROW: String = "settings_haptics_row"
    const val HAPTICS_SWITCH: String = "settings_haptics_switch"
    const val REDUCED_MOTION_ROW: String = "settings_reduced_motion_row"
    const val REDUCED_MOTION_SWITCH: String = "settings_reduced_motion_switch"
    const val COLORBLIND_ROW: String = "settings_colorblind_row"
    const val COLORBLIND_SWITCH: String = "settings_colorblind_switch"

    // IAP-4 — the "Remove Ads" paywall navigation entry.
    const val REMOVE_ADS_ROW: String = "settings_remove_ads_row"

    // ADS-0 (Sprint 8 spike) — debug ad trigger tags.
    const val DEBUG_AD_ROW: String = "settings_debug_ad_row"
    const val DEBUG_AD_BUTTON: String = "settings_debug_ad_button"
    const val DEBUG_AD_RESULT: String = "settings_debug_ad_result"
}
