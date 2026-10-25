package dev.david.braviahome.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

internal val Context.layoutStore by preferencesDataStore(name = "layout")

/** Speichert Reihenfolge und ausgeblendete Apps dauerhaft (ueberlebt Neustarts). */
class LayoutStore(private val context: Context) {

    suspend fun load(): Layout {
        val prefs = context.layoutStore.data.first()
        return Layout(
            order = prefs[ORDER]?.split(',')?.filter { it.isNotEmpty() }.orEmpty(),
            hidden = prefs[HIDDEN].orEmpty(),
        )
    }

    suspend fun save(layout: Layout) {
        context.layoutStore.edit { prefs ->
            prefs[ORDER] = layout.order.joinToString(",")
            prefs[HIDDEN] = layout.hidden
        }
    }

    private companion object {
        val ORDER = stringPreferencesKey("order")
        val HIDDEN = stringSetPreferencesKey("hidden")
    }
}
