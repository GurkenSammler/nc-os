package dev.david.braviahome.quick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.david.braviahome.data.SaverStyle
import dev.david.braviahome.data.SettingsStore
import dev.david.braviahome.system.SonyTv
import kotlinx.coroutines.runBlocking

/**
 * Test-Schalter fuer adb (nur Absender mit DUMP-Berechtigung, siehe Manifest):
 *   am broadcast -n dev.david.braviahome/.quick.QuickTestReceiver                 Schnellmenue auf
 *   ... --ez hide true                                                             Schnellmenue zu
 *   ... --es saver CODE_RAIN [--ei shuffle 1]                                      Schoner-Stil setzen
 *   ... --es saver VIDEO_LOOP [--es clip <datei>|ALL] [--ez sound true]            Video-Loop waehlen
 *   ... --es psk <schluessel>                                                      Schluessel fuer Sonys API (enable-sony-api.ps1)
 * Die echte Zahnrad-Taste und das Menue-Navigieren lassen sich per adb schlecht nachstellen.
 */
class QuickTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("psk")?.let { key ->
            runBlocking { SonyTv.setPsk(key) }
            return
        }
        intent.getStringExtra("saver")?.let { name ->
            val style = SaverStyle.entries.firstOrNull { it.name == name } ?: return
            val shuffle = intent.getIntExtra("shuffle", 0)
            val clip = intent.getStringExtra("clip")
            runBlocking {
                SettingsStore(context).update { s ->
                    s.copy(
                        saverStyle = style,
                        shuffleMinutes = if (shuffle > 0) shuffle else s.shuffleMinutes,
                        saverClip = if (clip == null) s.saverClip else if (clip == "ALL") "" else clip,
                        saverClipSound = intent.getBooleanExtra("sound", s.saverClipSound),
                    )
                }
            }
            return
        }
        QuickPanelService.instance?.let { if (intent.getBooleanExtra("hide", false)) it.hide() else it.show() }
    }
}
