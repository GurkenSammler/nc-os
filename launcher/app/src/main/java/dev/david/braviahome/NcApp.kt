package dev.david.braviahome

import android.app.Application
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.SettingsStore
import dev.david.braviahome.system.SonyTv
import dev.david.braviahome.ui.GlitchTiming
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Laedt die Launcher-Einstellungen einmal fuer den ganzen Prozess und haelt [Live] aktuell.
 * Launcher, Schnellmenue und Bildschirmschoner lesen alle daraus.
 */
class NcApp : Application() {

    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        SonyTv.init(this)
        scope.launch {
            SettingsStore(this@NcApp).flow.collect { s ->
                Live.settings = s
                GlitchTiming.apply(s.glitch)
            }
        }
    }
}
