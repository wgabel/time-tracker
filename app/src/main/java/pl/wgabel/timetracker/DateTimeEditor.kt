package pl.wgabel.timetracker

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Shared date/time input for sessions and events. All timestamps are epoch millis.
 * Validation and persistence belong to the caller; the dialog stays open on rejection.
 */
internal class DateTimeEditor(private val context: Context) {
    data class Config(
        val title: String,
        val initial: Long,
        val min: Long? = null,
        val max: Long? = null,
        val seconds: Boolean = false,
        val quickOffsets: Boolean = true
    )

    fun show(config: Config, onSave: (Long) -> Boolean) {
        val pl = context.resources.configuration.locales[0].language == "pl"
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        var selected = config.initial
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        val display = TextView(context).apply {
            textSize = 19f
            setPadding(0, dp(10), 0, dp(10))
        }
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM,
            context.resources.configuration.locales[0])
        fun refresh() { display.text = format.format(Date(selected)) }
        refresh()
        panel.addView(display)
        if (config.quickOffsets) {
            val shortcuts = LinearLayout(context)
            listOf(5, 10, 15, 30).forEach { minutes ->
                val button = Button(context).apply {
                    text = "$minutes min"
                    textSize = 11f
                    isAllCaps = false
                    setPadding(0, 0, 0, 0)
                    setOnClickListener {
                        selected = System.currentTimeMillis() - minutes * 60_000L
                        refresh()
                    }
                }
                shortcuts.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            panel.addView(shortcuts)
            val sliderLabel = TextView(context).apply {
                textSize = 13f
                setPadding(0, dp(10), 0, 0)
            }
            val slider = SeekBar(context).apply {
                max = 120
                progress = ((System.currentTimeMillis() - selected) / 60_000L).toInt().coerceIn(0, 120)
            }
            sliderLabel.text = if (pl) "Cofnij o ${slider.progress} min" else "Go back ${slider.progress} min"
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        selected = System.currentTimeMillis() - value * 60_000L
                        sliderLabel.text = if (pl) "Cofnij o $value min" else "Go back $value min"
                        refresh()
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
            panel.addView(sliderLabel)
            panel.addView(slider)
        }
        panel.addView(Button(context).apply {
            text = if (pl) "Dokładna data i godzina" else "Exact date and time"
            isAllCaps = false
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = selected }
                DatePickerDialog(context, { _, year, month, day ->
                    val dayCal = Calendar.getInstance().apply {
                        timeInMillis = selected
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                    }
                    TimePickerDialog(context, { _, hour, minute ->
                        dayCal.set(Calendar.HOUR_OF_DAY, hour)
                        dayCal.set(Calendar.MINUTE, minute)
                        if (!config.seconds) {
                            dayCal.set(Calendar.SECOND, 0)
                            dayCal.set(Calendar.MILLISECOND, 0)
                            selected = dayCal.timeInMillis
                            refresh()
                        } else {
                            val secondsPicker = android.widget.NumberPicker(context).apply {
                                minValue = 0
                                maxValue = 59
                                value = cal.get(Calendar.SECOND)
                            }
                            AlertDialog.Builder(context)
                                .setTitle(if (pl) "Sekundy" else "Seconds")
                                .setView(secondsPicker)
                                .setNegativeButton(if (pl) "Anuluj" else "Cancel", null)
                                .setPositiveButton(if (pl) "Zapisz" else "Save") { _, _ ->
                                    dayCal.set(Calendar.SECOND, secondsPicker.value)
                                    dayCal.set(Calendar.MILLISECOND, 0)
                                    selected = dayCal.timeInMillis
                                    refresh()
                                }.show()
                        }
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        })
        val dialog = AlertDialog.Builder(context)
            .setTitle(config.title)
            .setView(panel)
            .setNegativeButton(if (pl) "Anuluj" else "Cancel", null)
            .setPositiveButton(if (pl) "Zapisz" else "Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if ((config.min != null && selected < config.min) ||
                    (config.max != null && selected > config.max)) {
                    Toast.makeText(context,
                        if (pl) "Czas poza dozwolonym zakresem" else "Time outside allowed range",
                        Toast.LENGTH_LONG).show()
                } else if (onSave(selected)) dialog.dismiss()
            }
        }
        dialog.show()
    }
}
