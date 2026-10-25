package dev.david.braviahome.data

import androidx.compose.ui.graphics.ImageBitmap

/** Eine startbare TV-App. [art] ist das Banner (16:9) oder, falls keins da ist, das Icon. */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val art: ImageBitmap,
    val artIsBanner: Boolean,
)

/** Ein HDMI-Eingang mit dem Namen, den Sony anzeigt (z. B. "HDMI 2/ARC" oder eigener Name). */
data class InputEntry(
    val id: String,
    val label: String,
)

/** Reihenfolge und ausgeblendete Apps. Pakete, die nicht in [order] stehen, kommen hinten dran. */
data class Layout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
)

/** Eingaenge nach SYS.CONFIG > INPUTS: eigene Namen, ausgeblendete weg (ausser [includeHidden]). */
fun displayInputs(inputs: List<InputEntry>, settings: LauncherSettings, includeHidden: Boolean = false): List<InputEntry> =
    inputs
        .filter { includeHidden || it.id !in settings.hiddenInputs }
        .map { input -> settings.inputNames[input.id]?.takeIf { it.isNotBlank() }?.let { input.copy(label = it) } ?: input }

/** Teilt die Apps nach [layout] in sichtbare (sortiert) und ausgeblendete auf. */
fun arrange(apps: List<AppEntry>, layout: Layout): Pair<List<AppEntry>, List<AppEntry>> {
    val byPackage = apps.associateBy { it.packageName }
    val known = layout.order.toSet()
    val ordered = layout.order.mapNotNull { byPackage[it] } + apps.filter { it.packageName !in known }
    return ordered.partition { it.packageName !in layout.hidden }
}
