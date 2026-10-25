package dev.david.braviahome.system

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import androidx.core.graphics.drawable.toBitmap

data class AppDetails(
    val packageName: String,
    val label: String,
    val art: Bitmap?,
    val artIsBanner: Boolean,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    /** Vorinstalliert: [installedAt] ist dann das Platzhalter-Datum des Systemabbilds (2009). */
    val system: Boolean,
    val origin: String,
    val installedAt: Long,
    val updatedAt: Long,
    /** null = Nutzungsstatistik-Freigabe fehlt (dann auch keine Nutzungszeiten). */
    val size: AppSize?,
    val lastUsed: Long,
    val foregroundWeekMs: Long,
    /** Gewaehrte Laufzeit-Freigaben, kurz benannt ("MICROPHONE", "LOCATION"). */
    val permissions: List<String>,
)

/**
 * Daten fuer das eigene APP INFO-Fenster. Groesse und Nutzung brauchen dieselbe
 * Nutzungsstatistik-Freigabe wie SYS.CONFIG > APPS & STORAGE (install-launcher.ps1).
 */
class AppDetailsReader(private val context: Context) {

    fun read(packageName: String): AppDetails? {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS) }.getOrNull() ?: return null
        val app = info.applicationInfo ?: return null
        // Wie die Kacheln: Banner der Startseite, sonst das App-Banner, sonst das Icon.
        val launch = pm.getLeanbackLaunchIntentForPackage(packageName)?.component
        val banner = launch?.let { runCatching { pm.getActivityBanner(it) }.getOrNull() } ?: pm.getApplicationBanner(app)
        val art = runCatching { banner?.toBitmap(480, 270) ?: pm.getApplicationIcon(app).toBitmap(192, 192) }.getOrNull()

        val size = try {
            val s = context.getSystemService(StorageStatsManager::class.java)
                .queryStatsForPackage(StorageManager.UUID_DEFAULT, packageName, Process.myUserHandle())
            AppSize(packageName, "", s.appBytes, s.dataBytes, s.cacheBytes)
        } catch (e: Exception) {
            null
        }
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val month = runCatching { usage.queryAndAggregateUsageStats(now - 30 * DAY_MS, now)[packageName] }.getOrNull()
        val week = runCatching { usage.queryAndAggregateUsageStats(now - 7 * DAY_MS, now)[packageName] }.getOrNull()

        return AppDetails(
            packageName = packageName,
            label = app.loadLabel(pm).toString(),
            art = art,
            artIsBanner = banner != null,
            versionName = info.versionName.orEmpty(),
            versionCode = info.longVersionCode,
            targetSdk = app.targetSdkVersion,
            system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            origin = origin(pm, app, packageName),
            installedAt = info.firstInstallTime,
            updatedAt = info.lastUpdateTime,
            size = size,
            lastUsed = month?.lastTimeUsed ?: 0,
            foregroundWeekMs = week?.totalTimeInForeground ?: 0,
            permissions = grantedPermissions(pm, info),
        )
    }

    /** "SYSTEM APP // UPDATED VIA PLAY STORE", "USER APP // ADB" ... */
    private fun origin(pm: PackageManager, app: ApplicationInfo, packageName: String): String {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()
        val source = when (installer) {
            null -> null
            "com.android.vending" -> "PLAY STORE"
            "com.android.shell" -> "ADB"
            "com.android.packageinstaller", "com.google.android.packageinstaller" -> "PACKAGE INSTALLER"
            else -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(installer, 0)).toString().uppercase() }.getOrDefault(installer)
        }
        val system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val updated = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        return when {
            system && updated -> "SYSTEM APP // UPDATED" + (source?.let { " VIA $it" } ?: "")
            system -> "SYSTEM APP // PREINSTALLED"
            else -> "USER APP // " + (source ?: "UNKNOWN SOURCE")
        }
    }

    private fun grantedPermissions(pm: PackageManager, info: PackageInfo): List<String> {
        val names = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags ?: return emptyList()
        return names.indices
            .filter { (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
            .map { names[it] }
            .filter { runCatching { pm.getPermissionInfo(it, 0).protection == PermissionInfo.PROTECTION_DANGEROUS }.getOrDefault(false) }
            .map(::permissionLabel)
            .distinct()
    }

    private fun permissionLabel(permission: String): String = when (val name = permission.substringAfterLast('.')) {
        "RECORD_AUDIO" -> "MICROPHONE"
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION" -> "LOCATION"
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "ACCESS_MEDIA_LOCATION",
        "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO" -> "STORAGE"
        "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS" -> "CONTACTS"
        "READ_CALENDAR", "WRITE_CALENDAR" -> "CALENDAR"
        "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "CALL_PHONE" -> "PHONE"
        "BLUETOOTH_CONNECT", "BLUETOOTH_SCAN", "BLUETOOTH_ADVERTISE", "NEARBY_WIFI_DEVICES" -> "NEARBY DEVICES"
        "POST_NOTIFICATIONS" -> "NOTIFICATIONS"
        else -> name.replace('_', ' ')
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
