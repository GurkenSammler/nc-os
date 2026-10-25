package dev.david.braviahome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.david.braviahome.Actions
import dev.david.braviahome.HomeViewModel
import dev.david.braviahome.data.BackgroundStyle
import dev.david.braviahome.data.DateStyle
import dev.david.braviahome.data.GlitchLevel
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.QuickItem
import dev.david.braviahome.data.SaverStyle
import dev.david.braviahome.data.ScanlineLevel
import dev.david.braviahome.data.SystemInfo
import dev.david.braviahome.data.ThemeName
import dev.david.braviahome.data.TileSize
import dev.david.braviahome.data.Weather
import dev.david.braviahome.data.displayInputs
import dev.david.braviahome.dream.LoopClips
import dev.david.braviahome.quick.QuickPanelService
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.system.SettingsLink
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.theme.Cyber
import dev.david.braviahome.ui.theme.Palettes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private val SAVER_STEPS = listOf(5, 10, 15, 30, 60)
private val SHUFFLE_STEPS = listOf(1, 2, 5, 10, 15)

/** Naechster/vorheriger Wert einer Auswahl, im Kreis. */
private inline fun <reified E : Enum<E>> E.step(delta: Int): E {
    val all = enumValues<E>()
    return all[(ordinal + delta + all.size) % all.size]
}

/** Naechster Wert aus einer Stufenliste, am Rand stehen bleibend. */
private fun List<Int>.step(current: Int, delta: Int): Int {
    val i = (indexOfFirst { it >= current }.takeIf { it >= 0 } ?: lastIndex) + delta
    return this[i.coerceIn(0, lastIndex)]
}

// ---------------------------------------------------------------- 01 QUICK CONTROL

@Composable
fun ControlPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val sounds = LocalSounds.current
    val canStandby = remember { viewModel.tv.canStandby() }

    ConfigRow(
        label = "Volume",
        hint = "TV SPEAKER // STREAM MUSIC",
        modifier = Modifier.focusRequester(first),
        onAdjust = { viewModel.changeVolume(it > 0); sounds?.play(Sfx.MOVE) },
    ) { SegmentBar(volume.level, volume.max, it) }
    ConfigRow(
        label = "Mute",
        hint = "AUDIO OUTPUT",
        onClick = { viewModel.toggleMute(); sounds?.play(Sfx.PRESS) },
    ) { ToggleValue(volume.muted, it) }
    ConfigRow(
        label = "Start Screensaver",
        hint = "${Live.settings.saverStyle.label()} // NOW",
        onClick = { sounds?.play(Sfx.PRESS); viewModel.tv.startScreensaver() },
    ) { TextValue("EXECUTE", it) }
    // Standby nur nach zweitem OK innerhalb von 3 s: ein versehentlicher Druck schaltet nicht aus.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    ConfigRow(
        label = "Standby",
        hint = when {
            !canStandby -> "LOCKED // RUN install-launcher.ps1"
            armed -> "CONFIRM // PRESS OK AGAIN"
            else -> "POWER DOWN DISPLAY"
        },
        enabled = canStandby,
        onClick = {
            if (armed) {
                armed = false
                sounds?.play(Sfx.MENU_CLOSE)
                viewModel.tv.standby()
            } else {
                armed = true
                sounds?.play(Sfx.MENU_OPEN)
            }
        },
    ) { TextValue(if (armed) "CONFIRM?" else "POWER OFF", it, Cyber.redAlert) }
}

// ---------------------------------------------------------------- 02 LOOK

@Composable
fun LookPanel(viewModel: HomeViewModel, first: FocusRequester, onDialog: (ConfigDialog) -> Unit) {
    val s = Live.settings
    val sounds = LocalSounds.current
    fun change(transform: (dev.david.braviahome.data.LauncherSettings) -> dev.david.braviahome.data.LauncherSettings) {
        viewModel.updateSettings(transform)
        sounds?.play(Sfx.MOVE)
    }

    ConfigRow(
        label = "Theme",
        hint = "COLOR SCHEME // LIVE PREVIEW",
        modifier = Modifier.focusRequester(first),
        onAdjust = { d -> change { it.copy(theme = it.theme.step(d)) } },
    ) { ThemeValue(s.theme, it) }
    ConfigRow(
        label = "Title",
        hint = "TOP LEFT ON HOME // OK = EDIT",
        onClick = { sounds?.play(Sfx.MENU_OPEN); onDialog(ConfigDialog.Title) },
    ) { TextValue(s.title, it) }
    ConfigRow(
        label = "Scanlines",
        hint = "CRT LINES + ROLLING BAND",
        onAdjust = { d -> change { it.copy(scanlines = it.scanlines.step(d)) } },
    ) { ChoiceValue(s.scanlines.name, it) }
    ConfigRow(
        label = "Background",
        hint = "DOT GRID // CIRCUIT TRACES",
        onAdjust = { d -> change { it.copy(background = it.background.step(d)) } },
    ) { ChoiceValue(s.background.label(), it) }
    ConfigRow(
        label = "Logo Tint",
        hint = "UNFOCUSED LOGOS IN THEME COLOR",
        onClick = { change { it.copy(logoTint = !it.logoTint) } },
    ) { ToggleValue(s.logoTint, it) }
    ConfigRow(
        label = "Glitch",
        hint = "EFFECT ON FOCUS",
        onAdjust = { d -> change { it.copy(glitch = it.glitch.step(d)) } },
    ) { ChoiceValue(s.glitch.name, it) }
}

private fun BackgroundStyle.label() = when (this) {
    BackgroundStyle.CIRCUIT -> "GRID+CIRCUIT"
    BackgroundStyle.GRID -> "GRID"
    BackgroundStyle.PLAIN -> "PLAIN"
}

/** Name des Schemas plus drei Farbfelder. */
@Composable
private fun ThemeValue(theme: ThemeName, focused: Boolean) {
    val p = Palettes.of(theme)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CyberText("◀", size = 12.sp, color = if (focused) Cyber.ink else Cyber.redDim)
        Spacer(Modifier.width(8.dp))
        listOf(p.primary, p.accent, p.special).forEach { c ->
            Box(Modifier.size(14.dp).background(c).border(1.dp, if (focused) Cyber.ink else Color.Transparent))
            Spacer(Modifier.width(4.dp))
        }
        CyberText(theme.name, Modifier.width(80.dp).padding(start = 6.dp), size = 15.sp, color = if (focused) Cyber.ink else Cyber.cyan, weight = FontWeight.Bold)
        CyberText("▶", size = 12.sp, color = if (focused) Cyber.ink else Cyber.redDim)
    }
}

// ---------------------------------------------------------------- 03 LAYOUT

@Composable
fun LayoutPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val s = Live.settings
    val sounds = LocalSounds.current
    fun change(transform: (dev.david.braviahome.data.LauncherSettings) -> dev.david.braviahome.data.LauncherSettings) {
        viewModel.updateSettings(transform)
        sounds?.play(Sfx.MOVE)
    }
    val now = rememberNow()

    ConfigRow(
        label = "Tile Size",
        hint = "APP TILES ON HOME",
        modifier = Modifier.focusRequester(first),
        onAdjust = { d -> change { it.copy(tileSize = it.tileSize.step(d)) } },
    ) {
        ChoiceValue(
            when (s.tileSize) {
                TileSize.S -> "SMALL"
                TileSize.M -> "NORMAL"
                TileSize.L -> "LARGE"
            },
            it,
        )
    }
    ConfigRow(
        label = "Tile Labels",
        hint = "APP_01 // NAME + STATUS LINE",
        onClick = { change { it.copy(tileLabels = !it.tileLabels) } },
    ) { ToggleValue(s.tileLabels, it) }
    ConfigRow(
        label = "Tech Data",
        hint = "VERSION // IP // UPTIME BOTTOM LEFT",
        onClick = { change { it.copy(footer = !it.footer) } },
    ) { ToggleValue(s.footer, it) }
    ConfigRow(
        label = "Clock",
        hint = "NOW ${Formats.time(now)} ${Formats.suffix(now)}".trim(),
        onClick = { change { it.copy(clock24 = !it.clock24) } },
    ) { ToggleValue(s.clock24, it, onText = "24H", offText = "12H") }
    ConfigRow(
        label = "Date Format",
        hint = "NOW ${Formats.date(now)}",
        onAdjust = { d -> change { it.copy(dateStyle = it.dateStyle.step(d)) } },
    ) {
        ChoiceValue(
            when (s.dateStyle) {
                DateStyle.EN -> "ENGLISH"
                DateStyle.DE -> "DEUTSCH"
                DateStyle.ISO -> "ISO"
            },
            it,
        )
    }
}

// ---------------------------------------------------------------- 04 INPUTS & START

@Composable
fun InputsPanel(viewModel: HomeViewModel, first: FocusRequester, onDialog: (ConfigDialog) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = Live.settings
    val sounds = LocalSounds.current
    val inputs = displayInputs(state.inputs, s, includeHidden = true)

    // Start-Ziel: Startbildschirm, jeder Eingang, jede App.
    val targets = buildList {
        add("" to "HOME SCREEN")
        displayInputs(state.inputs, s).forEach { add(Actions.TARGET_INPUT + it.id to it.label) }
        (state.apps + state.hidden).forEach { add(Actions.TARGET_APP + it.packageName to it.label) }
    }
    val targetIndex = targets.indexOfFirst { it.first == s.startTarget }.coerceAtLeast(0)

    ConfigRow(
        label = "Power-On Target",
        hint = "AFTER BOOT SEQUENCE // HOME KEY UNCHANGED",
        modifier = Modifier.focusRequester(first),
        onAdjust = { d ->
            val next = targets[(targetIndex + d + targets.size) % targets.size].first
            viewModel.updateSettings { it.copy(startTarget = next) }
            sounds?.play(Sfx.MOVE)
        },
    ) { ChoiceValue(targets[targetIndex].second, it, if (targetIndex == 0) Cyber.redDim else Cyber.gold) }

    SectionLabel("HDMI // OK = RENAME // ◀▶ = SHOW/HIDE")
    inputs.forEach { input ->
        val original = state.inputs.firstOrNull { it.id == input.id }?.label ?: input.label
        val shown = input.id !in s.hiddenInputs
        ConfigRow(
            label = input.label,
            hint = if (input.label != original) "ORIGINAL // $original" else "INPUT // ${input.id.substringAfterLast('/')}",
            onClick = {
                sounds?.play(Sfx.MENU_OPEN)
                onDialog(ConfigDialog.RenameInput(input.id, s.inputNames[input.id].orEmpty(), original))
            },
            onAdjust = {
                viewModel.updateSettings { st ->
                    st.copy(hiddenInputs = if (input.id in st.hiddenInputs) st.hiddenInputs - input.id else st.hiddenInputs + input.id)
                }
                sounds?.play(Sfx.MOVE)
            },
        ) { ToggleValue(shown, it, onText = "SHOW", offText = "HIDE") }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(Cyber.cyan))
        Spacer(Modifier.width(8.dp))
        CyberText(text, size = 11.sp, color = Cyber.cyan, weight = FontWeight.Bold, spacing = 0.14f)
    }
}

// ---------------------------------------------------------------- 05 SCREENSAVER

fun SaverStyle.label() = when (this) {
    SaverStyle.WORLD_CLOCK -> "WORLD CLOCK"
    SaverStyle.CODE_RAIN -> "CODE RAIN"
    SaverStyle.NIGHT_CITY -> "NIGHT CITY"
    SaverStyle.BREACH_GRID -> "BREACH GRID"
    SaverStyle.VIDEO_LOOP -> "VIDEO LOOP"
    SaverStyle.SHUFFLE -> "SHUFFLE"
}

@Composable
fun SaverPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val s = Live.settings
    val saver by viewModel.saverMinutes.collectAsStateWithLifecycle()
    val sounds = LocalSounds.current
    val canSaver = remember { viewModel.tv.canChangeScreensaver() }
    val context = LocalContext.current
    // "" = alle Clips im Wechsel, dann jeder Clip einzeln
    val clips = remember { listOf("") + LoopClips.list(context).map { it.name } }
    val clipIndex = clips.indexOf(s.saverClip).coerceAtLeast(0)
    val usesClips = s.saverStyle == SaverStyle.VIDEO_LOOP || s.saverStyle == SaverStyle.SHUFFLE
    val rotating = s.saverStyle == SaverStyle.SHUFFLE || (s.saverStyle == SaverStyle.VIDEO_LOOP && clipIndex == 0)

    ConfigRow(
        label = "Style",
        hint = "WHAT RUNS WHEN IDLE",
        modifier = Modifier.focusRequester(first),
        onAdjust = { d -> viewModel.updateSettings { it.copy(saverStyle = it.saverStyle.step(d)) }; sounds?.play(Sfx.MOVE) },
    ) { ChoiceValue(s.saverStyle.label(), it) }
    ConfigRow(
        label = "Clip",
        hint = when {
            clips.size == 1 -> "NO CLIPS // RUN push-loops.ps1"
            s.saverStyle != SaverStyle.VIDEO_LOOP -> "ONLY FOR STYLE VIDEO LOOP"
            clipIndex == 0 -> "${clips.size - 1} CLIPS // ROTATE WITH GLITCH"
            else -> LoopClips.label(clips[clipIndex]).take(44)
        },
        enabled = s.saverStyle == SaverStyle.VIDEO_LOOP && clips.size > 1,
        onAdjust = { d ->
            viewModel.updateSettings { it.copy(saverClip = clips[(clipIndex + d + clips.size) % clips.size]) }
            sounds?.play(Sfx.MOVE)
        },
    ) { ChoiceValue(if (clipIndex == 0) "ALL" else "%02d / %02d".format(clipIndex, clips.size - 1), it) }
    ConfigRow(
        label = "Change Every",
        hint = when {
            s.saverStyle == SaverStyle.SHUFFLE -> "SWITCH STYLE WITH GLITCH"
            rotating -> "SWITCH CLIP WITH GLITCH"
            else -> "ONLY FOR SHUFFLE // VIDEO LOOP ALL"
        },
        enabled = rotating,
        onAdjust = { d -> viewModel.updateSettings { it.copy(shuffleMinutes = SHUFFLE_STEPS.step(it.shuffleMinutes, d)) }; sounds?.play(Sfx.MOVE) },
    ) { ChoiceValue("${s.shuffleMinutes} MIN", it) }
    ConfigRow(
        label = "Clip Sound",
        hint = if (usesClips) "AUDIO TRACK OF THE VIDEO" else "ONLY FOR VIDEO LOOP // SHUFFLE",
        enabled = usesClips,
        onClick = { viewModel.updateSettings { it.copy(saverClipSound = !it.saverClipSound) }; sounds?.play(Sfx.PRESS) },
    ) { ToggleValue(s.saverClipSound, it) }
    ConfigRow(
        label = "Clock Overlay",
        hint = "SMALL CLOCK ON ALL STYLES BUT WORLD CLOCK",
        onClick = { viewModel.updateSettings { it.copy(saverClock = !it.saverClock) }; sounds?.play(Sfx.PRESS) },
    ) { ToggleValue(s.saverClock, it) }
    ConfigRow(
        label = "Delay",
        hint = if (canSaver) "IDLE TIME UNTIL SCREENSAVER" else "LOCKED // RUN install-launcher.ps1",
        enabled = canSaver,
        onAdjust = { d -> viewModel.setScreensaverMinutes(SAVER_STEPS.step(saver, d)); sounds?.play(Sfx.MOVE) },
    ) { ChoiceValue("$saver MIN", it) }
    ConfigRow(
        label = "Preview",
        hint = "START NOW // ANY KEY ENDS",
        onClick = { sounds?.play(Sfx.PRESS); viewModel.tv.startScreensaver() },
    ) { TextValue("EXECUTE", it) }
}

// ---------------------------------------------------------------- 06 LAUNCHER

@Composable
fun LauncherPanel(viewModel: HomeViewModel, first: FocusRequester, onDialog: (ConfigDialog) -> Unit) {
    val s = Live.settings
    val location by viewModel.weatherLocation.collectAsStateWithLifecycle()
    val sounds = LocalSounds.current
    val scope = rememberCoroutineScope()
    var resetDone by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<Boolean?>(null) }
    var importArmed by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(importArmed) {
        if (importArmed) {
            delay(3_000)
            importArmed = false
        }
    }

    ConfigRow(
        label = "UI Sounds",
        hint = "HOVER // OPEN // MENU // BOOT",
        modifier = Modifier.focusRequester(first),
        onClick = {
            viewModel.updateSettings { it.copy(soundsOn = !it.soundsOn) }
            sounds?.play(Sfx.PRESS)
        },
    ) { ToggleValue(s.soundsOn, it) }
    ConfigRow(
        label = "Sound Volume",
        hint = "LAUNCHER EFFECTS ONLY",
        enabled = s.soundsOn,
        onAdjust = { d ->
            viewModel.updateSettings { it.copy(soundVolume = (it.soundVolume + d).coerceIn(0, 10)) }
            sounds?.play(Sfx.MOVE)
        },
    ) { SegmentBar(s.soundVolume * 10, 100, it, segments = 10, suffix = "%") }
    ConfigRow(
        label = "Boot Sequence",
        hint = "ON POWER UP",
        onClick = {
            viewModel.updateSettings { it.copy(bootSequence = !it.bootSequence) }
            sounds?.play(Sfx.PRESS)
        },
    ) { ToggleValue(s.bootSequence, it) }
    ConfigRow(
        label = "Play Boot Sequence",
        hint = "PREVIEW",
        onClick = {
            sounds?.play(Sfx.PRESS)
            viewModel.requestBoot()
            viewModel.onHomePressed()
        },
    ) { TextValue("EXECUTE", it) }
    ConfigRow(
        label = "Weather",
        hint = "OPEN-METEO // LOCATION",
        onClick = { sounds?.play(Sfx.MENU_OPEN); onDialog(ConfigDialog.Weather) },
    ) { TextValue(location?.name ?: "NOT SET", it, if (location == null) Cyber.redDim else Cyber.cyan) }
    ConfigRow(
        label = "Reset App Order",
        hint = "BACK TO A-Z",
        onClick = {
            viewModel.resetOrder()
            resetDone = true
            sounds?.play(Sfx.PRESS)
        },
    ) { TextValue(if (resetDone) "DONE" else "EXECUTE", it, if (resetDone) Cyber.green else Cyber.cyan) }

    SectionLabel("BACKUP // ALL LAUNCHER SETTINGS + APP ORDER")
    ConfigRow(
        label = "Export Backup",
        hint = "nc-os-backup.json // scripts\\backup-launcher.ps1 -Pull",
        onClick = {
            sounds?.play(Sfx.PRESS)
            scope.launch { exportResult = viewModel.exportBackup() != null }
        },
    ) {
        when (exportResult) {
            true -> TextValue("SAVED", it, Cyber.green)
            false -> TextValue("FAILED", it, Cyber.redAlert)
            null -> TextValue("EXECUTE", it)
        }
    }
    ConfigRow(
        label = "Import Backup",
        hint = if (importArmed) "CONFIRM // OVERWRITES CURRENT SETTINGS" else "READS nc-os-backup.json",
        onClick = {
            if (importArmed) {
                importArmed = false
                sounds?.play(Sfx.PRESS)
                scope.launch { importResult = viewModel.importBackup() }
            } else {
                importArmed = true
                sounds?.play(Sfx.MENU_OPEN)
            }
        },
    ) {
        when {
            importArmed -> TextValue("CONFIRM?", it, Cyber.redAlert)
            importResult == true -> TextValue("RESTORED", it, Cyber.green)
            importResult == false -> TextValue("NO FILE", it, Cyber.redAlert)
            else -> TextValue("EXECUTE", it)
        }
    }
}

// ---------------------------------------------------------------- 07 QUICK MENU

private fun QuickItem.label() = when (this) {
    QuickItem.PICTURE_MODE -> "Picture Mode"
    QuickItem.BACKLIGHT -> "Brightness"
    QuickItem.PICTURE_OFF -> "Picture Off"
    QuickItem.VOLUME -> "Volume"
    QuickItem.MUTE -> "Mute"
    QuickItem.SLEEP -> "Sleep Timer"
    QuickItem.SAVER -> "Screensaver"
    QuickItem.STANDBY -> "Standby"
    QuickItem.PICTURE_SOUND -> "Picture + Sound"
    QuickItem.CONFIG -> "SYS.CONFIG Link"
    QuickItem.INPUTS -> "Inputs (HDMI)"
}

@Composable
fun QuickMenuPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val s = Live.settings
    val sounds = LocalSounds.current
    val service = QuickPanelService.instance
    var learning by remember { mutableStateOf(false) }
    LaunchedEffect(learning) {
        if (learning) {
            delay(10_000)
            service?.cancelLearn()
            learning = false
        }
    }

    ConfigRow(
        label = "Quick Menu",
        hint = if (service == null) "SERVICE OFF // RUN install-launcher.ps1" else "OFF = TV'S OWN QUICK SETTINGS",
        modifier = Modifier.focusRequester(first),
        onClick = { viewModel.updateSettings { it.copy(quickEnabled = !it.quickEnabled) }; sounds?.play(Sfx.PRESS) },
    ) { ToggleValue(s.quickEnabled, it) }
    ConfigRow(
        label = "Menu Key",
        hint = when {
            service == null -> "SERVICE OFF"
            learning -> "PRESS THE KEY ON THE REMOTE NOW"
            else -> "OK = LEARN // DEFAULT 0xC3 = SONY GEAR"
        },
        enabled = service != null,
        onClick = {
            sounds?.play(Sfx.MENU_OPEN)
            learning = true
            service?.learnKey { scan ->
                viewModel.updateSettings { it.copy(quickScan = scan) }
                learning = false
            }
        },
    ) { TextValue(if (learning) "WAITING..." else "SCAN 0x%02X".format(s.quickScan), it, if (learning) Cyber.gold else Cyber.cyan) }

    SectionLabel("CONTENT // OK = SHOW/HIDE")
    QuickItem.entries.forEach { item ->
        val on = item in s.quickItems
        ConfigRow(
            label = item.label(),
            onClick = {
                viewModel.updateSettings { st -> st.copy(quickItems = if (on) st.quickItems - item else st.quickItems + item) }
                sounds?.play(Sfx.PRESS)
            },
        ) { ToggleValue(on, it, onText = "SHOW", offText = "HIDE") }
    }
}

// ---------------------------------------------------------------- 08 TV SYSTEM

private val OWN_MODULE_LINKS = setOf(SettingsLink.PICTURE, SettingsLink.SOUND, SettingsLink.NETWORK, SettingsLink.BLUETOOTH, SettingsLink.APPS, SettingsLink.STORAGE)

@Composable
fun SystemPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val sounds = LocalSounds.current
    MicroLabel("OPENS THE TV'S OWN MENU // BACK RETURNS HERE", color = Cyber.redDim)
    // Bild, Ton usw. haben eigene Module; deren Sony-Seiten stehen dort unter "Advanced".
    (SettingsLink.entries - OWN_MODULE_LINKS).chunked(3).forEachIndexed { row, links ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEachIndexed { col, link ->
                val index = row * 3 + col
                LinkChip(
                    title = link.title,
                    source = link.source,
                    index = index,
                    onClick = {
                        sounds?.play(Sfx.ENTER)
                        viewModel.tv.openSetting(link)
                    },
                    modifier = Modifier.weight(1f).then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                )
            }
            repeat(3 - links.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ---------------------------------------------------------------- 09 STATUS

@Composable
fun StatusPanel(system: SystemInfo, weather: Weather?, first: FocusRequester) {
    val uptimeMin = system.uptimeMs / 60_000
    val gb = 1_073_741_824.0
    val usedShare = if (system.totalBytes > 0) 1f - system.freeBytes.toFloat() / system.totalBytes else 0f
    val signal = when (system.wifiDbm) {
        null -> 0
        in -55..0 -> 4
        in -65..-56 -> 3
        in -75..-66 -> 2
        else -> 1
    }

    // Eine fokussierbare Zeile, damit ▶ / OK vom Modul ins Panel springen kann.
    ConfigRow(label = "System Scan", hint = "LIVE // REFRESH 10 S", modifier = Modifier.focusRequester(first)) {
        TextValue("ONLINE", it, Cyber.green)
    }
    StatusLine("Device", system.model)
    StatusLine("Android", "${system.android} // BUILD ${system.build}")
    StatusLine("Launcher", "NC-OS ${system.launcherVersion} // THEME ${Live.settings.theme.name}")
    StatusLine("Network", "IP ${system.ip} // WLAN ${system.wifiDbm?.let { "$it DBM" } ?: "--"}") { SignalBars(signal) }
    StatusLine(
        "Storage",
        String.format(Locale.US, "FREE %.1f GB OF %.1f GB", system.freeBytes / gb, system.totalBytes / gb),
    ) { UsageBar(usedShare) }
    StatusLine("Uptime", "%02d:%02d H".format(uptimeMin / 60, uptimeMin % 60))
    StatusLine("Debloat", "%d PACKAGES OFFLINE".format(system.disabledPackages), Cyber.green)
    StatusLine("Weather", weather?.let { "${it.place} // ${it.tempC.roundToInt()}°C ${it.label}" } ?: "NO LOCATION")
    // Open-Meteo-Daten stehen unter CC BY 4.0 und verlangen eine Quellenangabe.
    if (weather != null) StatusLine("Source", "WEATHER DATA BY OPEN-METEO.COM", Cyber.redDim)
}

@Composable
internal fun StatusLine(label: String, value: String, color: Color = Cyber.cyan, extra: @Composable () -> Unit = {}) {
    // Zeile + Trennlinie als ein Block, damit der Panel-Abstand nur einmal zaehlt.
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            CyberText(label.uppercase(), Modifier.width(110.dp), size = 12.sp, color = Cyber.redDim, weight = FontWeight.Bold, spacing = 0.14f)
            CyberText(value.uppercase(), Modifier.weight(1f), size = 14.sp, color = color, weight = FontWeight.SemiBold)
            extra()
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Cyber.redDim.copy(alpha = 0.35f)))
    }
}

@Composable
internal fun SignalBars(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..4) {
            Box(Modifier.size(5.dp, (4 + i * 4).dp).background(if (i <= level) Cyber.cyan else Cyber.redDim.copy(alpha = 0.5f)))
        }
    }
}

@Composable
internal fun UsageBar(share: Float) {
    Box(Modifier.size(120.dp, 10.dp).border(1.dp, Cyber.redDim)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).background(if (share > 0.9f) Cyber.redAlert else Cyber.red))
    }
}
