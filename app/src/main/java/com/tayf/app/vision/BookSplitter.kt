package com.tayf.app.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.roundToInt

object BookSplitter {
    private const val WORK_WIDTH = 800

    /** Splits a photo of an open book into [left, right] corrected pages. Returns null if the gutter cannot be found. */
    fun split(bitmap: Bitmap): List<Bitmap>? {
        OpenCvInit.ensure()
        val x = findGutter(bitmap) ?: return null
        val cut = (x * bitmap.width).roundToInt().coerceIn(bitmap.width / 5, bitmap.width * 4 / 5)
        val left = Bitmap.createBitmap(bitmap, 0, 0, cut, bitmap.height)
        val right = Bitmap.createBitmap(bitmap, cut, 0, bitmap.width - cut, bitmap.height)
        return listOf(correct(left), correct(right))
    }

    private fun correct(half: Bitmap): Bitmap {
        val quad = EdgeDetector.detect(half)
        if (quad == null) {
            val m = (half.width * 0.01f).roundToInt()
            val out = Bitmap.createBitmap(half, m, m, half.width - 2 * m, half.height - 2 * m)
            if (out !== half) half.recycle()
            return out
        }
        val warped = PerspectiveWarp.warp(half, quad)
        half.recycle()
        return warped
    }

    /** Normalized x of the gutter (darkest valley between the two pages), or null. */
    private fun findGutter(bitmap: Bitmap): Float? {
        val small = if (bitmap.width > WORK_WIDTH) {
            Bitmap.createScaledBitmap(bitmap, WORK_WIDTH, max(1, (bitmap.height * WORK_WIDTH.toFloat() / bitmap.width).roundToInt()), true)
        } else bitmap
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            val w = gray.cols()
            val h = gray.rows()
            val y0 = (h * 0.2).roundToInt()
            val y1 = (h * 0.8).roundToInt()
            val data = ByteArray(w * h)
            gray.get(0, 0, data)
            val profile = DoubleArray(w)
            for (cx in 0 until w) {
                var sum = 0L
                var y = y0
                while (y < y1) {
                    sum += (data[y * w + cx].toInt() and 0xFF)
                    y++
                }
                profile[cx] = sum.toDouble() / (y1 - y0).coerceAtLeast(1)
            }
            val smooth = DoubleArray(w)
            val r = max(2, w / 80)
            for (cx in 0 until w) {
                var s = 0.0
                var n = 0
                for (k in -r..r) {
                    val i = cx + k
                    if (i in 0 until w) { s += profile[i]; n++ }
                }
                smooth[cx] = s / n
            }
            val from = (w * 0.35).roundToInt()
            val to = (w * 0.65).roundToInt()
            var minIdx = from
            for (cx in from..to) if (smooth[cx] < smooth[minIdx]) minIdx = cx
            val avg = smooth.average()
            val depth = (avg - smooth[minIdx]) / max(avg, 1.0)
            if (depth < 0.04) return null
            return minIdx.toFloat() / w
        } finally {
            rgba.release()
            gray.release()
            if (small !== bitmap) small.recycle()
        }
    }
}
