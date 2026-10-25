package dev.david.braviahome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.ui.cyber.Barcode
import dev.david.braviahome.ui.cyber.ChamferShape
import dev.david.braviahome.ui.cyber.Corner
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.KeyHints
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.theme.Cyber
import dev.david.braviahome.ui.theme.cyberStyle

/** Dialog-Rahmen wie die Tooltips im Spiel: roter Kopfstreifen, Inhalt, Tasten-Hinweise. */
@Composable
fun CyberDialog(
    title: String,
    onDismiss: () -> Unit,
    width: Dp = 480.dp,
    footer: String = "",
    initialFocus: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sounds = LocalSounds.current
    val shape = remember { ChamferShape(16.dp, setOf(Corner.TopEnd, Corner.BottomStart)) }
    Dialog(onDismissRequest = {
        sounds?.play(Sfx.MENU_CLOSE)
        onDismiss()
    }) {
        Column(
            Modifier
                .width(width)
                .background(Cyber.panel, shape)
                .border(1.dp, Cyber.red, shape),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Cyber.red).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CyberText(title.uppercase(), Modifier.weight(1f), size = 15.sp, color = Cyber.ink, weight = FontWeight.Bold)
                Barcode(width = 44.dp, height = 12.dp, color = Cyber.ink)
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                Spacer(Modifier.height(2.dp))
                Row {
                    MicroLabel(footer, Modifier.weight(1f))
                    KeyHints("OK" to "Select", "←" to "Close")
                }
            }
        }
        // Im Dialog-Fenster selbst, sonst ist das Ziel beim Fokussieren noch nicht da.
        LaunchedEffect(Unit) {
            sounds?.play(Sfx.MENU_OPEN)
            initialFocus?.let { runCatching { it.requestFocus() } }
        }
    }
}

/** Einzeiliges Textfeld; OK oeffnet die Bildschirmtastatur, "Fertig" ruft [onSubmit]. */
@Composable
fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = remember { ChamferShape(8.dp) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = cyberStyle(18.sp, Cyber.cyan),
        cursorBrush = SolidColor(Cyber.cyan),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }, onSearch = { onSubmit() }),
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            // Textfelder halten auf dem TV die Pfeiltasten fest: ▲/▼ verlassen das Feld.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> false
                }
            }
            .border(if (focused) 2.dp else 1.dp, if (focused) Cyber.cyan else Cyber.redDim, shape),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) CyberText(placeholder, size = 15.sp, color = Cyber.redDim)
                inner()
            }
        },
    )
}

/** Text eingeben (Titel, Eingang umbenennen). Leer lassen + SAVE = auf Standard zuruecksetzen. */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
    val sounds = LocalSounds.current
    val keyboard = LocalSoftwareKeyboardController.current
    val field = remember { FocusRequester() }
    var text by remember { mutableStateOf(initial) }
    val save = {
        keyboard?.hide()
        sounds?.play(Sfx.PRESS)
        onConfirm(text.trim())
        onDismiss()
    }
    CyberDialog(title = title, onDismiss = onDismiss, footer = "MAX 24 CHARS", initialFocus = field) {
        CyberTextField(
            value = text,
            onValueChange = { text = it.take(24) },
            placeholder = placeholder,
            onSubmit = save,
            modifier = Modifier.focusRequester(field),
        )
        MenuRow(index = 0, label = "Save", modifier = Modifier, onClick = save)
        if (onReset != null && resetLabel != null) {
            MenuRow(index = 1, label = resetLabel, modifier = Modifier, onClick = {
                sounds?.play(Sfx.PRESS)
                onReset()
                onDismiss()
            })
        }
    }
}
