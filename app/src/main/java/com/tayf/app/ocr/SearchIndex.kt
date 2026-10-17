package com.tayf.app.ocr

import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.NormRect
import com.tayf.app.core.SearchHit
import com.tayf.app.data.db.TayfDatabase
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Text normalization shared by the index writer and the query side. */
internal object SearchText {
    fun normalize(s: String): String {
        val nfc = Normalizer.normalize(s, Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length)
        for (c in nfc) {
            when {
                c in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
                c in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
                c in '\u064B'..'\u065F' || c == '\u0670' || c == '\u0640' -> Unit
                c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ' -> sb.append('ا')
                c == 'ى' -> sb.append('ي')
                c == 'ة' -> sb.append('ه')
                else -> sb.append(c.lowercaseChar())
            }
        }
        return sb.toString()
    }

    fun tokens(query: String): List<String> =
        normalize(query).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
}

class SearchIndex(private val db: TayfDatabase) {

    /** Full-text search over all OCR text; hits carry highlight boxes taken from the stored OCR blocks. */
    suspend fun search(query: String, includeVault: Boolean): List<SearchHit> = withContext(AppDispatchers.io) {
        val tokens = SearchText.tokens(query)
        if (tokens.isEmpty()) return@withContext emptyList()
        val match = tokens.joinToString(" ") { "\"$it*\"" }
        val ids = try { db.ftsDao().search(match) } catch (e: Exception) { emptyList() }
        if (ids.isEmpty()) return@withContext emptyList()
        val pages = db.pageDao().getByIds(ids)
        val docs = db.documentDao().getRows(pages.map { it.documentId }.distinct()).associateBy { it.id }
        pages.mapNotNull { p ->
            val d = docs[p.documentId] ?: return@mapNotNull null
            if (d.isVault && !includeVault) return@mapNotNull null
            val text = p.ocrText.orEmpty()
            val snippet = text.lines().firstOrNull { line ->
                val n = SearchText.normalize(line)
                tokens.any { n.contains(it) }
            }?.trim()?.take(140) ?: text.take(140)
            val boxes = OcrJson.decode(p.ocrBlocksJson)
                .filter { b -> val n = SearchText.normalize(b.text); tokens.any { n.contains(it) } }
                .take(24)
                .map { NormRect(it.left, it.top, it.right, it.bottom) }
            Triple(d.updatedAt, p.position, SearchHit(d.id, d.name, p.id, p.position, snippet, boxes))
        }.sortedWith(compareByDescending<Triple<Long, Int, SearchHit>> { it.first }.thenBy { it.second })
            .map { it.third }
    }
}
