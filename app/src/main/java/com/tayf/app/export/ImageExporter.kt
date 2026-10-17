package com.tayf.app.export

import com.tayf.app.TayfApp
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.ExportFormat
import com.tayf.app.core.Formatters
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import kotlinx.coroutines.withContext
import java.io.File

object ImageExporter {

    /** Writes one JPG/PNG per page into [outDir]. */
    suspend fun export(
        pages: List<PageModel>, format: ExportFormat, quality: Int, outDir: File, baseName: String
    ): Outcome<List<File>> = withContext(AppDispatchers.io) {
        try {
            if (pages.isEmpty()) return@withContext Outcome.Fail("No pages")
            outDir.mkdirs()
            val ctx = TayfApp.instance
            val ext = if (format == ExportFormat.PNG) "png" else "jpg"
            val base = Formatters.sanitizeFileName(baseName)
            val files = ArrayList<File>()
            pages.forEachIndexed { index, page ->
                val bmp = BitmapIO.decodeFile(ctx, page.processedPath) ?: throw IllegalStateException("Cannot read a page image")
                val name = if (pages.size == 1) "$base.$ext" else "$base-${index + 1}.$ext"
                val file = File(outDir, name)
                try {
                    if (format == ExportFormat.PNG) BitmapIO.savePng(bmp, file) else BitmapIO.saveJpeg(bmp, file, quality)
                } finally {
                    bmp.recycle()
                }
                files += file
            }
            Outcome.Ok(files)
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "Image export failed", e)
        }
    }
}
