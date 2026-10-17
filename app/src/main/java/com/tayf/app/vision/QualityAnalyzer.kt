package com.tayf.app.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class QualityReport(
    val blurScore: Float,        // 0..1, 1 = very blurry
    val glareScore: Float,       // 0..1
    val shadowScore: Float,      // 0..1
    val isBlurry: Boolean, val hasGlare: Boolean, val hasShadow: Boolean
) { val isGood: Boolean get() = !isBlurry && !hasGlare && !hasShadow }

object QualityAnalyzer {
    private const val WORK_WIDTH = 480

    /** Fast capture-quality check (blur, glare, shadow) on a 480px downscale. */
    fun analyze(bitmap: Bitmap): QualityReport {
        OpenCvInit.ensure()
        val small = if (bitmap.width > WORK_WIDTH) {
            Bitmap.createScaledBitmap(
                bitmap, WORK_WIDTH, max(1, (bitmap.height * WORK_WIDTH.toFloat() / bitmap.width).roundToInt()), true
            )
        } else bitmap
        val rgba = Mat()
        val gray = Mat()
        val lap = Mat()
        val sat = Mat()
        val bg = Mat()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val gMean = MatOfDouble()
            val gStd = MatOfDouble()
            Core.meanStdDev(gray, gMean, gStd)
            val contrastStd = gStd.toArray()[0]

            Imgproc.Laplacian(gray, lap, CvType.CV_64F)
            val lMean = MatOfDouble()
            val lStd = MatOfDouble()
            Core.meanStdDev(lap, lMean, lStd)
            val variance = lStd.toArray()[0].pow(2)
            val blurScore = (1.0 - variance / 220.0).coerceIn(0.0, 1.0)
            val isBlurry = contrastStd > 12.0 && variance < 55.0

            Imgproc.threshold(gray, sat, 250.0, 255.0, Imgproc.THRESH_BINARY)
            val frac = Core.countNonZero(sat).toDouble() / (gray.rows().toDouble() * gray.cols())
            val glareScore = (frac / 0.20).coerceIn(0.0, 1.0)
            val hasGlare = frac > 0.08

            Imgproc.GaussianBlur(gray, bg, Size(0.0, 0.0), 25.0)
            val bMean = MatOfDouble()
            val bStd = MatOfDouble()
            Core.meanStdDev(bg, bMean, bStd)
            val m = bMean.toArray()[0]
            val cv = if (m <= 1.0) 0.0 else bStd.toArray()[0] / m
            val shadowScore = (cv / 0.30).coerceIn(0.0, 1.0)
            val hasShadow = cv > 0.17

            return QualityReport(
                blurScore.toFloat(), glareScore.toFloat(), shadowScore.toFloat(),
                isBlurry, hasGlare, hasShadow
            )
        } finally {
            rgba.release(); gray.release(); lap.release(); sat.release(); bg.release()
            if (small !== bitmap) small.recycle()
        }
    }
}
