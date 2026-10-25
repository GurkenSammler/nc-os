package dev.david.braviahome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.ui.cyber.Barcode
import dev.david.braviahome.ui.cyber.ChamferShape
import dev.david.braviahome.ui.cyber.Corner
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.KeyHints
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.theme.Cyber

class MenuItem(val label: String, val action: () -> Unit)

/** Optionen nach langem OK-Druck – gestaltet wie die Tooltips im Charakter-Menue. */
@Composable
fun TileMenu(title: String, code: String, items: List<MenuItem>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val sounds = LocalSounds.current
    val panelShape = remember { ChamferShape(16.dp, setOf(Corner.TopEnd, Corner.BottomStart)) }
    val close = {
        sounds?.play(Sfx.MENU_CLOSE)
        onDismiss()
    }

    Dialog(onDismissRequest = close) {
        Column(
            Modifier
                .width(380.dp)
                .background(Cyber.panel, panelShape)
                .border(1.dp, Cyber.red, panelShape),
        ) {
            // Kopfstreifen: rot gefuellt, schwarze Schrift
            Row(
                Modifier.fillMaxWidth().background(Cyber.red).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CyberText("OPTIONS // ${title.uppercase()}", Modifier.weight(1f), size = 15.sp, color = Cyber.ink, weight = FontWeight.Bold)
                Barcode(width = 44.dp, height = 12.dp, color = Cyber.ink)
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { index, item ->
                    MenuRow(
                        index = index,
                        label = item.label,
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = {
                            sounds?.play(Sfx.PRESS)
                            onDismiss()
                            item.action()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MicroLabel(code, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(12.dp))
                    KeyHints("OK" to "Select", "←" to "Close")
                }
            }
        }
        LaunchedEffect(Unit) {
            sounds?.play(Sfx.MENU_OPEN)
            runCatching { first.requestFocus() }
        }
    }
}

@Composable
internal fun MenuRow(index: Int, label: String, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val sounds = LocalSounds.current
    LaunchedEffect(focused) { if (focused) sounds?.play(Sfx.HOVER) }
    val shape = remember { ChamferShape(8.dp) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(42.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Cyber.bg,
            focusedContainerColor = Cyber.cyan,
            pressedContainerColor = Cyber.cyan,
        ),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize().border(1.dp, if (focused) Cyber.cyan else Cyber.redDim, shape))
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(if (focused) Cyber.ink else Cyber.red))
            Spacer(Modifier.width(10.dp))
            CyberText(label.uppercase(), Modifier.weight(1f), size = 16.sp, color = if (focused) Cyber.ink else Cyber.red, weight = FontWeight.Bold)
            MicroLabel("0x%02X".format(0x3F + index * 17), color = if (focused) Cyber.ink.copy(alpha = 0.6f) else Cyber.redDim)
        }
    }
}
