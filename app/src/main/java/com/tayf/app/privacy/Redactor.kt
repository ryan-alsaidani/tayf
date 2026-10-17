package com.tayf.app.privacy

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.tayf.app.core.NormRect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class RedactionRegion(val rect: NormRect, val style: RedactionStyle)
enum class RedactionStyle { BLACK_BOX, HEAVY_BLUR }

object Redactor {

    /** Irreversible: pixels are overwritten in a NEW bitmap (the source is left untouched). */
    fun apply(bitmap: Bitmap, regions: List<RedactionRegion>): Bitmap {
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val black = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (r in regions) {
            val l = (min(r.rect.left, r.rect.right) * out.width).roundToInt().coerceIn(0, out.width - 1)
            val t = (min(r.rect.top, r.rect.bottom) * out.height).roundToInt().coerceIn(0, out.height - 1)
            val rr = (max(r.rect.left, r.rect.right) * out.width).roundToInt().coerceIn(l + 1, out.width)
            val b = (max(r.rect.top, r.rect.bottom) * out.height).roundToInt().coerceIn(t + 1, out.height)
            val area = Rect(l, t, rr, b)
            when (r.style) {
                RedactionStyle.BLACK_BOX -> canvas.drawRect(area, black)
                RedactionStyle.HEAVY_BLUR -> blur(out, canvas, area)
            }
        }
        return out
    }

    /** Heavy pixelation (about 6 blocks across the shorter side) followed by a soft dark veil: unreadable and not invertible. */
    private fun blur(bmp: Bitmap, canvas: Canvas, area: Rect) {
        val w = area.width()
        val h = area.height()
        val block = max(6, min(w, h) / 6)
        val crop = Bitmap.createBitmap(bmp, area.left, area.top, w, h)
        val tiny = Bitmap.createScaledBitmap(crop, max(1, w / block), max(1, h / block), true)
        val paint = Paint().apply { isFilterBitmap = false }
        canvas.drawBitmap(tiny, null, area, paint)
        val veil = Paint().apply { color = Color.argb(70, 0, 0, 0); style = Paint.Style.FILL }
        canvas.drawRect(area, veil)
        crop.recycle()
        tiny.recycle()
    }
}
