package dev.david.braviahome.ui

import dev.david.braviahome.data.DateStyle
import dev.david.braviahome.data.Live
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uhrzeit und Datum nach SYS.CONFIG > LAYOUT (24/12 h, EN/DE/ISO). Ueberall gleich. */
object Formats {
    private val t24 = SimpleDateFormat("HH:mm", Locale.US)
    private val t12 = SimpleDateFormat("hh:mm", Locale.US)
    private val ampm = SimpleDateFormat("a", Locale.US)
    private val dateEn = SimpleDateFormat("EEE dd MMM yyyy", Locale.US)
    private val dateDe = SimpleDateFormat("EE dd.MM.yyyy", Locale.GERMANY)
    private val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun time(millis: Long): String = (if (Live.settings.clock24) t24 else t12).format(Date(millis))

    /** "PM" bei 12 h, sonst leer. */
    fun suffix(millis: Long): String = if (Live.settings.clock24) "" else ampm.format(Date(millis))

    fun date(millis: Long): String = when (Live.settings.dateStyle) {
        DateStyle.EN -> dateEn
        DateStyle.DE -> dateDe
        DateStyle.ISO -> dateIso
    }.format(Date(millis)).uppercase()
}
