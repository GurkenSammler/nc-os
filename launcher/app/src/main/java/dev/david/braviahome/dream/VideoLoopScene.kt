package dev.david.braviahome.dream

import android.content.Context
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.io.File

/**
 * Eigene Video-Loops fuer den Schoner (Stil VIDEO LOOP). Sie liegen im App-Ordner auf dem TV
 * (`/sdcard/Android/data/<paket>/files/loops`) und kommen per `scripts\push-loops.ps1` dorthin.
 * Nicht in der APK: Videos sind gross und gehoeren nicht ins Repo.
 */
object LoopClips {
    private val EXTENSIONS = setOf("mp4", "mkv", "webm", "mov")

    fun list(context: Context): List<File> =
        context.getExternalFilesDir("loops")
            ?.listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    /** Lesbarer Name fuer SYS.CONFIG: ohne Endung, Trennzeichen als Leerzeichen. */
    fun label(name: String) = name.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').uppercase()
}

/**
 * Spielt einen Clip endlos auf einer [SurfaceView] unter der [DreamView] ab. ExoPlayer statt
 * MediaPlayer, weil er die Schleife ohne Ruckler am Ende schliesst. Ton ist standardmaessig aus
 * (die Tonspur wird dann gar nicht erst dekodiert).
 */
@OptIn(UnstableApi::class)
class LoopPlayer(private val context: Context, private val surface: SurfaceView) {

    private var player: ExoPlayer? = null

    fun play(file: File, sound: Boolean) {
        val p = player ?: ExoPlayer.Builder(context).build().also { p ->
            player = p
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            p.setVideoSurfaceView(surface)
            p.addAnalyticsListener(object : AnalyticsListener {
                override fun onDroppedVideoFrames(time: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                    Log.i(TAG, "dropped $droppedFrames frames in $elapsedMs ms")
                }

                override fun onPlayerError(time: AnalyticsListener.EventTime, error: PlaybackException) {
                    Log.w(TAG, "playback failed: ${error.errorCodeName}", error)
                }
            })
        }
        surface.visibility = View.VISIBLE
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !sound)
            .build()
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        p.prepare()
        p.playWhenReady = true
        Log.i(TAG, "loop ${file.name} sound=$sound")
    }

    fun stop() {
        player?.release()
        player = null
        surface.visibility = View.GONE
    }

    private companion object {
        const val TAG = "NcLoop"
    }
}

/**
 * VIDEO LOOP: das Video liegt unter dieser Szene, sie selbst zeichnet nichts (transparent),
 * damit Uhr, Glitch und Scanlines der [DreamView] direkt ueber dem Video liegen.
 */
class VideoLoopScene(private val player: LoopPlayer, private var clip: File, private val sound: Boolean) : DreamScene {

    private var started = false

    override val frameMs = 1_000L

    override fun setup(w: Int, h: Int, density: Float) {
        if (started) return
        started = true
        player.play(clip, sound)
    }

    /** Naechster Clip im selben Player (kein Neuaufbau der Flaeche). */
    fun show(next: File) {
        clip = next
        player.play(next, sound)
    }

    override fun draw(canvas: Canvas, nowMs: Long) {}

    override fun release() {
        player.stop()
    }
}
