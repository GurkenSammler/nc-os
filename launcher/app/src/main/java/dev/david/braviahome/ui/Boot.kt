package dev.david.braviahome.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.theme.Cyber

private const val SWEEP_END = 0.32f
private const val COVER_START = 0.30f
private const val COVER_LENGTH = 0.25f
private const val BOOT_TEXT = "INITIALIZING // SYS.MENU"

/**
 * Boot-Sequenz wie der Ladeuebergang im Spiel: schwarzer Bildschirm, eine Cyan-Linie
 * mit Pfeil faehrt durch die Mitte, darunter tippt sich ein Statustext, dann gibt das
 * Schwarz den Startbildschirm frei (die Kacheln bauen sich ueber bootReveal auf).
 */
@Composable
fun BootOverlay(progress: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val p = progress()
                if (p >= 1f) return@drawBehind
                val cover = 1f - ((p - COVER_START) / COVER_LENGTH).coerceIn(0f, 1f)
                drawRect(Cyber.bg.copy(alpha = cover))

                val sweep = (p / SWEEP_END).coerceIn(0f, 1f)
                val fade = 1f - ((p - SWEEP_END) / 0.12f).coerceIn(0f, 1f)
                if (fade > 0f) {
                    val y = size.height / 2
                    val tail = size.width * 0.12f
                    val head = tail + size.width * 0.76f * sweep
                    drawLine(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Cyber.cyan.copy(alpha = 0.9f * fade)),
                            startX = tail,
                            endX = head,
                        ),
                        Offset(tail, y),
                        Offset(head, y),
                        strokeWidth = 2.dp.toPx(),
                    )
                    val a = 8.dp.toPx()
                    drawPath(
                        Path().apply {
                            moveTo(head, y - a / 2); lineTo(head + a, y); lineTo(head, y + a / 2); close()
                        },
                        Cyber.cyan.copy(alpha = fade),
                    )
                    // kleine Markierungen hinter dem Pfeil, wie im Ladebildschirm
                    for (i in 1..3) {
                        val x = head - i * 26.dp.toPx()
                        if (x > tail) drawLine(Cyber.cyan.copy(alpha = 0.5f * fade), Offset(x, y - 4.dp.toPx()), Offset(x, y + 4.dp.toPx()), 1.5f)
                    }
                }
            },
    ) {
        BootText(progress, Modifier.align(Alignment.Center).offset(y = 20.dp))
    }
}

@Composable
private fun BootText(progress: () -> Float, modifier: Modifier) {
    val p = progress()
    if (p >= 0.42f) return
    val chars = (BOOT_TEXT.length * (p / 0.28f)).toInt().coerceIn(0, BOOT_TEXT.length)
    val cursor = if ((p * 40).toInt() % 2 == 0) "_" else " "
    CyberText(BOOT_TEXT.take(chars) + cursor, modifier, size = 12.sp, color = Cyber.red, spacing = 0.16f)
}
