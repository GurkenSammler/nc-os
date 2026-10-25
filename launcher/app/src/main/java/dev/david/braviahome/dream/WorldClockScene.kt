package dev.david.braviahome.dream

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import dev.david.braviahome.data.WeatherStore
import dev.david.braviahome.ui.Formats
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * WORLD CLOCK im Stil grosser Konzern-Displays: grosse Ziffern ueber blassen "88:88"-Geisterziffern,
 * HRS/MNS darunter, ab und zu ein Glitch. Der ganze Block wandert langsam (gegen Einbrennen).
 */
class WorldClockScene(private val fonts: DreamFonts, private val c: DreamColors) : DreamScene {

    private val timePaint = fonts.paint(fonts.bold, c.primary, 10f)
    private val ghostPaint = fonts.paint(fonts.bold, c.deep, 10f)
    private val labelPaint = fonts.paint(fonts.medium, c.primaryDim, 10f, 0.14f)
    private val headPaint = fonts.paint(fonts.bold, c.primary, 10f, 0.14f)
    private val linePaint = Paint().apply { color = c.primaryDim; strokeWidth = 2f }
    private val accentPaint = Paint().apply { color = c.accent }
    private val glowPaint = Paint()
    private val rnd = Random(SystemClock.uptimeMillis())

    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var w = 0f
    private var h = 0f
    private var nextGlitch = 0L
    private var glitchUntil = 0L

    override val frameMs: Long get() = if (SystemClock.uptimeMillis() < glitchUntil) 16L else 40L
    override val hasOwnClock = true

    override fun setup(w: Int, h: Int, density: Float) {
        this.w = w.toFloat()
        this.h = h.toFloat()
        timePaint.textSize = h * 0.24f
        ghostPaint.textSize = h * 0.24f
        labelPaint.textSize = 11f * density
        headPaint.textSize = 13f * density
        x = w * 0.2f
        y = h * 0.3f
        vx = density * 0.35f
        vy = density * 0.22f
        glowPaint.shader = RadialGradient(w * 0.08f, h * 1.05f, w * 0.75f, c.deep.withAlpha(140), 0, Shader.TileMode.CLAMP)
        nextGlitch = SystemClock.uptimeMillis() + 4_000
    }

    override fun draw(canvas: Canvas, nowMs: Long) {
        val now = SystemClock.uptimeMillis()
        canvas.drawColor(c.bg)
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        val time = Formats.time(nowMs)
        val blockW = digitsWidth()
        val blockH = timePaint.textSize * 1.45f

        x += vx; y += vy
        if (x < w * 0.04f || x + blockW > w * 0.96f) vx = -vx
        if (y < h * 0.06f || y + blockH > h * 0.94f) vy = -vy
        x = x.coerceIn(w * 0.04f, max(w * 0.04f, w * 0.96f - blockW))
        y = y.coerceIn(h * 0.06f, max(h * 0.06f, h * 0.94f - blockH))

        // Kopfzeile: "WORLD CLOCK" mit Linie und Quadrat
        val headY = y + headPaint.textSize
        canvas.drawRect(x, headY - headPaint.textSize * 0.6f, x + headPaint.textSize * 0.45f, headY - headPaint.textSize * 0.15f, accentPaint)
        canvas.drawText("WORLD CLOCK", x + headPaint.textSize, headY, headPaint)
        canvas.drawLine(x, headY + 8f, x + blockW, headY + 8f, linePaint)
        val weather = WeatherStore.latest
        val rightLabel = Formats.date(nowMs) + Formats.suffix(nowMs).let { if (it.isEmpty()) "" else "  $it" } +
            (weather?.let { "  //  ${it.tempC.roundToInt()}°C ${it.place}" } ?: "")
        canvas.drawText(rightLabel, x + blockW - labelPaint.measureText(rightLabel), headY, labelPaint)

        // Ziffern
        val baseline = headY + 16f + timePaint.textSize * 0.92f
        drawDigits(canvas, "88:88", x, baseline, ghostPaint)
        if (now < glitchUntil) {
            // Zeilenversatz: Streifen der Ziffern seitlich verschoben, Akzent-Geist
            val top = baseline - timePaint.textSize * 0.8f
            val bands = 6
            val bandH = timePaint.textSize * 0.85f / bands
            for (i in 0 until bands) {
                canvas.save()
                canvas.clipRect(x - 40f, top + i * bandH, x + blockW + 40f, top + (i + 1) * bandH)
                drawDigits(canvas, time, x + rnd.nextInt(-18, 19), baseline, timePaint)
                canvas.restore()
            }
            timePaint.color = c.accent
            timePaint.alpha = 110
            drawDigits(canvas, time, x + 6f, baseline, timePaint)
            timePaint.color = c.primary
        } else {
            drawDigits(canvas, time, x, baseline, timePaint)
        }
        if (now > nextGlitch) {
            glitchUntil = now + 160
            nextGlitch = now + 7_000 + rnd.nextLong(6_000)
        }

        // HRS / MNS unter den Stunden und Minuten, Sekunden als duenner Balken
        val labelY = baseline + labelPaint.textSize * 2.2f
        canvas.drawText("HRS", x + 4f, labelY, labelPaint)
        canvas.drawText("MNS", x + 2 * ghostPaint.measureText("8") + ghostPaint.measureText(":") + 4f, labelY, labelPaint)
        val sec = Calendar.getInstance().get(Calendar.SECOND) / 60f
        val barY = labelY + labelPaint.textSize
        canvas.drawLine(x, barY, x + blockW, barY, linePaint)
        canvas.drawRect(x, barY - 2f, x + blockW * sec, barY + 2f, accentPaint)
        canvas.drawText("SYS.IDLE // SCREENSAVER", x, barY + labelPaint.textSize * 2f, labelPaint)
    }

    /** Breite von "88:88" bei festen Ziffernfeldern. */
    private fun digitsWidth(): Float = 4 * ghostPaint.measureText("8") + ghostPaint.measureText(":")

    /**
     * Zeichnet "HH:mm" mit fester Feldbreite pro Ziffer (wie eine Digitalanzeige), damit die
     * Geisterziffern "88:88" genau unter der Uhrzeit liegen; Rajdhani hat schmale Einsen.
     */
    private fun drawDigits(canvas: Canvas, text: String, left: Float, baseline: Float, paint: Paint) {
        val cell = paint.measureText("8")
        val colon = paint.measureText(":")
        var cx = left
        for (ch in text) {
            val slot = if (ch == ':') colon else cell
            val glyph = ch.toString()
            canvas.drawText(glyph, cx + (slot - paint.measureText(glyph)) / 2, baseline, paint)
            cx += slot
        }
    }
}
