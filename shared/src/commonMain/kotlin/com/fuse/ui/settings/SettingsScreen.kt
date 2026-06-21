package com.fuse.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.fuse.ui.theme.FuseTheme

/**
 * SHL-2 — placeholder **Settings** destination.
 *
 * SHL-2 only owns the Settings *route* and a navigable, back-able shell; SHL-3 fills this with
 * the real content (the three toggles: reduced motion, haptics, sound). For now it shows a title,
 * a "coming soon" line, and an in-screen back affordance that calls [onBack] — the one coherent
 * back this screen exposes. On Android the same destination is also popped by the system back
 * (wired by the NavHost in `App()`); on iOS this button is the back affordance (no hardware back).
 *
 * Presentational + value-driven (only an [onBack] callback) so it renders under preview/test with
 * no Koin. SHL-3 will add settings state/callbacks here.
 *
 * @param onBack invoked to navigate back to Home (nav `popBackStack`).
 * @param modifier outer modifier.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag(SettingsScreenTags.ROOT),
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

        Spacer(Modifier.heightIn(min = 16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Settings",
                style = FuseTheme.type.titleL.copy(color = c.text),
                modifier = Modifier.testTag(SettingsScreenTags.TITLE),
            )
            Text(
                text = "Coming soon",
                style = FuseTheme.type.headingM.copy(color = c.sub),
            )
        }
    }
}

/** Stable test tags so UI tests target Settings nodes without depending on copy. */
object SettingsScreenTags {
    const val ROOT: String = "settings_screen"
    const val TITLE: String = "settings_title"
    const val BACK: String = "settings_back"
}
