package dev.david.braviahome.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.david.braviahome.Actions
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.system.AppDetails
import dev.david.braviahome.system.AppDetailsReader
import dev.david.braviahome.system.bytesLabel
import dev.david.braviahome.ui.cyber.ChamferShape
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.theme.Cyber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Eigene Infoseite einer App statt Androids Einstellungsseite. Beenden erzwingen, Daten loeschen
 * und Deinstallieren darf nur das System, dafuer fuehrt "System Page" auf Androids Seite.
 */
@Composable
fun AppInfoDialog(packageName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sounds = LocalSounds.current
    val reader = remember { AppDetailsReader(context) }
    var details by remember { mutableStateOf<AppDetails?>(null) }
    LaunchedEffect(packageName) { details = withContext(Dispatchers.IO) { reader.read(packageName) } }
    val d = details
    // Die Aktionen sind sofort da, damit der erste Fokus sie findet; die Daten kommen kurz danach.
    val open = remember { FocusRequester() }

    CyberDialog(
        title = "App info // " + (d?.label ?: packageName),
        onDismiss = onDismiss,
        width = 560.dp,
        footer = if (d == null) packageName else "$packageName // TARGET SDK ${d.targetSdk}",
        initialFocus = open,
    ) {
        Header(d)
        val size = d?.size
        when {
            d == null -> StatusLine("Status", "SCANNING")
            size == null -> StatusLine("Usage", "LOCKED // RUN install-launcher.ps1", Cyber.redAlert)
            else -> {
                StatusLine("Storage", bytesLabel(size.total)) {
                    MicroLabel("APP ${bytesLabel(size.appBytes)} // DATA ${bytesLabel(size.dataBytes)} // CACHE ${bytesLabel(size.cacheBytes)}")
                }
                StatusLine("Last Used", if (d.lastUsed > 0) "${Formats.date(d.lastUsed)} // ${Formats.time(d.lastUsed)} ${Formats.suffix(d.lastUsed)}".trim() else "NOT IN 30 DAYS")
                StatusLine("Screen Time", "7 DAYS // " + durationLabel(d.foregroundWeekMs))
            }
        }
        if (d != null) {
            StatusLine(
                "Permissions",
                d.permissions.ifEmpty { listOf("NONE") }.joinToString(" // "),
                if (d.permissions.isEmpty()) Cyber.green else Cyber.gold,
            )
            StatusLine("Installed", if (d.system) "FACTORY IMAGE" else Formats.date(d.installedAt)) {
                MicroLabel("UPDATED ${Formats.date(d.updatedAt)}")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConfigRow(
                label = "Open",
                hint = "LAUNCH APP",
                modifier = Modifier.weight(1f).focusRequester(open),
                onClick = {
                    sounds?.play(Sfx.OPEN)
                    onDismiss()
                    Actions.openPackage(context, packageName)
                },
            ) { TextValue("▶", it) }
            ConfigRow(
                label = "System Page",
                hint = "STOP // CLEAR // UNINSTALL",
                modifier = Modifier.weight(1f),
                onClick = {
                    sounds?.play(Sfx.ENTER)
                    onDismiss()
                    Actions.openAppInfo(context, packageName)
                },
            ) { TextValue("▶", it) }
        }
    }
}

/** Banner oder Icon, daneben Name, Version und Herkunft. */
@Composable
private fun Header(d: AppDetails?) {
    val shape = remember { ChamferShape(8.dp) }
    val art = remember(d?.art) { d?.art?.asImageBitmap() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(128.dp, 72.dp).clip(shape).background(Cyber.bg).border(1.dp, Cyber.redDim, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (art != null) {
                if (d?.artIsBanner == true) {
                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Image(art, null, Modifier.size(44.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CyberText((d?.label ?: "--").uppercase(), size = 22.sp, color = Cyber.cyan, weight = FontWeight.Bold)
            MicroLabel(versionLabel(d), color = Cyber.red, size = 11.sp)
            MicroLabel(d?.origin ?: "--", size = 11.sp)
        }
    }
}

/** Manche Apps haben die Build-Nummer schon im Namen ("13.2.1 build 27207"), dann nicht doppelt. */
private fun versionLabel(d: AppDetails?): String {
    if (d == null) return "VERSION --"
    val name = d.versionName.ifBlank { "--" }
    return if (d.versionCode.toString() in name) "VERSION $name" else "VERSION $name // BUILD ${d.versionCode}"
}

/** 3 h 12 min, 45 min, < 1 min. */
private fun durationLabel(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "< 1 MIN"
        minutes < 60 -> "$minutes MIN"
        else -> "%d H %02d MIN".format(minutes / 60, minutes % 60)
    }
}
