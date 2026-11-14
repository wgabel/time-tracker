package pl.wgabel.timetracker.tabs

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** UI actions supplied by the screen host. */
internal interface TimerCardActions {
    fun toggle(person: Int)
    fun addEvent(person: Int)
    fun quickStartPicker(person: Int)
    fun registerUpdate(update: (Long) -> Unit)
}

/** Theme-aware widgets and layout helpers used by timer cards. */
internal interface TimerCardUi {
    val ink: Int
    val muted: Int
    val subtleNeutral: Int
    val darkTheme: Boolean
    fun card(): LinearLayout
    fun addCard(view: LinearLayout)
    fun markActive(view: LinearLayout)
    fun label(value: String, size: Float, bold: Boolean, color: Int): TextView
    fun shape(color: Int, radius: Int): GradientDrawable
    fun gap(container: LinearLayout, size: Int)
    fun action(title: String, color: Int, click: () -> Unit): Button
    fun stopButton(title: String, click: () -> Unit): Button
    fun iconButton(kind: String, color: Int, click: () -> Unit): View
    fun string(id: Int): String
    fun duration(millis: Long): String
}
