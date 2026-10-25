package dev.david.braviahome.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.david.braviahome.HomeViewModel
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.system.ApiGroup
import dev.david.braviahome.system.ApiSetting
import dev.david.braviahome.system.ApiStatus
import dev.david.braviahome.system.BluetoothInfo
import dev.david.braviahome.system.BtState
import dev.david.braviahome.system.NetStatus
import dev.david.braviahome.system.NetworkInfo
import dev.david.braviahome.system.SettingsLink
import dev.david.braviahome.system.SonyTv
import dev.david.braviahome.system.StorageInfo
import dev.david.braviahome.system.StorageState
import dev.david.braviahome.system.WifiNetwork
import dev.david.braviahome.system.bandLabel
import dev.david.braviahome.system.bytesLabel
import dev.david.braviahome.system.channel
import dev.david.braviahome.system.signalLevel
import dev.david.braviahome.ui.theme.Cyber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Panels fuer die TV-Menues (Bild, Ton ...). Bild- und Tonwerte kommen aus Sonys Fernsteuer-API
// (system/SonyApi.kt) und werden so angezeigt, wie der TV sie liefert: Bereich = Balken, Liste = Auswahl.

private val PICTURE_ORDER = listOf(
    "pictureMode", "brightness", "contrast", "color", "hue", "sharpness", "colorTemperature",
    "lightSensor", "autoLocalDimming", "xtendedDynamicRange", "hdrMode", "colorSpace", "autoPictureMode",
)

private val LABELS = mapOf(
    "pictureMode" to "Picture Mode",
    "brightness" to "Brightness",
    "contrast" to "Contrast",
    "color" to "Color",
    "hue" to "Hue",
    "sharpness" to "Sharpness",
    "colorTemperature" to "Color Temp",
    "lightSensor" to "Light Sensor",
    "autoLocalDimming" to "Local Dimming",
    "xtendedDynamicRange" to "X-tended DR",
    "hdrMode" to "HDR Mode",
    "colorSpace" to "Color Space",
    "autoPictureMode" to "Auto Mode",
    "outputTerminal" to "Audio Out",
    "tvPosition" to "TV Position",
)

private val HINTS = mapOf(
    "pictureMode" to "PRESET // CURRENT INPUT",
    "brightness" to "BACKLIGHT",
    "colorTemperature" to "WHITE BALANCE",
    "lightSensor" to "ADAPT TO ROOM LIGHT",
    "autoLocalDimming" to "BACKLIGHT ZONES",
    "xtendedDynamicRange" to "PEAK BRIGHTNESS",
    "hdrMode" to "HDR SIGNAL HANDLING",
    "colorSpace" to "COLOR GAMUT",
    "autoPictureMode" to "MODE BY CONTENT",
    "outputTerminal" to "WHERE THE SOUND PLAYS",
    "tvPosition" to "SOUND TUNED FOR STAND / WALL",
)

private val VALUES = mapOf(
    "sRgb/bt709" to "SRGB/BT.709",
    "dci" to "DCI-P3",
    "adobeRgb" to "ADOBE RGB",
    "bt2020" to "BT.2020",
    "expert1" to "EXPERT 1",
    "expert2" to "EXPERT 2",
    "auto24p" to "AUTO 24P",
    "hdr10" to "HDR10",
    "hlg" to "HLG",
    "speaker" to "TV SPEAKER",
    "speaker_hdmi" to "TV + ARC",
    "hdmi" to "HDMI ARC",
    "audioSystem" to "AUDIO SYS",
    "tableTop" to "TABLE TOP",
    "wallMount" to "WALL MOUNT",
)

/** "colorTemperature" -> "COLOR TEMPERATURE" fuer Werte, die keine eigene Beschriftung haben. */
private fun spaced(raw: String) = raw.replace(Regex("([a-z])([A-Z0-9])"), "$1 $2").replace('_', ' ').uppercase()

fun sonyLabel(target: String) = LABELS[target] ?: spaced(target)
fun sonyValue(value: String) = VALUES[value] ?: spaced(value)

/** Aktueller Wert aus dem Speicher (nicht aus der Komposition): auch bei gehaltener Taste aktuell. */
private fun latest(group: ApiGroup, target: String) = SonyTv.state.value.of(group).firstOrNull { it.target == target }

/** Eine Zeile fuer einen Sony-Wert. */
@Composable
fun ApiRow(group: ApiGroup, s: ApiSetting, ready: Boolean, modifier: Modifier = Modifier, segments: Int = 16) {
    val sounds = LocalSounds.current
    ConfigRow(
        label = sonyLabel(s.target),
        hint = when {
            !s.available -> "NOT AVAILABLE // CURRENT SIGNAL"
            s.isRange -> "${HINTS[s.target]?.plus(" // ") ?: ""}${s.min}..${s.max}"
            else -> HINTS[s.target] ?: "SONY // ${s.target.uppercase()}"
        },
        modifier = modifier,
        enabled = ready && s.available,
        onAdjust = { d ->
            latest(group, s.target)?.stepped(d)?.let {
                SonyTv.set(group, s.target, it)
                sounds?.play(Sfx.MOVE)
            }
        },
    ) { focused ->
        if (s.isRange) {
            val value = s.current.toIntOrNull() ?: s.min!!
            SegmentBar(
                level = value - s.min!!,
                max = s.max!! - s.min,
                focused = focused,
                segments = segments,
                valueText = if (s.min < 0) "%+d".format(value) else "%3d".format(value),
            )
        } else {
            ChoiceValue(sonyValue(s.current), focused)
        }
    }
}

/** Kopfzeile jedes Sony-Panels: Zustand der API, OK liest neu. */
@Composable
private fun ApiStatusRow(status: ApiStatus, modifier: Modifier) {
    val sounds = LocalSounds.current
    val (text, hint, color) = when (status) {
        ApiStatus.READY -> Triple("ONLINE", "SONY API // OK = RELOAD", Cyber.green)
        ApiStatus.LOADING -> Triple("SYNC", "SONY API // READING", Cyber.gold)
        ApiStatus.NO_KEY -> Triple("LOCKED", "READ ONLY // RUN enable-sony-api.ps1", Cyber.redAlert)
        ApiStatus.DENIED -> Triple("DENIED", "KEY REJECTED // RUN enable-sony-api.ps1", Cyber.redAlert)
        ApiStatus.OFFLINE -> Triple("OFFLINE", "SONY API NOT REACHABLE", Cyber.redAlert)
    }
    ConfigRow(
        label = "TV Link",
        hint = hint,
        modifier = modifier,
        onClick = { SonyTv.refresh(); sounds?.play(Sfx.PRESS) },
    ) { TextValue(text, it, color) }
}

/**
 * Gemeinsamer Aufbau: Kopfzeile, dann die Werte. Der Fokus springt beim Betreten auf den ersten
 * aenderbaren Wert (sonst auf die Kopfzeile), damit ▶ direkt beim Bildmodus landet.
 */
@Composable
private fun SonyRows(
    first: FocusRequester,
    rows: List<Pair<ApiGroup, ApiSetting>>,
    status: ApiStatus,
) {
    val ready = status == ApiStatus.READY
    val firstRow = rows.indexOfFirst { it.second.available }.takeIf { ready && it >= 0 }
    ApiStatusRow(status, if (firstRow == null) Modifier.focusRequester(first) else Modifier)
    rows.forEachIndexed { i, (group, s) ->
        ApiRow(group, s, ready, if (i == firstRow) Modifier.focusRequester(first) else Modifier)
    }
}

// ---------------------------------------------------------------- PICTURE

@Composable
fun PicturePanel(viewModel: HomeViewModel, first: FocusRequester) {
    val sony by SonyTv.state.collectAsStateWithLifecycle()
    val sounds = LocalSounds.current
    LaunchedEffect(Unit) { SonyTv.refresh() }

    val rows = sony.picture
        .sortedBy { PICTURE_ORDER.indexOf(it.target).let { i -> if (i < 0) PICTURE_ORDER.size else i } }
        .map { ApiGroup.PICTURE to it }
    SonyRows(first, rows, sony.status)

    SectionLabel("SCREEN")
    // "Bild aus" erst nach zweitem OK, wie Standby: ein versehentlicher Druck macht nichts dunkel.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    ConfigRow(
        label = "Picture Off",
        hint = if (armed) "CONFIRM // PRESS OK AGAIN" else "SCREEN DARK, SOUND ON // ANY KEY RETURNS",
        enabled = sony.status == ApiStatus.READY,
        onClick = {
            if (armed) {
                armed = false
                sounds?.play(Sfx.PRESS)
                SonyTv.pictureOff()
            } else {
                armed = true
                sounds?.play(Sfx.MENU_OPEN)
            }
        },
    ) { TextValue(if (armed) "CONFIRM?" else "EXECUTE", it, Cyber.gold) }
    ConfigRow(
        label = "Advanced",
        hint = "SONY PICTURE MENU // ALL OTHER OPTIONS",
        onClick = { sounds?.play(Sfx.ENTER); viewModel.tv.openSetting(SettingsLink.PICTURE) },
    ) { TextValue("▶", it) }
}

// ---------------------------------------------------------------- SOUND

@Composable
fun SoundPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val sony by SonyTv.state.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val sounds = LocalSounds.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { SonyTv.refresh() }

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

    SectionLabel("SONY AUDIO")
    val ready = sony.status == ApiStatus.READY
    ApiStatusRow(sony.status, Modifier)
    (sony.sound.map { ApiGroup.SOUND to it } + sony.speaker.map { ApiGroup.SPEAKER to it }).forEach { (group, s) ->
        ApiRow(group, s, ready)
    }

    SectionLabel("ACTIVE OUTPUTS")
    val outputs = remember(volume) { audioOutputs(context) }
    if (outputs.isEmpty()) StatusLine("Output", "NONE REPORTED")
    outputs.forEach { (type, name) -> StatusLine(type, name) }

    ConfigRow(
        label = "Advanced",
        hint = "SONY SOUND MENU // SOUND MODE, EQ, DIALOG",
        onClick = { sounds?.play(Sfx.ENTER); viewModel.tv.openSetting(SettingsLink.SOUND) },
    ) { TextValue("▶", it) }
}

/** Ausgaenge, die Android gerade kennt: Typ und Name (z. B. "BLUETOOTH" / "Sony WH-1000XM4"). */
private fun audioOutputs(context: Context): List<Pair<String, String>> {
    val audio = context.getSystemService(AudioManager::class.java) ?: return emptyList()
    return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .mapNotNull { d ->
            val type = when (d.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "TV Speaker"
                AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
                AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headphones"
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Optical"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
                else -> return@mapNotNull null
            }
            type to d.productName.toString().ifBlank { type }
        }
        .distinct()
}

// ---------------------------------------------------------------- NETWORK

@Composable
fun NetworkPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val context = LocalContext.current
    val sounds = LocalSounds.current
    val info = remember { NetworkInfo(context) }
    var net by remember { mutableStateOf(NetStatus()) }
    var nearby by remember { mutableStateOf(emptyList<WifiNetwork>()) }
    var scanAt by remember { mutableStateOf(0L) }
    var latency by remember { mutableStateOf<Pair<Int?, Int?>?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Signal und Liste laufend auffrischen (WLAN-Werte aendern sich staendig).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { info.startScan() }
        while (true) {
            net = withContext(Dispatchers.IO) { info.status() }
            nearby = withContext(Dispatchers.IO) { info.nearby(net.ssid) }
            delay(3_000)
        }
    }

    ConfigRow(
        label = "Connection",
        hint = when {
            net.ethernet -> "ETHERNET // OK = REFRESH"
            net.wifi -> "WLAN // ${net.ssid.ifEmpty { "HIDDEN" }}"
            else -> "NO NETWORK"
        },
        modifier = Modifier.focusRequester(first),
        onClick = { sounds?.play(Sfx.PRESS); scope.launch { net = withContext(Dispatchers.IO) { info.status() } } },
    ) {
        TextValue(if (net.wifi || net.ethernet) "ONLINE" else "OFFLINE", it, if (net.wifi || net.ethernet) Cyber.green else Cyber.redAlert)
    }
    if (net.wifi) {
        StatusLine("Network", net.ssid.ifEmpty { "HIDDEN // NO LOCATION ACCESS" }) { SignalBars(signalLevel(net.rssi)) }
        StatusLine("Signal", "${net.rssi ?: "--"} DBM // ${listOf("--", "WEAK", "OK", "GOOD", "EXCELLENT")[signalLevel(net.rssi)]}")
        StatusLine("Band", "${bandLabel(net.frequency)} // CH ${channel(net.frequency)} // ${net.linkMbps} MBIT/S")
    }
    StatusLine("IP", net.ip.ifEmpty { "--" })
    StatusLine("Gateway", net.gateway.ifEmpty { "--" })
    StatusLine("DNS", net.dns.joinToString(" // ").ifEmpty { "--" })
    ConfigRow(
        label = "Speed Check",
        hint = latency?.let { (router, web) -> "ROUTER ${router?.let { "$it MS" } ?: "NO REPLY"} // INTERNET ${web?.let { "$it MS" } ?: "NO REPLY"}" }
            ?: "RESPONSE TIME ROUTER + INTERNET",
        onClick = {
            if (!testing) {
                testing = true
                sounds?.play(Sfx.PRESS)
                scope.launch {
                    latency = withContext(Dispatchers.IO) { info.latency(net.gateway) }
                    testing = false
                }
            }
        },
    ) { TextValue(if (testing) "RUNNING" else "EXECUTE", it, if (testing) Cyber.gold else Cyber.cyan) }

    SectionLabel("NEARBY // OK = CONNECT VIA ANDROID")
    ConfigRow(
        label = "Scan",
        hint = when {
            !info.canScan() -> "LOCKED // RUN install-launcher.ps1"
            scanAt == 0L -> "${nearby.size} NETWORKS"
            else -> "${nearby.size} NETWORKS // SCANNED ${(System.currentTimeMillis() - scanAt) / 1000} S AGO"
        },
        enabled = info.canScan(),
        onClick = {
            sounds?.play(Sfx.PRESS)
            scope.launch {
                if (withContext(Dispatchers.IO) { info.startScan() }) scanAt = System.currentTimeMillis()
            }
        },
    ) { TextValue("EXECUTE", it) }
    nearby.take(12).forEach { w ->
        ConfigRow(
            label = w.ssid,
            hint = "${bandLabel(w.frequency)} // ${w.security} // ${w.rssi} DBM",
            onClick = { sounds?.play(Sfx.ENTER); viewModel.tv.openSetting(SettingsLink.NETWORK) },
        ) { focused ->
            if (w.current) TextValue("CONNECTED", focused, Cyber.green) else SignalBars(signalLevel(w.rssi))
        }
    }
    ConfigRow(
        label = "Advanced",
        hint = "ANDROID NETWORK MENU // PASSWORD, PROXY, IP",
        onClick = { sounds?.play(Sfx.ENTER); viewModel.tv.openSetting(SettingsLink.NETWORK) },
    ) { TextValue("▶", it) }
}

// ---------------------------------------------------------------- BLUETOOTH

@Composable
fun BluetoothPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val context = LocalContext.current
    val sounds = LocalSounds.current
    val info = remember { BluetoothInfo(context) }
    var bt by remember { mutableStateOf(BtState()) }
    LaunchedEffect(Unit) {
        while (true) {
            bt = withContext(Dispatchers.IO) { info.read() }
            delay(4_000)
        }
    }

    ConfigRow(
        label = "Bluetooth",
        hint = when {
            !bt.permitted -> "LOCKED // RUN install-launcher.ps1"
            else -> "TV NAME // ${bt.adapterName.ifEmpty { "--" }}"
        },
        modifier = Modifier.focusRequester(first),
        onClick = { sounds?.play(Sfx.PRESS) },
    ) { TextValue(if (bt.enabled) "ON" else "OFF", it, if (bt.enabled) Cyber.green else Cyber.redAlert) }

    SectionLabel("PAIRED DEVICES // OK = MANAGE")
    if (bt.permitted && bt.devices.isEmpty()) StatusLine("Devices", "NONE PAIRED")
    bt.devices.forEach { d ->
        ConfigRow(
            label = d.name,
            hint = listOfNotNull(d.kind, d.battery?.let { "BATTERY $it %" }, d.address).joinToString(" // "),
            onClick = { sounds?.play(Sfx.ENTER); viewModel.tv.openSetting(SettingsLink.BLUETOOTH) },
        ) { TextValue(if (d.connected) "CONNECTED" else "PAIRED", it, if (d.connected) Cyber.green else Cyber.redDim) }
    }
    ConfigRow(
        label = "Pair New Device",
        hint = "ANDROID // REMOTES, HEADPHONES, GAMEPADS",
        onClick = { sounds?.play(Sfx.ENTER); viewModel.tv.openSetting(SettingsLink.BLUETOOTH) },
    ) { TextValue("▶", it) }
}

// ---------------------------------------------------------------- APPS & STORAGE

@Composable
fun AppsPanel(viewModel: HomeViewModel, first: FocusRequester) {
    val context = LocalContext.current
    val sounds = LocalSounds.current
    val info = remember { StorageInfo(context) }
    var storage by remember { mutableStateOf<StorageState?>(null) }
    var infoFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { storage = withContext(Dispatchers.IO) { info.read() } }
    val s = storage
    infoFor?.let { AppInfoDialog(it, onDismiss = { infoFor = null }) }

    val used = if (s != null && s.totalBytes > 0) 1f - s.freeBytes.toFloat() / s.totalBytes else 0f
    ConfigRow(
        label = "Storage",
        hint = if (s == null) "SCANNING" else "FREE ${bytesLabel(s.freeBytes)} OF ${bytesLabel(s.totalBytes)}",
        modifier = Modifier.focusRequester(first),
        onClick = { sounds?.play(Sfx.ENTER); viewModel.tv.openSetting(SettingsLink.STORAGE) },
    ) { UsageBar(used) }
    if (s == null) return
    StatusLine("Video Loops", bytesLabel(s.loopsBytes))

    SectionLabel("APPS BY SIZE // OK = APP INFO")
    if (!s.permitted) StatusLine("App Sizes", "LOCKED // RUN install-launcher.ps1", Cyber.redAlert)
    s.apps.forEach { a ->
        ConfigRow(
            label = a.label,
            hint = "APP ${bytesLabel(a.appBytes)} // DATA ${bytesLabel(a.dataBytes)} // CACHE ${bytesLabel(a.cacheBytes)}",
            onClick = { sounds?.play(Sfx.ENTER); infoFor = a.packageName },
        ) { TextValue(bytesLabel(a.total), it) }
    }
    ConfigRow(
        label = "All Apps",
        hint = "ANDROID // INCL. SYSTEM APPS",
        onClick = { sounds?.play(Sfx.ENTER); viewModel.tv.openSetting(SettingsLink.APPS) },
    ) { TextValue("▶", it) }
}