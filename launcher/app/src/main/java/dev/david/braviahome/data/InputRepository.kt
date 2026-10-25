package dev.david.braviahome.data

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Die HDMI-Anschluesse des TVs (nur die echten Ports, keine CEC-Untergeraete). */
class InputRepository(private val context: Context) {

    private val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager

    fun load(): List<InputEntry> {
        val inputs = runCatching { manager?.tvInputList }.getOrNull().orEmpty()
        return inputs
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(context) }
            .map { info ->
                val label = info.loadCustomLabel(context) ?: info.loadLabel(context)
                InputEntry(id = info.id, label = label.toString())
            }
            .sortedBy { it.label }
    }
}
