package dev.david.braviahome.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import dev.david.braviahome.data.WeatherLocation
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.theme.Cyber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ort fuers Wetter: Stadt eintippen (Bildschirmtastatur), Suchen, Treffer auswaehlen. */
@Composable
fun WeatherSearchDialog(
    current: WeatherLocation?,
    onSearch: suspend (String) -> List<WeatherLocation>,
    onSelect: (WeatherLocation) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sounds = LocalSounds.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val field = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherLocation>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val search: () -> Unit = {
        if (query.isNotBlank() && !busy) {
            busy = true
            keyboard?.hide()
            sounds?.play(Sfx.PRESS)
            scope.launch {
                results = onSearch(query)
                busy = false
                delay(60)
                runCatching { firstResult.requestFocus() }
            }
        }
    }

    CyberDialog(title = "Weather // Set location", onDismiss = onDismiss, footer = "OPEN-METEO.COM // NO ACCOUNT", initialFocus = field) {
        MicroLabel("CURRENT // ${current?.name ?: "NOT SET"}", color = Cyber.red)
        CyberTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "CITY NAME // OK = KEYBOARD",
            onSubmit = search,
            modifier = Modifier.focusRequester(field),
        )
        MenuRow(index = 0, label = if (busy) "Searching..." else "Search", modifier = Modifier, onClick = search)

        results?.let { list ->
            if (list.isEmpty()) MicroLabel("NO MATCH // TRY ANOTHER NAME", color = Cyber.redAlert)
            list.forEachIndexed { i, loc ->
                MenuRow(
                    index = i + 1,
                    label = "${loc.name} // ${loc.region}",
                    modifier = if (i == 0) Modifier.focusRequester(firstResult) else Modifier,
                    onClick = {
                        sounds?.play(Sfx.PRESS)
                        onSelect(loc)
                        onDismiss()
                    },
                )
            }
        }
        if (current != null) {
            MenuRow(index = 9, label = "Remove location", modifier = Modifier, onClick = {
                sounds?.play(Sfx.PRESS)
                onClear()
                onDismiss()
            })
        }
    }
}
