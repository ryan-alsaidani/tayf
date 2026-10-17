package com.tayf.app.vision

import android.graphics.Bitmap
import com.tayf.app.core.FilterType
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageEnhancer {

    /** Applies filter, brightness (-1..1) and contrast (0.5..2). Always returns a NEW bitmap. */
    fun apply(bitmap: Bitmap, filter: FilterType, brightness: Float, contrast: Float, removeShadows: Boolean): Bitmap {
        OpenCvInit.ensure()
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        var work = Mat()
        Imgproc.cvtColor(rgba, work, Imgproc.COLOR_RGBA2RGB)
        rgba.release()

        if (removeShadows || filter == FilterType.BLACK_WHITE || filter == FilterType.ENHANCED_COLOR) {
            val flat = flatField(work)
            work.release()
            work = flat
        }
        var out: Mat = when (filter) {
            FilterType.ORIGINAL -> work
            FilterType.GRAY -> {
                val g = Mat()
                Imgproc.cvtColor(work, g, Imgproc.COLOR_RGB2GRAY)
                work.release()
                g
            }
            FilterType.BLACK_WHITE -> {
                val g = Mat()
                Imgproc.cvtColor(work, g, Imgproc.COLOR_RGB2GRAY)
                work.release()
                val bs = oddBlock(min(g.cols(), g.rows()) / 30)
                val bw = Mat()
                Imgproc.adaptiveThreshold(g, bw, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, bs, 12.0)
                g.release()
                val clean = Mat()
                Imgproc.medianBlur(bw, clean, 3)
                bw.release()
                clean
            }
            FilterType.ENHANCED_COLOR -> enhanceColor(work)
        }
        if (brightness != 0f || contrast != 1f) {
            val adj = Mat()
            out.convertTo(adj, -1, contrast.toDouble(), brightness * 127.0)
            out.release()
            out = adj
        }
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(out, result)
        out.release()
        return result
    }

    /** Flat-field shadow removal. Always returns a NEW bitmap. */
    fun removeShadows(bitmap: Bitmap): Bitmap {
        OpenCvInit.ensure()
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        rgba.release()
        val flat = flatField(rgb)
        rgb.release()
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(flat, result)
        flat.release()
        return result
    }

    fun makeThumbnail(bitmap: Bitmap, maxSidePx: Int = 320): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxSidePx) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val s = maxSidePx.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            bitmap, max(1, (bitmap.width * s).roundToInt()), max(1, (bitmap.height * s).roundToInt()), true
        )
    }

    private fun oddBlock(v: Int): Int {
        val c = v.coerceIn(15, 75)
        return if (c % 2 == 0) c + 1 else c
    }

    /** Divides each pixel by an estimate of the paper background so uneven light and shadows vanish. */
    private fun flatField(rgb: Mat): Mat {
        val f = min(1.0, 800.0 / max(rgb.cols(), 1))
        val small = Mat()
        Imgproc.resize(rgb, small, Size(), f, f, Imgproc.INTER_AREA)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0))
        val dilated = Mat()
        Imgproc.dilate(small, dilated, kernel)
        small.release()
        kernel.release()
        val blurred = Mat()
        Imgproc.medianBlur(dilated, blurred, 15)
        dilated.release()
        val bg = Mat()
        Imgproc.resize(blurred, bg, rgb.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        blurred.release()
        val out = Mat()
        Core.divide(rgb, bg, out, 255.0)
        bg.release()
        return out
    }

    private fun enhanceColor(rgb: Mat): Mat {
        val lab = Mat()
        Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
        rgb.release()
        val ch = ArrayList<Mat>()
        Core.split(lab, ch)
        lab.release()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val l2 = Mat()
        clahe.apply(ch[0], l2)
        ch[0].release()
        ch[0] = l2
        val merged = Mat()
        Core.merge(ch, merged)
        ch.forEach { it.release() }
        val back = Mat()
        Imgproc.cvtColor(merged, back, Imgproc.COLOR_Lab2RGB)
        merged.release()
        val blur = Mat()
        Imgproc.GaussianBlur(back, blur, Size(0.0, 0.0), 1.2)
        val sharp = Mat()
        Core.addWeighted(back, 1.5, blur, -0.5, 0.0, sharp)
        back.release()
        blur.release()
        return sharp
    }
}
