package dev.david.braviahome.dream

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

/**
 * NIGHT CITY: selbst gezeichnete Skyline in drei Ebenen (einmalig in Bitmaps vorgezeichnet),
 * flackernde Fenster, Neon-Schilder, Regen und langsames Parallax-Gleiten.
 * Alle Formen und Texte sind eigene; keine Spielgrafiken.
 */
class NightCityScene(private val fonts: DreamFonts, private val c: DreamColors) : DreamScene {

    private class Layer(val bitmap: Bitmap, val speed: Float, val y: Float)
    private class Window(val layer: Int, val x: Float, val y: Float, val w: Float, val h: Float, var on: Boolean)
    private class Sign(val layer: Int, val x: Float, val y: Float, val text: String, val color: Int, val vertical: Boolean)
    private class Drop(var x: Float, var y: Float, val len: Float, val speed: Float)

    private val rnd = Random(77)
    private val layers = ArrayList<Layer>()
    private val windows = ArrayList<Window>()
    private val signs = ArrayList<Sign>()
    private val rain = ArrayList<Drop>()

    private val skyPaint = Paint()
    private val hazePaint = Paint()
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowPaint = Paint()
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f }
    private val signPaint = fonts.paint(fonts.bold, c.accent, 10f, 0.12f)
    private val signGlow = fonts.paint(fonts.bold, c.accent, 10f, 0.12f)

    private var w = 0f
    private var h = 0f
    private var offset = 0f
    private var density = 1f

    override val frameMs = 40L

    override fun setup(w: Int, h: Int, density: Float) {
        this.w = w.toFloat()
        this.h = h.toFloat()
        this.density = density
        release()
        layers.clear(); windows.clear(); signs.clear(); rain.clear()

        skyPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(c.bg, c.deep, c.bg), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
        hazePaint.shader = LinearGradient(0f, h * 0.55f, 0f, h.toFloat(), 0, c.primaryDim.withAlpha(70), Shader.TileMode.CLAMP)
        moonPaint.shader = RadialGradient(w * 0.78f, h * 0.2f, h * 0.18f, intArrayOf(c.primary.withAlpha(90), c.primary.withAlpha(25), 0), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)

        // Hinten: hoch und blass, Mitte: dunkler, vorne: fast schwarz mit Neon.
        val specs = listOf(
            Triple(0.55f, c.deep, 0.15f),
            Triple(0.40f, mix(c.bg, c.deep, 0.45f), 0.35f),
            Triple(0.28f, c.bg, 0.7f),
        )
        specs.forEachIndexed { i, (heightShare, color, speed) ->
            val tileW = w * 2
            val bmp = Bitmap.createBitmap(tileW, h, Bitmap.Config.ARGB_8888)
            drawSkyline(Canvas(bmp), i, tileW.toFloat(), h.toFloat(), heightShare, color)
            layers += Layer(bmp, speed, 0f)
        }
        signPaint.textSize = 15f * density
        signGlow.textSize = 15f * density
        signGlow.maskFilter = android.graphics.BlurMaskFilter(6f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)

        repeat(140) {
            rain += Drop(rnd.nextFloat() * w, rnd.nextFloat() * h, (10 + rnd.nextInt(16)) * density, (9 + rnd.nextFloat() * 7) * density)
        }
    }

    /** Zeichnet eine Reihe Hochhaeuser in [canvas] und merkt sich Fenster und Schilder. */
    private fun drawSkyline(canvas: Canvas, layer: Int, tileW: Float, h: Float, heightShare: Float, color: Int) {
        val body = Paint().apply { this.color = color }
        val edge = Paint().apply { this.color = c.primaryDim.withAlpha(if (layer == 2) 140 else 60); strokeWidth = 1.5f }
        var x = 0f
        while (x < tileW) {
            val bw = (40 + rnd.nextInt(110)) * density * (1 + layer * 0.25f)
            val bh = h * heightShare * (0.45f + rnd.nextFloat() * 0.75f)
            val top = h - bh
            canvas.drawRect(x, top, x + bw, h, body)
            canvas.drawLine(x, top, x + bw, top, edge)
            // Dachaufbauten: Antenne oder Stufe
            if (rnd.nextInt(3) == 0) canvas.drawRect(x + bw * 0.4f, top - bh * 0.12f, x + bw * 0.46f, top, body)
            if (rnd.nextInt(4) == 0) canvas.drawRect(x + bw * 0.15f, top - bh * 0.05f, x + bw * 0.7f, top, body)

            // Fenster nur auf Mitte und vorne, als einzelne Rechtecke zum Flackern
            if (layer >= 1) {
                val ww = 3f * density
                val wh = 2f * density
                var wy = top + 10f * density
                while (wy < h - 8f * density) {
                    var wx = x + 6f * density
                    while (wx < x + bw - 6f * density) {
                        if (rnd.nextInt(7) == 0) windows += Window(layer, wx, wy, ww, wh, rnd.nextBoolean())
                        wx += ww * 3
                    }
                    wy += wh * 4
                }
            }
            // Neon-Schilder auf der vordersten Ebene
            if (layer == 2 && rnd.nextInt(3) == 0 && bh > h * 0.12f) {
                val text = SIGNS[rnd.nextInt(SIGNS.size)]
                val vertical = rnd.nextBoolean()
                val colorPick = listOf(c.accent, c.primary, c.special)[rnd.nextInt(3)]
                signs += Sign(layer, x + bw * 0.5f, top + bh * 0.2f, text, colorPick, vertical)
            }
            x += bw + rnd.nextInt(4, 18) * density
        }
    }

    override fun draw(canvas: Canvas, nowMs: Long) {
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        canvas.drawRect(0f, 0f, w, h, moonPaint)
        offset += 0.6f * density

        layers.forEachIndexed { i, layer ->
            val tileW = layer.bitmap.width.toFloat()
            val shift = (offset * layer.speed) % tileW
            canvas.drawBitmap(layer.bitmap, -shift, 0f, null)
            canvas.drawBitmap(layer.bitmap, tileW - shift, 0f, null)

            // Fenster dieser Ebene, an derselben Stelle wie die Gebaeude verschoben
            windowPaint.color = if (i == 2) c.special.withAlpha(200) else c.primary.withAlpha(120)
            for (win in windows) {
                if (win.layer != i) continue
                if (rnd.nextInt(900) == 0) win.on = !win.on
                if (!win.on) continue
                for (rep in 0..1) {
                    val wx = win.x - shift + rep * tileW
                    if (wx < -10 || wx > w + 10) continue
                    canvas.drawRect(wx, win.y, wx + win.w, win.y + win.h, windowPaint)
                }
            }
            if (i == 2) drawSigns(canvas, shift, tileW, nowMs)
        }
        canvas.drawRect(0f, 0f, w, h, hazePaint)
        drawRain(canvas)
    }

    private fun drawSigns(canvas: Canvas, shift: Float, tileW: Float, nowMs: Long) {
        for ((index, s) in signs.withIndex()) {
            // Einzelne Schilder flackern kurz
            val flicker = ((nowMs / 90 + index * 7) % 47).toInt() == 0
            if (flicker) continue
            for (rep in 0..1) {
                val sx = s.x - shift + rep * tileW
                if (sx < -200 || sx > w + 200) continue
                signPaint.color = s.color
                signGlow.color = s.color.withAlpha(150)
                if (s.vertical) {
                    var sy = s.y
                    for (ch in s.text) {
                        canvas.drawText(ch.toString(), sx, sy, signGlow)
                        canvas.drawText(ch.toString(), sx, sy, signPaint)
                        sy += signPaint.textSize * 1.05f
                    }
                } else {
                    canvas.drawText(s.text, sx, s.y, signGlow)
                    canvas.drawText(s.text, sx, s.y, signPaint)
                }
            }
        }
    }

    private fun drawRain(canvas: Canvas) {
        rainPaint.color = c.accent.withAlpha(70)
        for (d in rain) {
            d.y += d.speed
            d.x -= d.speed * 0.18f
            if (d.y > h) {
                d.y = -d.len
                d.x = rnd.nextFloat() * (w + 100)
            }
            canvas.drawLine(d.x, d.y, d.x - d.len * 0.18f, d.y + d.len, rainPaint)
        }
    }

    override fun release() {
        layers.forEach { it.bitmap.recycle() }
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private companion object {
        // Eigene Fantasie-Schilder (keine echten Marken, keine Spielnamen).
        val SIGNS = listOf("NOODLES", "24/7", "HOTEL", "RAMEN", "OPEN", "NC-OS", "BAR", "ARCADE", "DATA", "SUSHI", "NEON")
    }
}
