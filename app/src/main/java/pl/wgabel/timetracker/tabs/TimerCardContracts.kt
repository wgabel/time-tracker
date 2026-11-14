package pl.wgabel.timetracker.tabs

import android.view.View
import android.widget.LinearLayout

/** UI actions supplied by the screen host. */
internal interface TimerCardActions {
    fun toggle(person: Int)
    fun addEvent(person: Int)
    fun quickStartPicker(person: Int)
    fun registerUpdate(update: (Long) -> Unit)
}

/** Theme-aware widgets and layout helpers used by timer cards. */
internal interface TimerCardUi {
    fun addCard(view: LinearLayout)
    fun markActive(view: LinearLayout)
    fun iconButton(kind: String, color: Int, click: () -> Unit): View
    fun string(id: Int): String
    fun duration(millis: Long): String
}
