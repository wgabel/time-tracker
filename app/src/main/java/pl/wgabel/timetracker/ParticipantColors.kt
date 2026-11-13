package pl.wgabel.timetracker

import android.graphics.Color

/** Muted categorical colors, inspired by ColorBrewer Set2 and Tableau palettes. */
internal object ParticipantColors {
    val palette = intArrayOf(
        0xFF397FA6.toInt(), 0xFF318B77.toInt(), 0xFF9A72AD.toInt(),
        0xFFC47B52.toInt(), 0xFFB76583.toInt(), 0xFF668C5C.toInt(),
        0xFF657FAF.toInt(), 0xFFAD8550.toInt(), 0xFF9B7180.toInt(),
        0xFF528D93.toInt()
    )

    /** Only used to assign a new color; stored participant colors never depend on this palette. */
    fun suggested(ordinal: Int): Int {
        val index = Math.floorMod(ordinal, palette.size)
        val cycle = Math.floorDiv(ordinal, palette.size)
        val base = palette[index]
        if (cycle == 0) return base
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        hsv[0] = (hsv[0] + cycle * 23f) % 360f
        hsv[1] = (hsv[1] + (cycle % 3 - 1) * 0.035f).coerceIn(0.35f, 0.75f)
        return Color.HSVToColor(hsv)
    }

    /** Preserve the exact colors of existing participants during the v5 -> v6 migration. */
    fun legacyColor(participantId: Int): Int {
        val old = intArrayOf(
            0xFF356AC3.toInt(), 0xFF21896F.toInt(), 0xFF9B59B6.toInt(),
            0xFFD47A25.toInt(), 0xFFC34F69.toInt(), 0xFF526E83.toInt()
        )
        return old[Math.floorMod(participantId, old.size)]
    }
}
