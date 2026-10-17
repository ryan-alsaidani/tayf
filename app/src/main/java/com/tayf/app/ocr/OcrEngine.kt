package com.tayf.app.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.Normalizer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Serializable
data class OcrBlock(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val lang: String) // normalized 0..1

data class OcrResult(val fullText: String, val blocks: List<OcrBlock>, val languageGuess: String)  // "ar", "en", "mixed" or another code

object OcrJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(OcrBlock.serializer())

    fun encode(blocks: List<OcrBlock>): String = json.encodeToString(serializer, blocks)

    fun decode(json: String?): List<OcrBlock> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            this.json.decodeFromString(serializer, json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** Awaits a Play-services style Task without extra dependencies. */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

class OcrEngine(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val recognizers = HashMap<Script, TextRecognizer>()
    private var tess: TessBaseAPI? = null
    private var tessKey: String? = null

    private class Plan(val mlScripts: Set<Script>, val tessKey: String?, val otherLang: String)
    private enum class Dom { ARABIC, LATIN, OTHER, NONE }

    suspend fun recognize(bitmap: Bitmap): Outcome<OcrResult> = withContext(AppDispatchers.cpu) {
        mutex.withLock {
            try {
                TessData.ensureBundled(appContext)
                val plan = plan(OcrPrefs.current(appContext))
                val ml = ArrayList<OcrBlock>()
                for (s in plan.mlScripts) ml += runMlKit(s, bitmap)
                val ts = if (plan.tessKey != null) runTesseract(plan.tessKey, bitmap, plan.otherLang) else emptyList()
                val merged = merge(ml, ts)
                val text = Normalizer.normalize(assemble(merged), Normalizer.Form.NFC)
                Outcome.Ok(OcrResult(text, merged, guess(text, plan.otherLang)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Outcome.Fail(e.message ?: "OCR failed", e)
            }
        }
    }

    fun close() {
        recognizers.values.forEach { runCatching { it.close() } }
        recognizers.clear()
        runCatching { tess?.recycle() }
        tess = null
        tessKey = null
    }

    // ---- planning ----

    private fun plan(active: Set<String>): Plan {
        val specs = active.mapNotNull { LanguageCatalog.byCode[it] }
        val ml = specs.map { it.script }.filter { it != Script.OTHER }.toMutableSet()
        val tessLangs = LinkedHashSet<String>()
        var otherLang = "und"
        for (s in specs) {
            val t = s.tess
            if (s.script == Script.OTHER && t != null && TessData.isInstalled(appContext, t)) {
                tessLangs += t
                if (otherLang == "und") otherLang = s.code
            }
        }
        if (tessLangs.isNotEmpty() && TessData.isInstalled(appContext, "eng")) tessLangs += "eng"
        if (ml.isEmpty() && tessLangs.isEmpty()) {
            ml += Script.LATIN
            tessLangs += listOf("ara", "eng")
            otherLang = "ar"
        }
        val ordered = tessLangs.filter { it != "eng" } + tessLangs.filter { it == "eng" }
        val key = if (ordered.isEmpty()) null else ordered.joinToString("+")
        return Plan(ml, key, otherLang)
    }

    // ---- engines ----

    private fun recognizerFor(script: Script): TextRecognizer = recognizers.getOrPut(script) {
        when (script) {
            Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            Script.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            Script.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
    }

    private fun scriptLang(script: Script): String = when (script) {
        Script.CHINESE -> "zh"
        Script.DEVANAGARI -> "hi"
        Script.JAPANESE -> "ja"
        Script.KOREAN -> "ko"
        else -> "en"
    }

    private suspend fun runMlKit(script: Script, bitmap: Bitmap): List<OcrBlock> {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val result = recognizerFor(script).process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
        val out = ArrayList<OcrBlock>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val r = line.boundingBox ?: continue
                val t = line.text.trim()
                if (t.isEmpty()) continue
                out += OcrBlock(
                    Normalizer.normalize(t, Normalizer.Form.NFC),
                    (r.left / w).coerceIn(0f, 1f), (r.top / h).coerceIn(0f, 1f),
                    (r.right / w).coerceIn(0f, 1f), (r.bottom / h).coerceIn(0f, 1f),
                    scriptLang(script)
                )
            }
        }
        return out
    }

    private fun tessFor(key: String): TessBaseAPI {
        val current = tess
        if (current != null && tessKey == key) return current
        runCatching { current?.recycle() }
        val api = TessBaseAPI()
        if (!api.init(appContext.filesDir.absolutePath, key)) {
            runCatching { api.recycle() }
            tess = null
            tessKey = null
            throw IllegalStateException("OCR model could not be loaded: $key")
        }
        api.setPageSegMode(3) // PSM_AUTO
        tess = api
        tessKey = key
        return api
    }

    private fun runTesseract(key: String, source: Bitmap, otherLang: String): List<OcrBlock> {
        val maxSide = 3600
        val longSide = max(source.width, source.height)
        val bmp = if (longSide > maxSide) {
            val s = maxSide.toFloat() / longSide
            Bitmap.createScaledBitmap(source, max(1, (source.width * s).roundToInt()), max(1, (source.height * s).roundToInt()), true)
        } else source
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val api = tessFor(key)
        val out = ArrayList<OcrBlock>()
        try {
            api.setImage(bmp)
            api.getUTF8Text()
            val iter = api.getResultIterator()
            if (iter != null) {
                val level = 2 // RIL_TEXTLINE
                iter.begin()
                do {
                    val t = iter.getUTF8Text(level)?.trim()
                    val conf = iter.confidence(level)
                    val r = iter.getBoundingRect(level)
                    if (!t.isNullOrEmpty() && conf >= 30f && r != null && r.width() > 0 && r.height() > 0) {
                        val text = Normalizer.normalize(t, Normalizer.Form.NFC)
                        val lang = when (dominant(text)) {
                            Dom.ARABIC -> if (otherLang in setOf("fa", "ur", "ps", "ku")) otherLang else "ar"
                            Dom.OTHER -> otherLang
                            else -> "en"
                        }
                        out += OcrBlock(
                            text,
                            (r.left / w).coerceIn(0f, 1f), (r.top / h).coerceIn(0f, 1f),
                            (r.right / w).coerceIn(0f, 1f), (r.bottom / h).coerceIn(0f, 1f), lang
                        )
                    }
                } while (iter.next(level))
                iter.delete()
            }
        } finally {
            runCatching { api.clear() }
            if (bmp !== source) bmp.recycle()
        }
        return out
    }

    // ---- merging / assembling ----

    private fun dominant(text: String): Dom {
        var ar = 0
        var la = 0
        var ot = 0
        text.codePoints().forEach { cp ->
            if (!Character.isLetter(cp)) return@forEach
            when {
                cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF ||
                    cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF -> ar++
                cp < 0x250 -> la++
                else -> ot++
            }
        }
        if (ar == 0 && la == 0 && ot == 0) return Dom.NONE
        return when {
            ar >= la && ar >= ot -> Dom.ARABIC
            la >= ar && la >= ot -> Dom.LATIN
            else -> Dom.OTHER
        }
    }

    private fun overlap(a: OcrBlock, b: OcrBlock): Float {
        val ix = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val m = min(areaA, areaB)
        return if (m <= 0f) 0f else inter / m
    }

    /** Merges by position without duplicating text: non-Latin Tesseract lines first, then ML Kit, then the rest. */
    private fun merge(ml: List<OcrBlock>, ts: List<OcrBlock>): List<OcrBlock> {
        val result = ArrayList<OcrBlock>()
        fun add(b: OcrBlock) { if (result.none { overlap(it, b) > 0.4f }) result += b }
        ts.filter { dominant(it.text) == Dom.ARABIC || dominant(it.text) == Dom.OTHER }.forEach { add(it) }
        ml.forEach { add(it) }
        ts.filter { dominant(it.text) == Dom.LATIN || dominant(it.text) == Dom.NONE }.forEach { add(it) }
        return result
    }

    private fun assemble(blocks: List<OcrBlock>): String {
        if (blocks.isEmpty()) return ""
        val sorted = blocks.sortedBy { (it.top + it.bottom) / 2f }
        val rows = ArrayList<MutableList<OcrBlock>>()
        for (b in sorted) {
            val cy = (b.top + b.bottom) / 2f
            val row = rows.lastOrNull()
            if (row != null) {
                val ref = row.first()
                val rcy = (ref.top + ref.bottom) / 2f
                val hh = min(b.bottom - b.top, ref.bottom - ref.top).coerceAtLeast(0.004f)
                if (abs(cy - rcy) < 0.5f * hh) { row.add(b); continue }
            }
            rows.add(mutableListOf(b))
        }
        return rows.joinToString("\n") { row ->
            val arabicChars = row.sumOf { r -> r.text.count { dominant(it.toString()) == Dom.ARABIC } }
            val latinChars = row.sumOf { r -> r.text.count { dominant(it.toString()) == Dom.LATIN } }
            val ordered = if (arabicChars > latinChars) row.sortedByDescending { it.left } else row.sortedBy { it.left }
            ordered.joinToString(" ") { it.text }
        }
    }

    private fun guess(text: String, otherLang: String): String {
        var ar = 0
        var la = 0
        var ot = 0
        text.codePoints().forEach { cp ->
            if (!Character.isLetter(cp)) return@forEach
            when {
                cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF ||
                    cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF -> ar++
                cp < 0x250 -> la++
                else -> ot++
            }
        }
        val total = (ar + la + ot).coerceAtLeast(1)
        return when {
            ar * 100 / total > 15 && la * 100 / total > 15 -> "mixed"
            ar >= la && ar >= ot -> "ar"
            la >= ar && la >= ot -> "en"
            else -> otherLang
        }
    }
}
