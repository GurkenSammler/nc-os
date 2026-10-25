package dev.david.braviahome.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import androidx.compose.runtime.staticCompositionLocalOf
import dev.david.braviahome.R
import dev.david.braviahome.data.Live

/** Die UI-Sounds (res/raw, beim Bauen aus sounds/default bzw. sounds/custom zusammengestellt, siehe app/build.gradle.kts). */
enum class Sfx(val res: Int, val volume: Float) {
    HOVER(R.raw.sfx_hover, 0.45f),          // Fokus bewegen
    PRESS(R.raw.sfx_press, 0.6f),           // OK / bestaetigen
    OPEN(R.raw.sfx_open, 0.55f),            // App starten
    ENTER(R.raw.sfx_enter, 0.55f),          // Eingang / Einstellungen oeffnen
    MENU_OPEN(R.raw.sfx_menu_open, 0.55f),  // Optionen (langes OK) auf
    MENU_CLOSE(R.raw.sfx_menu_close, 0.55f),
    BOOT(R.raw.sfx_boot, 0.6f),             // Start-Sequenz
    MOVE(R.raw.sfx_move, 0.5f),             // Kachel verschieben
    TAB(R.raw.sfx_tab, 0.5f),               // ausgeblendete Apps auf-/zuklappen
}

class UiSounds(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loaded = HashSet<Int>()
    private val ids: Map<Sfx, Int>
    private var lastHover = 0L

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
        ids = Sfx.entries.associateWith { pool.load(context, it.res, 1) }
    }

    fun play(sfx: Sfx) {
        // An/aus und Lautstaerke aus SYS.CONFIG > LAUNCHER.
        val settings = Live.settings
        val master = settings.soundVolume / 10f
        if (!settings.soundsOn || master <= 0f) return
        val id = ids[sfx] ?: return
        if (synchronized(loaded) { id !in loaded }) return
        if (sfx == Sfx.HOVER) {
            // Beim Gedrueckthalten der Pfeiltaste nicht zu einem Brummen verschmelzen.
            val now = SystemClock.uptimeMillis()
            if (now - lastHover < 45) return
            lastHover = now
        }
        val v = sfx.volume * master
        pool.play(id, v, v, 1, 0, 1f)
    }

    fun release() = pool.release()
}

/** Zugriff auf die Sounds aus jeder Composable heraus. Null in Vorschauen. */
val LocalSounds = staticCompositionLocalOf<UiSounds?> { null }
