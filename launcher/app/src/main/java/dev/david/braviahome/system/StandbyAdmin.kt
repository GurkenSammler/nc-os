package dev.david.braviahome.system

import android.app.admin.DeviceAdminReceiver

/**
 * Geraeteadministrator mit genau einem Recht ("Bildschirm sperren"). Damit kann der Launcher
 * den TV in Standby schicken (DevicePolicyManager.lockNow). Aktiviert per adb in
 * scripts\install-launcher.ps1, entfernt von scripts\restore.ps1.
 */
class StandbyAdmin : DeviceAdminReceiver()
