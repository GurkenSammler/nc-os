package dev.david.braviahome.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class WeatherLocation(val lat: Double, val lon: Double, val name: String, val region: String = "")

data class Weather(val tempC: Double, val code: Int, val place: String) {
    /** WMO-Wettercode als kurzes Label im Spiel-Stil. */
    val label: String
        get() = when (code) {
            0 -> "CLEAR"
            1, 2 -> "PARTLY CLOUDY"
            3 -> "OVERCAST"
            45, 48 -> "FOG"
            in 51..57 -> "DRIZZLE"
            in 61..67 -> "RAIN"
            in 71..77 -> "SNOW"
            in 80..82 -> "SHOWERS"
            85, 86 -> "SNOW SHOWERS"
            in 95..99 -> "STORM"
            else -> "UNKNOWN"
        }
}

/**
 * Wetter von Open-Meteo (kostenlos, ohne Konto). Der Ort kommt aus weather.txt im
 * App-eigenen Ordner ("breite;laenge;NAME"), gesetzt mit scripts\set-weather.ps1.
 * Ohne Ort gibt es kein Wetter und keine Netzabfrage.
 */
class WeatherRepository(private val context: Context) {

    private var cachedFor: WeatherLocation? = null
    private var fetchedAt = 0L

    private val locationFile get() = File(context.getExternalFilesDir(null), "weather.txt")

    /** Orte suchen (Open-Meteo Geocoding). Blockierend, auf einem IO-Thread aufrufen. */
    fun search(query: String): List<WeatherLocation> = runCatching {
        val url = URL(
            "https://geocoding-api.open-meteo.com/v1/search?count=6&language=de&format=json&name=" +
                URLEncoder.encode(query.trim(), "UTF-8"),
        )
        val body = (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 6_000
            readTimeout = 6_000
            try { inputStream.bufferedReader().use { it.readText() } } finally { disconnect() }
        }
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        List(results.length()) { i ->
            val r = results.getJSONObject(i)
            val region = listOf(r.optString("admin1"), r.optString("country_code")).filter { it.isNotBlank() }.joinToString(", ")
            WeatherLocation(r.getDouble("latitude"), r.getDouble("longitude"), r.getString("name").uppercase(), region)
        }
    }.getOrDefault(emptyList())

    fun setLocation(loc: WeatherLocation) {
        locationFile.writeText(String.format(Locale.US, "%.4f;%.4f;%s", loc.lat, loc.lon, loc.name))
        cachedFor = null
    }

    fun clearLocation() {
        locationFile.delete()
        cachedFor = null
        WeatherStore.latest = null
    }

    fun location(): WeatherLocation? = runCatching {
        val file = locationFile
        if (!file.exists()) return null
        val parts = file.readText().trim().split(';')
        WeatherLocation(parts[0].trim().toDouble(), parts[1].trim().toDouble(), parts[2].trim().uppercase())
    }.getOrNull()

    /** Blockierend (auf einem IO-Thread aufrufen). Fragt hoechstens alle 30 Minuten neu ab. */
    fun current(): Weather? {
        val loc = location()
        if (loc == null) {
            WeatherStore.latest = null
            return null
        }
        val now = System.currentTimeMillis()
        if (loc == cachedFor && now - fetchedAt < REFRESH_MS) return WeatherStore.latest
        val fresh = fetch(loc)
        if (fresh != null) {
            WeatherStore.latest = fresh
            cachedFor = loc
            fetchedAt = now
        } else {
            // Fehlgeschlagen: in 5 Minuten nochmal versuchen, bis dahin den alten Wert zeigen.
            cachedFor = loc
            fetchedAt = now - REFRESH_MS + RETRY_MS
        }
        return WeatherStore.latest
    }

    private fun fetch(loc: WeatherLocation): Weather? = runCatching {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
                loc.lat, loc.lon,
            ),
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 6_000
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            Weather(current.getDouble("temperature_2m"), current.getInt("weather_code"), loc.name)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val REFRESH_MS = 30 * 60 * 1000L
        const val RETRY_MS = 5 * 60 * 1000L
    }
}

/** Letzter Wetterwert im Prozess; auch der Bildschirmschoner liest ihn. */
object WeatherStore {
    @Volatile
    var latest: Weather? = null
}
