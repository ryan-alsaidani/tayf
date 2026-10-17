package com.tayf.app.ui.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.DocType
import com.tayf.app.core.DocumentModel
import com.tayf.app.core.ExportFormat
import com.tayf.app.core.Formatters
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import com.tayf.app.core.PageSize
import com.tayf.app.export.ImageExporter
import com.tayf.app.export.PdfExporter
import com.tayf.app.export.PdfOptions
import com.tayf.app.export.ShareHelper
import com.tayf.app.export.SizeCompressor
import com.tayf.app.export.TextExporters
import com.tayf.app.vision.DocTypeClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ExportUiState(
    val loading: Boolean = true,
    val doc: DocumentModel? = null,
    val pages: List<PageModel> = emptyList(),
    val format: ExportFormat = ExportFormat.PDF,
    val pageSize: PageSize = PageSize.A4,
    val searchable: Boolean = true,
    val password: String = "",
    val quality: Int = 90,
    val compress: Boolean = false,
    val targetKb: String = "500",
    val working: Boolean = false,
    val files: List<File> = emptyList(),
    val errorText: String? = null,
    val messageRes: Int? = null
) {
    val hasText: Boolean get() = pages.any { !it.ocrText.isNullOrBlank() }
}

class ExportViewModel(
    private val container: AppContainer,
    private val documentId: Long,
    private val pageIds: List<Long>?
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val doc = container.documents.getDocument(documentId)
            var pages = container.documents.getPages(documentId)
            if (pageIds != null) pages = pages.filter { it.id in pageIds }
            val defaultSize = container.settings.defaultPageSize.first()
            val size = if (doc != null && doc.docType != DocType.UNKNOWN) DocTypeClassifier.suggestedPageSize(doc.docType) else defaultSize
            val quality = container.settings.jpegQuality.first()
            _state.update { it.copy(loading = false, doc = doc, pages = pages, pageSize = size, quality = quality) }
        }
    }

    private fun change(block: (ExportUiState) -> ExportUiState) = _state.update { block(it).copy(files = emptyList(), errorText = null, messageRes = null) }

    fun setFormat(f: ExportFormat) = change { it.copy(format = f) }
    fun setPageSize(s: PageSize) = change { it.copy(pageSize = s) }
    fun setSearchable(v: Boolean) = change { it.copy(searchable = v) }
    fun setPassword(v: String) = change { it.copy(password = v) }
    fun setQuality(v: Int) = change { it.copy(quality = v) }
    fun setCompress(v: Boolean) = change { it.copy(compress = v) }
    fun setTargetKb(v: String) = change { it.copy(targetKb = v.filter { c -> c.isDigit() }.take(6)) }
    fun clearMessage() = _state.update { it.copy(messageRes = null) }

    fun mime(format: ExportFormat = _state.value.format): String = when (format) {
        ExportFormat.PDF -> "application/pdf"
        ExportFormat.JPG -> "image/jpeg"
        ExportFormat.PNG -> "image/png"
        ExportFormat.TXT -> "text/plain"
        ExportFormat.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ExportFormat.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    fun extension(format: ExportFormat = _state.value.format): String = when (format) {
        ExportFormat.PDF -> "pdf"
        ExportFormat.JPG -> "jpg"
        ExportFormat.PNG -> "png"
        ExportFormat.TXT -> "txt"
        ExportFormat.DOCX -> "docx"
        ExportFormat.XLSX -> "xlsx"
    }

    /** Builds the output file(s) in the export cache; the UI then offers share / save / print. */
    fun export() {
        val s = _state.value
        if (s.working || s.pages.isEmpty()) return
        _state.update { it.copy(working = true, files = emptyList(), errorText = null) }
        viewModelScope.launch {
            val dir = container.fileStore.exportDir()
            withContext(AppDispatchers.io) { dir.listFiles()?.forEach { it.delete() } }
            val base = Formatters.sanitizeFileName(s.doc?.name ?: "scan")
            val target = File(dir, "$base.${extension(s.format)}")
            val targetBytes = (s.targetKb.toLongOrNull() ?: 0L) * 1024L
            val wantSize = s.compress && targetBytes > 0
            val result: Outcome<List<File>> = when (s.format) {
                ExportFormat.PDF -> {
                    val opts = PdfOptions(s.pageSize, s.password.ifBlank { null }, s.searchable, s.quality, null)
                    val r = if (wantSize) SizeCompressor.compressPdfToSize(s.pages, targetBytes, opts, target)
                    else PdfExporter.export(s.pages, target, opts)
                    when (r) { is Outcome.Ok -> Outcome.Ok(listOf(r.value)); is Outcome.Fail -> r }
                }
                ExportFormat.JPG, ExportFormat.PNG -> {
                    if (wantSize && s.format == ExportFormat.JPG && s.pages.size == 1) {
                        val bmp = withContext(AppDispatchers.io) { BitmapIO.decodeFile(container.context, s.pages[0].processedPath) }
                        if (bmp == null) Outcome.Fail("Cannot read the page") else {
                            val r = SizeCompressor.compressImageToSize(bmp, targetBytes, target)
                            bmp.recycle()
                            when (r) { is Outcome.Ok -> Outcome.Ok(listOf(r.value)); is Outcome.Fail -> r }
                        }
                    } else ImageExporter.export(s.pages, s.format, s.quality, dir, base)
                }
                ExportFormat.TXT -> TextExporters.exportTxt(s.pages, target).asList()
                ExportFormat.DOCX -> TextExporters.exportDocx(s.pages, target).asList()
                ExportFormat.XLSX -> TextExporters.exportXlsx(s.pages, target).asList()
            }
            when (result) {
                is Outcome.Ok -> _state.update { it.copy(working = false, files = result.value) }
                is Outcome.Fail -> _state.update { it.copy(working = false, errorText = result.message) }
            }
        }
    }

    private fun Outcome<File>.asList(): Outcome<List<File>> = when (this) {
        is Outcome.Ok -> Outcome.Ok(listOf(value))
        is Outcome.Fail -> this
    }

    fun share(context: Context) {
        val files = _state.value.files
        if (files.isNotEmpty()) ShareHelper.share(context, files, mime())
    }

    /** Single file -> CreateDocument result. */
    fun saveTo(context: Context, uri: Uri) {
        val f = _state.value.files.firstOrNull() ?: return
        viewModelScope.launch {
            val r = withContext(AppDispatchers.io) { ShareHelper.saveToUri(context, f, uri) }
            _state.update { it.copy(messageRes = if (r is Outcome.Ok) R.string.export_saved else R.string.error_generic) }
        }
    }

    /** Several files -> chosen folder. */
    fun saveToTree(context: Context, tree: Uri) {
        val files = _state.value.files
        val mime = mime()
        viewModelScope.launch {
            val ok = withContext(AppDispatchers.io) {
                var all = true
                try {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    for (f in files) {
                        val target = DocumentsContract.createDocument(context.contentResolver, parent, mime, f.name)
                        if (target == null || ShareHelper.saveToUri(context, f, target) !is Outcome.Ok) all = false
                    }
                } catch (e: Throwable) {
                    all = false
                }
                all
            }
            _state.update { it.copy(messageRes = if (ok) R.string.export_saved else R.string.error_generic) }
        }
    }

    companion object {
        fun factory(container: AppContainer, documentId: Long, pageIds: List<Long>?): ViewModelProvider.Factory = viewModelFactory {
            initializer { ExportViewModel(container, documentId, pageIds) }
        }
    }
}
