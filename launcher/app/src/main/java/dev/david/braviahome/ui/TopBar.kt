package dev.david.braviahome.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.david.braviahome.data.SystemInfo
import dev.david.braviahome.data.Weather
import dev.david.braviahome.ui.cyber.Barcode
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.cyber.VerticalRule
import dev.david.braviahome.ui.theme.Cyber
import kotlin.math.roundToInt

enum class Section(val title: String) { APPS("APPS"), INPUTS("INPUTS"), SYSTEM("SYSTEM") }

/** Kopfzeile: Titel, Reiter (zeigen, wo der Fokus ist), Wetter, Uhr. */
@Composable
fun TopBar(
    title: String,
    tabs: List<String>,
    activeTab: Int,
    system: SystemInfo,
    weather: Weather?,
    modifier: Modifier = Modifier,
) {
    val now = rememberNow()

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            CyberText(title, size = 30.sp, color = Cyber.red, weight = FontWeight.Bold, spacing = 0.04f)
            MicroLabel("NC-OS ${system.launcherVersion} // ${system.model}")
        }
        Spacer(Modifier.width(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab -> TabLabel(tab, index == activeTab) }
        }
        Spacer(Modifier.weight(1f))
        Barcode(width = 56.dp, height = 12.dp)
        if (weather != null) {
            VerticalRule(30.dp)
            Column(horizontalAlignment = Alignment.End) {
                CyberText("${weather.tempC.roundToInt()}°C", size = 22.sp, color = Cyber.red, weight = FontWeight.Bold)
                MicroLabel("${weather.place} // ${weather.label}")
            }
        }
        VerticalRule(30.dp)
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                CyberText(Formats.time(now), size = 30.sp, color = Cyber.red, weight = FontWeight.Bold, spacing = 0.02f)
                val suffix = Formats.suffix(now)
                if (suffix.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    CyberText(suffix, Modifier.padding(bottom = 5.dp), size = 13.sp, color = Cyber.redDim, weight = FontWeight.Bold)
                }
            }
            MicroLabel(Formats.date(now))
        }
    }
}

@Composable
private fun TabLabel(title: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active) {
                Box(Modifier.size(5.dp).background(Cyber.cyan))
                Spacer(Modifier.width(6.dp))
            }
            CyberText(title, size = 14.sp, color = if (active) Cyber.cyan else Cyber.redDim, weight = FontWeight.Bold, spacing = 0.16f)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .width(if (active) 44.dp else 0.dp)
                .height(2.dp)
                .background(Cyber.cyan),
        )
    }
}
