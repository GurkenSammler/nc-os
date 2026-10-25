package dev.david.braviahome.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Alle Apps, die auf dem TV einen Leanback-Eintrag haben. Null, bis zum ersten Mal geladen wurde. */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {

    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps

    // Bilder nur neu rendern, wenn sich die App geaendert hat.
    private val cache = HashMap<String, AppEntry>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            scope.launch { refresh() }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun stop() {
        context.unregisterReceiver(receiver)
    }

    suspend fun refresh() {
        _apps.value = withContext(Dispatchers.IO) { load() }
    }

    private fun load(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .mapNotNull { info -> runCatching { entryFor(info) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    private fun entryFor(info: ActivityInfo): AppEntry {
        val updated = pm.getPackageInfo(info.packageName, 0).lastUpdateTime
        val key = "${info.packageName}/${info.name}@$updated"
        cache[key]?.let { return it }

        val component = ComponentName(info.packageName, info.name)
        val banner = pm.getActivityBanner(component) ?: pm.getApplicationBanner(info.packageName)
        val art = if (banner != null) {
            banner.toBitmap(BANNER_W, BANNER_H)
        } else {
            pm.getActivityIcon(component).toBitmap(ICON_SIZE, ICON_SIZE)
        }
        val entry = AppEntry(
            packageName = info.packageName,
            activityName = info.name,
            label = info.loadLabel(pm).toString(),
            art = art.asImageBitmap(),
            artIsBanner = banner != null,
        )
        cache[key] = entry
        return entry
    }

    private companion object {
        const val BANNER_W = 480
        const val BANNER_H = 270
        const val ICON_SIZE = 192
    }
}
