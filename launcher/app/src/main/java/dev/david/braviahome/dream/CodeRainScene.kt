package dev.david.braviahome.dream

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.random.Random

/**
 * CODE RAIN: Spalten fallender Hex-Paare (4E, A9, FF ...). Der Kopf jeder Spur leuchtet in der
 * Akzentfarbe, die Spur verblasst in der Hauptfarbe. Ab und zu stoppt eine Zeile und blitzt als
 * "BREACH"-Treffer auf. Wenige Zeichen pro Bild, damit der TV-Prozessor entspannt bleibt.
 */
class CodeRainScene(private val fonts: DreamFonts, private val c: DreamColors) : DreamScene {

    private class Drop(var col: Int, var head: Float, var speed: Float, var length: Int, val cells: Array<String>)

    private val rnd = Random(2077)
    private val glyphPaint = fonts.paint(fonts.semibold, c.primary, 10f, 0.04f)
    private val headPaint = fonts.paint(fonts.bold, c.accent, 10f, 0.04f)
    private val breachPaint = fonts.paint(fonts.bold, c.bg, 10f, 0.2f)
    private val breachBox = Paint().apply { color = c.accent }
    private val breachLabel = fonts.paint(fonts.bold, c.accent, 10f, 0.2f)

    private var cellW = 0f
    private var cellH = 0f
    private var cols = 0
    private var rows = 0
    private var w = 0f
    private var h = 0f
    private val drops = ArrayList<Drop>()

    private var breachText = ""
    private var breachX = 0f
    private var breachY = 0f
    private var breachUntil = 0L
    private var nextBreach = 0L

    override val frameMs = 50L

    override fun setup(w: Int, h: Int, density: Float) {
        this.w = w.toFloat()
        this.h = h.toFloat()
        val size = 18f * density
        glyphPaint.textSize = size
        headPaint.textSize = size
        breachPaint.textSize = size * 1.4f
        breachLabel.textSize = size * 0.7f
        cellW = glyphPaint.measureText("FF") + size * 0.9f
        cellH = size * 1.25f
        cols = (w / cellW).toInt().coerceAtLeast(1)
        rows = (h / cellH).toInt() + 2
        drops.clear()
        // Genau eine Spur pro Spalte (sonst ueberlagern sich Zeichen); versetzt gestartet.
        repeat(cols) { col -> drops += newDrop(col, startAbove = false) }
        nextBreach = System.currentTimeMillis() + 5_000
    }

    private fun newDrop(col: Int, startAbove: Boolean) = Drop(
        col = col,
        head = if (startAbove) -rnd.nextInt(2, rows / 2 + 3).toFloat() else rnd.nextInt(-rows, rows).toFloat(),
        speed = 0.18f + rnd.nextFloat() * 0.35f,
        length = rnd.nextInt(8, 22),
        cells = Array(rows + 20) { hex() },
    )

    private fun hex() = "%02X".format(rnd.nextInt(256))

    override fun draw(canvas: Canvas, nowMs: Long) {
        canvas.drawColor(c.bg)

        for (i in drops.indices) {
            val d = drops[i]
            d.head += d.speed
            // Gelegentlich wechselt ein Zeichen in der Spur (wie flackernder Code).
            if (rnd.nextInt(6) == 0) d.cells[rnd.nextInt(d.cells.size)] = hex()
            val headRow = d.head.toInt()
            val x = d.col * cellW + cellW * 0.15f
            for (k in 0 until d.length) {
                val row = headRow - k
                if (row < 0 || row >= rows) continue
                val y = (row + 1) * cellH
                val text = d.cells[row % d.cells.size]
                if (k == 0) {
                    canvas.drawText(text, x, y, headPaint)
                } else {
                    glyphPaint.alpha = (230 * (1f - k / d.length.toFloat())).toInt().coerceIn(18, 230)
                    canvas.drawText(text, x, y, glyphPaint)
                }
            }
            if (headRow - d.length > rows) drops[i] = newDrop(d.col, startAbove = true)
        }

        // BREACH-Treffer: ein Wort aus Hex-Code blitzt in einer gefuellten Box auf.
        if (nowMs > nextBreach) {
            breachText = BREACH_WORDS[rnd.nextInt(BREACH_WORDS.size)] + " " + hex() + hex()
            breachX = rnd.nextFloat() * (w * 0.6f) + w * 0.1f
            breachY = rnd.nextFloat() * (h * 0.6f) + h * 0.2f
            breachUntil = nowMs + 900
            nextBreach = nowMs + 6_000 + rnd.nextLong(6_000)
        }
        if (nowMs < breachUntil) {
            val tw = breachPaint.measureText(breachText)
            val pad = breachPaint.textSize * 0.4f
            // Flackern am Anfang und Ende des Treffers
            val left = breachUntil - nowMs
            if (left > 820 || left < 120 || (left / 60) % 2 == 0L) {
                canvas.drawRect(breachX - pad, breachY - breachPaint.textSize, breachX + tw + pad, breachY + pad * 0.6f, breachBox)
                canvas.drawText(breachText, breachX, breachY, breachPaint)
                canvas.drawText("// BREACH PROTOCOL", breachX - pad, breachY - breachPaint.textSize - pad * 0.6f, breachLabel)
            }
        }
    }

    private companion object {
        val BREACH_WORDS = listOf("BREACH", "ACCESS", "DAEMON", "UPLOAD", "ICEBREAK", "NODE")
    }
}
