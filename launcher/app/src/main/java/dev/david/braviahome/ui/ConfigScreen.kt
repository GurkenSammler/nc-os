package dev.david.braviahome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import dev.david.braviahome.HomeViewModel
import dev.david.braviahome.data.LauncherSettings
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.ui.cyber.Barcode
import dev.david.braviahome.ui.cyber.ChamferShape
import dev.david.braviahome.ui.cyber.Corner
import dev.david.braviahome.ui.cyber.CyberBackground
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.HeaderLine
import dev.david.braviahome.ui.cyber.KeyHints
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.cyber.Scanlines
import dev.david.braviahome.ui.theme.Cyber
import kotlinx.coroutines.delay

enum class ConfigModule(val tab: String, val title: String, val subtitle: String) {
    CONTROL("CONTROL", "Quick Control", "VOLUME // POWER"),
    PICTURE("PICTURE", "Picture", "MODE // BRIGHTNESS // COLOR"),
    SOUND("SOUND", "Sound", "VOLUME // OUTPUT // SPEAKER"),
    NETWORK("NETWORK", "Network", "WLAN // SIGNAL // SCAN"),
    BLUETOOTH("BLUETOOTH", "Bluetooth", "REMOTES // AUDIO // PADS"),
    APPS("APPS", "Apps & Storage", "SIZE // CACHE // FREE"),
    LOOK("LOOK", "Look", "THEME // TITLE // FX"),
    LAYOUT("LAYOUT", "Layout", "TILES // CLOCK // DATE"),
    INPUTS("INPUTS", "Inputs & Start", "RENAME // HIDE // POWER-ON"),
    SAVER("SCREENSAVER", "Screensaver", "STYLE // DELAY // PREVIEW"),
    LAUNCHER("LAUNCHER", "Launcher", "SOUNDS // BOOT // WEATHER // BACKUP"),
    QUICK("QUICK MENU", "Quick Menu", "KEY // CONTENT"),
    SYSTEM("TV SYSTEM", "TV System", "DATE // LANGUAGE // ACCESSIBILITY"),
    STATUS("STATUS", "Status", "NETWORK // STORAGE // UPTIME"),
}

/** Offene Dialoge in SYS.CONFIG (Texteingaben brauchen ein eigenes Fenster). */
sealed interface ConfigDialog {
    data object Weather : ConfigDialog
    data object Title : ConfigDialog
    data class RenameInput(val id: String, val current: String, val original: String) : ConfigDialog
}

/**
 * SYS.CONFIG: links die Module (wie die PC-Oberflaechen im Spiel), rechts das Panel.
 * Fokus auf einem Modul schaltet das Panel um, ▶ / OK springt hinein.
 */
@Composable
fun ConfigScreen(viewModel: HomeViewModel, onBack: () -> Unit) {
    val system by viewModel.system.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val sounds = LocalSounds.current

    var module by remember { mutableStateOf(ConfigModule.CONTROL) }
    var dialog by remember { mutableStateOf<ConfigDialog?>(null) }
    var panelFocused by remember { mutableStateOf(false) }
    val moduleFocus = remember { ConfigModule.entries.associateWith { FocusRequester() } }
    val panelFirst = remember { FocusRequester() }

    // Zurueck: aus dem Panel erst in die Modulliste, von dort zum Startbildschirm.
    // (◀ veraendert in vielen Zeilen den Wert und taugt deshalb nicht zum Zuruecknavigieren.)
    BackHandler {
        if (panelFocused) {
            sounds?.play(Sfx.HOVER)
            runCatching { moduleFocus.getValue(module).requestFocus() }
        } else {
            sounds?.play(Sfx.MENU_CLOSE)
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        delay(40)
        runCatching { moduleFocus.getValue(ConfigModule.CONTROL).requestFocus() }
        // Lautstaerke kann sich ueber die Fernbedienung aendern, solange wir offen sind.
        while (true) {
            viewModel.refreshTv()
            delay(1_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        CyberBackground()
        Column(Modifier.fillMaxSize().padding(horizontal = Cyber.padH, vertical = Cyber.padV)) {
            TopBar(
                title = "//SYS.CONFIG",
                tabs = listOf("%02d/%02d  %s".format(module.ordinal + 1, ConfigModule.entries.size, module.tab)),
                activeTab = 0,
                system = system,
                weather = weather,
            )
            HeaderLine(Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))

            Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConfigModule.entries.forEachIndexed { index, m ->
                        ModuleItem(
                            index = index,
                            module = m,
                            selected = module == m,
                            onFocused = { module = m },
                            onClick = { runCatching { panelFirst.requestFocus() } },
                            modifier = Modifier.focusRequester(moduleFocus.getValue(m)),
                        )
                    }
                }
                Spacer(Modifier.width(24.dp))
                Panel(
                    module,
                    Modifier.weight(1f).fillMaxHeight().onFocusChanged { panelFocused = it.hasFocus },
                ) {
                    when (module) {
                        ConfigModule.CONTROL -> ControlPanel(viewModel, panelFirst)
                        ConfigModule.PICTURE -> PicturePanel(viewModel, panelFirst)
                        ConfigModule.SOUND -> SoundPanel(viewModel, panelFirst)
                        ConfigModule.NETWORK -> NetworkPanel(viewModel, panelFirst)
                        ConfigModule.BLUETOOTH -> BluetoothPanel(viewModel, panelFirst)
                        ConfigModule.APPS -> AppsPanel(viewModel, panelFirst)
                        ConfigModule.LOOK -> LookPanel(viewModel, panelFirst, onDialog = { dialog = it })
                        ConfigModule.LAYOUT -> LayoutPanel(viewModel, panelFirst)
                        ConfigModule.INPUTS -> InputsPanel(viewModel, panelFirst, onDialog = { dialog = it })
                        ConfigModule.SAVER -> SaverPanel(viewModel, panelFirst)
                        ConfigModule.LAUNCHER -> LauncherPanel(viewModel, panelFirst, onDialog = { dialog = it })
                        ConfigModule.QUICK -> QuickMenuPanel(viewModel, panelFirst)
                        ConfigModule.SYSTEM -> SystemPanel(viewModel, panelFirst)
                        ConfigModule.STATUS -> StatusPanel(system, weather, panelFirst)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("SYS.CONFIG // CHANGES APPLY INSTANTLY // NC-OS ${system.launcherVersion}")
                Spacer(Modifier.weight(1f))
                KeyHints("◀▶" to "Adjust", "OK" to "Select", "←" to if (panelFocused) "Modules" else "Home")
            }
        }
        Scanlines()

        when (val d = dialog) {
            null -> Unit
            ConfigDialog.Weather -> {
                val current by viewModel.weatherLocation.collectAsStateWithLifecycle()
                WeatherSearchDialog(
                    current = current,
                    onSearch = { viewModel.searchWeather(it) },
                    onSelect = { viewModel.setWeatherLocation(it) },
                    onClear = { viewModel.setWeatherLocation(null) },
                    onDismiss = { dialog = null },
                )
            }
            ConfigDialog.Title -> TextInputDialog(
                title = "Look // Title",
                initial = dev.david.braviahome.data.Live.settings.title,
                placeholder = LauncherSettings.DEFAULT_TITLE,
                onConfirm = { t -> viewModel.updateSettings { it.copy(title = t.ifBlank { LauncherSettings.DEFAULT_TITLE }) } },
                onDismiss = { dialog = null },
                resetLabel = "Reset to ${LauncherSettings.DEFAULT_TITLE}",
                onReset = { viewModel.updateSettings { it.copy(title = LauncherSettings.DEFAULT_TITLE) } },
            )
            is ConfigDialog.RenameInput -> TextInputDialog(
                title = "Inputs // Rename ${d.original}",
                initial = d.current,
                placeholder = d.original.uppercase(),
                onConfirm = { name -> viewModel.updateSettings { s -> s.copy(inputNames = s.inputNames.withName(d.id, name)) } },
                onDismiss = { dialog = null },
                resetLabel = "Reset to ${d.original}",
                onReset = { viewModel.updateSettings { s -> s.copy(inputNames = s.inputNames - d.id) } },
            )
        }
    }
}

private fun Map<String, String>.withName(id: String, name: String) = if (name.isBlank()) this - id else this + (id to name)

@Composable
private fun ModuleItem(
    index: Int,
    module: ConfigModule,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val (interaction, focused) = rememberTileFocus { if (it) onFocused() }
    val glitch = rememberGlitch(focused)
    val shape = remember { ChamferShape(12.dp) }
    val accent = when {
        focused -> Cyber.ink
        selected -> Cyber.cyan
        else -> Cyber.red
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            // ▶ springt immer auf den ersten Eintrag im Panel, nicht auf den, der zufaellig daneben liegt.
            .onPreviewKeyEvent { e ->
                if (e.key != Key.DirectionRight) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) onClick()
                true
            }
            .glitch(glitch, shape),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Cyber.panel,
            focusedContainerColor = Cyber.cyan,
            pressedContainerColor = Cyber.cyan,
        ),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize().border(1.dp, if (focused || selected) Cyber.cyan else Cyber.redDim, shape))
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).border(1.dp, accent),
                contentAlignment = Alignment.Center,
            ) {
                CyberText("%02d".format(index + 1), size = 14.sp, color = accent, weight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                CyberText(module.title.uppercase(), size = 16.sp, color = accent, weight = FontWeight.Bold)
                MicroLabel(module.subtitle, color = if (focused) Cyber.ink.copy(alpha = 0.65f) else Cyber.redDim, size = 8.sp)
            }
        }
    }
}

/** Rechter Rahmen mit Kopfzeile; Inhalt scrollt, wenn er laenger ist. */
@Composable
private fun Panel(module: ConfigModule, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = remember { ChamferShape(16.dp, setOf(Corner.TopEnd, Corner.BottomStart)) }
    Column(modifier.background(Cyber.panel.copy(alpha = 0.75f), shape).border(1.dp, Cyber.redDim, shape)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(Cyber.cyan))
            Spacer(Modifier.width(8.dp))
            CyberText("[%02d] %s".format(module.ordinal + 1, module.title.uppercase()), size = 15.sp, color = Cyber.cyan, weight = FontWeight.Bold, spacing = 0.14f)
            Spacer(Modifier.weight(1f))
            MicroLabel(module.subtitle)
            Spacer(Modifier.width(10.dp))
            Barcode(width = 44.dp, height = 10.dp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Cyber.redDim))
        // key(module): jedes Modul bekommt einen eigenen Scroll-Zustand (startet oben).
        androidx.compose.runtime.key(module) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}
