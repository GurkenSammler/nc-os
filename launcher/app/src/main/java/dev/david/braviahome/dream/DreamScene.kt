package dev.david.braviahome.dream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import dev.david.braviahome.R
import dev.david.braviahome.data.Live
import dev.david.braviahome.ui.theme.Palette
import dev.david.braviahome.ui.theme.Palettes

/** Farben des aktuellen Schemas als Android-Ints (die Schoner zeichnen ohne Compose). */
class DreamColors(p: Palette) {
    val primary = p.primary.toArgb()
    val primaryDim = p.primaryDim.toArgb()
    val deep = p.deep.toArgb()
    val alert = p.alert.toArgb()
    val accent = p.accent.toArgb()
    val accentDim = p.accentDim.toArgb()
    val special = p.special.toArgb()
    val bg = p.bg.toArgb()

    companion object {
        fun current() = DreamColors(Palettes.of(Live.settings.theme))
    }
}

/** Gemeinsame Schriften fuer alle Schoner (Rajdhani wie im Launcher). */
class DreamFonts(context: Context) {
    val bold: Typeface = ResourcesCompat.getFont(context, R.font.rajdhani_bold) ?: Typeface.DEFAULT_BOLD
    val semibold: Typeface = ResourcesCompat.getFont(context, R.font.rajdhani_semibold) ?: Typeface.DEFAULT_BOLD
    val medium: Typeface = ResourcesCompat.getFont(context, R.font.rajdhani_medium) ?: Typeface.DEFAULT

    fun paint(face: Typeface, color: Int, size: Float, spacing: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        this.color = color
        textSize = size
        letterSpacing = spacing
    }
}

/**
 * Eine Schoner-Szene. Zeichnet sich selbst komplett (inkl. Hintergrund) und sagt, wie schnell
 * sie neu gezeichnet werden will. Scanlines, Uhr-Overlay und Wechsel macht [DreamView].
 */
interface DreamScene {
    /** Bildgroesse bekannt oder geaendert; einmalige Vorberechnung hier. */
    fun setup(w: Int, h: Int, density: Float)

    fun draw(canvas: Canvas, nowMs: Long)

    /** Millisekunden bis zum naechsten Bild. */
    val frameMs: Long

    /** Zeigt die Szene selbst eine grosse Uhr? Dann kein Uhr-Overlay. */
    val hasOwnClock: Boolean get() = false

    fun release() {}
}

/** Hilfen fuer Farben mit Transparenz. */
fun Int.withAlpha(alpha: Int): Int = Color.argb(alpha.coerceIn(0, 255), Color.red(this), Color.green(this), Color.blue(this))
