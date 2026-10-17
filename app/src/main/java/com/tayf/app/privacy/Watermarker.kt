package com.tayf.app.privacy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object Watermarker {

    /** Diagonal repeating text over the whole image, burned into the pixels of a NEW bitmap. */
    fun apply(bitmap: Bitmap, text: String, opacity: Float = 0.35f, colorArgb: Int = 0xFF8B0000.toInt()): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        if (text.isBlank()) return out
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            alpha = (opacity.coerceIn(0.05f, 1f) * 255).toInt()
            textSize = max(18f, min(out.width, out.height) / 16f)
            isFakeBoldText = true
        }
        val label = text.trim()
        val textW = paint.measureText(label)
        val lineGap = paint.textSize * 3.2f
        val colGap = textW + paint.textSize * 2f
        val diag = hypot(out.width.toFloat(), out.height.toFloat())
        canvas.save()
        canvas.rotate(-30f, out.width / 2f, out.height / 2f)
        var row = 0
        var y = -diag * 0.2f
        while (y < diag * 1.2f) {
            var x = -diag * 0.2f - if (row % 2 == 0) 0f else colGap / 2f
            while (x < diag * 1.2f) {
                canvas.drawText(label, x, y, paint)
                x += colGap
            }
            y += lineGap
            row++
        }
        canvas.restore()
        return out
    }
}
