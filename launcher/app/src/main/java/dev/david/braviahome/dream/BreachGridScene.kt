package dev.david.braviahome.dream

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.random.Random

/**
 * BREACH GRID: eine 6x6-Hex-Matrix wie beim Hacking-Minispiel. Eine Zeile bzw. Spalte ist
 * markiert, der Cursor waehlt abwechselnd darin einen Code, der wandert in den PUFFER.
 * Ist die Folge komplett, blitzt "BREACH SUCCESSFUL", dann kommt ein neues Gitter.
 * Der ganze Block verschiebt sich bei jedem neuen Gitter (gegen Einbrennen).
 */
class BreachGridScene(private val fonts: DreamFonts, private val c: DreamColors) : DreamScene {

    private val rnd = Random(System.nanoTime())
    private val codes = listOf("1C", "55", "BD", "E9", "7A", "FF")
    private val n = 6

    private var grid = Array(n) { Array(n) { "" } }
    private val used = HashSet<Int>()
    private val buffer = ArrayList<String>()
    private var target = listOf<String>()
    private var row = 0
    private var col = 0
    private var horizontal = true
    private var stepAt = 0L
    private var doneAt = 0L

    private val cellPaint = fonts.paint(fonts.bold, c.primary, 10f, 0.04f)
    private val dimPaint = fonts.paint(fonts.bold, c.primaryDim, 10f, 0.04f)
    private val labelPaint = fonts.paint(fonts.bold, c.primary, 10f, 0.16f)
    private val smallPaint = fonts.paint(fonts.medium, c.primaryDim, 10f, 0.14f)
    private val hiPaint = fonts.paint(fonts.bold, c.accent, 10f, 0.16f)
    private val successPaint = fonts.paint(fonts.bold, c.bg, 10f, 0.2f)
    private val bandPaint = Paint().apply { color = c.accent.withAlpha(36) }
    private val cursorPaint = Paint().apply { color = c.accent }
    private val framePaint = Paint().apply { color = c.primaryDim; style = Paint.Style.STROKE; strokeWidth = 2f }
    private val fillPaint = Paint().apply { color = c.accent }
    private val bufferBox = Paint().apply { color = c.primaryDim; style = Paint.Style.STROKE; strokeWidth = 2f }

    private var w = 0f
    private var h = 0f
    private var cell = 0f
    private var ox = 0f
    private var oy = 0f
    private var density = 1f

    override val frameMs = 50L

    override fun setup(w: Int, h: Int, density: Float) {
        this.w = w.toFloat()
        this.h = h.toFloat()
        this.density = density
        cell = h * 0.085f
        cellPaint.textSize = cell * 0.42f
        dimPaint.textSize = cell * 0.42f
        labelPaint.textSize = 16f * density
        hiPaint.textSize = 16f * density
        smallPaint.textSize = 10f * density
        successPaint.textSize = 30f * density
        newGrid(System.currentTimeMillis())
    }

    private fun newGrid(now: Long) {
        grid = Array(n) { Array(n) { codes[rnd.nextInt(codes.size)] } }
        used.clear()
        buffer.clear()
        target = List(4) { codes[rnd.nextInt(codes.size)] }
        row = 0
        col = 0
        horizontal = true
        stepAt = now + 1_200
        doneAt = 0L
        // Neue Position fuer den ganzen Block
        val blockW = cell * n + w * 0.32f
        ox = w * 0.06f + rnd.nextFloat() * (w - blockW - w * 0.12f).coerceAtLeast(1f)
        oy = h * 0.18f + rnd.nextFloat() * (h * 0.14f)
    }

    private fun step(now: Long) {
        // Kandidaten in der aktiven Zeile/Spalte; bevorzugt der naechste gesuchte Code.
        val wanted = target.getOrNull(buffer.size)
        val candidates = (0 until n).map { i -> if (horizontal) row to i else i to col }
            .filter { (r, cc) -> (r * n + cc) !in used }
        if (candidates.isEmpty() || buffer.size >= target.size) {
            doneAt = now
            return
        }
        val pick = candidates.firstOrNull { (r, cc) -> grid[r][cc] == wanted && rnd.nextInt(3) != 0 }
            ?: candidates[rnd.nextInt(candidates.size)]
        row = pick.first
        col = pick.second
        used += row * n + col
        buffer += grid[row][col]
        horizontal = !horizontal
        stepAt = now + 900 + rnd.nextLong(500)
        if (buffer.size >= target.size) doneAt = now + 700
    }

    override fun draw(canvas: Canvas, nowMs: Long) {
        canvas.drawColor(c.bg)
        if (doneAt == 0L && nowMs >= stepAt) step(nowMs)
        if (doneAt != 0L && nowMs > doneAt + 2_400) newGrid(nowMs)

        // Kopf
        canvas.drawText("CODE MATRIX", ox, oy - cell * 0.7f, labelPaint)
        canvas.drawText("BREACH TIME REMAINING  %02d".format(((stepAt - nowMs).coerceAtLeast(0) / 100) % 100), ox, oy - cell * 0.35f, smallPaint)

        // Markierte Zeile bzw. Spalte
        if (doneAt == 0L) {
            if (horizontal) canvas.drawRect(ox, oy + row * cell, ox + n * cell, oy + (row + 1) * cell, bandPaint)
            else canvas.drawRect(ox + col * cell, oy, ox + (col + 1) * cell, oy + n * cell, bandPaint)
        }
        canvas.drawRect(ox - 8f, oy - 8f, ox + n * cell + 8f, oy + n * cell + 8f, framePaint)

        for (r in 0 until n) for (cc in 0 until n) {
            val x = ox + cc * cell + cell * 0.22f
            val y = oy + r * cell + cell * 0.64f
            val taken = (r * n + cc) in used
            if (r == row && cc == col && taken) {
                canvas.drawRect(ox + cc * cell + 4f, oy + r * cell + 4f, ox + (cc + 1) * cell - 4f, oy + (r + 1) * cell - 4f, cursorPaint)
                cellPaint.color = c.bg
                canvas.drawText(grid[r][cc], x, y, cellPaint)
                cellPaint.color = c.primary
            } else {
                canvas.drawText(if (taken) "[ ]" else grid[r][cc], x, y, if (taken) dimPaint else cellPaint)
            }
        }

        // Rechts: Puffer und gesuchte Folge
        val px = ox + n * cell + w * 0.04f
        canvas.drawText("BUFFER", px, oy + labelPaint.textSize, labelPaint)
        val box = cell * 0.62f
        for (i in target.indices) {
            val bx = px + i * (box + 8f)
            val by = oy + labelPaint.textSize * 1.6f
            canvas.drawRect(bx, by, bx + box, by + box, bufferBox)
            buffer.getOrNull(i)?.let { canvas.drawText(it, bx + box * 0.18f, by + box * 0.7f, hiPaint) }
        }
        canvas.drawText("SEQUENCE REQUIRED", px, oy + cell * 2.3f, labelPaint)
        var sx = px
        for ((i, code) in target.withIndex()) {
            val ok = buffer.getOrNull(i) == code
            canvas.drawText(code, sx, oy + cell * 2.9f, if (ok) hiPaint else dimPaint.also { it.textSize = hiPaint.textSize })
            sx += hiPaint.measureText("FF") + 16f * density
        }
        dimPaint.textSize = cell * 0.42f
        canvas.drawText("PROBE_V3 // SIGNAL TRACE", px, oy + cell * 3.5f, smallPaint)

        // Erfolg
        if (doneAt != 0L && nowMs > doneAt) {
            val text = if (buffer == target) "BREACH SUCCESSFUL" else "UPLOAD COMPLETE"
            val tw = successPaint.measureText(text)
            val bx = ox
            val by = oy + n * cell + 40f * density
            val flick = ((nowMs - doneAt) / 80) % 5 != 0L
            if (flick) {
                canvas.drawRect(bx, by - successPaint.textSize, bx + tw + 32f * density, by + 12f * density, fillPaint)
                canvas.drawText(text, bx + 16f * density, by, successPaint)
            }
        }
    }
}
