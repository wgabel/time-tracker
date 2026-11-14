package pl.wgabel.timetracker.design

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

/**
 * Immutable design tokens bundled with the application.
 *
 * Loaded once per Activity instance from assets/design_tokens.json. This is not a remote
 * configuration and does not change persisted participant colors or user theme preferences.
 * Missing/invalid keys fail with an actionable error instead of silently changing the UI.
 */
internal class DesignTokens(context: Context) {
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
}
