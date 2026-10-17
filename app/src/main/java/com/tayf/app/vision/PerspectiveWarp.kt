package com.tayf.app.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import com.tayf.app.core.Quad
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object PerspectiveWarp {
    private const val MAX_SIDE = 4000

    /** Crops and straightens the region described by quad. Always returns a NEW bitmap. */
    fun warp(source: Bitmap, quad: Quad): Bitmap {
        OpenCvInit.ensure()
        val w = source.width.toDouble()
        val h = source.height.toDouble()
        val isFull = abs(quad.tlX) < 0.002f && abs(quad.tlY) < 0.002f && abs(quad.trX - 1f) < 0.002f &&
            abs(quad.trY) < 0.002f && abs(quad.brX - 1f) < 0.002f && abs(quad.brY - 1f) < 0.002f &&
            abs(quad.blX) < 0.002f && abs(quad.blY - 1f) < 0.002f
        if (isFull) return source.copy(Bitmap.Config.ARGB_8888, true)

        val tl = Point(quad.tlX * w, quad.tlY * h)
        val tr = Point(quad.trX * w, quad.trY * h)
        val br = Point(quad.brX * w, quad.brY * h)
        val bl = Point(quad.blX * w, quad.blY * h)
        var outW = max(hypot(tr.x - tl.x, tr.y - tl.y), hypot(br.x - bl.x, br.y - bl.y))
        var outH = max(hypot(bl.x - tl.x, bl.y - tl.y), hypot(br.x - tr.x, br.y - tr.y))
        val longSide = max(outW, outH)
        if (longSide > MAX_SIDE) {
            val s = MAX_SIDE / longSide
            outW *= s
            outH *= s
        }
        val ow = max(16, outW.roundToInt())
        val oh = max(16, outH.roundToInt())

        val src = Mat()
        val dst = Mat()
        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0), Point(ow - 1.0, 0.0), Point(ow - 1.0, oh - 1.0), Point(0.0, oh - 1.0)
        )
        try {
            Utils.bitmapToMat(source, src)
            val m = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            Imgproc.warpPerspective(src, dst, m, Size(ow.toDouble(), oh.toDouble()), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
            m.release()
            val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, out)
            return out
        } finally {
            src.release()
            dst.release()
            srcPts.release()
            dstPts.release()
        }
    }

    /** Returns the same instance when degrees is a multiple of 360, otherwise a new rotated bitmap. */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return bitmap
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }
}
