package dev.david.braviahome.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.david.braviahome.Actions
import dev.david.braviahome.HomeViewModel
import dev.david.braviahome.data.AppEntry
import dev.david.braviahome.data.LauncherSettings
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.displayInputs
import dev.david.braviahome.data.SystemInfo
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.ui.cyber.CyberBackground
import dev.david.braviahome.ui.cyber.HeaderLine
import dev.david.braviahome.ui.cyber.KeyHints
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.cyber.Scanlines
import dev.david.braviahome.ui.cyber.SectionHeader
import dev.david.braviahome.ui.cyber.bootReveal
import dev.david.braviahome.ui.theme.Cyber
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenConfig: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homePresses by viewModel.homePresses.collectAsStateWithLifecycle()
    val bootRequests by viewModel.bootRequests.collectAsStateWithLifecycle()
    val system by viewModel.system.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sounds = LocalSounds.current

    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var infoFor by remember { mutableStateOf<String?>(null) }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var refocus by remember { mutableIntStateOf(0) }
    var activeSection by remember { mutableStateOf(Section.APPS) }
    val appsRow = rememberLazyListState()
    val inputsRow = rememberLazyListState()
    val firstApp = remember { FocusRequester() }
    val firstHidden = remember { FocusRequester() }

    // Boot-Sequenz: 0 = schwarz, 1 = alles da.
    // Nur neue Anforderungen abspielen: kommt man aus SYS.CONFIG zurueck, wird dieser
    // Bildschirm neu aufgebaut, soll aber nicht nochmal booten.
    val boot = remember { Animatable(if (bootRequests.id > viewModel.playedBootId) 0f else 1f) }
    val progress = { boot.value }
    LaunchedEffect(bootRequests.id) {
        if (bootRequests.id == 0 || bootRequests.id == viewModel.playedBootId) {
            boot.snapTo(1f)
            return@LaunchedEffect
        }
        viewModel.playedBootId = bootRequests.id
        boot.snapTo(0f)
        delay(bootRequests.delayMs)
        Log.i("BraviaHome", "Boot-Sequenz startet (Verzoegerung ${bootRequests.delayMs} ms)")
        sounds?.play(Sfx.BOOT)
        boot.animateTo(1f, tween(viewModel.bootDurationMs, easing = LinearEasing))
        // Nach dem Einschalten: Start-Ziel aus SYS.CONFIG > INPUTS & START (nicht bei der Vorschau).
        if (bootRequests.runStartTarget) {
            delay(250)
            Actions.openStartTarget(context, Live.settings.startTarget)
        }
    }

    BackHandler(enabled = movingPackage != null) {
        movingPackage = null
        sounds?.play(Sfx.PRESS)
    }

    // Erster Start, Home-Taste und Ausblenden: Fokus zurueck auf die erste App.
    LaunchedEffect(state.loaded, homePresses, refocus) {
        if (!state.loaded) return@LaunchedEffect
        menuFor = null
        infoFor = null
        movingPackage = null
        showHidden = false
        // Kurz warten, bis eine gerade ausgeblendete Kachel wirklich weg ist.
        delay(60)
        appsRow.scrollToItem(0)
        inputsRow.scrollToItem(0)
        runCatching { firstApp.requestFocus() }
    }

    // Ausgeblendete Apps aufgeklappt: hinspringen.
    LaunchedEffect(showHidden) {
        if (!showHidden || state.hidden.isEmpty()) return@LaunchedEffect
        appsRow.animateScrollToItem(state.apps.size)
        delay(80)
        runCatching { firstHidden.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        CyberBackground()

        Column(Modifier.fillMaxSize().padding(horizontal = Cyber.padH, vertical = Cyber.padV)) {
            TopBar(
                title = Live.settings.title.ifBlank { LauncherSettings.DEFAULT_TITLE },
                tabs = Section.entries.map { it.title },
                activeTab = activeSection.ordinal,
                system = system,
                weather = weather,
                modifier = Modifier.bootReveal(progress, 0.30f),
            )
            HeaderLine(Modifier.padding(top = 6.dp).bootReveal(progress, 0.34f))
            Spacer(Modifier.height(18.dp))

            // Reihen erst zeigen, wenn alles geladen ist: sonst landet der erste Fokus kurz
            // auf "Settings" (die einzige Kachel, die sofort da ist).
            if (state.loaded) {
                SectionHeader(
                    index = 1,
                    title = if (movingPackage != null) "APPS // MOVE MODE" else "APPS",
                    status = "NODES %02d // ONLINE".format(state.apps.size),
                    active = activeSection == Section.APPS,
                    modifier = Modifier.bootReveal(progress, 0.40f),
                )
                LazyRow(
                    state = appsRow,
                    horizontalArrangement = Arrangement.spacedBy(Cyber.gap),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    itemsIndexed(state.apps, key = { _, app -> app.packageName }) { index, app ->
                        val moving = movingPackage == app.packageName
                        val bringIntoView = remember { BringIntoViewRequester() }
                        LaunchedEffect(moving, index) { if (moving) bringIntoView.bringIntoView() }
                        CyberAppTile(
                            app = app,
                            index = index,
                            moving = moving,
                            onClick = {
                                if (moving) {
                                    movingPackage = null
                                    sounds?.play(Sfx.PRESS)
                                } else {
                                    sounds?.play(Sfx.OPEN)
                                    Actions.openApp(context, app)
                                }
                            },
                            onLongClick = { if (!moving) menuFor = app },
                            onFocusChanged = { if (it) activeSection = Section.APPS },
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstApp) else Modifier)
                                .bringIntoViewRequester(bringIntoView)
                                .tileKeys(
                                    moving = moving,
                                    onMove = { delta ->
                                        sounds?.play(Sfx.MOVE)
                                        viewModel.move(app.packageName, delta)
                                    },
                                    onMoveDone = {
                                        movingPackage = null
                                        sounds?.play(Sfx.PRESS)
                                    },
                                    onMenu = { menuFor = app },
                                ),
                            blockModifier = Modifier.bootReveal(progress, 0.46f + index * 0.05f),
                        )
                    }
                    if (showHidden) {
                        itemsIndexed(state.hidden, key = { _, app -> "hidden:" + app.packageName }) { index, app ->
                            CyberAppTile(
                                app = app,
                                index = state.apps.size + index,
                                dimmed = true,
                                onClick = {
                                    sounds?.play(Sfx.OPEN)
                                    Actions.openApp(context, app)
                                },
                                onLongClick = { menuFor = app },
                                onFocusChanged = { if (it) activeSection = Section.APPS },
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstHidden) else Modifier)
                                    .tileKeys(onMenu = { menuFor = app }),
                            )
                        }
                    }
                }

                // Eigene Namen und ausgeblendete Eingaenge aus SYS.CONFIG > INPUTS & START.
                val inputs = displayInputs(state.inputs, Live.settings)
                Spacer(Modifier.height(6.dp))
                SectionHeader(
                    index = 2,
                    title = "INPUTS // SYSTEM",
                    status = "PORTS %02d // LINKED".format(inputs.size),
                    active = activeSection != Section.APPS,
                    modifier = Modifier.bootReveal(progress, 0.62f),
                )
                LazyRow(
                    state = inputsRow,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    itemsIndexed(inputs, key = { _, input -> input.id }) { index, input ->
                        // "HDMI 3 (eARC/ARC)" -> Titel "HDMI 3", Zusatz in die kleine Zeile
                        val extra = input.label.substringAfter('(', "").substringBefore(')').takeIf { it.isNotBlank() }
                        CyberChip(
                            title = input.label.substringBefore('(').trim(),
                            subtitle = "INPUT // " + input.id.substringAfterLast('/') + (extra?.let { " // $it" } ?: ""),
                            onClick = {
                                sounds?.play(Sfx.ENTER)
                                Actions.openInput(context, input)
                            },
                            onFocusChanged = { if (it) activeSection = Section.INPUTS },
                            modifier = Modifier.bootReveal(progress, 0.66f + index * 0.03f),
                        )
                    }
                    item(key = "settings") {
                        CyberChip(
                            title = "Settings",
                            subtitle = "SYSTEM // CONFIG",
                            onClick = {
                                sounds?.play(Sfx.ENTER)
                                onOpenConfig()
                            },
                            onFocusChanged = { if (it) activeSection = Section.SYSTEM },
                            modifier = Modifier.bootReveal(progress, 0.80f),
                        )
                    }
                    if (state.hidden.isNotEmpty()) {
                        item(key = "hidden") {
                            CyberChip(
                                title = "Hidden",
                                subtitle = "%02d APPS // %s".format(state.hidden.size, if (showHidden) "CLOSE" else "SHOW"),
                                onClick = {
                                    sounds?.play(Sfx.TAB)
                                    showHidden = !showHidden
                                },
                                onFocusChanged = { if (it) activeSection = Section.SYSTEM },
                                modifier = Modifier.bootReveal(progress, 0.83f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Footer(system, Modifier.bootReveal(progress, 0.86f))
        }

        Scanlines()
        BootOverlay(progress)

        menuFor?.let { app ->
            val isHidden = state.hidden.any { it.packageName == app.packageName }
            val items = if (isHidden) {
                listOf(
                    MenuItem("Unhide") {
                        viewModel.setHidden(app.packageName, false)
                        refocus++
                    },
                    MenuItem("App info") { infoFor = app.packageName },
                )
            } else {
                listOf(
                    MenuItem("Move") { movingPackage = app.packageName },
                    MenuItem("Hide") {
                        viewModel.setHidden(app.packageName, true)
                        refocus++
                    },
                    MenuItem("App info") { infoFor = app.packageName },
                )
            }
            TileMenu(title = app.label, code = app.packageName, items = items, onDismiss = { menuFor = null })
        }
        infoFor?.let { AppInfoDialog(it, onDismiss = { infoFor = null }) }
    }
}

/** Unten links echte TV-Daten im Stil der "SW VERSION"-Labels, unten rechts die Tasten. */
@Composable
private fun Footer(system: SystemInfo, modifier: Modifier = Modifier) {
    val uptimeMin = system.uptimeMs / 60_000
    val freeGb = system.freeBytes / 1_073_741_824.0
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (Live.settings.footer) {
            Column {
                MicroLabel("SW VERSION ${system.launcherVersion} // ANDROID ${system.android}", color = Cyber.red)
                MicroLabel("BUILD ${system.build}")
                MicroLabel("IP ${system.ip} // WLAN ${system.wifiDbm?.let { "$it DBM" } ?: "--"}")
                MicroLabel(
                    "UPTIME %02d:%02d // FREE %s GB".format(
                        uptimeMin / 60, uptimeMin % 60, String.format(Locale.US, "%.1f", freeGb),
                    ),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        KeyHints("OK" to "Open", "OK" to "Hold // Options", "⌂" to "Home")
    }
}

/**
 * Tasten einer Kachel. Im Verschiebe-Modus: ◀ ▶ verschieben, OK beendet,
 * hoch/runter ist gesperrt. Die Menue-Taste oeffnet immer das Kachel-Menue.
 */
private fun Modifier.tileKeys(
    moving: Boolean = false,
    onMove: (Int) -> Unit = {},
    onMoveDone: () -> Unit = {},
    onMenu: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    val down = event.type == KeyEventType.KeyDown
    if (event.key == Key.Menu) {
        if (down) onMenu()
        return@onPreviewKeyEvent true
    }
    if (!moving) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionLeft -> { if (down) onMove(-1); true }
        Key.DirectionRight -> { if (down) onMove(1); true }
        Key.DirectionUp, Key.DirectionDown -> true
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (!down) onMoveDone(); true }
        else -> false
    }
}
