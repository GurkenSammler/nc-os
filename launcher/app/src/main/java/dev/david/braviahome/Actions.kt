package dev.david.braviahome

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import dev.david.braviahome.data.AppEntry
import dev.david.braviahome.data.InputEntry

/** Alles, was der Launcher startet. Fehler landen als kurzer Hinweis auf dem Bildschirm. */
object Actions {

    fun openApp(context: Context, app: AppEntry) = start(
        context,
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setClassName(app.packageName, app.activityName),
    )

    /** Schaltet auf einen HDMI-Eingang; das Bild zeigt Sonys TV-App. */
    fun openInput(context: Context, input: InputEntry) = start(
        context,
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)),
    )

    /** App nur ueber den Paketnamen starten (Start-Ziel). */
    fun openPackage(context: Context, packageName: String) {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, "App nicht gefunden: $packageName", Toast.LENGTH_SHORT).show()
            return
        }
        start(context, intent)
    }

    /** Start-Ziel aus SYS.CONFIG > INPUTS & START: "" = nichts, "input:<id>", "app:<paket>". */
    fun openStartTarget(context: Context, target: String) {
        when {
            target.startsWith(TARGET_INPUT) -> openInput(context, InputEntry(target.removePrefix(TARGET_INPUT), ""))
            target.startsWith(TARGET_APP) -> openPackage(context, target.removePrefix(TARGET_APP))
        }
    }

    const val TARGET_INPUT = "input:"
    const val TARGET_APP = "app:"

    fun openSettings(context: Context) = start(context, Intent(Settings.ACTION_SETTINGS))

    fun openAppInfo(context: Context, packageName: String) = start(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )

    private fun start(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Konnte nicht gestartet werden", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Keine Berechtigung zum Starten", Toast.LENGTH_SHORT).show()
        }
    }
}
