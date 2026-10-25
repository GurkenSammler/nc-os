package dev.david.braviahome.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.SystemClock
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class NetStatus(
    val wifi: Boolean = false,
    val ethernet: Boolean = false,
    val ssid: String = "",
    val rssi: Int? = null,
    val frequency: Int = 0,
    val linkMbps: Int = 0,
    val ip: String = "",
    val gateway: String = "",
    val dns: List<String> = emptyList(),
)

data class WifiNetwork(val ssid: String, val rssi: Int, val frequency: Int, val security: String, val current: Boolean)

/** 0..4 Balken wie im Status-Modul. */
fun signalLevel(rssi: Int?) = when (rssi) {
    null -> 0
    in -55..0 -> 4
    in -65..-56 -> 3
    in -75..-66 -> 2
    else -> 1
}

fun bandLabel(frequency: Int) = when {
    frequency >= 5925 -> "6 GHZ"
    frequency >= 4900 -> "5 GHZ"
    frequency > 0 -> "2.4 GHZ"
    else -> "--"
}

fun channel(frequency: Int) = when {
    frequency in 2412..2472 -> (frequency - 2407) / 5
    frequency == 2484 -> 14
    frequency in 5000..5895 -> (frequency - 5000) / 5
    frequency >= 5955 -> (frequency - 5950) / 5
    else -> 0
}

/**
 * WLAN/Ethernet fuer SYS.CONFIG > NETWORK. Lesen und Scannen geht mit Standort-Freigabe
 * (install-launcher.ps1); Verbinden und Passwoerter bleiben bei Androids eigener Seite.
 */
class NetworkInfo(private val context: Context) {

    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    fun canScan(): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION") // connectionInfo: der einfachste Weg zu SSID/RSSI ohne NetworkCallback
    fun status(): NetStatus {
        val net = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(net)
        val props = cm?.getLinkProperties(net)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val info = if (isWifi) wifi?.connectionInfo else null
        return NetStatus(
            wifi = isWifi,
            ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
            ssid = info?.ssid?.takeIf { it != WifiManager.UNKNOWN_SSID }?.trim('"').orEmpty(),
            rssi = info?.rssi,
            frequency = info?.frequency ?: 0,
            linkMbps = info?.linkSpeed ?: 0,
            ip = props?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress.orEmpty(),
            gateway = props?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress.orEmpty(),
            dns = props?.dnsServers?.sortedBy { it !is Inet4Address }?.mapNotNull { it.hostAddress }.orEmpty(),
        )
    }

    /** Neuer Scan (Android drosselt auf ca. 4 pro 2 Minuten). */
    @Suppress("DEPRECATION")
    fun startScan(): Boolean = canScan() && wifi?.startScan() == true

    /** Netze in der Naehe, pro Name nur der staerkste Sender, das eigene zuerst. */
    fun nearby(currentSsid: String): List<WifiNetwork> {
        if (!canScan()) return emptyList()
        val results = runCatching { wifi?.scanResults }.getOrNull().orEmpty()
        @Suppress("DEPRECATION")
        return results
            .filter { it.SSID.isNotBlank() }
            .groupBy { it.SSID }
            .map { (ssid, list) ->
                val best = list.maxBy { it.level }
                WifiNetwork(ssid, best.level, best.frequency, security(best.capabilities), ssid == currentSsid)
            }
            .sortedWith(compareByDescending<WifiNetwork> { it.current }.thenByDescending { it.rssi })
    }

    private fun security(caps: String) = when {
        "SAE" in caps && "PSK" in caps -> "WPA2/3"
        "SAE" in caps -> "WPA3"
        "EAP" in caps -> "ENTERPRISE"
        "PSK" in caps -> "WPA2"
        "WEP" in caps -> "WEP"
        else -> "OPEN"
    }

    /** Antwortzeit Router (TCP-Verbindung) und Internet (HTTPS-Abruf), in ms; null = keine Antwort. */
    fun latency(gateway: String): Pair<Int?, Int?> {
        val router = gateway.takeIf { it.isNotEmpty() }?.let { gw ->
            listOf(80, 53, 443).firstNotNullOfOrNull { port -> timed { Socket().use { it.connect(InetSocketAddress(gw, port), 1_500) } } }
        }
        val internet = timed {
            val conn = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpsURLConnection
            try {
                conn.connectTimeout = 3_000
                conn.readTimeout = 3_000
                check(conn.responseCode in 200..299)
            } finally {
                conn.disconnect()
            }
        }
        return router to internet
    }

    private inline fun timed(block: () -> Unit): Int? {
        val start = SystemClock.elapsedRealtime()
        return runCatching { block() }.map { (SystemClock.elapsedRealtime() - start).toInt() }.getOrNull()
    }
}
