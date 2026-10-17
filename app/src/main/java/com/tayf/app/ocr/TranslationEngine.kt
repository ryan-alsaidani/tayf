package com.tayf.app.ocr

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Offline translation on top of ML Kit. Models are downloaded once (explicit user action), then work offline. */
class TranslationEngine(context: Context) {
    private val appContext = context.applicationContext
    private val manager = RemoteModelManager.getInstance()
    private val translators = HashMap<String, Translator>()
    private val transient = MutableStateFlow<Map<String, PackState>>(emptyMap())
    private val tick = MutableStateFlow(0)
    private val languageId by lazy { LanguageIdentification.getClient() }

    private fun tag(code: String): String? = TranslateLanguage.fromLanguageTag(code)

    /** BCP-47 code or null if undetermined. */
    suspend fun detectLanguage(text: String): String? {
        if (text.isBlank()) return null
        return try {
            val result = languageId.identifyLanguage(text.take(1000)).awaitTask()
            if (result == "und") null else result.substringBefore('-')
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    fun modelState(code: String): Flow<PackState> =
        combine(transient, tick) { map, _ -> map }
            .map { map -> map[code] ?: if (code in installedModels()) PackState.Installed else PackState.NotInstalled }
            .distinctUntilChanged()

    suspend fun downloadModel(code: String): Outcome<Unit> {
        val lang = tag(code) ?: return Outcome.Fail("Unsupported language")
        if (lang == TranslateLanguage.ENGLISH) return Outcome.Ok(Unit)
        transient.update { it + (code to PackState.Downloading(0f)) }
        return try {
            val model = TranslateRemoteModel.Builder(lang).build()
            manager.download(model, DownloadConditions.Builder().build()).awaitTask()
            transient.update { it - code }
            tick.update { it + 1 }
            Outcome.Ok(Unit)
        } catch (e: CancellationException) {
            transient.update { it - code }
            throw e
        } catch (e: Throwable) {
            val msg = e.message ?: "Download failed"
            transient.update { it + (code to PackState.Failed(msg)) }
            Outcome.Fail(msg, e)
        }
    }

    suspend fun deleteModel(code: String) {
        val lang = tag(code) ?: return
        if (lang == TranslateLanguage.ENGLISH) return
        try {
            closeTranslators()
            manager.deleteDownloadedModel(TranslateRemoteModel.Builder(lang).build()).awaitTask()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // ignore: the state flow will show the real state
        }
        transient.update { it - code }
        tick.update { it + 1 }
    }

    suspend fun installedModels(): Set<String> {
        return try {
            val set = manager.getDownloadedModels(TranslateRemoteModel::class.java).awaitTask()
            val codes = set.map { it.language }.toMutableSet()
            codes.add(TranslateLanguage.ENGLISH)
            codes
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setOf(TranslateLanguage.ENGLISH)
        }
    }

    /** Offline once both models are installed (ML Kit pivots through English for non-English pairs). */
    suspend fun translate(text: String, from: String, to: String): Outcome<String> {
        if (text.isBlank()) return Outcome.Ok("")
        val src = tag(from) ?: return Outcome.Fail("Unsupported source language: $from")
        val dst = tag(to) ?: return Outcome.Fail("Unsupported target language: $to")
        if (src == dst) return Outcome.Ok(text)
        val installed = installedModels()
        if (src !in installed) return Outcome.Fail("Translation model not installed: $from")
        if (dst !in installed) return Outcome.Fail("Translation model not installed: $to")
        return try {
            val translator = translators.getOrPut("$src>$dst") {
                Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(dst).build()
                )
            }
            translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).awaitTask()
            val out = StringBuilder()
            for (chunk in chunks(text)) {
                if (out.isNotEmpty()) out.append('\n')
                out.append(translator.translate(chunk).awaitTask())
            }
            Outcome.Ok(out.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "Translation failed", e)
        }
    }

    suspend fun translatePage(page: PageModel, from: String?, to: String): Outcome<String> {
        val text = page.ocrText
        if (text.isNullOrBlank()) return Outcome.Fail("No recognized text on this page")
        val source = from ?: detectLanguage(text) ?: return Outcome.Fail("Could not detect the language")
        return translate(text, source, to)
    }

    fun close() {
        closeTranslators()
        runCatching { languageId.close() }
    }

    private fun closeTranslators() {
        translators.values.forEach { runCatching { it.close() } }
        translators.clear()
    }

    private fun chunks(text: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        for (line in text.lines()) {
            if (current.length + line.length > 700 && current.isNotEmpty()) {
                result += current.toString()
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append('\n')
            current.append(line)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}
