package dev.david.braviahome.quick

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.QuickItem
import dev.david.braviahome.system.ApiGroup
import dev.david.braviahome.system.ApiStatus
import dev.david.braviahome.system.SettingsLink
import dev.david.braviahome.system.SonyTv
import dev.david.braviahome.ui.ApiRow
import dev.david.braviahome.ui.ChoiceValue
import dev.david.braviahome.ui.ConfigRow
import dev.david.braviahome.ui.Formats
import dev.david.braviahome.ui.SegmentBar
import dev.david.braviahome.ui.TextValue
import dev.david.braviahome.ui.ToggleValue
import dev.david.braviahome.ui.cyber.Barcode
import dev.david.braviahome.ui.cyber.ChamferShape
import dev.david.braviahome.ui.cyber.Corner
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.KeyHints
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.label
import dev.david.braviahome.ui.rememberNow
import dev.david.braviahome.ui.theme.Cyber
import kotlinx.coroutines.delay

private val SLEEP_STEPS = listOf(0, 15, 30, 45, 60, 90, 120)
private val PANEL_WIDTH = 540.dp

/** Eigenes Schnellmenue: faehrt von rechts ueber die laufende App. Inhalt aus SYS.CONFIG > QUICK MENU. */
@Composable
fun QuickPanel(service: QuickPanelService) {
    val controls = service.controls
    val items = Live.settings.quickItems
    val first = remember { FocusRequester() }
    val slide = remember { Animatable(1f) }
    var volume by remember { mutableStateOf(controls.volume()) }
    var armed by remember { mutableStateOf(false) }
    val inputs = remember { if (QuickItem.INPUTS in items) service.inputs() else emptyList() }
    val now = rememberNow()
    val sleepAt = service.sleepAt.longValue
    val sleepLeftMin = if (sleepAt == 0L) 0 else ((sleepAt - SystemClock.elapsedRealtime()) / 60_000L + 1).toInt()
    val shape = remember { ChamferShape(18.dp, setOf(Corner.BottomStart)) }

    // Der erste sichtbare Eintrag bekommt den Fokus.
    var firstTaken = false
    fun firstOrNone(): Modifier = if (firstTaken) Modifier else {
        firstTaken = true
        Modifier.focusRequester(first)
    }

    LaunchedEffect(Unit) {
        delay(30)
        runCatching { first.requestFocus() }
        slide.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
    }
    // Lautstaerke kann sich ueber die Lautstaerketasten aendern, solange das Panel offen ist.
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            volume = controls.volume()
        }
    }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    val sony by SonyTv.state.collectAsState()
    var pictureArmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { SonyTv.refresh() }
    LaunchedEffect(pictureArmed) {
        if (pictureArmed) {
            delay(3_000)
            pictureArmed = false
        }
    }

    // Zweiter Weg zum Schliessen, falls eine Taste nicht durch den Dienst-Filter kam
    // (z. B. per adb simulierte Tasten). Jede Taste haelt das Panel offen.
    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                service.touch()
                val close = e.key == Key.Back || e.nativeKeyEvent.scanCode == Live.settings.quickScan
                if (close && e.type == KeyEventType.KeyUp) service.hide()
                close
            },
    ) {
        // Schatten zur laufenden App hin, damit das Panel lesbar bleibt
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(PANEL_WIDTH + 160.dp)
                .graphicsLayer { alpha = 1f - slide.value }
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCC000000)))),
        )
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(PANEL_WIDTH)
                .graphicsLayer { translationX = slide.value * size.width }
                .background(Cyber.panel.copy(alpha = 0.96f), shape)
                .border(1.dp, Cyber.red, shape),
        ) {
            Row(
                Modifier.fillMaxWidth().background(Cyber.red).padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    CyberText("//QUICK.CTRL", size = 24.sp, color = Cyber.ink, weight = FontWeight.Bold)
                    MicroLabel("NC-OS // OVERLAY ACTIVE", color = Cyber.ink.copy(alpha = 0.7f))
                }
                Barcode(width = 48.dp, height = 14.dp, color = Cyber.ink)
                Spacer(Modifier.width(14.dp))
                CyberText(Formats.time(now), size = 26.sp, color = Cyber.ink, weight = FontWeight.Bold)
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (QuickItem.VOLUME in items || QuickItem.MUTE in items) Section("AUDIO")
                if (QuickItem.VOLUME in items) {
                    ConfigRow(
                        label = "Volume",
                        hint = "TV SPEAKER",
                        modifier = firstOrNone(),
                        onAdjust = { d ->
                            controls.changeVolume(d > 0)
                            volume = controls.volume()
                            service.play(Sfx.MOVE)
                        },
                    ) { SegmentBar(volume.level, volume.max, it, segments = 12) }
                }
                if (QuickItem.MUTE in items) {
                    ConfigRow(
                        label = "Mute",
                        modifier = firstOrNone(),
                        onClick = {
                            controls.toggleMute()
                            volume = controls.volume()
                            service.play(Sfx.PRESS)
                        },
                    ) { ToggleValue(volume.muted, it) }
                }

                val pictureItems = items.filter { it == QuickItem.PICTURE_MODE || it == QuickItem.BACKLIGHT || it == QuickItem.PICTURE_OFF }
                if (pictureItems.isNotEmpty()) {
                    Section("PICTURE")
                    val ready = sony.status == ApiStatus.READY
                    if (QuickItem.PICTURE_MODE in items) {
                        sony.picture("pictureMode")?.let { ApiRow(ApiGroup.PICTURE, it, ready, firstOrNone()) }
                    }
                    if (QuickItem.BACKLIGHT in items) {
                        sony.picture("brightness")?.let { ApiRow(ApiGroup.PICTURE, it, ready, firstOrNone(), segments = 12) }
                    }
                    if (QuickItem.PICTURE_OFF in items) {
                        ConfigRow(
                            label = "Picture Off",
                            hint = if (pictureArmed) "CONFIRM // PRESS OK AGAIN" else "SOUND KEEPS PLAYING",
                            modifier = firstOrNone(),
                            enabled = ready,
                            onClick = {
                                if (pictureArmed) {
                                    service.hide()
                                    SonyTv.pictureOff()
                                } else {
                                    pictureArmed = true
                                    service.play(Sfx.MENU_OPEN)
                                }
                            },
                        ) { TextValue(if (pictureArmed) "CONFIRM?" else "EXECUTE", it, Cyber.gold) }
                    }
                    if (sony.status != ApiStatus.READY && sony.status != ApiStatus.LOADING) {
                        MicroLabel("SONY API ${sony.status.name} // SEE SYS.CONFIG > PICTURE", color = Cyber.redDim)
                    }
                }

                if (items.any { it == QuickItem.SLEEP || it == QuickItem.SAVER || it == QuickItem.STANDBY }) Section("POWER")
                if (QuickItem.SLEEP in items) {
                    ConfigRow(
                        label = "Sleep Timer",
                        hint = if (sleepAt == 0L) "STANDBY AFTER" else "STANDBY IN $sleepLeftMin MIN",
                        modifier = firstOrNone(),
                        onAdjust = { d ->
                            val current = SLEEP_STEPS.indexOfFirst { it >= sleepLeftMin }.takeIf { sleepAt != 0L && it >= 0 } ?: 0
                            val next = SLEEP_STEPS[(current + d).coerceIn(0, SLEEP_STEPS.lastIndex)]
                            service.setSleepTimer(next)
                            service.play(Sfx.MOVE)
                        },
                    ) { ChoiceValue(if (sleepAt == 0L) "OFF" else "$sleepLeftMin MIN", it, if (sleepAt == 0L) Cyber.redDim else Cyber.gold) }
                }
                if (QuickItem.SAVER in items) {
                    ConfigRow(
                        label = "Screensaver",
                        hint = "${Live.settings.saverStyle.label()} // NOW",
                        modifier = firstOrNone(),
                        onClick = { service.startScreensaver() },
                    ) { TextValue("EXECUTE", it) }
                }
                if (QuickItem.STANDBY in items) {
                    ConfigRow(
                        label = "Standby",
                        hint = if (armed) "CONFIRM // PRESS OK AGAIN" else "POWER DOWN DISPLAY",
                        modifier = firstOrNone(),
                        enabled = controls.canStandby(),
                        onClick = {
                            if (armed) service.standby() else {
                                armed = true
                                service.play(Sfx.MENU_OPEN)
                            }
                        },
                    ) { TextValue(if (armed) "CONFIRM?" else "POWER OFF", it, Cyber.redAlert) }
                }

                if (inputs.isNotEmpty()) {
                    Section("INPUTS")
                    inputs.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { input ->
                                LinkRow(input.label, "HDMI", Modifier.weight(1f).then(firstOrNone())) {
                                    service.play(Sfx.ENTER)
                                    service.openInput(input)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                val showLinks = QuickItem.PICTURE_SOUND in items
                // Ohne jeden Eintrag bleibt wenigstens der Weg zu SYS.CONFIG.
                val showConfig = QuickItem.CONFIG in items || items.isEmpty()
                if (showLinks || showConfig) {
                    Section("SYSTEM")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showLinks) {
                            LinkRow("Picture", "TV", Modifier.weight(1f).then(firstOrNone())) { service.play(Sfx.ENTER); service.openSetting(SettingsLink.PICTURE) }
                            LinkRow("Sound", "TV", Modifier.weight(1f).then(firstOrNone())) { service.play(Sfx.ENTER); service.openSetting(SettingsLink.SOUND) }
                        }
                        if (showConfig) {
                            LinkRow("Config", "SYS.CONFIG", Modifier.weight(1f).then(firstOrNone())) { service.play(Sfx.ENTER); service.openConfig() }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("AUTO CLOSE 30 S", Modifier.weight(1f))
                KeyHints("◀▶" to "Adjust", "⚙" to "Close")
            }
        }
    }
}
@Composable
private fun Section(title: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(Cyber.cyan))
        Spacer(Modifier.width(8.dp))
        CyberText(title, size = 12.sp, color = Cyber.cyan, weight = FontWeight.Bold, spacing = 0.16f)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Cyber.redDim.copy(alpha = 0.6f)))
    }
}

@Composable
private fun LinkRow(title: String, source: String, modifier: Modifier, onClick: () -> Unit) {
    ConfigRow(label = title, hint = source, modifier = modifier, onClick = onClick) { TextValue("▶", it) }
}
