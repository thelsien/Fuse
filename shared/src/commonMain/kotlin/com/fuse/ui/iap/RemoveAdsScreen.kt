package com.fuse.ui.iap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fuse.iap.PurchaseResult
import com.fuse.presentation.RemoveAdsStore
import com.fuse.presentation.RemoveAdsUiState
import com.fuse.presentation.RestoreResult
import com.fuse.ui.theme.FuseTheme
import org.koin.compose.koinInject

/**
 * IAP-4 (Sprint 9) — the **Remove Ads** paywall: a simple screen presenting the Remove-Ads value +
 * the store-localized price, with a **Buy** and a (store-review-mandated) **Restore purchases**
 * action. It is the real entry that replaces IAP-0's debug "Buy Remove Ads (spike)" Settings row;
 * reached from a "Remove Ads" entry in Settings via the [com.fuse.ui.nav.FuseDestinations.REMOVE_ADS]
 * nav route.
 *
 * ## Two layers (testability), mirroring Home/Settings/Collection
 *  - A thin **stateful wrapper** [RemoveAdsScreen] (the one `App()`/the nav graph calls) that
 *    resolves the shared [RemoveAdsStore] from Koin (overridable so UI tests inject a store over
 *    [com.fuse.iap.FakeBillingProvider]), collects its [RemoveAdsStore.state], binds Buy/Restore to
 *    [RemoveAdsStore.purchase]/[RemoveAdsStore.restore], and collects the one-shot
 *    [RemoveAdsStore.outcomes] / [RemoveAdsStore.restoreOutcomes] into a fire-once status message
 *    (so a "Ads removed — thanks!" / "Purchases restored" never re-shows on recomposition).
 *  - A **presentational** [RemoveAdsScreen] overload that takes the immutable [RemoveAdsUiState], a
 *    `message` string, and `onBuy`/`onRestore`/`onBack`, so it renders with no Koin and no flows.
 *
 * ## What it shows
 *  - A **value prop**: the title "Remove Ads" + an honest benefit line — "Removes the between-game
 *    ads. Rewarded options like revive and the streak-saver stay available." (rewarded ads are never
 *    entitlement-gated; only interstitials are suppressed — IAP-2).
 *  - The **localized price** rendered VERBATIM from [RemoveAdsUiState.price] (never reformatted).
 *  - A **Buy** button enabled on [RemoveAdsUiState.canPurchase] → [RemoveAdsStore.purchase]. When
 *    [RemoveAdsUiState.owned] it is replaced by "You own Remove Ads — thank you!".
 *  - A **Restore purchases** secondary button — ALWAYS present (store review requires it), enabled on
 *    [RemoveAdsUiState.canRestore] → [RemoveAdsStore.restore]. Present even when `product == null` so
 *    a returning owner on a fresh install can re-entitle.
 *  - Graceful states: `product == null` → "Store unavailable" (Buy hidden, Restore still present);
 *    `loading`/in-progress → disabled controls + a progress indicator.
 *
 * ## Back
 * A single "‹ Settings" affordance wired to [onBack] (`popBackStack`) — the SHL-2 pattern (system
 * back on Android, this in-screen button on iOS).
 *
 * @param onBack navigate back to Settings (nav `popBackStack`).
 * @param store the shared Remove-Ads store; resolved from Koin, overridable for tests.
 */
@Composable
fun RemoveAdsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    store: RemoveAdsStore = koinInject(),
) {
    val state by store.state.collectAsState()

    // IAP-4 — collect the one-shot purchase/restore outcomes into a fire-once status message. Hot,
    // non-replaying flows (RemoveAdsStore.outcomes / restoreOutcomes), so a message shows exactly
    // once and never re-fires on recomposition (mirrors GameScreen's effect collectors).
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        store.outcomes.collect { result ->
            message = when (result) {
                PurchaseResult.Purchased -> "Ads removed — thanks!"
                PurchaseResult.AlreadyOwned -> "Ads removed — thanks!"
                PurchaseResult.Failed -> "Purchase failed"
                PurchaseResult.Pending -> "Purchase pending"
                // Cancelled is quiet — the user backed out on purpose.
                PurchaseResult.Cancelled -> null
            }
        }
    }
    LaunchedEffect(store) {
        store.restoreOutcomes.collect { result ->
            message = when (result) {
                RestoreResult.Restored -> "Purchases restored"
                RestoreResult.NothingToRestore -> "Nothing to restore"
                RestoreResult.Failed -> "Restore failed"
            }
        }
    }

    RemoveAdsScreen(
        state = state,
        message = message,
        onBuy = store::purchase,
        onRestore = store::restore,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * IAP-4 — the **presentational** Remove-Ads paywall (value-driven; no Koin, no flows).
 *
 * @param state the Remove-Ads UI projection (product + localized price + owned/loading/in-progress).
 * @param message the fire-once status line, or `null` for none.
 * @param onBuy invoked when the enabled Buy button is tapped.
 * @param onRestore invoked when the enabled Restore button is tapped.
 * @param onBack navigate back to Settings.
 */
@Composable
fun RemoveAdsScreen(
    state: RemoveAdsUiState,
    message: String?,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.card
    val busy = state.loading || state.purchaseInProgress || state.restoreInProgress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .testTag(RemoveAdsScreenTags.ROOT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: a single back affordance (SHL-2 pattern — system back on Android, this on iOS).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.testTag(RemoveAdsScreenTags.BACK),
            ) {
                Text("‹ Settings", style = FuseTheme.type.headingM.copy(color = c.text))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.heightIn(min = 8.dp))

            // Value prop — the title.
            Text(
                text = "Remove Ads",
                style = FuseTheme.type.titleL.copy(color = c.text),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RemoveAdsScreenTags.TITLE),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.heightIn(min = 12.dp))

            // Value prop — the honest benefit line.
            Text(
                text = "Removes the between-game ads. Rewarded options like revive and the " +
                    "streak-saver stay available.",
                style = FuseTheme.type.bodyL.copy(color = c.sub),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(RemoveAdsScreenTags.BENEFIT),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.heightIn(min = 24.dp))

            // The price / availability card.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.card)
                    .border(1.dp, c.line, shape)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    state.price != null -> Text(
                        text = state.price!!,
                        style = FuseTheme.type.titleM.copy(color = c.text),
                        modifier = Modifier.testTag(RemoveAdsScreenTags.PRICE),
                    )
                    else -> Text(
                        // product == null → graceful unavailable (Restore stays present below).
                        text = "Store unavailable",
                        style = FuseTheme.type.headingM.copy(color = c.sub),
                        modifier = Modifier.testTag(RemoveAdsScreenTags.UNAVAILABLE),
                    )
                }

                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(22.dp)
                            .testTag(RemoveAdsScreenTags.PROGRESS),
                        color = c.accent,
                    )
                }
            }

            Spacer(Modifier.heightIn(min = 20.dp))

            // Primary action: Buy, OR the owned acknowledgement.
            if (state.owned) {
                Text(
                    text = "You own Remove Ads — thank you!",
                    style = FuseTheme.type.headingM.copy(color = c.good),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(RemoveAdsScreenTags.OWNED),
                    textAlign = TextAlign.Center,
                )
            } else {
                PrimaryButton(
                    label = "Buy",
                    enabled = state.canPurchase,
                    onClick = onBuy,
                    tag = RemoveAdsScreenTags.BUY,
                )
            }

            Spacer(Modifier.heightIn(min = 12.dp))

            // Secondary action: Restore — ALWAYS present (store review requires it).
            SecondaryButton(
                label = "Restore purchases",
                enabled = state.canRestore,
                onClick = onRestore,
                tag = RemoveAdsScreenTags.RESTORE,
            )

            Spacer(Modifier.heightIn(min = 16.dp))

            // The fire-once status message (purchase/restore outcome).
            if (message != null) {
                Text(
                    text = message,
                    style = FuseTheme.type.bodyM.copy(color = c.sub),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(RemoveAdsScreenTags.MESSAGE),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A token-styled filled primary button (accent), disabled-dimmed when not [enabled]. */
@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.pill
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) c.accent else c.card2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp)
            .testTag(tag)
            .semantics { contentDescription = "$label ${if (enabled) "enabled" else "disabled"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FuseTheme.type.headingM.copy(color = if (enabled) c.bg else c.sub),
        )
    }
}

/** A token-styled outlined secondary button, disabled-dimmed when not [enabled]. */
@Composable
private fun SecondaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    val shape = FuseTheme.shapes.pill
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp)
            .testTag(tag)
            .semantics { contentDescription = "$label ${if (enabled) "enabled" else "disabled"}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FuseTheme.type.headingM.copy(color = if (enabled) c.text else c.sub),
        )
    }
}

/** Stable test tags so UI tests target paywall nodes without depending on copy. */
object RemoveAdsScreenTags {
    const val ROOT: String = "remove_ads_screen"
    const val BACK: String = "remove_ads_back"
    const val TITLE: String = "remove_ads_title"
    const val BENEFIT: String = "remove_ads_benefit"
    const val PRICE: String = "remove_ads_price"
    const val UNAVAILABLE: String = "remove_ads_unavailable"
    const val PROGRESS: String = "remove_ads_progress"
    const val BUY: String = "remove_ads_buy"
    const val OWNED: String = "remove_ads_owned"
    const val RESTORE: String = "remove_ads_restore"
    const val MESSAGE: String = "remove_ads_message"
}
