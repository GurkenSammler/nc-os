package dev.david.braviahome.quick

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import dev.david.braviahome.Actions
import dev.david.braviahome.HomeActivity
import dev.david.braviahome.data.InputEntry
import dev.david.braviahome.data.InputRepository
import dev.david.braviahome.data.Live
import dev.david.braviahome.data.displayInputs
import dev.david.braviahome.sound.LocalSounds
import dev.david.braviahome.sound.Sfx
import dev.david.braviahome.sound.UiSounds
import dev.david.braviahome.system.SettingsLink
import dev.david.braviahome.system.TvControls
import dev.david.braviahome.ui.theme.BraviaTheme

/**
 * Ersetzt Sonys Schnelleinstellungen: faengt die Zahnrad-Taste der Fernbedienung ab, bevor
 * Sony sie verarbeitet (Bedienungshilfe-Dienste sehen Tasten vor interceptKeyBeforeDispatching),
 * und zeigt stattdessen ein eigenes Panel ueber der laufenden App. Die App laeuft weiter.
 * Aktiviert per adb in scripts\install-launcher.ps1, abgeschaltet von restore.ps1.
 */
class QuickPanelService : AccessibilityService() {

    private lateinit var windows: WindowManager
    private lateinit var sounds: UiSounds
    lateinit var controls: TvControls
        private set
    private val handler = Handler(Looper.getMainLooper())

    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var lastInput = 0L

    /** Sleep-Timer: Zeitpunkt (elapsedRealtime) fuer Standby, 0 = aus. Compose liest direkt mit. */
    val sleepAt = mutableLongStateOf(0L)
    private val sleepRunnable = Runnable {
        sleepAt.longValue = 0L
        hide()
        controls.standby()
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            setSleepTimer(0)
            hide()
        }
    }

    override fun onServiceConnected() {
        instance = this
        windows = getSystemService(WindowManager::class.java)
        sounds = UiSounds(this)
        controls = TvControls(this)
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_EXPORTED)
        Log.i(TAG, "Schnellmenue-Dienst aktiv")
    }

    // --- Taste anlernen (SYS.CONFIG > QUICK MENU > MENU KEY) ---
    private var learnCallback: ((Int) -> Unit)? = null
    private var swallowScan = -1

    /** Die naechste Taste (ausser Navigation) wird zur Schnellmenue-Taste; [onLearned] bekommt ihren Scancode. */
    fun learnKey(onLearned: (Int) -> Unit) {
        learnCallback = onLearned
    }

    fun cancelLearn() {
        learnCallback = null
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        learnCallback?.let { learned ->
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode !in NAVIGATION_KEYS && event.scanCode > 0) {
                learnCallback = null
                swallowScan = event.scanCode
                Log.i(TAG, "Neue Schnellmenue-Taste: scan=0x%02X keyCode=%d".format(event.scanCode, event.keyCode))
                learned(event.scanCode)
                return true
            }
        }
        if (event.scanCode == swallowScan && event.action == KeyEvent.ACTION_UP) {
            swallowScan = -1
            return true
        }

        val settings = Live.settings
        if (settings.quickEnabled && event.scanCode == settings.quickScan) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (view == null) show() else hide()
            }
            return true // Sonys Panel bekommt die Taste nie zu sehen
        }
        if (view == null) return false

        lastInput = SystemClock.uptimeMillis()
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) hide()
                true
            }
            KeyEvent.KEYCODE_HOME -> {
                if (event.action == KeyEvent.ACTION_DOWN) hide()
                false // Home selbst soll weiter funktionieren
            }
            else -> false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun show() {
        if (view != null) return
        val compose = ComposeView(this)
        val lifecycle = OverlayOwner().also { it.attachTo(compose) }
        compose.setContent {
            BraviaTheme {
                CompositionLocalProvider(LocalSounds provides sounds) {
                    QuickPanel(this)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.END }

        windows.addView(compose, params)
        view = compose
        owner = lifecycle
        lastInput = SystemClock.uptimeMillis()
        sounds.play(Sfx.MENU_OPEN)
        handler.postDelayed(idleCheck, IDLE_CHECK_MS)
    }

    fun hide() {
        val v = view ?: return
        handler.removeCallbacks(idleCheck)
        runCatching { windows.removeViewImmediate(v) }
        owner?.destroy()
        view = null
        owner = null
        sounds.play(Sfx.MENU_CLOSE)
    }

    /** Wie Sonys Panel: schliesst sich nach einer Weile ohne Eingabe von selbst. */
    private val idleCheck = object : Runnable {
        override fun run() {
            if (view == null) return
            if (SystemClock.uptimeMillis() - lastInput > IDLE_CLOSE_MS) hide() else handler.postDelayed(this, IDLE_CHECK_MS)
        }
    }

    /** Eingabe im Panel: Auto-Schliessen neu starten. */
    fun touch() {
        lastInput = SystemClock.uptimeMillis()
    }

    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            sleepAt.longValue = 0L
        } else {
            sleepAt.longValue = SystemClock.elapsedRealtime() + minutes * 60_000L
            handler.postDelayed(sleepRunnable, minutes * 60_000L)
        }
    }

    fun play(sfx: Sfx) = sounds.play(sfx)

    fun openSetting(link: SettingsLink) {
        hide()
        controls.openSetting(link)
    }

    fun startScreensaver() {
        hide()
        controls.startScreensaver()
    }

    fun openConfig() {
        hide()
        startActivity(
            Intent(this, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_OPEN_CONFIG, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun standby() {
        hide()
        controls.standby()
    }

    /** HDMI-Eingaenge mit eigenen Namen, ohne ausgeblendete (fuer die Eingangs-Reihe im Panel). */
    fun inputs(): List<InputEntry> = displayInputs(InputRepository(this).load(), Live.settings)

    fun openInput(input: InputEntry) {
        hide()
        Actions.openInput(this, input)
    }

    override fun onDestroy() {
        instance = null
        hide()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOff) }
        sounds.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BraviaQuick"

        /** Laufender Dienst, null wenn nicht aktiviert. Fuer den Test-Schalter in HomeActivity. */
        var instance: QuickPanelService? = null
            private set

        /** Tasten, die beim Anlernen nicht zaehlen (sonst lernt man die Navigation an). */
        private val NAVIGATION_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
        )
        private const val IDLE_CLOSE_MS = 30_000L
        private const val IDLE_CHECK_MS = 2_000L
    }
}
