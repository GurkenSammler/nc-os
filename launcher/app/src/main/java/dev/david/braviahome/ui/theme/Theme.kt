package dev.david.braviahome.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import dev.david.braviahome.R
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.ThemeName
import dev.david.braviahome.data.TileSize

/** Ein Farbschema. Rollen statt Farben, damit jedes Schema ueberall passt. */
data class Palette(
    val primary: Color,     // Text, Linien, Rahmen (in RED: lachsrot)
    val primaryDim: Color,  // gedimmte Linien, kleine Labels
    val deep: Color,        // sehr dunkle Flaechen in der Hauptfarbe
    val alert: Color,       // Warnung, Standby
    val accent: Color,      // Auswahl/Fokus (in RED: cyan)
    val accentDim: Color,
    val special: Color,     // Verschiebe-Modus, Sleep-Timer
    val bg: Color,
    val panel: Color,
)

/** Eigene Schemata nach den Spielmenues; Namen bewusst ohne Marken. */
object Palettes {
    val RED = Palette(
        primary = Color(0xFFE8604F), primaryDim = Color(0xFF7A2A28), deep = Color(0xFF2A0E10),
        alert = Color(0xFFF83840), accent = Color(0xFF5EF0FF), accentDim = Color(0xFF1F5A63),
        special = Color(0xFFF8B030), bg = Color(0xFF05080D), panel = Color(0xFF0B1017),
    )
    val AMBER = Palette(
        primary = Color(0xFFFCEE0A), primaryDim = Color(0xFF6E6810), deep = Color(0xFF24220A),
        alert = Color(0xFFFF3B3B), accent = Color(0xFF02D7F2), accentDim = Color(0xFF0E5560),
        special = Color(0xFFFF9F1C), bg = Color(0xFF07070A), panel = Color(0xFF101012),
    )
    val ACID = Palette(
        primary = Color(0xFF3DFF9A), primaryDim = Color(0xFF1C6B45), deep = Color(0xFF0A2416),
        alert = Color(0xFFFF2E63), accent = Color(0xFFFF4FD8), accentDim = Color(0xFF6B1F5C),
        special = Color(0xFFE8FF5A), bg = Color(0xFF04090A), panel = Color(0xFF08110F),
    )
    val VIOLET = Palette(
        primary = Color(0xFFC77DFF), primaryDim = Color(0xFF55307A), deep = Color(0xFF1D0F2A),
        alert = Color(0xFFFF3D6E), accent = Color(0xFF5EF0FF), accentDim = Color(0xFF1F5A63),
        special = Color(0xFFFFC857), bg = Color(0xFF07050D), panel = Color(0xFF100B18),
    )
    val ICE = Palette(
        primary = Color(0xFF5EF0FF), primaryDim = Color(0xFF1F5A63), deep = Color(0xFF0A2226),
        alert = Color(0xFFF83840), accent = Color(0xFFE8604F), accentDim = Color(0xFF7A2A28),
        special = Color(0xFFF8B030), bg = Color(0xFF04080C), panel = Color(0xFF0A1016),
    )

    fun of(name: ThemeName): Palette = when (name) {
        ThemeName.RED -> RED
        ThemeName.AMBER -> AMBER
        ThemeName.ACID -> ACID
        ThemeName.VIOLET -> VIOLET
        ThemeName.ICE -> ICE
    }
}

/**
 * Cyberpunk-Look nach den Spielmenues. Die Farben folgen dem gewaehlten Schema (SYS.CONFIG > LOOK);
 * die alten Namen bleiben als Rollen: "red" = Hauptfarbe, "cyan" = Auswahlfarbe.
 * Alles wird aus [Live] gelesen, Compose zeichnet bei Aenderungen von selbst neu.
 */
object Cyber {
    val palette: Palette get() = Palettes.of(Live.settings.theme)

    val bg get() = palette.bg
    val panel get() = palette.panel
    val red get() = palette.primary
    val redDim get() = palette.primaryDim
    val redDeep get() = palette.deep
    val redAlert get() = palette.alert
    val cyan get() = palette.accent
    val cyanDim get() = palette.accentDim
    val gold get() = palette.special
    val green = Color(0xFF2BE38A)
    /** Dunkle Schrift auf gefuellten Auswahlflaechen. */
    val ink get() = palette.bg

    private val tileScale: Float
        get() = when (Live.settings.tileSize) {
            TileSize.S -> 0.82f
            TileSize.M -> 1f
            TileSize.L -> 1.2f
        }

    val padH = 40.dp
    val padV = 20.dp
    val tileW: Dp get() = 204.dp * tileScale
    val tileH: Dp get() = 115.dp * tileScale
    val tileCut = 14.dp
    val chipW = 130.dp
    val chipH = 54.dp
    val chipCut = 10.dp
    val gap = 18.dp
    const val focusScale = 1.05f
}

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

/** Schriftstil: Rajdhani, Labels in Grossbuchstaben mit Sperrung wie im Spiel. */
fun cyberStyle(
    size: TextUnit,
    color: Color = Cyber.red,
    weight: FontWeight = FontWeight.SemiBold,
    spacing: Float = 0.06f,
) = TextStyle(
    fontFamily = Rajdhani,
    fontWeight = weight,
    fontSize = size,
    color = color,
    letterSpacing = spacing.em,
)

@Composable
fun BraviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyber.cyan,
            background = Cyber.bg,
            surface = Cyber.panel,
            onSurface = Cyber.red,
        ),
        content = content,
    )
}
