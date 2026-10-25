package dev.david.braviahome.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager

data class BtDevice(val name: String, val address: String, val kind: String, val connected: Boolean, val battery: Int?)

data class BtState(
    val permitted: Boolean = false,
    val enabled: Boolean = false,
    val adapterName: String = "",
    val devices: List<BtDevice> = emptyList(),
)

/**
 * Gekoppelte Bluetooth-Geraete fuer SYS.CONFIG > BLUETOOTH (nur lesen; Koppeln bleibt bei Android).
 * Verbindung und Akku sind in Android 12 nicht oeffentlich: erst per Reflection, sonst ueber GATT.
 */
class BluetoothInfo(private val context: Context) {

    private val manager = context.getSystemService(BluetoothManager::class.java)

    fun permitted(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // geprueft in permitted()
    fun read(): BtState {
        val adapter = manager?.adapter ?: return BtState(permitted = permitted())
        if (!permitted()) return BtState(permitted = false, enabled = adapter.isEnabled)
        val gatt = runCatching { manager.getConnectedDevices(BluetoothProfile.GATT).map { it.address }.toSet() }.getOrDefault(emptySet())
        val devices = adapter.bondedDevices.orEmpty().map { d ->
            val name = d.name ?: d.address
            BtDevice(
                name = name,
                address = d.address,
                kind = kind(d, name),
                connected = hidden<Boolean>(d, "isConnected") ?: (d.address in gatt),
                battery = hidden<Int>(d, "getBatteryLevel")?.takeIf { it in 0..100 },
            )
        }.sortedWith(compareByDescending<BtDevice> { it.connected }.thenBy { it.name.lowercase() })
        return BtState(true, adapter.isEnabled, adapter.name.orEmpty(), devices)
    }

    private fun kind(d: BluetoothDevice, name: String): String {
        val lower = name.lowercase()
        return when {
            "remote" in lower || "rmf" in lower || "vrc" in lower -> "REMOTE"
            "controller" in lower || "pad" in lower || "xbox" in lower || "dualsense" in lower -> "GAMEPAD"
            else -> when (runCatching { d.bluetoothClass?.majorDeviceClass }.getOrNull()) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> "AUDIO"
                BluetoothClass.Device.Major.PERIPHERAL -> "INPUT"
                BluetoothClass.Device.Major.PHONE -> "PHONE"
                BluetoothClass.Device.Major.COMPUTER -> "COMPUTER"
                else -> "DEVICE"
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> hidden(d: BluetoothDevice, method: String): T? =
        runCatching { BluetoothDevice::class.java.getMethod(method).invoke(d) as T }.getOrNull()
}
