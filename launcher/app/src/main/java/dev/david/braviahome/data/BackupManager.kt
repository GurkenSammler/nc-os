package dev.david.braviahome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sichert alle Launcher-Einstellungen (SYS.CONFIG) und das Layout (Reihenfolge, ausgeblendete
 * Apps) als JSON im App-Ordner: /sdcard/Android/data/<paket>/files/nc-os-backup.json.
 * scripts\backup-launcher.ps1 holt die Datei auf den PC bzw. bringt sie auf einen anderen TV.
 * Gespeichert wird generisch mit Typ, damit kuenftige Einstellungen automatisch mitkommen.
 */
class BackupManager(private val context: Context) {

    val file: File get() = File(context.getExternalFilesDir(null), FILE_NAME)

    private val stores: Map<String, DataStore<Preferences>>
        get() = mapOf("settings" to context.settingsStore, "layout" to context.layoutStore)

    fun export(): String? = runCatching {
        val root = JSONObject().put("format", FORMAT).put("app", context.packageName)
        runBlocking {
            for ((name, store) in stores) root.put(name, toJson(store.data.first()))
        }
        file.writeText(root.toString(2))
        file.absolutePath
    }.getOrNull()

    fun import(): Boolean = runCatching {
        val root = JSONObject(file.readText())
        require(root.optString("format") == FORMAT)
        runBlocking {
            for ((name, store) in stores) {
                val section = root.optJSONObject(name) ?: continue
                store.edit { prefs ->
                    prefs.clear()
                    fromJson(section, prefs)
                }
            }
        }
        true
    }.getOrDefault(false)

    private fun toJson(prefs: Preferences): JSONObject {
        val out = JSONObject()
        for ((key, value) in prefs.asMap()) {
            val (type, json) = when (value) {
                is String -> "s" to value
                is Int -> "i" to value
                is Boolean -> "b" to value
                is Long -> "l" to value
                is Float -> "f" to value.toDouble()
                is Set<*> -> "ss" to JSONArray(value.map { it.toString() })
                else -> continue
            }
            out.put(key.name, JSONObject().put("t", type).put("v", json))
        }
        return out
    }

    private fun fromJson(section: JSONObject, prefs: MutablePreferences) {
        for (name in section.keys()) {
            val entry = section.getJSONObject(name)
            when (entry.getString("t")) {
                "s" -> prefs[stringPreferencesKey(name)] = entry.getString("v")
                "i" -> prefs[intPreferencesKey(name)] = entry.getInt("v")
                "b" -> prefs[booleanPreferencesKey(name)] = entry.getBoolean("v")
                "l" -> prefs[longPreferencesKey(name)] = entry.getLong("v")
                "f" -> prefs[floatPreferencesKey(name)] = entry.getDouble("v").toFloat()
                "ss" -> {
                    val arr = entry.getJSONArray("v")
                    prefs[stringSetPreferencesKey(name)] = List(arr.length()) { arr.getString(it) }.toSet()
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "nc-os-backup.json"
        private const val FORMAT = "nc-os-backup-1"
    }
}
