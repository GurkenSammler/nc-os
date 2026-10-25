package dev.david.braviahome.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

enum class GlitchLevel { OFF, NORMAL, STRONG }
enum class ThemeName { RED, AMBER, ACID, VIOLET, ICE }
enum class ScanlineLevel { OFF, LOW, NORMAL, HIGH }
enum class BackgroundStyle { CIRCUIT, GRID, PLAIN }
enum class TileSize { S, M, L }
enum class DateStyle { EN, DE, ISO }
enum class SaverStyle { WORLD_CLOCK, CODE_RAIN, NIGHT_CITY, BREACH_GRID, VIDEO_LOOP, SHUFFLE }
enum class QuickItem { PICTURE_MODE, BACKLIGHT, PICTURE_OFF, VOLUME, MUTE, SLEEP, SAVER, STANDBY, PICTURE_SOUND, CONFIG, INPUTS }

/** Einstellungen des Launchers selbst (nicht des TVs). Defaults = Verhalten von v0.5. */
data class LauncherSettings(
    // Launcher
    val soundsOn: Boolean = true,
    val soundVolume: Int = 8,          // 0..10
    val glitch: GlitchLevel = GlitchLevel.NORMAL,
    val bootSequence: Boolean = true,
    // Look
    val theme: ThemeName = ThemeName.RED,
    val title: String = DEFAULT_TITLE,
    val scanlines: ScanlineLevel = ScanlineLevel.NORMAL,
    val background: BackgroundStyle = BackgroundStyle.CIRCUIT,
    val logoTint: Boolean = true,
    // Layout
    val tileSize: TileSize = TileSize.M,
    val tileLabels: Boolean = true,
    val footer: Boolean = true,
    val clock24: Boolean = true,
    val dateStyle: DateStyle = DateStyle.EN,
    // Eingaenge & Start
    val inputNames: Map<String, String> = emptyMap(),
    val hiddenInputs: Set<String> = emptySet(),
    /** "" = Startbildschirm, "input:<id>" oder "app:<paket>". */
    val startTarget: String = "",
    // Schnellmenue
    val quickEnabled: Boolean = true,
    val quickScan: Int = 0xC3,
    val quickItems: Set<QuickItem> = DEFAULT_QUICK_ITEMS,
    // Bildschirmschoner
    val saverStyle: SaverStyle = SaverStyle.WORLD_CLOCK,
    val shuffleMinutes: Int = 10,
    val saverClock: Boolean = true,
    /** "" = alle Clips im Wechsel, sonst Dateiname in files/loops. */
    val saverClip: String = "",
    val saverClipSound: Boolean = false,
) {
    companion object {
        const val DEFAULT_TITLE = "//SYS.MENU"
        val DEFAULT_QUICK_ITEMS = QuickItem.entries.toSet() - QuickItem.INPUTS
        /** Seit v0.9; wer schon eine Auswahl gespeichert hat, bekommt sie einmalig dazu. */
        val QUICK_ITEMS_REV2 = setOf(QuickItem.PICTURE_MODE, QuickItem.BACKLIGHT, QuickItem.PICTURE_OFF)
    }
}

/**
 * Die aktuellen Einstellungen fuer den ganzen Prozess (Launcher, Schnellmenue, Bildschirmschoner).
 * Compose liest sie direkt und baut sich bei Aenderungen neu auf. Gesetzt von [NcApp].
 */
object Live {
    var settings by mutableStateOf(LauncherSettings())
}

internal val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    val flow: Flow<LauncherSettings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun load(): LauncherSettings = context.settingsStore.data.first().toSettings()

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) = save(transform(load()))

    private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
        this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    private fun Preferences.toSettings(): LauncherSettings {
        val d = LauncherSettings()
        return LauncherSettings(
            soundsOn = this[SOUNDS_ON] ?: d.soundsOn,
            soundVolume = this[SOUND_VOLUME] ?: d.soundVolume,
            glitch = enum(GLITCH, d.glitch),
            bootSequence = this[BOOT] ?: d.bootSequence,
            theme = enum(THEME, d.theme),
            title = this[TITLE] ?: d.title,
            scanlines = enum(SCANLINES, d.scanlines),
            background = enum(BACKGROUND, d.background),
            logoTint = this[LOGO_TINT] ?: d.logoTint,
            tileSize = enum(TILE_SIZE, d.tileSize),
            tileLabels = this[TILE_LABELS] ?: d.tileLabels,
            footer = this[FOOTER] ?: d.footer,
            clock24 = this[CLOCK24] ?: d.clock24,
            dateStyle = enum(DATE_STYLE, d.dateStyle),
            inputNames = this[INPUT_NAMES]?.let(::parseMap) ?: d.inputNames,
            hiddenInputs = this[HIDDEN_INPUTS] ?: d.hiddenInputs,
            startTarget = this[START_TARGET] ?: d.startTarget,
            quickEnabled = this[QUICK_ENABLED] ?: d.quickEnabled,
            quickScan = this[QUICK_SCAN] ?: d.quickScan,
            quickItems = this[QUICK_ITEMS]?.mapNotNull { v -> QuickItem.entries.firstOrNull { it.name == v } }?.toSet()
                ?.let { if ((this[QUICK_ITEMS_REV] ?: 1) < 2) it + LauncherSettings.QUICK_ITEMS_REV2 else it }
                ?: d.quickItems,
            saverStyle = enum(SAVER_STYLE, d.saverStyle),
            shuffleMinutes = this[SHUFFLE_MINUTES] ?: d.shuffleMinutes,
            saverClock = this[SAVER_CLOCK] ?: d.saverClock,
            saverClip = this[SAVER_CLIP] ?: d.saverClip,
            saverClipSound = this[SAVER_CLIP_SOUND] ?: d.saverClipSound,
        )
    }

    suspend fun save(s: LauncherSettings) {
        context.settingsStore.edit { p ->
            p[SOUNDS_ON] = s.soundsOn
            p[SOUND_VOLUME] = s.soundVolume
            p[GLITCH] = s.glitch.name
            p[BOOT] = s.bootSequence
            p[THEME] = s.theme.name
            p[TITLE] = s.title
            p[SCANLINES] = s.scanlines.name
            p[BACKGROUND] = s.background.name
            p[LOGO_TINT] = s.logoTint
            p[TILE_SIZE] = s.tileSize.name
            p[TILE_LABELS] = s.tileLabels
            p[FOOTER] = s.footer
            p[CLOCK24] = s.clock24
            p[DATE_STYLE] = s.dateStyle.name
            p[INPUT_NAMES] = JSONObject(s.inputNames).toString()
            p[HIDDEN_INPUTS] = s.hiddenInputs
            p[START_TARGET] = s.startTarget
            p[QUICK_ENABLED] = s.quickEnabled
            p[QUICK_SCAN] = s.quickScan
            p[QUICK_ITEMS] = s.quickItems.map { it.name }.toSet()
            p[QUICK_ITEMS_REV] = 2
            p[SAVER_STYLE] = s.saverStyle.name
            p[SHUFFLE_MINUTES] = s.shuffleMinutes
            p[SAVER_CLOCK] = s.saverClock
            p[SAVER_CLIP] = s.saverClip
            p[SAVER_CLIP_SOUND] = s.saverClipSound
        }
    }

    private fun parseMap(json: String): Map<String, String> = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private companion object {
        val SOUNDS_ON = booleanPreferencesKey("sounds_on")
        val SOUND_VOLUME = intPreferencesKey("sound_volume")
        val GLITCH = stringPreferencesKey("glitch")
        val BOOT = booleanPreferencesKey("boot_sequence")
        val THEME = stringPreferencesKey("theme")
        val TITLE = stringPreferencesKey("title")
        val SCANLINES = stringPreferencesKey("scanlines")
        val BACKGROUND = stringPreferencesKey("background")
        val LOGO_TINT = booleanPreferencesKey("logo_tint")
        val TILE_SIZE = stringPreferencesKey("tile_size")
        val TILE_LABELS = booleanPreferencesKey("tile_labels")
        val FOOTER = booleanPreferencesKey("footer")
        val CLOCK24 = booleanPreferencesKey("clock24")
        val DATE_STYLE = stringPreferencesKey("date_style")
        val INPUT_NAMES = stringPreferencesKey("input_names")
        val HIDDEN_INPUTS = stringSetPreferencesKey("hidden_inputs")
        val START_TARGET = stringPreferencesKey("start_target")
        val QUICK_ENABLED = booleanPreferencesKey("quick_enabled")
        val QUICK_SCAN = intPreferencesKey("quick_scan")
        val QUICK_ITEMS = stringSetPreferencesKey("quick_items")
        val QUICK_ITEMS_REV = intPreferencesKey("quick_items_rev")
        val SAVER_STYLE = stringPreferencesKey("saver_style")
        val SHUFFLE_MINUTES = intPreferencesKey("shuffle_minutes")
        val SAVER_CLOCK = booleanPreferencesKey("saver_clock")
        val SAVER_CLIP = stringPreferencesKey("saver_clip")
        val SAVER_CLIP_SOUND = booleanPreferencesKey("saver_clip_sound")
    }
}
