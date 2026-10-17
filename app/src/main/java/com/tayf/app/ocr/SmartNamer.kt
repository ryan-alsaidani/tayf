package com.tayf.app.ocr

import com.tayf.app.core.DocType
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object SmartNamer {

    private val genericWords = setOf(
        "فاتورة", "الفاتورة", "invoice", "facture", "receipt", "reçu", "total", "المجموع", "date", "التاريخ"
    )

    /** e.g. "فاتورة ستاغ - 2026-10-13". Fallback: "مستند - <date>". */
    fun suggestName(text: String, type: DocType, nowMillis: Long, hijri: Boolean): String {
        val lang = Locale.getDefault().language
        val typeWord = typeWord(type, lang)
        val dateMillis = DateParser.findAll(text).firstOrNull()?.epoch ?: nowMillis
        val dateStr = numericDate(dateMillis, hijri)
        val entity = firstMeaningfulLine(text)
        val base = when {
            entity == null -> typeWord
            type == DocType.UNKNOWN -> entity
            entity.startsWith(typeWord, ignoreCase = true) -> entity
            else -> "$typeWord $entity"
        }
        return "$base - $dateStr"
    }

    private fun typeWord(type: DocType, lang: String): String = when (lang) {
        "fr" -> when (type) {
            DocType.ID_CARD -> "Pièce d'identité"
            DocType.INVOICE -> "Facture"
            DocType.BOOK -> "Livre"
            DocType.HANDWRITTEN -> "Manuscrit"
            DocType.WHITEBOARD -> "Tableau"
            DocType.UNKNOWN -> "Document"
        }
        "en" -> when (type) {
            DocType.ID_CARD -> "ID card"
            DocType.INVOICE -> "Invoice"
            DocType.BOOK -> "Book"
            DocType.HANDWRITTEN -> "Handwritten"
            DocType.WHITEBOARD -> "Whiteboard"
            DocType.UNKNOWN -> "Document"
        }
        else -> when (type) {
            DocType.ID_CARD -> "بطاقة تعريف"
            DocType.INVOICE -> "فاتورة"
            DocType.BOOK -> "كتاب"
            DocType.HANDWRITTEN -> "مخطوط"
            DocType.WHITEBOARD -> "سبورة"
            DocType.UNKNOWN -> "مستند"
        }
    }

    private fun numericDate(epoch: Long, hijri: Boolean): String {
        val date = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
        return try {
            if (hijri) {
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).withChronology(HijrahChronology.INSTANCE)
                    .format(HijrahDate.from(date))
            } else {
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US).format(date)
            }
        } catch (e: Exception) {
            date.toString()
        }
    }

    private fun firstMeaningfulLine(text: String): String? {
        for (raw in text.lines()) {
            val line = raw.trim().trim { !it.isLetterOrDigit() }
            if (line.length < 3) continue
            val letters = line.count { it.isLetter() }
            if (letters < 3 || letters * 10 < line.length * 6) continue
            if (DateParser.findAll(line).isNotEmpty()) continue
            if (genericWords.any { line.equals(it, ignoreCase = true) }) continue
            return if (line.length <= 28) line else line.substring(0, 28).substringBeforeLast(' ', line.substring(0, 28)).trim()
        }
        return null
    }
}
