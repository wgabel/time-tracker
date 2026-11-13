package pl.wgabel.timetracker

import android.content.res.Resources
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats user-facing timestamps using the application's effective locale.
 * Create formatters on demand: DateFormat/SimpleDateFormat are mutable and not thread-safe.
 * Persisted timestamps and machine-readable CSV dates must remain locale-independent.
 */
internal object DatePresentation {
    fun locale(resources: Resources): Locale = resources.configuration.locales[0]

    fun date(resources: Resources): DateFormat =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale(resources))

    fun dateTime(resources: Resources): DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale(resources))

    fun shortDateTime(resources: Resources): DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale(resources))

    fun time(resources: Resources): DateFormat =
        DateFormat.getTimeInstance(DateFormat.MEDIUM, locale(resources))

    fun shortTime(resources: Resources): DateFormat =
        DateFormat.getTimeInstance(DateFormat.SHORT, locale(resources))

    fun uses24HourClock(resources: Resources): Boolean {
        val pattern = (shortTime(resources) as? SimpleDateFormat)?.toPattern() ?: return true
        val hourSymbols = pattern.filter { it == 'H' || it == 'k' || it == 'h' || it == 'K' }
        return hourSymbols.firstOrNull()?.let { it == 'H' || it == 'k' } ?: true
    }

    fun weekdayHeading(resources: Resources, timestamp: Long): String {
        val locale = locale(resources)
        val pattern = if (locale.language == "pl") "EEEE, d MMMM" else "EEEE, MMMM d"
        return SimpleDateFormat(pattern, locale).format(Date(timestamp))
            .replaceFirstChar { it.titlecase(locale) }
    }

    fun weekdayShort(resources: Resources): DateFormat =
        SimpleDateFormat("EEE", locale(resources))

    fun monthYear(resources: Resources): DateFormat =
        SimpleDateFormat(if (locale.language == "pl") "MM.yy" else "MM/yy", locale(resources))
}
