package com.fuse.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FND-4 swatch / token preview screen.
 *
 * Renders the design system purely from the token layer — there is **no literal
 * hex anywhere below**; every color/size/shape comes from [FuseTheme] accessors
 * and [TileRamp]. A switch toggles the surrounding theme so dark and light can be
 * verified side by side. This is the AC's "swatch preview screen renders from the
 * tokens".
 *
 * Self-contained: owns its own [FuseTheme] and the dark/light toggle, so it can be
 * dropped into the app root or a future preview route without extra wiring. It
 * uses no Koin, so it renders in a plain preview/test harness too.
 */
@Composable
fun SwatchScreen(
    modifier: Modifier = Modifier,
    initialDark: Boolean = true,
    diStatus: String? = null,
) {
    var dark by remember { mutableStateOf(initialDark) }
    FuseTheme(darkTheme = dark) {
        SwatchContent(
            dark = dark,
            onToggleTheme = { dark = it },
            diStatus = diStatus,
            modifier = modifier,
        )
    }
}

@Composable
private fun SwatchContent(
    dark: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    diStatus: String?,
    modifier: Modifier = Modifier,
) {
    val c = FuseTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Header + theme toggle.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Fuse", style = FuseTheme.type.displayM.copy(color = c.text))
                Text(
                    "Design tokens — FND-4",
                    style = FuseTheme.type.bodyM.copy(color = c.sub),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (dark) "Dark" else "Light",
                    style = FuseTheme.type.caption.copy(color = c.sub),
                )
                Spacer(Modifier.width(8.dp))
                Switch(checked = dark, onCheckedChange = onToggleTheme)
            }
        }

        SectionTitle("Semantic palette")
        PaletteRow(c)

        SectionTitle("Tile color ramp")
        TileRampRow()

        SectionTitle("Type scale")
        TypeScale()

        SectionTitle("Shape / radius")
        ShapeRow()

        SectionTitle("Spacing (board geometry)")
        SpacingRow()

        if (diStatus != null) {
            Text(
                diStatus,
                style = FuseTheme.type.captionS.copy(color = c.good),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val c = FuseTheme.colors
    Column {
        Text(text, style = FuseTheme.type.headingXL.copy(color = c.text))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun PaletteRow(c: FuseColors) {
    val swatches = listOf(
        "bg" to c.bg,
        "card" to c.card,
        "card2" to c.card2,
        "line" to c.line,
        "text" to c.text,
        "sub" to c.sub,
        "accent" to c.accent,
        "accentSoft" to c.accentSoft,
        "good" to c.good,
        "gold" to c.gold,
        "boardBg" to c.boardBg,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        swatches.forEach { (name, color) -> Swatch(name, color) }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    val c = FuseTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(FuseTheme.shapes.chip)
                .background(color)
                .border(1.dp, c.line, FuseTheme.shapes.chip),
        )
        Spacer(Modifier.height(4.dp))
        Text(name, style = FuseTheme.type.captionS.copy(color = c.sub))
    }
}

@Composable
private fun TileRampRow() {
    val c = FuseTheme.colors
    val entries = TileRamp.entries + (4096 to TileRamp.forValue(4096))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        entries.forEach { (value, tile) ->
            val label = if (value > 2048) ">2048" else value.toString()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(FuseTheme.shapes.tile)
                        .background(tile.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = FuseTheme.type.caption.copy(color = tile.fg, fontWeight = FontWeight.Bold))
                }
                if (tile.glow != null) {
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(width = 24.dp, height = 3.dp).clip(FuseTheme.shapes.pill).background(tile.glow))
                } else {
                    Spacer(Modifier.height(6.dp))
                }
                Text(label, style = FuseTheme.type.captionXS.copy(color = c.sub))
            }
        }
    }
}

@Composable
private fun TypeScale() {
    val c = FuseTheme.colors
    val samples = listOf(
        "displayM 34" to FuseTheme.type.displayM,
        "titleXL 28" to FuseTheme.type.titleXL,
        "titleS 22" to FuseTheme.type.titleS,
        "headingXL 20" to FuseTheme.type.headingXL,
        "headingXS 16" to FuseTheme.type.headingXS,
        "bodyL 15" to FuseTheme.type.bodyL,
        "bodyM 14" to FuseTheme.type.bodyM,
        "caption 12" to FuseTheme.type.caption,
        "captionXS 10" to FuseTheme.type.captionXS,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        samples.forEach { (label, style) ->
            Text("Fuse — $label", style = style.copy(color = c.text))
        }
    }
}

@Composable
private fun ShapeRow() {
    val c = FuseTheme.colors
    val shapes = listOf(
        "card 16" to FuseTheme.shapes.card,
        "tile 12" to FuseTheme.shapes.tile,
        "chip 10" to FuseTheme.shapes.chip,
        "largeCard 18" to FuseTheme.shapes.largeCard,
        "pill" to FuseTheme.shapes.pill,
        "sheet 30/0" to FuseTheme.shapes.bottomSheet,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        shapes.forEach { (name, shape) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(shape)
                        .background(c.card2)
                        .border(1.dp, c.accent, shape),
                )
                Spacer(Modifier.height(4.dp))
                Text(name, style = FuseTheme.type.captionS.copy(color = c.sub))
            }
        }
    }
}

@Composable
private fun SpacingRow() {
    val c = FuseTheme.colors
    val spaces = listOf(
        "pad 12" to FuseTheme.dimens.boardPad,
        "gap 11" to FuseTheme.dimens.gap,
        "cell 72" to FuseTheme.dimens.cell,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        spaces.forEach { (name, dp) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(dp).height(dp).clip(RoundedCornerShape(4.dp)).background(c.accent))
                Spacer(Modifier.height(4.dp))
                Text(name, style = FuseTheme.type.captionS.copy(color = c.sub))
            }
        }
    }
}
