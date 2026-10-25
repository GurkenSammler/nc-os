package dev.david.braviahome.system

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import dev.david.braviahome.dream.LoopClips

data class AppSize(val packageName: String, val label: String, val appBytes: Long, val dataBytes: Long, val cacheBytes: Long) {
    val total get() = appBytes + dataBytes
}

data class StorageState(
    val totalBytes: Long = 0,
    val freeBytes: Long = 0,
    val loopsBytes: Long = 0,
    val permitted: Boolean = true,
    val apps: List<AppSize> = emptyList(),
)

/**
 * Speicher fuer SYS.CONFIG > APPS & STORAGE: /data belegt/frei, eigene Video-Loops und die Groesse
 * jeder App mit Kachel. Die App-Groessen brauchen die Nutzungsstatistik-Freigabe (install-launcher.ps1).
 */
class StorageInfo(private val context: Context) {

    fun read(): StorageState {
        val stat = StatFs(Environment.getDataDirectory().path)
        val loops = LoopClips.list(context).sumOf { it.length() }
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val pm = context.packageManager
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val packages = pm.queryIntentActivities(launchable, 0).map { it.activityInfo.packageName }.distinct()
        var permitted = true
        val apps = packages.mapNotNull { pkg ->
            val s = try {
                stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
            } catch (e: SecurityException) {
                permitted = false
                return@mapNotNull null
            } catch (e: Exception) {
                return@mapNotNull null
            }
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            AppSize(pkg, label, s.appBytes, s.dataBytes, s.cacheBytes)
        }.sortedByDescending { it.total }
        return StorageState(stat.totalBytes, stat.availableBytes, loops, permitted, apps)
    }
}

/** 1536 MB -> "1.5 GB", 230 MB -> "230 MB". */
fun bytesLabel(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024) else String.format(java.util.Locale.US, "%.0f MB", mb)
}
