package dev.david.braviahome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.ui.cyber.ChamferShape
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.theme.Cyber

/**
 * Eine Zeile im SYS.CONFIG-Panel. [onAdjust] macht sie zu einem Regler: ◀ ▶ aendern den Wert
 * (statt den Fokus zu verlassen). [value] zeichnet rechts den Wert, je nach Fokus in Tinte oder Rot.
 */
@Composable
fun ConfigRow(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onAdjust: ((Int) -> Unit)? = null,
    value: @Composable (focused: Boolean) -> Unit = {},
) {
    val (interaction, focused) = rememberTileFocus {}
    val glitch = rememberGlitch(focused)
    val sounds = LocalSounds.current
    val shape = remember { ChamferShape(8.dp) }
    val ink = if (focused) Cyber.ink else if (enabled) Cyber.red else Cyber.redDim

    Surface(
        onClick = {
            when {
                !enabled -> sounds?.play(Sfx.MENU_CLOSE)
                onClick != null -> onClick()
                onAdjust != null -> onAdjust(1)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onPreviewKeyEvent { e ->
                if (onAdjust == null || !enabled) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { if (e.type == KeyEventType.KeyDown) onAdjust(-1); true }
                    Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown) onAdjust(1); true }
                    else -> false
                }
            }
            .glitch(glitch, shape),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Cyber.bg.copy(alpha = 0.6f),
            focusedContainerColor = Cyber.cyan,
            pressedContainerColor = Cyber.cyan,
        ),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize().border(1.dp, if (focused) Cyber.cyan else Cyber.redDim, shape))
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(ink))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                CyberText(label.uppercase(), size = 15.sp, color = ink, weight = FontWeight.Bold)
                if (hint != null) MicroLabel(hint, color = if (focused) Cyber.ink.copy(alpha = 0.65f) else Cyber.redDim)
            }
            value(focused)
        }
    }
}

/** Balken aus Segmenten wie die Ladebalken im Spiel, dazu die Zahl. */
@Composable
fun SegmentBar(level: Int, max: Int, focused: Boolean, segments: Int = 16, suffix: String = "", valueText: String? = null) {
    val filled = if (max <= 0) 0 else Math.round(level.toFloat() / max * segments)
    val on = if (focused) Cyber.ink else Cyber.cyan
    val off = if (focused) Cyber.ink.copy(alpha = 0.25f) else Cyber.redDim.copy(alpha = 0.6f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CyberText("◀", size = 12.sp, color = if (focused) Cyber.ink else Cyber.redDim)
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(segments) { i -> Box(Modifier.size(6.dp, 14.dp).background(if (i < filled) on else off)) }
        }
        Spacer(Modifier.width(6.dp))
        CyberText("▶", size = 12.sp, color = if (focused) Cyber.ink else Cyber.redDim)
        Spacer(Modifier.width(10.dp))
        CyberText(valueText ?: "%3d%s".format(level, suffix), Modifier.width(52.dp), size = 15.sp, color = if (focused) Cyber.ink else Cyber.cyan, weight = FontWeight.Bold)
    }
}

/** [ON] [OFF] wie Schalter im Spielmenue. */
@Composable
fun ToggleValue(on: Boolean, focused: Boolean, onText: String = "ON", offText: String = "OFF") {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ToggleBox(onText, on, focused)
        ToggleBox(offText, !on, focused)
    }
}

@Composable
private fun ToggleBox(text: String, active: Boolean, focused: Boolean) {
    val color = if (focused) Cyber.ink else if (active) Cyber.cyan else Cyber.redDim
    Box(
        Modifier
            .width(48.dp)
            .height(24.dp)
            .then(if (active) Modifier.background(color.copy(alpha = if (focused) 1f else 0.18f)) else Modifier)
            .border(1.dp, color),
        contentAlignment = Alignment.Center,
    ) {
        CyberText(text, size = 12.sp, color = if (active && focused) Cyber.cyan else color, weight = FontWeight.Bold)
    }
}

/** "◀ NORMAL ▶" fuer Auswahlwerte. */
@Composable
fun ChoiceValue(text: String, focused: Boolean, color: Color = Cyber.cyan) {
    val c = if (focused) Cyber.ink else color
    Row(verticalAlignment = Alignment.CenterVertically) {
        CyberText("◀", size = 12.sp, color = if (focused) Cyber.ink else Cyber.redDim)
        CyberText(text.uppercase(), Modifier.width(120.dp).padding(horizontal = 8.dp), size = 15.sp, color = c, weight = FontWeight.Bold)
        CyberText("▶", size = 12.sp, color = if (focused) Cyber.ink else Cyber.redDim)
    }
}

@Composable
fun TextValue(text: String, focused: Boolean, color: Color = Cyber.cyan) {
    CyberText(text.uppercase(), size = 14.sp, color = if (focused) Cyber.ink else color, weight = FontWeight.Bold)
}

/** Kachel, die in eine Seite der echten Sony-/Android-Einstellungen springt. */
@Composable
fun LinkChip(title: String, source: String, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (interaction, focused) = rememberTileFocus {}
    val glitch = rememberGlitch(focused)
    val shape = remember { ChamferShape(8.dp) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp).glitch(glitch, shape),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Cyber.bg.copy(alpha = 0.6f),
            focusedContainerColor = Cyber.cyan,
            pressedContainerColor = Cyber.cyan,
        ),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize().border(1.dp, if (focused) Cyber.cyan else Cyber.redDim, shape))
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
            CyberText(title.uppercase(), size = 15.sp, color = if (focused) Cyber.ink else Cyber.red, weight = FontWeight.Bold)
            MicroLabel("%02X // %s".format(0x10 + index, source), color = if (focused) Cyber.ink.copy(alpha = 0.65f) else Cyber.redDim)
        }
    }
}
