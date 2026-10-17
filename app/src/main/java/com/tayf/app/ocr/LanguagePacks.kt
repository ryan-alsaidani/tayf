package com.tayf.app.ocr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.googlecode.tesseract.android.TessBaseAPI
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.FileStore
import com.tayf.app.core.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.Collator
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

/** A language the app can OCR and/or translate. `code` = BCP-47. */
data class LangInfo(
    val code: String,
    val displayName: String,
    val tessCode: String?,
    val canTranslate: Boolean,
    val ocrEngine: String /* "mlkit" | "tesseract" | "both" */
)

sealed class PackState {
    object NotInstalled : PackState()
    data class Downloading(val progress: Float) : PackState()   // 0f..1f
    object Installed : PackState()
    data class Failed(val message: String) : PackState()
}

internal enum class Script { LATIN, CHINESE, DEVANAGARI, JAPANESE, KOREAN, OTHER }

internal data class LangSpec(val code: String, val tess: String?, val translate: Boolean, val script: Script)

internal object LanguageCatalog {
    private fun l(code: String, tess: String?, tr: Boolean) = LangSpec(code, tess, tr, Script.LATIN)
    private fun o(code: String, tess: String?, tr: Boolean) = LangSpec(code, tess, tr, Script.OTHER)
    private fun d(code: String, tess: String?, tr: Boolean) = LangSpec(code, tess, tr, Script.DEVANAGARI)

    val all: List<LangSpec> = listOf(
        l("en", "eng", true), l("fr", "fra", true), l("es", "spa", true), l("de", "deu", true),
        l("it", "ita", true), l("pt", "por", true), l("nl", "nld", true), l("sv", "swe", true),
        l("no", "nor", true), l("da", "dan", true), l("fi", "fin", true), l("pl", "pol", true),
        l("cs", "ces", true), l("sk", "slk", true), l("hu", "hun", true), l("ro", "ron", true),
        l("hr", "hrv", true), l("sl", "slv", true), l("ca", "cat", true), l("tr", "tur", true),
        l("id", "ind", true), l("ms", "msa", true), l("vi", "vie", true), l("tl", "fil", true),
        l("sw", "swa", true), l("af", "afr", true), l("sq", "sqi", true), l("lt", "lit", true),
        l("lv", "lav", true), l("et", "est", true), l("is", "isl", true), l("cy", "cym", true),
        l("ga", "gle", true), l("mt", "mlt", true), l("gl", "glg", true), l("eu", "eus", false),
        l("eo", "epo", true), l("ht", "hat", true), l("la", "lat", false), l("az", "aze", false),
        l("uz", "uzb", false),
        o("ru", "rus", true), o("uk", "ukr", true), o("bg", "bul", true), o("be", "bel", true),
        o("mk", "mkd", true), o("sr", "srp", false), o("kk", "kaz", false), o("mn", "mon", false),
        o("ar", "ara", true), o("fa", "fas", true), o("ur", "urd", true), o("he", "heb", true),
        o("el", "ell", true), o("th", "tha", true), o("ka", "kat", true), o("hy", "hye", false),
        o("bn", "ben", true), o("ta", "tam", true), o("te", "tel", true), o("kn", "kan", true),
        o("gu", "guj", true), o("pa", "pan", false), o("ml", "mal", false), o("si", "sin", false),
        o("my", "mya", false), o("km", "khm", false), o("lo", "lao", false), o("ps", "pus", false),
        o("am", "amh", false), o("ti", "tir", false), o("yi", "yid", false), o("ku", "kmr", false),
        d("hi", "hin", true), d("mr", "mar", true), d("ne", "nep", false), d("sa", "san", false),
        LangSpec("zh", "chi_sim", true, Script.CHINESE),
        LangSpec("ja", "jpn", true, Script.JAPANESE),
        LangSpec("ko", "kor", true, Script.KOREAN)
    )

    val byCode: Map<String, LangSpec> = all.associateBy { it.code }
}

internal val Context.ocrPrefsStore: DataStore<Preferences> by preferencesDataStore(name = "ocr_prefs")

internal object OcrPrefs {
    private val KEY = stringSetPreferencesKey("active_ocr_languages")
    val DEFAULT: Set<String> = setOf("ar", "en")

    fun flow(context: Context): Flow<Set<String>> =
        context.applicationContext.ocrPrefsStore.data
            .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
            .map { it[KEY] ?: DEFAULT }

    suspend fun current(context: Context): Set<String> = flow(context).first()

    suspend fun save(context: Context, codes: Set<String>) {
        context.applicationContext.ocrPrefsStore.edit { it[KEY] = codes }
    }
}

/** Tesseract data directory management (bundled ara/eng are copied from assets on first use). */
internal object TessData {
    val BUNDLED = setOf("ara", "eng")

    fun dir(context: Context): File = File(context.filesDir, "tessdata").also { it.mkdirs() }
    fun fileFor(context: Context, tess: String): File = File(dir(context), "$tess.traineddata")

    fun isInstalled(context: Context, tess: String): Boolean {
        val f = fileFor(context, tess)
        return (f.exists() && f.length() > 0) || tess in BUNDLED
    }

    @Synchronized
    fun ensureBundled(context: Context) {
        for (t in BUNDLED) {
            val f = fileFor(context, t)
            try {
                val assetName = "tessdata/$t.traineddata"
                val size = context.assets.open(assetName).use { it.available().toLong() }
                if (size <= 0L) continue
                if (f.exists() && f.length() == size) continue
                val tmp = File(f.path + ".tmp")
                context.assets.open(assetName).use { input -> tmp.outputStream().use { input.copyTo(it, 64 * 1024) } }
                if (f.exists()) f.delete()
                tmp.renameTo(f)
            } catch (e: Exception) {
                // asset missing: OCR for this language will report a clear error
            }
        }
    }
}

class LanguagePacks(context: Context, private val fileStore: FileStore) {
    private val appContext = context.applicationContext
    private val transient = MutableStateFlow<Map<String, PackState>>(emptyMap())
    private val tick = MutableStateFlow(0)

    /** Full list, sorted by displayName. */
    fun supportedLanguages(): List<LangInfo> {
        val collator = Collator.getInstance(Locale.getDefault())
        return LanguageCatalog.all.map {
            LangInfo(
                code = it.code,
                displayName = displayName(it.code),
                tessCode = it.tess,
                canTranslate = it.translate,
                ocrEngine = if (it.script == Script.OTHER) "tesseract" else "both"
            )
        }.sortedWith { a, b -> collator.compare(a.displayName, b.displayName) }
    }

    private fun displayName(code: String): String =
        Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .ifBlank { code }

    private fun diskState(code: String): PackState {
        val spec = LanguageCatalog.byCode[code] ?: return PackState.NotInstalled
        if (spec.script != Script.OTHER) return PackState.Installed
        val tess = spec.tess ?: return PackState.NotInstalled
        return if (TessData.isInstalled(appContext, tess)) PackState.Installed else PackState.NotInstalled
    }

    fun ocrState(code: String): Flow<PackState> =
        combine(transient, tick) { map, _ -> map[code] ?: diskState(code) }.distinctUntilChanged()

    suspend fun downloadOcrPack(code: String): Outcome<Unit> = withContext(AppDispatchers.io) {
        val spec = LanguageCatalog.byCode[code] ?: return@withContext Outcome.Fail("Unknown language")
        val tess = spec.tess ?: return@withContext Outcome.Fail("No OCR pack for this language")
        if (spec.script != Script.OTHER || TessData.isInstalled(appContext, tess)) return@withContext Outcome.Ok(Unit)
        transient.update { it + (code to PackState.Downloading(0f)) }
        val part = File(TessData.dir(appContext), "$tess.traineddata.part")
        try {
            val conn = URL("https://github.com/tesseract-ocr/tessdata_best/raw/main/$tess.traineddata")
                .openConnection() as HttpsURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            var lastReported = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                transient.update { it + (code to PackState.Downloading(done.toFloat() / total)) }
                            }
                        }
                    }
                }
            }
            if (part.length() <= 0L) throw IllegalStateException("Empty file")
            val target = TessData.fileFor(appContext, tess)
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IllegalStateException("Cannot save file")
            val api = TessBaseAPI()
            val ok = try { api.init(appContext.filesDir.absolutePath, tess) } finally { api.recycle() }
            if (!ok) {
                target.delete()
                throw IllegalStateException("Model cannot be loaded")
            }
            transient.update { it - code }
            tick.update { it + 1 }
            Outcome.Ok(Unit)
        } catch (e: CancellationException) {
            part.delete()
            transient.update { it - code }
            throw e
        } catch (e: Throwable) {
            part.delete()
            val msg = e.message ?: "Download failed"
            transient.update { it + (code to PackState.Failed(msg)) }
            Outcome.Fail(msg, e)
        }
    }

    suspend fun deleteOcrPack(code: String) = withContext(AppDispatchers.io) {
        val spec = LanguageCatalog.byCode[code] ?: return@withContext
        val tess = spec.tess ?: return@withContext
        if (spec.script != Script.OTHER || tess in TessData.BUNDLED) return@withContext
        TessData.fileFor(appContext, tess).delete()
        transient.update { it - code }
        tick.update { it + 1 }
    }

    /** Includes bundled ara + eng. */
    fun installedOcrCodes(): Set<String> =
        LanguageCatalog.all.filter { diskState(it.code) == PackState.Installed }.map { it.code }.toSet()

    /** Active OCR languages chosen by the user (multi-select, default ["ar","en"]). */
    val activeOcrLanguages: Flow<Set<String>> = OcrPrefs.flow(appContext)

    suspend fun setActiveOcrLanguages(codes: Set<String>) {
        OcrPrefs.save(appContext, if (codes.isEmpty()) OcrPrefs.DEFAULT else codes)
    }
}
