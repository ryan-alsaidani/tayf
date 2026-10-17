package com.tayf.app.export

import android.graphics.Bitmap
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object SizeCompressor {

    private data class Level(val quality: Int, val maxSide: Int)

    private val levels = listOf(
        Level(90, 3000), Level(85, 2600), Level(80, 2200), Level(75, 1900), Level(70, 1600),
        Level(65, 1400), Level(60, 1200), Level(55, 1000), Level(50, 900), Level(45, 800),
        Level(40, 700), Level(35, 600), Level(30, 500)
    )

    /** Finds the best quality/scale whose PDF is <= targetBytes (binary search). Fail if impossible. */
    suspend fun compressPdfToSize(
        pages: List<PageModel>, targetBytes: Long, options: PdfOptions, target: File
    ): Outcome<File> = withContext(AppDispatchers.io) {
        val dir = target.parentFile ?: File(".")
        dir.mkdirs()
        val probe = File(dir, "probe_${System.nanoTime()}.pdf")
        try {
            suspend fun sizeAt(i: Int): Long? {
                val lv = levels[i]
                val opts = options.copy(jpegQuality = minOf(lv.quality, options.jpegQuality), maxSidePx = lv.maxSide)
                return when (val r = PdfExporter.export(pages, probe, opts)) {
                    is Outcome.Ok -> probe.length()
                    is Outcome.Fail -> null
                }
            }
            val worst = sizeAt(levels.size - 1) ?: return@withContext Outcome.Fail("Export failed")
            if (worst > targetBytes) return@withContext Outcome.Fail("Cannot reach the target size")
            var lo = 0
            var hi = levels.size - 1
            while (lo < hi) {
                val mid = (lo + hi) / 2
                val s = sizeAt(mid)
                if (s != null && s <= targetBytes) hi = mid else lo = mid + 1
            }
            val lv = levels[lo]
            val finalOpts = options.copy(jpegQuality = minOf(lv.quality, options.jpegQuality), maxSidePx = lv.maxSide)
            PdfExporter.export(pages, target, finalOpts)
        } finally {
            probe.delete()
        }
    }

    /** Writes a JPEG <= targetBytes, trading scale first and quality second. */
    suspend fun compressImageToSize(bitmap: Bitmap, targetBytes: Long, target: File): Outcome<File> =
        withContext(AppDispatchers.cpu) {
            try {
                for (scale in listOf(1f, 0.85f, 0.7f, 0.55f, 0.4f, 0.3f, 0.2f)) {
                    val bmp = if (scale == 1f) bitmap else Bitmap.createScaledBitmap(
                        bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true
                    )
                    var lo = 25
                    var hi = 95
                    var best: ByteArray? = null
                    while (lo <= hi) {
                        val mid = (lo + hi) / 2
                        val bos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, mid, bos)
                        if (bos.size() <= targetBytes) { best = bos.toByteArray(); lo = mid + 1 } else hi = mid - 1
                    }
                    if (bmp !== bitmap) bmp.recycle()
                    if (best != null) {
                        target.parentFile?.mkdirs()
                        target.writeBytes(best)
                        return@withContext Outcome.Ok(target)
                    }
                }
                Outcome.Fail("Cannot reach the target size")
            } catch (e: Throwable) {
                Outcome.Fail(e.message ?: "Compression failed", e)
            }
        }
}
