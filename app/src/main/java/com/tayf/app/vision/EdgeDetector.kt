package com.tayf.app.vision

import android.graphics.Bitmap
import com.tayf.app.core.Quad
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object EdgeDetector {
    private const val WORK_WIDTH = 640
    private const val MIN_CONFIDENCE = 0.40

    private class Candidate(val pts: List<Point>, val conf: Double)

    /** Returns the document quad (normalized) or null if no confident quad found. */
    fun detect(bitmap: Bitmap): Quad? {
        val r = detectWithConfidence(bitmap) ?: return null
        return if (r.second >= MIN_CONFIDENCE) r.first else null
    }

    /** Same, plus a 0f..1f confidence, used by the auto-capture stability check. */
    fun detectWithConfidence(bitmap: Bitmap): Pair<Quad, Float>? {
        OpenCvInit.ensure()
        val small = if (bitmap.width > WORK_WIDTH) {
            Bitmap.createScaledBitmap(
                bitmap, WORK_WIDTH,
                max(1, (bitmap.height * WORK_WIDTH.toFloat() / bitmap.width).roundToInt()), true
            )
        } else bitmap
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(small, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            val w = gray.cols().toDouble()
            val h = gray.rows().toDouble()
            val imgArea = w * h
            val all = ArrayList<Candidate>()

            val mean = Core.mean(gray).`val`[0]
            val edges = Mat()
            Imgproc.Canny(gray, edges, max(10.0, 0.66 * mean), min(250.0, max(60.0, 1.33 * mean)))
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, kernel)
            all += candidatesFrom(edges, imgArea)
            edges.release()

            val bin = Mat()
            Imgproc.threshold(gray, bin, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
            val k5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_CLOSE, k5)
            all += candidatesFrom(bin, imgArea)
            bin.release()
            kernel.release()
            k5.release()

            val best = all.maxByOrNull { it.conf } ?: return null
            val p = best.pts
            val quad = Quad(
                norm(p[0].x, w), norm(p[0].y, h), norm(p[1].x, w), norm(p[1].y, h),
                norm(p[2].x, w), norm(p[2].y, h), norm(p[3].x, w), norm(p[3].y, h)
            )
            return Pair(quad, best.conf.toFloat().coerceIn(0f, 1f))
        } finally {
            rgba.release()
            gray.release()
            if (small !== bitmap) small.recycle()
        }
    }

    private fun norm(v: Double, size: Double): Float = (v / size).toFloat().coerceIn(0f, 1f)

    private fun candidatesFrom(src: Mat, imgArea: Double): List<Candidate> {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val copy = src.clone()
        Imgproc.findContours(copy, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        copy.release()
        hierarchy.release()
        val out = ArrayList<Candidate>()
        val top = contours.sortedByDescending { Imgproc.contourArea(it) }.take(8)
        for (c in top) {
            val area = Imgproc.contourArea(c)
            val ratio = area / imgArea
            if (ratio < 0.12 || ratio > 0.985) continue
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
            var factor = 1.0
            val pts: List<Point>
            if (approx.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                pts = approx.toList()
            } else if (ratio >= 0.25) {
                val rect = Imgproc.minAreaRect(c2f)
                val arr = Array(4) { Point() }
                rect.points(arr)
                pts = arr.toList()
                factor = 0.7
            } else {
                c2f.release(); approx.release()
                continue
            }
            c2f.release()
            approx.release()
            val ordered = order(pts) ?: continue
            out += Candidate(ordered, score(ordered, imgArea) * factor)
        }
        contours.forEach { it.release() }
        return out
    }

    private fun order(p: List<Point>): List<Point>? {
        if (p.size != 4) return null
        val tl = p.minByOrNull { it.x + it.y } ?: return null
        val br = p.maxByOrNull { it.x + it.y } ?: return null
        val tr = p.minByOrNull { it.y - it.x } ?: return null
        val bl = p.maxByOrNull { it.y - it.x } ?: return null
        if (setOf(tl, tr, br, bl).size != 4) return null
        return listOf(tl, tr, br, bl)
    }

    private fun score(p: List<Point>, imgArea: Double): Double {
        var a = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            a += p[i].x * p[j].y - p[j].x * p[i].y
        }
        val area = abs(a) / 2.0
        val areaScore = (area / imgArea / 0.5).coerceIn(0.0, 1.0)
        var dev = 0.0
        for (i in 0 until 4) {
            val prev = p[(i + 3) % 4]
            val cur = p[i]
            val next = p[(i + 1) % 4]
            val v1x = prev.x - cur.x; val v1y = prev.y - cur.y
            val v2x = next.x - cur.x; val v2y = next.y - cur.y
            val denom = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
            val ang = if (denom == 0.0) 0.0 else Math.toDegrees(acos(((v1x * v2x + v1y * v2y) / denom).coerceIn(-1.0, 1.0)))
            dev += abs(ang - 90.0)
        }
        val angleScore = (1.0 - (dev / 4.0) / 45.0).coerceIn(0.0, 1.0)
        val mar = Imgproc.minAreaRect(MatOfPoint2f(*p.toTypedArray()))
        val marArea = mar.size.width * mar.size.height
        val rectScore = if (marArea <= 0.0) 0.0 else (area / marArea).coerceIn(0.0, 1.0)
        return 0.4 * areaScore + 0.3 * angleScore + 0.3 * rectScore
    }
}
