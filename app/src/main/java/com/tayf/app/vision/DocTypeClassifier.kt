package com.tayf.app.vision

import android.graphics.Bitmap
import com.tayf.app.core.DocType
import com.tayf.app.core.FilterType
import com.tayf.app.core.PageSize
import kotlin.math.max
import kotlin.math.min

object DocTypeClassifier {

    private val invoiceWords = listOf(
        "فاتورة", "الفاتورة", "المجموع", "الإجمالي", "اجمالي", "ضريبة", "الضريبة", "وصل", "قبض",
        "invoice", "receipt", "total", "subtotal", "vat", "tax", "amount due",
        "facture", "reçu", "montant", "tva", "ticket", "quittance"
    )
    private val idWords = listOf(
        "بطاقة التعريف", "بطاقة التعريف الوطنية", "جواز سفر", "رخصة", "الجمهورية", "تاريخ الولادة", "اللقب",
        "identity", "passport", "driver", "licence", "license", "date of birth", "nationality",
        "carte nationale", "carte d'identité", "permis", "date de naissance", "nom"
    )

    /** Heuristics only: aspect ratio, text density and OCR keywords when provided. */
    fun classify(bitmap: Bitmap, ocrText: String?): DocType {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        if (w <= 0f || h <= 0f) return DocType.UNKNOWN
        val ratio = max(w, h) / min(w, h)
        val text = ocrText?.lowercase().orEmpty()

        if (text.isNotBlank()) {
            val idHits = idWords.count { text.contains(it) }
            if (idHits >= 2 && ratio in 1.3f..1.9f) return DocType.ID_CARD
            val invHits = invoiceWords.count { text.contains(it) }
            if (invHits >= 2) return DocType.INVOICE
            if (invHits >= 1 && ratio > 1.9f) return DocType.INVOICE
            if (idHits >= 2) return DocType.ID_CARD
        }
        if (ratio in 1.5f..1.7f && text.length < 400) return DocType.ID_CARD
        if (ratio > 2.2f) return DocType.INVOICE
        if (text.isNotBlank()) {
            if (text.length > 1400) return DocType.BOOK
            val ink = inkRatio(bitmap)
            if (text.length < 80 && ink > 0.035f) return DocType.HANDWRITTEN
        }
        return DocType.UNKNOWN
    }

    fun suggestedFilter(type: DocType): FilterType = when (type) {
        DocType.ID_CARD -> FilterType.ENHANCED_COLOR
        DocType.INVOICE -> FilterType.BLACK_WHITE
        DocType.BOOK -> FilterType.BLACK_WHITE
        DocType.HANDWRITTEN -> FilterType.ENHANCED_COLOR
        DocType.WHITEBOARD -> FilterType.ENHANCED_COLOR
        DocType.UNKNOWN -> FilterType.ENHANCED_COLOR
    }

    fun suggestedPageSize(type: DocType): PageSize = when (type) {
        DocType.ID_CARD -> PageSize.A4
        DocType.INVOICE -> PageSize.A5
        DocType.BOOK -> PageSize.A5
        DocType.HANDWRITTEN -> PageSize.A4
        DocType.WHITEBOARD -> PageSize.A4
        DocType.UNKNOWN -> PageSize.A4
    }

    /** Share of dark pixels on a coarse sample grid. */
    private fun inkRatio(bitmap: Bitmap): Float {
        val step = max(1, max(bitmap.width, bitmap.height) / 120)
        var dark = 0
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
                if (lum < 110) dark++
                total++
                x += step
            }
            y += step
        }
        return if (total == 0) 0f else dark.toFloat() / total
    }
}
