package com.tayf.app.export

import android.content.Context
import com.tayf.app.TayfApp
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import com.tayf.app.core.PageSize
import com.tayf.app.ocr.OcrJson
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.min

data class PdfOptions(
    val pageSize: PageSize = PageSize.A4,
    val password: String? = null,
    val searchable: Boolean = true,      // invisible OCR text layer when ocr data exists
    val jpegQuality: Int = 90,
    val maxSidePx: Int? = null
)

object PdfExporter {
    private const val MM_TO_PT = 72f / 25.4f

    suspend fun export(pages: List<PageModel>, target: File, options: PdfOptions): Outcome<File> =
        withContext(AppDispatchers.io) {
            val ctx = TayfApp.instance
            var doc: PDDocument? = null
            try {
                if (pages.isEmpty()) return@withContext Outcome.Fail("No pages")
                PDFBoxResourceLoader.init(ctx)
                val d = PDDocument()
                doc = d
                val font = if (options.searchable) loadFont(d, ctx) else null
                for (page in pages) {
                    val bmp = BitmapIO.decodeFile(ctx, page.processedPath, options.maxSidePx ?: BitmapIO.MAX_SIDE)
                        ?: throw IllegalStateException("Cannot read a page image")
                    try {
                        addPage(d, bmp, page, options, font)
                    } finally {
                        bmp.recycle()
                    }
                }
                val pw = options.password
                if (!pw.isNullOrEmpty()) {
                    val policy = StandardProtectionPolicy(UUID.randomUUID().toString(), pw, AccessPermission())
                    policy.encryptionKeyLength = 128
                    d.protect(policy)
                }
                target.parentFile?.mkdirs()
                d.save(target)
                Outcome.Ok(target)
            } catch (e: Throwable) {
                Outcome.Fail(e.message ?: "PDF export failed", e)
            } finally {
                runCatching { doc?.close() }
            }
        }

    private fun loadFont(doc: PDDocument, ctx: Context): PDType0Font? = try {
        ctx.assets.open("fonts/NotoNaskhArabic-Regular.ttf").use { PDType0Font.load(doc, it) }
    } catch (e: Throwable) {
        null
    }

    private fun pageDims(size: PageSize, bw: Int, bh: Int): Pair<Float, Float> {
        if (size == PageSize.AUTO || size.widthMm == 0) return Pair(bw * 0.48f, bh * 0.48f) // ~150 dpi
        var w = size.widthMm * MM_TO_PT
        var h = size.heightMm * MM_TO_PT
        if (bw > bh) { val t = w; w = h; h = t }
        return Pair(w, h)
    }

    private fun addPage(doc: PDDocument, bmp: android.graphics.Bitmap, page: PageModel, options: PdfOptions, font: PDType0Font?) {
        val (pw, ph) = pageDims(options.pageSize, bmp.width, bmp.height)
        val pdPage = PDPage(PDRectangle(pw, ph))
        doc.addPage(pdPage)
        bmp.setHasAlpha(false)
        val image = JPEGFactory.createFromImage(doc, bmp, options.jpegQuality.coerceIn(10, 100) / 100f)
        val scale = min(pw / bmp.width, ph / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val x0 = (pw - dw) / 2f
        val y0 = (ph - dh) / 2f
        PDPageContentStream(doc, pdPage).use { it.drawImage(image, x0, y0, dw, dh) }

        val blocks = OcrJson.decode(page.ocrBlocksJson)
        if (font != null && blocks.isNotEmpty()) {
            PDPageContentStream(doc, pdPage, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.setRenderingMode(RenderingMode.NEITHER)
                for (b in blocks) {
                    val h = (b.bottom - b.top) * dh
                    val size = (h * 0.8f).coerceIn(4f, 72f)
                    val x = x0 + b.left * dw
                    val y = y0 + (1f - b.bottom) * dh + h * 0.2f
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.newLineAtOffset(x, y)
                    showSafe(cs, b.text.replace('\n', ' ').replace('\t', ' '))
                    cs.endText()
                }
            }
        }
    }

    private fun showSafe(cs: PDPageContentStream, text: String) {
        try {
            cs.showText(text)
        } catch (e: IllegalArgumentException) {
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                i += Character.charCount(cp)
                try {
                    cs.showText(String(Character.toChars(cp)))
                } catch (inner: IllegalArgumentException) {
                    // glyph not in font: skip it (the layer is invisible anyway)
                }
            }
        }
    }
}
