package dev.david.braviahome.dream

import android.service.dreams.DreamService
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout

/**
 * Eigener Bildschirmschoner (ersetzt Sonys Ambient Mode). Stil aus SYS.CONFIG > SCREENSAVER.
 * Aktivieren: scripts\install-launcher.ps1 setzt screensaver_components auf diesen Dienst.
 * Aufbau: unten die Videoflaeche (nur bei VIDEO LOOP sichtbar), darueber die [DreamView].
 */
class ScreensaverService : DreamService() {

    private var loop: LoopPlayer? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val surface = SurfaceView(this).apply { visibility = View.GONE }
        val player = LoopPlayer(this, surface).also { loop = it }
        setContentView(FrameLayout(this).apply {
            addView(surface, MATCH_PARENT, MATCH_PARENT)
            addView(DreamView(this@ScreensaverService, player), MATCH_PARENT, MATCH_PARENT)
        })
    }

    override fun onDetachedFromWindow() {
        loop?.stop()
        loop = null
        super.onDetachedFromWindow()
    }
}
