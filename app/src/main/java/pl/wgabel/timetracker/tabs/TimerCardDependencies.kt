package pl.wgabel.timetracker.tabs

import android.view.View
import android.widget.LinearLayout

/** Screen-host presentation operations that are not part of the shared design system. */
internal class TimerCardPresentation(
    val placeCard: (LinearLayout) -> Unit,
    val animateActiveCard: (LinearLayout) -> Unit,
    val createIcon: (String, Int, () -> Unit) -> View,
    val resolveString: (Int) -> String,
    val formatDuration: (Long) -> String
)

/** Timer-card actions and lifecycle update subscription. */
internal class TimerCardCommands(
    val toggle: (Int) -> Unit,
    val addEvent: (Int) -> Unit,
    val selectStartTime: (Int) -> Unit,
    val subscribeTick: ((Long) -> Unit) -> Unit
)
