package dev.david.braviahome.system

import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast

/** Lautstaerke, Bildschirmschoner, Standby und Spruenge in die Sony-/Android-Einstellungen. */
class TvControls(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, StandbyAdmin::class.java)

    data class Volume(val level: Int, val max: Int, val muted: Boolean)

    fun volume(): Volume = Volume(
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )

    fun changeVolume(up: Boolean) {
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            0,
        )
    }

    fun toggleMute() {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
    }

    /** Minuten bis zum Bildschirmschoner (Settings.System.SCREEN_OFF_TIMEOUT). */
    fun screensaverMinutes(): Int =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 600_000) / 60_000

    /** Braucht die einmalige Freigabe WRITE_SETTINGS (per adb, siehe install-launcher.ps1). */
    fun canChangeScreensaver(): Boolean = Settings.System.canWrite(context)

    fun setScreensaverMinutes(minutes: Int): Boolean = runCatching {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60_000)
    }.getOrDefault(false)

    /** Startet den Bildschirmschoner sofort (dieselbe Aktion wie "Ok Google, schlafen"). */
    fun startScreensaver() = open(Intent("com.google.android.pano.action.SLEEP"))

    fun canStandby(): Boolean = dpm.isAdminActive(admin)

    fun standby() {
        if (canStandby()) dpm.lockNow() else toast("Standby nicht freigegeben")
    }

    fun openSetting(link: SettingsLink) {
        val intent = Intent(link.action)
        link.packageName?.let { intent.setPackage(it) }
        open(intent)
    }

    private fun open(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Nicht verfuegbar")
        } catch (e: SecurityException) {
            toast("Keine Berechtigung")
        }
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

/** Seiten der echten Einstellungen, auf dem X80J geprueft (cmd package resolve-activity). */
enum class SettingsLink(val title: String, val source: String, val action: String, val packageName: String? = null) {
    PICTURE("Picture", "SONY", "com.sony.dtv.base.picturesettings"),
    SOUND("Sound", "SONY", "com.sony.dtv.base.soundsettings"),
    SCREEN("Screen", "SONY", "com.sony.dtv.base.screensettings"),
    AUDIO_SYSTEM("Audio System", "SONY", "com.sony.dtv.base.audiosystemsettings"),
    NETWORK("Network", "ANDROID", "android.settings.NETWORK_SETTINGS_TV_OEM_LINK", TV_SETTINGS),
    BLUETOOTH("Remotes & BT", "ANDROID", "com.google.android.intent.action.CONNECT_INPUT", TV_SETTINGS),
    APPS("Apps", "ANDROID", "android.settings.MANAGE_APPLICATIONS_SETTINGS", TV_SETTINGS),
    STORAGE("Storage", "ANDROID", "android.settings.INTERNAL_STORAGE_SETTINGS", TV_SETTINGS),
    DATE("Date & Time", "ANDROID", "android.settings.DATE_SETTINGS", TV_SETTINGS),
    LANGUAGE("Language", "ANDROID", "android.settings.LOCALE_SETTINGS", TV_SETTINGS),
    ACCESSIBILITY("Accessibility", "ANDROID", "android.settings.ACCESSIBILITY_SETTINGS", TV_SETTINGS),
    LED("LED", "SONY", "com.sony.dtv.settings.action.LED_INDICATOR", TV_SETTINGS),
    ABOUT("Device Info", "ANDROID", "android.settings.DEVICE_INFO_SETTINGS", TV_SETTINGS),
    ALL("All Settings", "ANDROID", "android.settings.SETTINGS", TV_SETTINGS),
}

private const val TV_SETTINGS = "com.android.tv.settings"
