package dev.david.braviahome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.david.braviahome.data.SettingsStore
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.UiSounds
import dev.david.braviahome.ui.GlitchTiming
import dev.david.braviahome.ui.LauncherRoot
import dev.david.braviahome.ui.theme.BraviaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var sounds: UiSounds

    // Boot-Sequenz beim Kaltstart und beim ersten Erscheinen nach dem Einschalten.
    // Gemerkt wird "Bildschirm aus": SCREEN_ON kommt beim Aufwachen erst NACH onResume an
    // (am X80J gemessen: onResume 43.324, SCREEN_ON 43.368) und waere damit zu spaet.
    private var bootPending = true
    private var wokeFromSleep = false
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            bootPending = true
            wokeFromSleep = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sounds = UiSounds(this)
        // Test: adb shell am start -n dev.david.braviahome/.HomeActivity --ei bootMs 15000 (Zeitlupe)
        intent?.getIntExtra("bootMs", 0)?.takeIf { it > 0 }?.let { viewModel.bootDurationMs = it }
        // Test: --ei glitchFrameMs 1500 (Glitch in Zeitlupe, ein Bild pro 1,5 s)
        intent?.getIntExtra("glitchFrameMs", 0)?.takeIf { it > 0 }?.let { GlitchTiming.frameMs = it.toLong() }
        if (intent?.getBooleanExtra(EXTRA_OPEN_CONFIG, false) == true) viewModel.requestConfig()
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED)

        // Auf dem Startbildschirm gibt es kein "Zurueck".
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refreshSystem()
                    delay(10_000)
                }
            }
        }

        setContent {
            BraviaTheme {
                CompositionLocalProvider(LocalSounds provides sounds) {
                    LauncherRoot(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        viewModel.refreshTv()
        if (bootPending) {
            bootPending = false
            val woke = wokeFromSleep
            wokeFromSleep = false
            // Gespeicherte Werte lesen: beim Kaltstart ist Live.settings evtl. noch nicht geladen.
            lifecycleScope.launch {
                val s = SettingsStore(this@HomeActivity).load()
                if (s.bootSequence) {
                    // Nach dem Einschalten braucht das Panel einen Moment, bis es Bild zeigt.
                    // Das Start-Ziel startet am Ende der Boot-Sequenz (HomeScreen).
                    viewModel.requestBoot(delayMs = if (woke) PANEL_WAKE_MS else 0, runStartTarget = true)
                } else {
                    Actions.openStartTarget(this@HomeActivity, s.startTarget)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Aus dem Schnellmenue: direkt SYS.CONFIG statt Startbildschirm.
        if (intent.getBooleanExtra(EXTRA_OPEN_CONFIG, false)) viewModel.requestConfig() else viewModel.onHomePressed()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        sounds.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_CONFIG = "openConfig"
        private const val PANEL_WAKE_MS = 900L
    }
}
