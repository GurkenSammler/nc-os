package dev.david.braviahome.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.net.Inet4Address

/** Echte TV-Werte fuer die kleinen Technik-Labels und das Status-Panel. */
data class SystemInfo(
    val ip: String = "--",
    val wifiDbm: Int? = null,
    val uptimeMs: Long = 0,
    val freeBytes: Long = 0,
    val totalBytes: Long = 0,
    val disabledPackages: Int = 0,
    val android: String = Build.VERSION.RELEASE,
    val build: String = Build.ID,
    val model: String = Build.MODEL,
    val launcherVersion: String = "",
)

class SystemInfoRepository(private val context: Context) {

    private val launcherVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private var disabledCount = 0
    private var disabledCountedAt = 0L

    fun read(): SystemInfo {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val ip = runCatching {
            cm?.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address }
                ?.hostAddress
        }.getOrNull() ?: "OFFLINE"

        @Suppress("DEPRECATION")
        val rssi = runCatching {
            context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.rssi
        }.getOrNull()?.takeIf { it in -126..-1 }

        val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()

        return SystemInfo(
            ip = ip,
            wifiDbm = rssi,
            uptimeMs = SystemClock.elapsedRealtime(),
            freeBytes = stat?.availableBytes ?: 0L,
            totalBytes = stat?.totalBytes ?: 0L,
            disabledPackages = countDisabled(),
            launcherVersion = launcherVersion,
        )
    }

    /** Ausgeschaltete Pakete (das Debloat). Teuer genug, um nur einmal pro Minute zu zaehlen. */
    private fun countDisabled(): Int {
        val now = SystemClock.elapsedRealtime()
        if (now - disabledCountedAt > 60_000 || disabledCountedAt == 0L) {
            disabledCount = runCatching {
                context.packageManager
                    .getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
                    .count { !it.enabled }
            }.getOrDefault(disabledCount)
            disabledCountedAt = now
        }
        return disabledCount
    }
}
