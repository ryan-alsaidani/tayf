package com.tayf.app.ui.translate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.ExportFormat
import com.tayf.app.core.Formatters
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import com.tayf.app.export.ShareHelper
import com.tayf.app.export.TextExporters
import com.tayf.app.ocr.LangInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

data class TranslateUiState(
    val loading: Boolean = true,
    val docName: String = "",
    val pages: List<PageModel> = emptyList(),
    val pageIndex: Int = 0,
    val source: String? = null,                 // null = auto-detect
    val target: String = "en",
    val translations: Map<Int, String> = emptyMap(),
    val working: Boolean = false,
    val downloading: Boolean = false,
    val missingModels: List<String> = emptyList(),
    val errorText: String? = null,
    val noTextRes: Int? = null
)

class TranslateViewModel(private val container: AppContainer, private val docId: Long) : ViewModel() {
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    val languages: List<LangInfo> = container.languagePacks.supportedLanguages().filter { it.canTranslate }

    init {
        viewModelScope.launch {
            val doc = container.documents.getDocument(docId)
            val pages = container.documents.getPages(docId)
            val ui = Locale.getDefault().language
            val preferred = when (ui) { "ar" -> "en"; "en" -> "ar"; else -> ui }
            val target = if (languages.any { it.code == preferred }) preferred else "en"
            _state.update { it.copy(loading = false, docName = doc?.name.orEmpty(), pages = pages, target = target) }
        }
    }

    fun selectPage(i: Int) = _state.update { it.copy(pageIndex = i, errorText = null, missingModels = emptyList(), noTextRes = null) }
    fun setSource(code: String?) = _state.update { it.copy(source = code, translations = emptyMap(), missingModels = emptyList(), errorText = null) }
    fun setTarget(code: String) = _state.update { it.copy(target = code, translations = emptyMap(), missingModels = emptyList(), errorText = null) }

    fun translateCurrent() = startTranslation(listOf(_state.value.pageIndex))
    fun translateAll() = startTranslation(_state.value.pages.indices.toList())

    private fun startTranslation(indices: List<Int>) {
        val s = _state.value
        if (s.working) return
        _state.update { it.copy(working = true, errorText = null, missingModels = emptyList(), noTextRes = null) }
        viewModelScope.launch {
            val results = HashMap(s.translations)
            var failure: String? = null
            var noText = false
            for (i in indices) {
                val page = s.pages.getOrNull(i) ?: continue
                if (page.ocrText.isNullOrBlank()) { noText = true; continue }
                when (val r = container.translator.translatePage(page, s.source, s.target)) {
                    is Outcome.Ok -> results[i] = r.value
                    is Outcome.Fail -> { failure = r.message; break }
                }
            }
            val missing = failure?.takeIf { it.startsWith("Translation model not installed: ") }
                ?.removePrefix("Translation model not installed: ")?.let { listOf(it) }.orEmpty()
            _state.update {
                it.copy(
                    working = false, translations = results,
                    missingModels = if (missing.isNotEmpty()) modelsToFetch(s, missing[0]) else emptyList(),
                    errorText = if (missing.isEmpty()) failure else null,
                    noTextRes = if (noText && results.isEmpty() && failure == null) R.string.translate_no_text else null
                )
            }
        }
    }

    private suspend fun modelsToFetch(s: TranslateUiState, first: String): List<String> {
        val installed = container.translator.installedModels()
        val wanted = LinkedHashSet<String>()
        wanted += first
        s.source?.let { wanted += it }
        wanted += s.target
        return wanted.filter { it !in installed }
    }

    /** Explicit user action: downloads the missing models (the only network use besides OCR packs), then retries. */
    fun downloadMissingAndRetry() {
        val models = _state.value.missingModels
        if (models.isEmpty()) return
        _state.update { it.copy(downloading = true, errorText = null) }
        viewModelScope.launch {
            var ok = true
            for (code in models) {
                val r = container.translator.downloadModel(code)
                if (r is Outcome.Fail) { ok = false; _state.update { it.copy(errorText = r.message) }; break }
            }
            _state.update { it.copy(downloading = false, missingModels = if (ok) emptyList() else it.missingModels) }
            if (ok) translateCurrent()
        }
    }

    /** Exports the translated pages as TXT/DOCX and opens the share sheet. */
    fun exportTranslation(context: Context, format: ExportFormat) {
        val s = _state.value
        val pages = s.translations.toSortedMap().mapNotNull { (i, text) ->
            s.pages.getOrNull(i)?.copy(ocrText = text, ocrBlocksJson = null)
        }
        if (pages.isEmpty()) return
        viewModelScope.launch {
            val dir = container.fileStore.exportDir()
            val base = Formatters.sanitizeFileName(s.docName) + " (" + s.target + ")"
            val ext = if (format == ExportFormat.DOCX) "docx" else "txt"
            val target = File(dir, "$base.$ext")
            val r = if (format == ExportFormat.DOCX) TextExporters.exportDocx(pages, target) else TextExporters.exportTxt(pages, target)
            when (r) {
                is Outcome.Ok -> ShareHelper.share(
                    context, listOf(r.value),
                    if (format == ExportFormat.DOCX) "application/vnd.openxmlformats-officedocument.wordprocessingml.document" else "text/plain"
                )
                is Outcome.Fail -> _state.update { it.copy(errorText = r.message) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, docId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { TranslateViewModel(container, docId) }
        }
    }
}
