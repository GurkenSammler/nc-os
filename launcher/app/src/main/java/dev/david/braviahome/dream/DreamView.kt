package dev.david.braviahome.dream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.SaverStyle
import dev.david.braviahome.data.ScanlineLevel
import dev.david.braviahome.data.WeatherStore
import dev.david.braviahome.ui.Formats
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Rahmen fuer alle Bildschirmschoner: waehlt die Szene nach SYS.CONFIG > SCREENSAVER, legt
 * Scanlines und (optional) eine kleine Uhr darueber und wechselt bei SHUFFLE mit Glitch.
 * Bei VIDEO LOOP laeuft das Video in [video] darunter, diese View bleibt dann durchsichtig.
 */
class DreamView(context: Context, private val video: LoopPlayer) : View(context) {

    private val fonts = DreamFonts(context)
    private val colors = DreamColors.current()
    private val settings = Live.settings
    private val density = resources.displayMetrics.density
    private val rnd = Random(SystemClock.uptimeMillis())

    private val clips = LoopClips.list(context)
    private val fixedClip = clips.indexOfFirst { it.name == settings.saverClip }
    private var clipIndex = if (fixedClip >= 0 || clips.isEmpty()) fixedClip.coerceAtLeast(0) else rnd.nextInt(clips.size)
    /** VIDEO LOOP mit ALL: Clips wechseln im Takt von "Change Every". */
    private val rotateClips = settings.saverStyle == SaverStyle.VIDEO_LOOP && fixedClip < 0 && clips.size > 1

    // VIDEO LOOP nimmt nur teil, wenn Clips auf dem TV liegen.
    private val styles = SaverStyle.entries.filter { it != SaverStyle.SHUFFLE && (it != SaverStyle.VIDEO_LOOP || clips.isNotEmpty()) }
    private var style = if (settings.saverStyle == SaverStyle.SHUFFLE) styles[rnd.nextInt(styles.size)] else settings.saverStyle
    private var scene: DreamScene = create(style)
    private var nextSwitch = SystemClock.uptimeMillis() + settings.shuffleMinutes * 60_000L
    private var glitchUntil = 0L

    private val scanPaint = Paint()
    private val clockPaint = fonts.paint(fonts.bold, colors.primary, 34f * density, 0.02f)
    private val clockSub = fonts.paint(fonts.medium, colors.primaryDim, 11f * density, 0.14f)
    private val clockBox = Paint().apply { color = colors.bg.withAlpha(170) }
    private val clockEdge = Paint().apply { color = colors.accent }
    private val glitchPaint = Paint()

    private var clockCorner = 0

    private fun create(style: SaverStyle): DreamScene = when (style) {
        SaverStyle.CODE_RAIN -> CodeRainScene(fonts, colors)
        SaverStyle.NIGHT_CITY -> NightCityScene(fonts, colors)
        SaverStyle.BREACH_GRID -> BreachGridScene(fonts, colors)
        SaverStyle.VIDEO_LOOP ->
            if (clips.isEmpty()) WorldClockScene(fonts, colors)
            else VideoLoopScene(video, clips[clipIndex], settings.saverClipSound)
        else -> WorldClockScene(fonts, colors)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scene.setup(w, h, density)
        val strength = when (settings.scanlines) {
            ScanlineLevel.OFF -> 0
            ScanlineLevel.LOW -> 40
            ScanlineLevel.NORMAL -> 70
            ScanlineLevel.HIGH -> 110
        }
        if (strength > 0) {
            val pattern = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888).apply { setPixel(0, 2, Color.argb(strength, 0, 0, 0)) }
            scanPaint.shader = BitmapShader(pattern, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val wall = System.currentTimeMillis()

        // Wechsel mit kurzem Glitch davor: SHUFFLE wechselt die Szene, VIDEO LOOP (ALL) den Clip.
        if ((settings.saverStyle == SaverStyle.SHUFFLE || rotateClips) && now > nextSwitch) {
            glitchUntil = now + 450
            nextSwitch = now + settings.shuffleMinutes * 60_000L
            if (clips.isNotEmpty()) clipIndex = (clipIndex + 1) % clips.size
            val current = scene
            if (rotateClips && current is VideoLoopScene) {
                current.show(clips[clipIndex])
            } else {
                val next = (styles - style).let { it[rnd.nextInt(it.size)] }
                scene.release()
                style = next
                scene = create(next).also { it.setup(width, height, density) }
            }
            clockCorner = (clockCorner + 1) % 4
        }

        scene.draw(canvas, wall)
        if (settings.saverClock && !scene.hasOwnClock) drawClock(canvas, wall)
        if (now < glitchUntil) drawGlitch(canvas)
        if (scanPaint.shader != null) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scanPaint)

        postInvalidateDelayed(if (now < glitchUntil) 16L else scene.frameMs)
    }

    /** Kleine Uhr in einer Ecke; die Ecke wechselt beim Szenenwechsel (gegen Einbrennen). */
    private fun drawClock(canvas: Canvas, wall: Long) {
        val time = Formats.time(wall)
        val weather = WeatherStore.latest
        val sub = Formats.date(wall) + (weather?.let { "  //  ${it.tempC.roundToInt()}°C" } ?: "")
        val pad = 14f * density
        val bw = maxOf(clockPaint.measureText(time), clockSub.measureText(sub)) + pad * 2
        val bh = clockPaint.textSize + clockSub.textSize + pad * 2
        val m = 36f * density
        val x = if (clockCorner % 2 == 0) width - bw - m else m
        val y = if (clockCorner < 2) height - bh - m else m
        canvas.drawRect(x, y, x + bw, y + bh, clockBox)
        canvas.drawRect(x, y, x + 4f * density, y + bh, clockEdge)
        canvas.drawText(time, x + pad, y + pad + clockPaint.textSize * 0.82f, clockPaint)
        canvas.drawText(sub, x + pad, y + bh - pad * 0.8f, clockSub)
    }

    /** Uebergang: Bildstreifen versetzt, mit Farbsaum (wie der Glitch im Launcher). */
    private fun drawGlitch(canvas: Canvas) {
        repeat(6) {
            val bandH = height * (0.03f + rnd.nextFloat() * 0.1f)
            val top = rnd.nextFloat() * (height - bandH)
            glitchPaint.color = (if (rnd.nextBoolean()) colors.accent else colors.alert).withAlpha(90 + rnd.nextInt(120))
            val dx = rnd.nextInt(-80, 81) * density
            canvas.drawRect(dx, top, width + dx, top + bandH, glitchPaint)
        }
    }

    override fun onDetachedFromWindow() {
        scene.release()
        super.onDetachedFromWindow()
    }
}
