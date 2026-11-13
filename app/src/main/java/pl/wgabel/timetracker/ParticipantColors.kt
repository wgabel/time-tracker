package pl.wgabel.timetracker

import android.graphics.Color

/** Vibrant categorical palette with balanced warm, cool and complementary hues. */
internal object ParticipantColors {
    val palette = intArrayOf(
        0xFFFF7F50.toInt(), 0xFF08CFA6.toInt(), 0xFF5378F5.toInt(),
        0xFFFFC857.toInt(), 0xFF9B62E8.toInt(), 0xFF23B9DF.toInt(),
        0xFFF45B8D.toInt(), 0xFF52C56A.toInt(), 0xFFEA9146.toInt(),
        0xFFBC5EC7.toInt()
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
        hsv[1] = (hsv[1] + (cycle % 3 - 1) * 0.025f).coerceIn(0.60f, 0.95f)
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
