package dev.david.braviahome

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.david.braviahome.data.AppEntry
import dev.david.braviahome.data.AppRepository
import dev.david.braviahome.data.InputEntry
import dev.david.braviahome.data.InputRepository
import dev.david.braviahome.data.BackupManager
import dev.david.braviahome.data.Layout
import dev.david.braviahome.data.LauncherSettings
import dev.david.braviahome.data.LayoutStore
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.SettingsStore
import dev.david.braviahome.data.WeatherLocation
import dev.david.braviahome.system.TvControls
import dev.david.braviahome.data.SystemInfo
import dev.david.braviahome.data.SystemInfoRepository
import dev.david.braviahome.data.Weather
import dev.david.braviahome.data.WeatherRepository
import dev.david.braviahome.data.arrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HomeUiState(
    val apps: List<AppEntry> = emptyList(),
    val hidden: List<AppEntry> = emptyList(),
    val inputs: List<InputEntry> = emptyList(),
    val loaded: Boolean = false,
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val appRepo = AppRepository(app, viewModelScope)
    private val inputRepo = InputRepository(app)
    private val store = LayoutStore(app)

    // Die Reihenfolge lebt hier im Speicher und wird nur nachgeschrieben,
    // damit schnelles Verschieben per Fernbedienung keine Schritte verliert.
    private val layout = MutableStateFlow<Layout?>(null)
    private val inputs = MutableStateFlow<List<InputEntry>>(emptyList())

    /** Zaehlt hoch, wenn die Home-Taste gedrueckt wird, waehrend wir schon vorne sind. */
    private val _homePresses = MutableStateFlow(0)
    val homePresses: StateFlow<Int> = _homePresses

    /**
     * Boot-Sequenz anfordern (Kaltstart, TV eingeschaltet). [delayMs] = erst so lange schwarz bleiben,
     * [runStartTarget] = danach das Start-Ziel oeffnen (nur beim Einschalten, nicht bei der Vorschau).
     */
    data class BootRequest(val id: Int = 0, val delayMs: Long = 0, val runStartTarget: Boolean = false)

    private val _bootRequests = MutableStateFlow(BootRequest())
    val bootRequests: StateFlow<BootRequest> = _bootRequests

    /** Dauer der Boot-Sequenz. Zum Testen per adb verlaengerbar (Extra "bootMs"). */
    var bootDurationMs = 1_500

    /** Zuletzt abgespielte Boot-Anforderung: nicht nochmal, wenn der Startbildschirm neu aufgebaut wird. */
    var playedBootId = 0

    // --- Einstellungen (SYS.CONFIG); die aktuellen Werte stehen in Live.settings ---
    private val settingsStore = SettingsStore(app)
    private val backup = BackupManager(app)

    val tv = TvControls(app)
    private val _volume = MutableStateFlow(tv.volume())
    val volume: StateFlow<TvControls.Volume> = _volume
    private val _saverMinutes = MutableStateFlow(tv.screensaverMinutes())
    val saverMinutes: StateFlow<Int> = _saverMinutes
    private val _weatherLocation = MutableStateFlow<WeatherLocation?>(null)
    val weatherLocation: StateFlow<WeatherLocation?> = _weatherLocation

    private val systemRepo = SystemInfoRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val _system = MutableStateFlow(SystemInfo())
    val system: StateFlow<SystemInfo> = _system
    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    val state: StateFlow<HomeUiState> =
        combine(appRepo.apps.filterNotNull(), layout.filterNotNull(), inputs) { apps, l, ins ->
            val (visible, hidden) = arrange(apps, l)
            HomeUiState(apps = visible, hidden = hidden, inputs = ins, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        appRepo.start()
        viewModelScope.launch { layout.value = store.load() }
        refresh()
    }

    /** Sofort anzeigen (Live), dann speichern; der Speicher meldet denselben Wert zurueck. */
    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(Live.settings)
        Live.settings = next
        viewModelScope.launch { settingsStore.save(next) }
    }

    /** Backup schreiben; Rueckgabe = Datei-Pfad oder null bei Fehler. */
    suspend fun exportBackup(): String? = withContext(Dispatchers.IO) { backup.export() }

    /** Backup einlesen und alles neu laden. true = geklappt. */
    suspend fun importBackup(): Boolean {
        val ok = withContext(Dispatchers.IO) { backup.import() }
        if (ok) layout.value = store.load()
        return ok
    }

    fun refreshTv() {
        _volume.value = tv.volume()
        _saverMinutes.value = tv.screensaverMinutes()
    }

    fun changeVolume(up: Boolean) {
        tv.changeVolume(up)
        _volume.value = tv.volume()
    }

    fun toggleMute() {
        tv.toggleMute()
        _volume.value = tv.volume()
    }

    fun setScreensaverMinutes(minutes: Int) {
        if (tv.setScreensaverMinutes(minutes)) _saverMinutes.value = minutes
    }

    suspend fun searchWeather(query: String): List<WeatherLocation> =
        withContext(Dispatchers.IO) { weatherRepo.search(query) }

    fun setWeatherLocation(location: WeatherLocation?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (location == null) weatherRepo.clearLocation() else weatherRepo.setLocation(location)
            }
            refreshSystem()
        }
    }

    /** Reihenfolge der Apps vergessen: wieder alphabetisch. */
    fun resetOrder() {
        val current = layout.value ?: return
        update(current.copy(order = emptyList()))
    }

    fun refresh() {
        viewModelScope.launch {
            appRepo.refresh()
            inputs.value = withContext(Dispatchers.IO) { inputRepo.load() }
            importHideList()
        }
    }

    /**
     * Ausblenden vom PC aus: scripts\hide-apps.ps1 legt hide.txt (ein Paket pro Zeile) in
     * den App-eigenen Ordner, an den nur adb und wir selbst herankommen. Datei wird danach geloescht.
     */
    private suspend fun importHideList() {
        val packages = withContext(Dispatchers.IO) {
            val file = File(getApplication<Application>().getExternalFilesDir(null), "hide.txt")
            if (!file.exists()) return@withContext emptyList()
            val lines = file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            file.delete()
            lines
        }
        if (packages.isEmpty()) return
        val current = layout.filterNotNull().first()
        update(current.copy(hidden = current.hidden + packages))
    }

    fun onHomePressed() {
        _homePresses.value++
    }

    /** Zaehlt hoch, wenn SYS.CONFIG direkt geoeffnet werden soll (aus dem Schnellmenue). */
    private val _configRequests = MutableStateFlow(0)
    val configRequests: StateFlow<Int> = _configRequests

    fun requestConfig() {
        _configRequests.value++
    }

    fun requestBoot(delayMs: Long = 0, runStartTarget: Boolean = false) {
        _bootRequests.value = BootRequest(_bootRequests.value.id + 1, delayMs, runStartTarget)
    }

    /** Technik-Labels und Wetter auffrischen; laeuft nur, solange der Startbildschirm sichtbar ist. */
    suspend fun refreshSystem() {
        _system.value = withContext(Dispatchers.IO) { systemRepo.read() }
        _weatherLocation.value = withContext(Dispatchers.IO) { weatherRepo.location() }
        _weather.value = withContext(Dispatchers.IO) { weatherRepo.current() }
    }

    /** Verschiebt eine sichtbare App um [delta] Plaetze (-1 = nach links). */
    fun move(packageName: String, delta: Int) {
        val current = layout.value ?: return
        val (visible, hidden) = arrange(appRepo.apps.value.orEmpty(), current)
        val order = visible.map { it.packageName }.toMutableList()
        val from = order.indexOf(packageName)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from == to) return
        order.add(to, order.removeAt(from))
        update(current.copy(order = order + hidden.map { it.packageName }))
    }

    fun setHidden(packageName: String, hidden: Boolean) {
        val current = layout.value ?: return
        val set = if (hidden) current.hidden + packageName else current.hidden - packageName
        update(current.copy(hidden = set))
    }

    private fun update(newLayout: Layout) {
        layout.value = newLayout
        viewModelScope.launch { store.save(newLayout) }
    }

    override fun onCleared() {
        appRepo.stop()
    }
}
