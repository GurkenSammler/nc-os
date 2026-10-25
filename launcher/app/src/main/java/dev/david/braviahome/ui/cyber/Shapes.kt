package dev.david.braviahome.ui.cyber

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

enum class Corner { TopStart, TopEnd, BottomEnd, BottomStart }

/** Rechteck mit abgeschraegten Ecken – die Grundform aller Boxen im Spiel. */
class ChamferShape(
    private val cut: Dp,
    private val corners: Set<Corner> = setOf(Corner.BottomEnd),
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }
        fun at(corner: Corner) = if (corner in corners) c else 0f
        val path = Path().apply {
            moveTo(at(Corner.TopStart), 0f)
            lineTo(size.width - at(Corner.TopEnd), 0f)
            lineTo(size.width, at(Corner.TopEnd))
            lineTo(size.width, size.height - at(Corner.BottomEnd))
            lineTo(size.width - at(Corner.BottomEnd), size.height)
            lineTo(at(Corner.BottomStart), size.height)
            lineTo(0f, size.height - at(Corner.BottomStart))
            lineTo(0f, at(Corner.TopStart))
            close()
        }
        return Outline.Generic(path)
    }
}
