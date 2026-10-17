package com.tayf.app.data

import com.tayf.app.AppContainer
import com.tayf.app.core.AppScope
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.DocType
import com.tayf.app.core.Outcome
import com.tayf.app.ocr.ExpiryExtractor
import com.tayf.app.ocr.OcrJson
import com.tayf.app.ocr.SmartNamer
import com.tayf.app.vision.DocTypeClassifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Background OCR + document classification + smart naming + expiry extraction for a document. */
object PostScan {

    /** [placeholderName]: the automatic name given at creation; the document is renamed only if it still has it. */
    fun launch(container: AppContainer, documentId: Long, placeholderName: String?) {
        AppScope.scope.launch {
            runCatching { run(container, documentId, placeholderName) }
        }
    }

    suspend fun run(container: AppContainer, documentId: Long, placeholderName: String?) {
        val pages = container.documents.getPages(documentId)
        val all = StringBuilder()
        var detected: DocType = DocType.UNKNOWN
        pages.forEachIndexed { index, page ->
            if (page.ocrText != null) {
                all.append(page.ocrText).append('\n')
                return@forEachIndexed
            }
            val bmp = BitmapIO.decodeFile(container.context, page.processedPath, 3600) ?: return@forEachIndexed
            val result = container.ocr.recognize(bmp)
            if (result is Outcome.Ok) {
                container.documents.saveOcr(page.id, result.value.fullText, OcrJson.encode(result.value.blocks))
                all.append(result.value.fullText).append('\n')
                if (index == 0) detected = DocTypeClassifier.classify(bmp, result.value.fullText)
            }
            bmp.recycle()
        }
        val doc = container.documents.getDocument(documentId) ?: return
        if (doc.docType == DocType.UNKNOWN && detected != DocType.UNKNOWN) {
            container.documents.setDocType(documentId, detected)
        }
        val text = all.toString()
        if (text.isBlank()) return
        if (doc.expiryDate == null) ExpiryExtractor.extract(text)?.let { container.documents.setExpiry(documentId, it) }
        if (placeholderName != null && doc.name == placeholderName) {
            val hijri = container.settings.hijriDates.first()
            val type = if (doc.docType != DocType.UNKNOWN) doc.docType else detected
            container.documents.renameDocument(documentId, SmartNamer.suggestName(text, type, System.currentTimeMillis(), hijri))
        }
    }
}
