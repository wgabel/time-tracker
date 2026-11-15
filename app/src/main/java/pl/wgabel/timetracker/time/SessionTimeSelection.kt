package pl.wgabel.timetracker.time

import java.time.ZoneId

/** Two independent drafts edited in one dialog; switching the visible boundary never commits either. */
internal class SessionTimeSelection(started: Long, ended: Long, zoneId: ZoneId = ZoneId.systemDefault()) {
    enum class Boundary { START, END }
    enum class ValidationError {
        START_MISSING_LOCAL_TIME, END_MISSING_LOCAL_TIME,
        START_AFTER_MAXIMUM, END_AFTER_MAXIMUM, END_NOT_AFTER_START
    }

    val start = DateTimeSelection(started, zoneId)
    val end = DateTimeSelection(ended, zoneId)
    var selectedBoundary = Boundary.START
        private set
    val selection: DateTimeSelection get() = if (selectedBoundary == Boundary.START) start else end

    fun selectBoundary(boundary: Boundary) { selectedBoundary = boundary }

    fun validationError(maximum: Long?): ValidationError? {
        val started = start.timestamp ?: return ValidationError.START_MISSING_LOCAL_TIME
        val ended = end.timestamp ?: return ValidationError.END_MISSING_LOCAL_TIME
        return when {
            maximum != null && started > maximum -> ValidationError.START_AFTER_MAXIMUM
            maximum != null && ended > maximum -> ValidationError.END_AFTER_MAXIMUM
            ended <= started -> ValidationError.END_NOT_AFTER_START
            else -> null
        }
    }
}
