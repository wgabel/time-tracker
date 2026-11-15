package pl.wgabel.timetracker.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** One editable wall-clock value for the scale, hour/minute controls and date/second details. */
internal class DateTimeSelection(initialTimestamp: Long, val zoneId: ZoneId = ZoneId.systemDefault()) {
    enum class ValidationError { MISSING_LOCAL_TIME, BEFORE_MINIMUM, AFTER_MAXIMUM }

    var dateTime: LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(initialTimestamp), zoneId)
        private set
    private var preferredOffset = Instant.ofEpochMilli(initialTimestamp).atZone(zoneId).offset

    val minuteOfDay: Int get() = dateTime.hour * 60 + dateTime.minute
    val availableOffsets: List<ZoneOffset> get() = zoneId.rules.getValidOffsets(dateTime)
    val selectedOffset: ZoneOffset? get() = availableOffsets.firstOrNull { it == preferredOffset } ?: availableOffsets.firstOrNull()
    val timestamp: Long? get() = selectedOffset?.let { dateTime.toInstant(it).toEpochMilli() }

    fun selectMinuteOfDay(value: Int) {
        val bounded = value.coerceIn(0, 1_439)
        dateTime = dateTime.withHour(bounded / 60).withMinute(bounded % 60)
    }

    fun selectMinuteFromScale(value: Int, stepMinutes: Int = 5) {
        require(stepMinutes in 1..60)
        val bounded = value.coerceIn(0, 1_439)
        selectMinuteOfDay(((bounded + stepMinutes / 2) / stepMinutes) * stepMinutes)
    }

    fun adjustHours(amount: Int) {
        dateTime = dateTime.withHour((dateTime.hour.toLong() + amount).coerceIn(0, 23).toInt())
    }
    fun adjustMinutes(amount: Int) = adjustMinutesWithinDay(amount.toLong())

    private fun adjustMinutesWithinDay(amount: Long) {
        selectMinuteOfDay((minuteOfDay.toLong() + amount).coerceIn(0, 1_439).toInt())
    }

    fun adjustSeconds(amount: Int) {
        val seconds = (dateTime.toLocalTime().toSecondOfDay().toLong() + amount).coerceIn(0, 86_399).toInt()
        dateTime = dateTime.withHour(seconds / 3_600).withMinute(seconds / 60 % 60).withSecond(seconds % 60).withNano(0)
    }

    fun selectDate(date: LocalDate) { dateTime = LocalDateTime.of(date, dateTime.toLocalTime()) }

    fun selectTimestamp(timestamp: Long) {
        val zoned = Instant.ofEpochMilli(timestamp).atZone(zoneId)
        dateTime = zoned.toLocalDateTime()
        preferredOffset = zoned.offset
    }

    fun selectOffset(offset: ZoneOffset) {
        require(offset in availableOffsets)
        preferredOffset = offset
    }

    fun validationError(minimum: Long?, maximum: Long?): ValidationError? {
        val selected = timestamp ?: return ValidationError.MISSING_LOCAL_TIME
        return when {
            minimum != null && selected < minimum -> ValidationError.BEFORE_MINIMUM
            maximum != null && selected > maximum -> ValidationError.AFTER_MAXIMUM
            else -> null
        }
    }
}
