package pl.wgabel.timetracker.design

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

/**
 * Immutable design tokens bundled with the application.
 *
 * Loaded by the application container from assets/design_tokens.json. This is not a remote
 * configuration and does not change persisted participant colors or user theme preferences.
 * Missing/invalid keys fail with an actionable error instead of silently changing the UI.
 */
internal class DesignTokens(context: Context) {
    data class DayTimeScaleStyle(val colors: IntArray, val positions: FloatArray, val labelIntervalHours: Int)
    private val root = JSONObject(
        context.assets.open("design_tokens.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
    ).also { require(it.getInt("version") == 1) { "Unsupported design token schema" } }

    fun color(name: String, dark: Boolean): Int {
        val palette = root.getJSONObject("colors").getJSONObject(if (dark) "dark" else "light")
        return Color.parseColor(palette.getString(name))
    }

    fun dimension(name: String): Int = root.getJSONObject("dimensions").getInt(name).also {
        require(it >= 0) { "Negative design dimension: $name" }
    }

    fun proportion(name: String): Float = root.getJSONObject("proportions").getDouble(name).toFloat().also {
        require(it in 0f..1f) { "Design proportion must be between zero and one: $name" }
    }

    fun dayTimeScale(dark: Boolean): DayTimeScaleStyle {
        val configuration = root.getJSONObject("dayTimeScale")
        val stops = configuration.getJSONArray("gradientStops")
        require(stops.length() >= 2) { "The day scale needs at least two gradient stops" }
        val minutes = List(stops.length()) { stops.getJSONObject(it).getInt("minute") }
        require(minutes.first() == 0 && minutes.last() == 1_440 &&
            minutes.zipWithNext().all { (first, second) -> first < second }) {
            "Day scale gradient stops must increase from 0 to 1440 minutes"
        }
        val labelInterval = configuration.getInt("labelIntervalHours")
        require(labelInterval in 1..24 && 24 % labelInterval == 0) { "Invalid day scale label interval" }
        return DayTimeScaleStyle(
            IntArray(stops.length()) { color(stops.getJSONObject(it).getString("color"), dark) },
            FloatArray(stops.length()) { minutes[it] / 1_440f }, labelInterval)
    }
}
