package dev.david.braviahome.ui.cyber

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.david.braviahome.data.BackgroundStyle
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.ScanlineLevel
import dev.david.braviahome.ui.theme.Cyber
import dev.david.braviahome.ui.theme.cyberStyle
import kotlin.random.Random

@Composable
fun CyberText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = Cyber.red,
    weight: FontWeight = FontWeight.SemiBold,
    spacing: Float = 0.06f,
    maxLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = cyberStyle(size, color, weight, spacing),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Winzige Technik-Beschriftung wie "SW VERSION 13-450C". */
@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier, color: Color = Cyber.redDim, size: TextUnit = 9.sp) {
    CyberText(text.uppercase(), modifier, size = size, color = color, weight = FontWeight.Medium, spacing = 0.12f)
}

/** Ueberschrift einer Reihe: Quadrat, "[01] APPS", rechts ein Status. */
@Composable
fun SectionHeader(index: Int, title: String, status: String, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) Cyber.cyan else Cyber.red
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color))
        Spacer(Modifier.width(8.dp))
        CyberText("[%02d] %s".format(index, title), size = 13.sp, color = color, weight = FontWeight.Bold, spacing = 0.14f)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Cyber.redDim.copy(alpha = 0.5f)))
        Spacer(Modifier.width(12.dp))
        MicroLabel(status, color = if (active) Cyber.cyanDim else Cyber.redDim)
    }
}

/** Tasten-Hinweis wie im Spiel: Kreis mit Tastenname, daneben die Aktion. */
@Composable
fun KeyHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).border(1.dp, Cyber.cyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CyberText(key, size = 8.sp, color = Cyber.cyan, weight = FontWeight.Bold, spacing = 0f)
        }
        Spacer(Modifier.width(6.dp))
        CyberText(action.uppercase(), size = 11.sp, color = Cyber.red, weight = FontWeight.SemiBold, spacing = 0.1f)
    }
}

@Composable
fun KeyHints(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (key, action) -> KeyHint(key, action) }
    }
}

/**
 * Hintergrund: fast schwarz, dunkelroter Schimmer unten links, Punkteraster,
 * ein paar Leiterbahnen und Eckmarken. Alles statisch und nur einmal berechnet.
 */
@Composable
fun CyberBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val step = 24.dp.toPx()
                val w = size.width
                val h = size.height
                val dots = buildList {
                    var y = step / 2
                    while (y < h) {
                        var x = step / 2
                        while (x < w) { add(Offset(x, y)); x += step }
                        y += step
                    }
                }
                val traces = circuitTraces(size.width, size.height, Random(2077))
                // Schimmer in den Farben des gewaehlten Schemas (Cache wird bei Wechsel neu gebaut).
                val glow = Brush.radialGradient(
                    colors = listOf(Cyber.redDeep.copy(alpha = 0.6f), Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 1.05f),
                    radius = size.width * 0.75f,
                )
                val coolGlow = Brush.radialGradient(
                    colors = listOf(Cyber.cyanDim.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.95f, 0f),
                    radius = size.width * 0.5f,
                )
                val style = Live.settings.background
                val tick = 18.dp.toPx()
                val inset = 14.dp.toPx()
                onDrawBehind {
                    drawRect(Cyber.bg)
                    drawRect(glow)
                    drawRect(coolGlow)
                    if (style != BackgroundStyle.PLAIN) {
                        drawPoints(dots, PointMode.Points, Cyber.red.copy(alpha = 0.12f), strokeWidth = 2f)
                    }
                    if (style == BackgroundStyle.CIRCUIT) {
                        traces.forEach { drawPath(it, Cyber.red.copy(alpha = 0.08f), style = Stroke(width = 1.5f)) }
                    }
                    val c = Cyber.redDim
                    // Eckmarken
                    drawLine(c, Offset(inset, inset), Offset(inset + tick, inset), 2f)
                    drawLine(c, Offset(inset, inset), Offset(inset, inset + tick), 2f)
                    drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset - tick, inset), 2f)
                    drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset, inset + tick), 2f)
                    drawLine(c, Offset(inset, size.height - inset), Offset(inset + tick, size.height - inset), 2f)
                    drawLine(c, Offset(inset, size.height - inset), Offset(inset, size.height - inset - tick), 2f)
                    drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - tick, size.height - inset), 2f)
                    drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - tick), 2f)
                }
            },
    )
}

/** Leiterbahnen mit 45-Grad-Knicken, wie im Attribut-Screen. */
private fun circuitTraces(w: Float, h: Float, rnd: Random): List<Path> = List(9) {
    Path().apply {
        var x = rnd.nextFloat() * w
        var y = if (rnd.nextBoolean()) h * (0.55f + rnd.nextFloat() * 0.45f) else rnd.nextFloat() * h * 0.2f
        moveTo(x, y)
        repeat(4) {
            val len = w * (0.05f + rnd.nextFloat() * 0.12f)
            when (rnd.nextInt(3)) {
                0 -> x += len
                1 -> { x += len * 0.7f; y += if (y > h / 2) -len * 0.7f else len * 0.7f }
                else -> y += if (y > h / 2) -len * 0.5f else len * 0.5f
            }
            lineTo(x, y)
        }
        addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), 4f))
    }
}

/** Scanlines ueber allem, dazu ein heller Streifen, der langsam nach unten wandert. Staerke aus SYS.CONFIG > LOOK. */
@Composable
fun Scanlines(modifier: Modifier = Modifier) {
    val strength = when (Live.settings.scanlines) {
        ScanlineLevel.OFF -> return
        ScanlineLevel.LOW -> 0.5f
        ScanlineLevel.NORMAL -> 1f
        ScanlineLevel.HIGH -> 1.8f
    }
    // Als State lesen und erst beim Zeichnen auswerten: so wird nur neu gezeichnet, nicht neu komponiert.
    val roll = rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "roll",
    )
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val pattern = ImageBitmap(1, 3).also { bmp ->
                    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
                    canvas.drawRect(0f, 2f, 1f, 3f, androidx.compose.ui.graphics.Paint().apply { color = Color.Black.copy(alpha = (0.18f * strength).coerceAtMost(0.5f)) })
                }
                val brush = ShaderBrush(ImageShader(pattern, TileMode.Repeated, TileMode.Repeated))
                onDrawWithContent {
                    drawContent()
                    drawRect(brush)
                    val bandH = size.height * 0.18f
                    val top = size.height * roll.value - bandH / 2
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.04f * strength), Color.Transparent),
                            startY = top,
                            endY = top + bandH,
                        ),
                    )
                }
            },
    )
}

/** Trennlinie unter der Kopfzeile mit erhoehtem Mittelstueck (wie im Microtech-Screen). */
@Composable
fun HeaderLine(modifier: Modifier = Modifier, color: Color = Cyber.redDim) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val y = size.height - 1f
        val rise = 6.dp.toPx()
        val a = size.width * 0.28f
        val b = size.width * 0.58f
        val path = Path().apply {
            moveTo(0f, y); lineTo(a, y); lineTo(a + rise, y - rise); lineTo(b - rise, y - rise)
            lineTo(b, y); lineTo(size.width, y)
        }
        drawPath(path, color, style = Stroke(width = 2f))
        // Schraffur am rechten Ende
        val hatchW = 90.dp.toPx()
        val start = size.width - hatchW
        var x = start
        while (x < size.width) {
            drawLine(color, Offset(x, y - rise + 1f), Offset(x + rise, y - 1f), 2f)
            x += 7f
        }
    }
}

/** Barcode-Streifen als Deko (feste Zufallsbreiten). */
@Composable
fun Barcode(modifier: Modifier = Modifier, width: Dp = 64.dp, height: Dp = 14.dp, color: Color = Cyber.redDim) {
    Canvas(modifier.size(width, height)) {
        val rnd = Random(77)
        var x = 0f
        while (x < size.width) {
            val w = 1f + rnd.nextInt(4)
            drawRect(color, Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
            x += w + 1f + rnd.nextInt(3)
        }
    }
}

/**
 * Blendet ein Element waehrend der Boot-Sequenz flackernd ein. [progress] wird erst beim
 * Zeichnen gelesen, damit die Animation nicht den ganzen Bildschirm neu aufbaut.
 */
fun Modifier.bootReveal(progress: () -> Float, start: Float, length: Float = 0.18f): Modifier = graphicsLayer {
    // Spaete Elemente (start + length > 1) muessen am Ende trotzdem ganz sichtbar sein.
    val local = ((progress() - start) / length.coerceAtMost(1f - start)).coerceIn(0f, 1f)
    val flicker = if (local in 0.01f..0.99f && ((local * 23f).toInt() % 3 == 0)) 0.35f else 1f
    alpha = local * flicker
    translationX = (1f - local) * -14.dp.toPx()
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

@Composable
fun VerticalRule(height: Dp, color: Color = Cyber.redDim) {
    Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(height).background(color))
}
