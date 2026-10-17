package com.tayf.app.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object LongScanStitcher {
    private const val MATCH_WIDTH = 240
    private const val MAX_HEIGHT = 16000

    /** Stitches frames captured while moving down a long document into one tall bitmap (vertical motion). */
    fun stitch(frames: List<Bitmap>): Bitmap? {
        if (frames.isEmpty()) return null
        OpenCvInit.ensure()
        return try {
            val width = frames.minOf { it.width }
            val scaled = frames.map { f ->
                if (f.width == width) f else Bitmap.createScaledBitmap(f, width, max(1, (f.height * width.toFloat() / f.width).roundToInt()), true)
            }
            // rows of each frame (in frame pixels) that must be drawn
            val starts = IntArray(scaled.size)
            for (i in 1 until scaled.size) {
                starts[i] = newContentStart(scaled[i - 1], scaled[i])
            }
            var total = scaled[0].height
            for (i in 1 until scaled.size) total += scaled[i].height - starts[i]
            val shrink = if (total > MAX_HEIGHT) MAX_HEIGHT.toFloat() / total else 1f
            val outW = max(1, (width * shrink).roundToInt())
            val outH = max(1, (total * shrink).roundToInt())
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            var y = 0
            for (i in scaled.indices) {
                val bmp = scaled[i]
                val srcTop = if (i == 0) 0 else starts[i]
                val rows = bmp.height - srcTop
                if (rows <= 0) continue
                val src = android.graphics.Rect(0, srcTop, bmp.width, bmp.height)
                val dst = android.graphics.Rect(
                    0, (y * shrink).roundToInt(), outW, ((y + rows) * shrink).roundToInt().coerceAtMost(outH)
                )
                canvas.drawBitmap(bmp, src, dst, null)
                y += rows
            }
            scaled.forEachIndexed { i, b -> if (b !== frames[i]) b.recycle() }
            out
        } catch (e: Throwable) {
            null
        }
    }

    /** First row of [next] that is not already covered by [prev] (template match of prev's bottom strip). */
    private fun newContentStart(prev: Bitmap, next: Bitmap): Int {
        val fallback = (next.height * 0.25f).roundToInt()
        val a = Mat()
        val b = Mat()
        val ga = Mat()
        val gb = Mat()
        val result = Mat()
        try {
            val s = MATCH_WIDTH.toFloat() / prev.width
            val sp = Bitmap.createScaledBitmap(prev, MATCH_WIDTH, max(8, (prev.height * s).roundToInt()), true)
            val sn = Bitmap.createScaledBitmap(next, MATCH_WIDTH, max(8, (next.height * s).roundToInt()), true)
            Utils.bitmapToMat(sp, a)
            Utils.bitmapToMat(sn, b)
            sp.recycle(); sn.recycle()
            Imgproc.cvtColor(a, ga, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(b, gb, Imgproc.COLOR_RGBA2GRAY)
            val tplH = max(8, (ga.rows() * 0.30).roundToInt())
            val tpl = ga.submat(Rect(0, ga.rows() - tplH, ga.cols(), tplH))
            val searchH = min(gb.rows(), max(tplH + 4, (gb.rows() * 0.80).roundToInt()))
            val area = gb.submat(Rect(0, 0, gb.cols(), searchH))
            if (area.rows() < tpl.rows() || area.cols() < tpl.cols()) return fallback
            Imgproc.matchTemplate(area, tpl, result, Imgproc.TM_CCOEFF_NORMED)
            val mm = Core.minMaxLoc(result)
            if (mm.maxVal < 0.45) return fallback
            val matchedTopSmall = mm.maxLoc.y
            val startSmall = matchedTopSmall + tplH
            val start = (startSmall / s).roundToInt()
            return start.coerceIn(0, next.height - 1)
        } catch (e: Throwable) {
            return fallback
        } finally {
            a.release(); b.release(); ga.release(); gb.release(); result.release()
        }
    }
}
