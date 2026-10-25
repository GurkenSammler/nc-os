package dev.david.braviahome.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ApiStatus { LOADING, READY, NO_KEY, DENIED, OFFLINE }

/** Welche Sony-Gruppe ein Wert ist (bestimmt, welche Set-Methode ihn aendert). */
enum class ApiGroup { PICTURE, SOUND, SPEAKER }

data class SonyState(
    val status: ApiStatus = ApiStatus.LOADING,
    val picture: List<ApiSetting> = emptyList(),
    val sound: List<ApiSetting> = emptyList(),
    val speaker: List<ApiSetting> = emptyList(),
    val powerSaving: String = "off",
) {
    fun of(group: ApiGroup) = when (group) {
        ApiGroup.PICTURE -> picture
        ApiGroup.SOUND -> sound
        ApiGroup.SPEAKER -> speaker
    }

    fun picture(target: String) = picture.firstOrNull { it.target == target }
}

/**
 * Bild- und Tonwerte des TVs fuer den ganzen Prozess (SYS.CONFIG und Schnellmenue lesen dasselbe).
 * Aenderungen erscheinen sofort, gesendet wird erst, wenn ◀ ▶ kurz ruht; danach wird neu gelesen,
 * weil Sony abhaengige Werte mitaendert (z. B. Bildmodus -> Helligkeit).
 */
object SonyTv {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SonyState())
    val state: StateFlow<SonyState> = _state

    private var api: SonyApi? = null
    private val pending = HashMap<String, Job>()
    private var refreshJob: Job? = null

    fun init(context: Context) {
        if (api == null) api = SonyApi(context.applicationContext)
    }

    fun refresh() {
        val api = api ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch { load(api) }
    }

    private suspend fun load(api: SonyApi) {
        _state.value = try {
            val key = api.psk()
            val previous = _state.value
            SonyState(
                status = when {
                    key == null -> ApiStatus.NO_KEY
                    previous.status == ApiStatus.DENIED -> ApiStatus.DENIED
                    else -> ApiStatus.READY
                },
                picture = api.picture(),
                sound = runCatching { api.sound() }.getOrDefault(emptyList()).withKnownChoices(),
                speaker = runCatching { api.speaker() }.getOrDefault(emptyList()).withKnownChoices(),
                powerSaving = runCatching { api.powerSaving() }.getOrDefault("off"),
            )
        } catch (e: SonyApiException) {
            Log.w(TAG, "load failed: ${e.code} ${e.message}")
            _state.value.copy(status = if (e.code == -1) ApiStatus.OFFLINE else ApiStatus.DENIED)
        }
    }

    suspend fun setPsk(key: String) {
        api?.setPsk(key)
        _state.value = _state.value.copy(status = ApiStatus.LOADING)
        refresh()
    }

    /** Wert setzen: sofort anzeigen, nach 250 ms Ruhe senden, dann neu lesen. */
    fun set(group: ApiGroup, target: String, value: String) {
        val api = api ?: return
        _state.value = _state.value.let { s ->
            fun List<ApiSetting>.with() = map { if (it.target == target) it.copy(current = value) else it }
            when (group) {
                ApiGroup.PICTURE -> s.copy(picture = s.picture.with())
                ApiGroup.SOUND -> s.copy(sound = s.sound.with())
                ApiGroup.SPEAKER -> s.copy(speaker = s.speaker.with())
            }
        }
        val key = "$group/$target"
        pending[key]?.cancel()
        pending[key] = scope.launch {
            delay(250)
            try {
                when (group) {
                    ApiGroup.PICTURE -> api.setPicture(target, value)
                    ApiGroup.SOUND -> api.setSound(target, value)
                    ApiGroup.SPEAKER -> api.setSpeaker(target, value)
                }
                if (_state.value.status == ApiStatus.DENIED) _state.value = _state.value.copy(status = ApiStatus.READY)
            } catch (e: SonyApiException) {
                Log.w(TAG, "set $target=$value failed: ${e.code} ${e.message}")
                if (e.code == 403) _state.value = _state.value.copy(status = ApiStatus.DENIED)
            }
            delay(400)
            load(api)
        }
    }

    /** "Bild aus": Bildschirm dunkel, Ton laeuft weiter. Eine Taste (ausser Lautstaerke) holt das Bild zurueck. */
    fun pictureOff() {
        val api = api ?: return
        scope.launch {
            try {
                api.setPowerSaving("pictureOff")
            } catch (e: SonyApiException) {
                Log.w(TAG, "pictureOff failed: ${e.code} ${e.message}")
                if (e.code == 403) _state.value = _state.value.copy(status = ApiStatus.DENIED)
            }
        }
    }

    /** Ton- und Lautsprecherwerte kommen ohne Auswahlliste; die Werte stehen in Sonys API-Doku. */
    private fun List<ApiSetting>.withKnownChoices() = mapNotNull { s ->
        val known = KNOWN_CHOICES[s.target] ?: return@mapNotNull null
        if (s.choices.isNotEmpty() || s.isRange) s else s.copy(choices = known)
    }

    private val KNOWN_CHOICES = mapOf(
        "outputTerminal" to listOf("speaker", "speaker_hdmi", "hdmi", "audioSystem"),
        "tvPosition" to listOf("tableTop", "wallMount"),
    )

    private const val TAG = "NcSony"
}
