package pl.wgabel.timetracker.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.widget.LinearLayout
import pl.wgabel.timetracker.R
import pl.wgabel.timetracker.data.ProjectRepository
import pl.wgabel.timetracker.design.AppUi

/** Deterministic offline project artwork, shared by project rows, settings and navigation. */
internal class ProjectIcons(context: Context, private val projects: ProjectRepository, private val components: AppUi) : ContextWrapper(context) {
    private fun densityPixels(value: Int) = components.densityPixels(value)
    private fun iconVariant(projectId: Long) = projects.iconVariant(projectId)
    private fun drawCreatureIcon(c: Canvas, r: java.util.Random) {
        val palettes = arrayOf(
            intArrayOf(0xFFFCE9CB.toInt(), 0xFFDA916E.toInt(), 0xFF633E44.toInt()),
            intArrayOf(0xFFD8F0E4.toInt(), 0xFF6AB5A1.toInt(), 0xFF264C57.toInt()),
            intArrayOf(0xFFE9E2FB.toInt(), 0xFF9B89D6.toInt(), 0xFF43365F.toInt()),
            intArrayOf(0xFFFFE3E8.toInt(), 0xFFE7A2B3.toInt(), 0xFF6D4656.toInt()),
            intArrayOf(0xFFE0EDFF.toInt(), 0xFF729ED4.toInt(), 0xFF304C76.toInt()),
            intArrayOf(0xFFFFF0BA.toInt(), 0xFFD9AF50.toInt(), 0xFF65503A.toInt())
        )
        val palette = palettes[r.nextInt(palettes.size)]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun fill(color: Int) { paint.color = color; paint.style = Paint.Style.FILL }
        fun circle(x: Float, y: Float, radius: Float) = c.drawCircle(x, y, radius, paint)
        fun oval(left: Float, top: Float, right: Float, bottom: Float) =
            c.drawOval(RectF(left, top, right, bottom), paint)
        fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            c.drawPath(Path().apply {
                moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close()
            }, paint)
        }
        fill(palette[0])
        c.drawRoundRect(0f, 0f, 100f, 100f, 20f, 20f, paint)
        c.save()
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), 20f, 20f, Path.Direction.CW) })
        val shape = r.nextInt(8)
        val ears = r.nextInt(7)
        fill(palette[1])
        when (ears) {
            0 -> { triangle(19f, 39f, 22f, 8f, 47f, 29f); triangle(53f, 29f, 78f, 8f, 81f, 39f) }
            1 -> { oval(24f, 2f, 40f, 48f); oval(60f, 2f, 76f, 48f) }
            2 -> { circle(25f, 29f, 17f); circle(75f, 29f, 17f) }
            3 -> { triangle(15f, 35f, 9f, 13f, 38f, 28f); triangle(62f, 28f, 91f, 13f, 85f, 35f) }
            4 -> { oval(5f, 31f, 36f, 52f); oval(64f, 31f, 95f, 52f) }
            5 -> { triangle(31f, 34f, 28f, 7f, 45f, 32f); triangle(55f, 32f, 72f, 7f, 69f, 34f) }
            else -> { circle(30f, 24f, 9f); circle(70f, 24f, 9f) }
        }
        when (shape) {
            0 -> circle(50f, 58f, 37f)
            1 -> oval(17f, 23f, 83f, 93f)
            2 -> c.drawRoundRect(17f, 25f, 83f, 91f, 24f, 24f, paint)
            3 -> oval(10f, 34f, 90f, 86f)
            4 -> { circle(50f, 57f, 34f); oval(18f, 60f, 82f, 95f) }
            5 -> { triangle(13f, 35f, 87f, 35f, 50f, 96f); circle(50f, 52f, 28f) }
            6 -> oval(22f, 17f, 78f, 96f)
            else -> { c.drawRoundRect(12f, 32f, 88f, 90f, 33f, 33f, paint) }
        }
        val eyeY = 51f + r.nextInt(10)
        val spacing = 12f + r.nextInt(9)
        val eyeType = r.nextInt(5)
        fill(palette[2])
        when (eyeType) {
            0 -> { circle(50f-spacing, eyeY, 3.5f); circle(50f+spacing, eyeY, 3.5f) }
            1 -> { oval(46f-spacing, eyeY-6f, 54f-spacing, eyeY+6f); oval(46f+spacing, eyeY-6f, 54f+spacing, eyeY+6f) }
            2 -> { oval(45f-spacing, eyeY-2f, 55f-spacing, eyeY+2f); oval(45f+spacing, eyeY-2f, 55f+spacing, eyeY+2f) }
            3 -> { circle(50f-spacing, eyeY, 5f); circle(50f+spacing, eyeY, 5f); fill(0xFFFFFFFF.toInt()); circle(48f-spacing, eyeY-2f, 2f); circle(48f+spacing, eyeY-2f, 2f) }
            else -> { circle(50f-spacing, eyeY, 2.5f); circle(50f+spacing, eyeY, 2.5f) }
        }
        fill(palette[0])
        if (r.nextBoolean()) oval(34f, 65f, 66f, 85f)
        fill(palette[2])
        when (r.nextInt(5)) {
            0 -> triangle(44f, 68f, 56f, 68f, 50f, 77f)
            1 -> oval(44f, 70f, 56f, 75f)
            2 -> { circle(44f, 74f, 3f); circle(56f, 74f, 3f) }
            3 -> c.drawArc(RectF(41f, 68f, 59f, 82f), 0f, 180f, false, paint)
            else -> c.drawRoundRect(47f, 68f, 53f, 80f, 3f, 3f, paint)
        }
        c.restore()
    }

    fun createIcon(id: Long, size: Int = 40): View = object : View(this) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun color(value: Long) { p.color = value.toInt(); p.style = Paint.Style.FILL }
        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            if (width == 0 || height == 0) return
            val save = c.save()
            c.scale(width / 100f, height / 100f)
            val r = java.util.Random((id xor 0xBAA055L) + iconVariant(id) * 0x5DEECE66DL)
            // Alternate icon families on regeneration, so the next result looks distinctly different.
            if (((id + iconVariant(id)) and 1L) == 1L) {
                drawCreatureIcon(c, r)
                c.restoreToCount(save)
                return
            }
            val biome = r.nextInt(8) // meadow, beach, forest, desert, snow, lake, coast, hills
            val night = r.nextInt(4) == 0
            val sunset = !night && r.nextInt(4) == 0
            val sky = if (night) 0xFF192C52L else if (sunset) 0xFFF5A782L else 0xFF8EC9E8L
            color(sky)
            c.drawRoundRect(0f, 0f, 100f, 100f, 20f, 20f, p)
            c.save()
            c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, 100f, 100f), 20f, 20f, Path.Direction.CW) })
            val sunX = 18f + r.nextInt(62)
            val sunY = 16f + r.nextInt(20)
            color(if (night) 0xFFF4EDCFL else 0xFFFFE19AL)
            c.drawCircle(sunX, sunY, if (night) 8f else 12f, p)
            if (night) {
                color(sky)
                c.drawCircle(sunX + 4f, sunY - 3f, 8f, p)
                color(0xFFE9EAF5L)
                repeat(5) {
                    val x = 9f + r.nextInt(83)
                    val y = 8f + r.nextInt(38)
                    c.drawCircle(x, y, 1f + r.nextInt(2), p)
                }
            } else if (r.nextBoolean()) {
                color(0xFFF7F5EAL)
                c.drawOval(RectF(9f, 20f, 36f, 29f), p)
                c.drawOval(RectF(53f, 12f, 77f, 20f), p)
            }
            val mountain = biome == 0 || biome == 3 || biome == 4 || biome == 7 || (biome == 5 && r.nextBoolean())
            if (mountain) {
                color(if (night) 0xFF34466BL else if (biome == 4) 0xFFB6CBD8L else 0xFF7B9CA0L)
                val peaks = Path().apply {
                    moveTo(-12f, 69f)
                    lineTo(20f, 30f + r.nextInt(16))
                    lineTo(45f, 64f)
                    lineTo(70f, 27f + r.nextInt(17))
                    lineTo(115f, 72f)
                    lineTo(115f, 100f)
                    lineTo(-12f, 100f)
                    close()
                }
                c.drawPath(peaks, p)
            }
            val ground = when (biome) {
                1 -> 0xFFF4D28AL // sand
                3 -> 0xFFE9AE68L // desert
                4 -> 0xFFE8F3F4L // snow
                5 -> 0xFF4E9FC0L // lake
                6 -> 0xFF4A9FB6L // coast
                2 -> 0xFF356D58L // forest
                else -> 0xFF75B77AL // meadow / hills
            }
            color(ground)
            val horizon = if (biome == 1 || biome == 5 || biome == 6) 62f else 70f
            c.drawOval(RectF(-25f, horizon, 130f, 139f), p)
            if (biome == 1 || biome == 6) {
                color(0xFF4FABC5L)
                c.drawRect(0f, 51f, 100f, 70f, p)
                color(if (biome == 1) 0xFFF4D28AL else 0xFFE7C38BL)
                c.drawOval(RectF(-25f, 67f, 130f, 138f), p)
            }
            if (biome == 5) {
                color(0xFFB1DEE0L)
                c.drawRoundRect(20f, 81f, 75f, 84f, 2f, 2f, p)
            }
            val treeX = 22f + r.nextInt(56)
            when (biome) {
                1, 6 -> { // palm
                    color(0xFF7B5B3EL)
                    c.drawRect(treeX, 57f, treeX + 5f, 91f, p)
                    color(0xFF297D66L)
                    c.drawOval(RectF(treeX - 21f, 46f, treeX + 5f, 57f), p)
                    c.drawOval(RectF(treeX, 44f, treeX + 27f, 57f), p)
                }
                3 -> { // cactus
                    color(0xFF327E5BL)
                    c.drawRoundRect(treeX, 61f, treeX + 11f, 94f, 5f, 5f, p)
                    c.drawRoundRect(treeX - 9f, 69f, treeX - 2f, 82f, 3f, 3f, p)
                    c.drawRoundRect(treeX + 13f, 66f, treeX + 20f, 81f, 3f, 3f, p)
                }
                else -> {
                    color(0xFF70533CL)
                    c.drawRect(treeX - 3f, 67f, treeX + 4f, 94f, p)
                    color(if (biome == 4) 0xFF2D6966L else if (night) 0xFF275A56L else 0xFF2F8766L)
                    if (biome == 2 || biome == 4 || r.nextBoolean()) {
                        val pine = Path().apply {
                            moveTo(treeX, 39f)
                            lineTo(treeX - 19f, 80f)
                            lineTo(treeX + 19f, 80f)
                            close()
                        }
                        c.drawPath(pine, p)
                    } else {
                        c.drawCircle(treeX, 59f, 19f, p)
                        c.drawCircle(treeX - 10f, 66f, 13f, p)
                    }
                }
            }
            c.restore()
            c.restoreToCount(save)
        }
    }.apply {
        layoutParams = LinearLayout.LayoutParams(densityPixels(size), densityPixels(size))
        contentDescription = getString(R.string.icon_description)
    }

}
