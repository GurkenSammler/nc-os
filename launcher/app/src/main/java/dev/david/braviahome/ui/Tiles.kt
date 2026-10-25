package dev.david.braviahome.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import dev.david.braviahome.data.AppEntry
import dev.david.braviahome.data.GlitchLevel
import dev.david.braviahome.data.Live
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.ui.cyber.ChamferShape
import dev.david.braviahome.ui.cyber.CyberText
import dev.david.braviahome.ui.cyber.Dot
import dev.david.braviahome.ui.cyber.MicroLabel
import dev.david.braviahome.ui.theme.Cyber
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Logos ausserhalb des Fokus: einfarbig in der Hauptfarbe und gedimmt, damit das Design dominiert.
 * Null, wenn die Toenung in SYS.CONFIG > LOOK aus ist.
 */
private val logoTints = HashMap<Color, ColorFilter>()

private fun logoTint(): ColorFilter? {
    if (!Live.settings.logoTint) return null
    val c = Cyber.red
    return logoTints.getOrPut(c) {
        val k = 0.62f
        val r = c.red * k
        val g = c.green * k
        val b = c.blue * k
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
                    0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
                    0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
}

/**
 * Glitch direkt nachdem ein Element den Fokus bekommt: ein paar Bilder lang ein neuer
 * Zufallswert (0 = aus). Jedes Bild sieht dadurch anders zerrissen aus.
 */
@Composable
internal fun rememberGlitch(focused: Boolean): State<Int> {
    val seed = remember { mutableIntStateOf(0) }
    LaunchedEffect(focused) {
        // Springt der Fokus mitten im Glitch weiter, wird dieser Effekt abgebrochen;
        // deshalb hier immer erst zuruecksetzen, sonst bleibt ein zerrissenes Bild stehen.
        seed.intValue = 0
        if (!focused) return@LaunchedEffect
        repeat(GlitchTiming.frames) {
            seed.intValue = Random.nextInt(1, Int.MAX_VALUE)
            delay(GlitchTiming.frameMs)
        }
        seed.intValue = 0
    }
    return seed
}

/**
 * Normal 6 Bilder a 34 ms. Stufe aus SYS.CONFIG > LAUNCHER > GLITCH (apply).
 * Zum Pruefen per adb-Extra "glitchFrameMs" verlangsamen.
 */
object GlitchTiming {
    var frames = 6
    var frameMs = 34L
    var strength = 1f

    fun apply(level: GlitchLevel) {
        when (level) {
            GlitchLevel.OFF -> { frames = 0; strength = 0f }
            GlitchLevel.NORMAL -> { frames = 6; strength = 1f }
            GlitchLevel.STRONG -> { frames = 10; strength = 1.7f }
        }
    }
}

/**
 * Digitaler Glitch wie im Spiel, ohne das Element zu bewegen: waagerechte Streifen reissen
 * seitlich weg (die Luecke bleibt schwarz), an der Bruchkante ein rot/cyaner Farbsaum,
 * verschobene Geister-Umrisse und kurz aufblitzende Pixelbloecke.
 */
internal fun Modifier.glitch(seed: State<Int>, shape: Shape): Modifier = drawWithContent {
    val s = seed.value
    drawContent()
    if (s == 0) return@drawWithContent
    val rnd = Random(s)
    val k = GlitchTiming.strength
    val w = size.width
    val h = size.height
    val over = w * 0.06f * k // Platz fuer die Fokus-Vergroesserung

    repeat(((2 + rnd.nextInt(3)) * k).toInt().coerceAtLeast(1)) {
        val bandH = h * (0.05f + rnd.nextFloat() * 0.16f)
        val top = rnd.nextFloat() * (h - bandH)
        val dx = (if (rnd.nextBoolean()) 1f else -1f) * w * (0.03f + rnd.nextFloat() * 0.10f) * k
        clipRect(-over, top, w + over, top + bandH) {
            drawRect(Cyber.bg, Offset(-over, top), Size(w + 2 * over, bandH))
            translate(left = dx) { this@drawWithContent.drawContent() }
        }
        val edge = if (dx > 0) dx else w + dx
        val fringe = 3.dp.toPx()
        drawRect(Cyber.cyan.copy(alpha = 0.85f), Offset(edge - fringe, top), Size(fringe, bandH))
        drawRect(Cyber.redAlert.copy(alpha = 0.85f), Offset(edge, top), Size(fringe, bandH))
    }

    val outline = shape.createOutline(size, layoutDirection, this)
    val off = (2 + rnd.nextInt(5)).dp.toPx()
    translate(left = -off) { drawOutline(outline, Cyber.redAlert.copy(alpha = 0.7f), style = Stroke(2f)) }
    translate(left = off, top = off * 0.3f) { drawOutline(outline, Cyber.cyan.copy(alpha = 0.7f), style = Stroke(2f)) }

    val blockColors = listOf(Cyber.cyan, Cyber.redAlert, Color.White)
    repeat(3 + rnd.nextInt(4)) {
        val bw = w * (0.02f + rnd.nextFloat() * 0.10f)
        val bh = h * (0.02f + rnd.nextFloat() * 0.05f)
        drawRect(
            blockColors[rnd.nextInt(blockColors.size)].copy(alpha = 0.5f + rnd.nextFloat() * 0.45f),
            Offset(rnd.nextFloat() * (w - bw), rnd.nextFloat() * (h - bh)),
            Size(bw, bh),
        )
    }
}

/** Fokus-Zustand + Hover-Sound fuer eine Kachel. */
@Composable
internal fun rememberTileFocus(onFocusChanged: (Boolean) -> Unit): Pair<MutableInteractionSource, Boolean> {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val sounds = LocalSounds.current
    val callback by rememberUpdatedState(onFocusChanged)
    LaunchedEffect(focused) {
        callback(focused)
        if (focused) sounds?.play(Sfx.HOVER)
    }
    return interaction to focused
}

@Composable
fun CyberAppTile(
    app: AppEntry,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    blockModifier: Modifier = Modifier,
    moving: Boolean = false,
    dimmed: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val (interaction, focused) = rememberTileFocus(onFocusChanged)
    val focus by animateFloatAsState(if (focused || moving) 1f else 0f, tween(180), label = "focus")
    val glitch = rememberGlitch(focused)
    val shape = remember { ChamferShape(Cyber.tileCut) }
    val accent = when {
        moving -> Cyber.gold
        focused -> Cyber.cyan
        else -> Cyber.redDim
    }

    // [modifier] gehoert zur fokussierbaren Kachel (Tasten, Fokus), [blockModifier] zum ganzen
    // Block samt Labels (z. B. Einblenden in der Boot-Sequenz).
    val labels = Live.settings.tileLabels
    val tint = logoTint()
    Column(blockModifier.width(Cyber.tileW).alpha(if (dimmed) 0.5f else 1f)) {
        if (labels) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MicroLabel("APP_%02d".format(index + 1), color = if (focused) Cyber.cyan else Cyber.red)
                Spacer(Modifier.width(6.dp))
                MicroLabel("// ${app.label}", color = if (focused) Cyber.cyan else Cyber.redDim)
            }
            Spacer(Modifier.height(5.dp))
        }
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(Cyber.tileW, Cyber.tileH).glitch(glitch, shape),
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = Cyber.focusScale),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Cyber.panel,
                focusedContainerColor = Cyber.panel,
                pressedContainerColor = Cyber.panel,
            ),
            interactionSource = interaction,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (app.artIsBanner) {
                    Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (focus < 1f && tint != null) {
                        Image(
                            app.art, null, Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 1f - focus,
                            colorFilter = tint,
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(app.art, null, Modifier.size(42.dp), colorFilter = if (focused) null else tint)
                        Spacer(Modifier.height(6.dp))
                        CyberText(app.label.uppercase(), size = 13.sp, color = if (focused) Cyber.cyan else Cyber.red)
                    }
                }
                Box(Modifier.fillMaxSize().border(if (focus > 0.5f) 2.dp else 1.dp, accent, shape))
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(7.dp).background(accent))
                if (moving) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).background(Cyber.ink.copy(alpha = 0.8f)),
                    ) {
                        CyberText("◀  MOVE  ▶", Modifier.padding(horizontal = 10.dp, vertical = 2.dp), size = 13.sp, color = Cyber.gold, weight = FontWeight.Bold)
                    }
                }
            }
        }
        if (labels || moving) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(
                    when {
                        moving -> Cyber.gold
                        focused -> Cyber.green
                        else -> Cyber.redDim
                    },
                    5.dp,
                )
                Spacer(Modifier.width(5.dp))
                MicroLabel(
                    when {
                        moving -> "MOVING // CONFIRM WITH OK"
                        focused -> "ONLINE // READY"
                        else -> "STANDBY"
                    },
                    color = when {
                        moving -> Cyber.gold
                        focused -> Cyber.green
                        else -> Cyber.redDim
                    },
                )
            }
        }
    }
}

/** Kleine Box fuer Eingaenge und System: im Fokus cyan gefuellt wie ausgewaehlte Listeneintraege im Spiel. */
@Composable
fun CyberChip(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val (interaction, focused) = rememberTileFocus(onFocusChanged)
    val glitch = rememberGlitch(focused)
    val shape = remember { ChamferShape(Cyber.chipCut) }
    Surface(
        onClick = onClick,
        modifier = modifier.size(Cyber.chipW, Cyber.chipH).glitch(glitch, shape),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Cyber.focusScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Cyber.panel,
            focusedContainerColor = Cyber.cyan,
            pressedContainerColor = Cyber.cyan,
        ),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize().border(1.dp, if (focused) Cyber.cyan else Cyber.redDim, shape))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            CyberText(title.uppercase(), size = 17.sp, color = if (focused) Cyber.ink else Cyber.red, weight = FontWeight.Bold)
            MicroLabel(subtitle, color = if (focused) Cyber.ink.copy(alpha = 0.7f) else Cyber.redDim)
        }
    }
}
