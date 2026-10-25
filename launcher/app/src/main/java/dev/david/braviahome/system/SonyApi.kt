package dev.david.braviahome.system

import android.content.Context
import android.net.ConnectivityManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/** Ein Wert aus Sonys Fernsteuer-API: Zahlenbereich ([min]..[max]) oder Auswahlliste ([choices]). */
data class ApiSetting(
    val target: String,
    val current: String,
    val choices: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
    val step: Int = 1,
    val available: Boolean = true,
) {
    val isRange get() = min != null && max != null

    /** Wert [delta] Schritte weiter; Auswahl im Kreis, Bereich am Rand stehen bleibend. */
    fun stepped(delta: Int): String? = when {
        isRange -> ((current.toIntOrNull() ?: min!!) + delta * step).coerceIn(min!!, max!!).toString()
        choices.isNotEmpty() -> choices[(choices.indexOf(current).coerceAtLeast(0) + delta + choices.size) % choices.size]
        else -> null
    }
}

/** [code] -1 = TV nicht erreichbar, 403 = Schluessel falsch/fehlt, sonst Sony-Fehlercode. */
class SonyApiException(val code: Int, message: String) : Exception(message)

internal val Context.sonyApiStore by preferencesDataStore(name = "sony_api")

/**
 * Sonys Fernsteuer-API (JSON-RPC unter /sony/<dienst>), dieselbe, die Sony fuer Steuerungen per
 * Netzwerk anbietet. Lesen geht ohne Schluessel, Aendern braucht den PSK aus
 * scripts\enable-sony-api.ps1 (Header X-Auth-PSK). Der Schluessel liegt in einem eigenen Speicher,
 * der nicht ins Backup wandert.
 */
class SonyApi(private val context: Context) {

    private var lastHost: String? = null

    suspend fun psk(): String? = context.sonyApiStore.data.first()[PSK]?.takeIf { it.isNotBlank() }

    suspend fun setPsk(key: String) {
        context.sonyApiStore.edit { it[PSK] = key }
    }

    suspend fun picture(): List<ApiSetting> = settings(call("video", "getPictureQualitySettings", targetAll()))
    suspend fun setPicture(target: String, value: String) { call("video", "setPictureQualitySettings", change(target, value)) }

    suspend fun sound(): List<ApiSetting> = settings(call("audio", "getSoundSettings", targetAll(), "1.1"))
    suspend fun setSound(target: String, value: String) { call("audio", "setSoundSettings", change(target, value), "1.1") }

    suspend fun speaker(): List<ApiSetting> = settings(call("audio", "getSpeakerSettings", targetAll()))
    suspend fun setSpeaker(target: String, value: String) { call("audio", "setSpeakerSettings", change(target, value)) }

    /** off / low / high / pictureOff ("Bild aus", Ton laeuft weiter). */
    suspend fun powerSaving(): String = call("system", "getPowerSavingMode").optJSONObject(0)?.optString("mode") ?: "off"
    suspend fun setPowerSaving(mode: String) { call("system", "setPowerSavingMode", JSONArray().put(JSONObject().put("mode", mode))) }

    private fun targetAll() = JSONArray().put(JSONObject().put("target", ""))

    private fun change(target: String, value: String) =
        JSONArray().put(JSONObject().put("settings", JSONArray().put(JSONObject().put("target", target).put("value", value))))

    /** Ergebnis [[{target, currentValue, isAvailable, candidate: [{min,max,step} | {value}]}]]. */
    private fun settings(result: JSONArray): List<ApiSetting> {
        val list = result.optJSONArray(0) ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val target = o.optString("target").ifEmpty { return@mapNotNull null }
            val candidates = o.optJSONArray("candidate")
            val first = candidates?.optJSONObject(0)
            if (first != null && first.has("min")) {
                ApiSetting(
                    target = target,
                    current = o.optString("currentValue"),
                    min = first.optInt("min"),
                    max = first.optInt("max"),
                    step = first.optInt("step", 1).coerceAtLeast(1),
                    available = o.optBoolean("isAvailable", true),
                )
            } else {
                val values = (0 until (candidates?.length() ?: 0)).mapNotNull { j ->
                    candidates?.optJSONObject(j)?.takeIf { it.optBoolean("isAvailable", true) }?.optString("value")?.ifEmpty { null }
                }
                ApiSetting(target, o.optString("currentValue"), values, available = o.optBoolean("isAvailable", true))
            }
        }
    }

    private suspend fun call(service: String, method: String, params: JSONArray = JSONArray(), version: String = "1.0"): JSONArray =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("method", method).put("id", 1).put("params", params).put("version", version).toString()
            val key = psk()
            var failure: Exception? = null
            for (host in listOfNotNull(lastHost, "127.0.0.1", ownIp()).distinct()) {
                val json = try {
                    post("http://$host/sony/$service", body, key)
                } catch (e: IOException) {
                    failure = e
                    continue
                }
                lastHost = host
                json.optJSONArray("error")?.let { throw SonyApiException(it.optInt(0), it.optString(1)) }
                return@withContext json.optJSONArray("result") ?: JSONArray()
            }
            throw SonyApiException(-1, failure?.message ?: "offline")
        }

    private fun post(url: String, body: String, key: String?): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 1_500
            conn.readTimeout = 5_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            key?.let { conn.setRequestProperty("X-Auth-PSK", it) }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code == 401 || code == 403) throw SonyApiException(403, "forbidden")
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw SonyApiException(code, "HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Eigene IPv4-Adresse (falls die API nicht auf 127.0.0.1 antwortet). */
    private fun ownIp(): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    private companion object {
        val PSK = stringPreferencesKey("psk")
    }
}
