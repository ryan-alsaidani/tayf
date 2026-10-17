package com.tayf.app.ui.library

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import com.tayf.app.core.Formatters
import com.tayf.app.core.FilterType
import com.tayf.app.core.FolderModel
import com.tayf.app.core.PageModel
import com.tayf.app.core.Quad
import com.tayf.app.core.SortOrder
import com.tayf.app.data.PostScan
import com.tayf.app.vision.ImageEnhancer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val _folderId = MutableStateFlow<Long?>(null)
    private val _sort = MutableStateFlow(SortOrder.DATE_DESC)
    private val _favorites = MutableStateFlow(false)
    private val _importing = MutableStateFlow(false)
    private val _message = MutableStateFlow<Int?>(null)

    val folderId: StateFlow<Long?> = _folderId.asStateFlow()
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()
    val favoritesOnly: StateFlow<Boolean> = _favorites.asStateFlow()
    val importing: StateFlow<Boolean> = _importing.asStateFlow()
    val message: StateFlow<Int?> = _message.asStateFlow()

    /** null while the first query is running. */
    val documents: StateFlow<List<DocumentModel>?> =
        combine(_folderId, _sort, _favorites) { f, s, fav -> Triple(f, s, fav) }
            .flatMapLatest { (f, s, fav) -> container.documents.observeDocuments(f, s, fav) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val folders: StateFlow<List<FolderModel>> =
        container.documents.observeFolders(false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFolder(id: Long?) { _folderId.value = id }
    fun setSort(s: SortOrder) { _sort.value = s }
    fun toggleFavorites() { _favorites.update { !it } }
    fun clearMessage() { _message.value = null }

    fun createFolder(name: String) = viewModelScope.launch { container.documents.createFolder(name) }
    fun renameFolder(id: Long, name: String) = viewModelScope.launch { container.documents.renameFolder(id, name) }
    fun deleteFolder(id: Long) = viewModelScope.launch {
        container.documents.deleteFolder(id)
        if (_folderId.value == id) _folderId.value = null
    }
    fun renameDocument(id: Long, name: String) = viewModelScope.launch { container.documents.renameDocument(id, name) }
    fun toggleFavorite(doc: DocumentModel) = viewModelScope.launch { container.documents.setFavorite(doc.id, !doc.isFavorite) }
    fun deleteDocument(id: Long) = viewModelScope.launch { container.documents.deleteDocument(id) }
    fun moveDocument(id: Long, folderId: Long?) = viewModelScope.launch { container.documents.moveDocument(id, folderId) }

    /** Imports a PDF file: every page is rendered to an image and becomes a page of a new document. */
    fun importPdf(uri: Uri, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val docId = withContext(AppDispatchers.cpu) { renderPdf(uri) }
                if (docId == null) _message.value = R.string.library_import_failed else onDone(docId)
            } catch (e: Throwable) {
                _message.value = R.string.library_import_failed
            } finally {
                _importing.value = false
            }
        }
    }

    private suspend fun renderPdf(uri: Uri): Long? {
        val pfd: ParcelFileDescriptor = container.context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        var docId: Long? = null
        var placeholder: String? = null
        val quality = container.settings.jpegQuality.first()
        val hijri = container.settings.hijriDates.first()
        PdfRenderer(pfd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                val bmp: Bitmap
                renderer.openPage(i).use { page ->
                    val scale = 1654f / page.width
                    bmp = Bitmap.createBitmap(1654, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                if (docId == null) {
                    placeholder = container.context.getString(R.string.doc_default_name) + " - " +
                        Formatters.formatDate(System.currentTimeMillis(), hijri)
                    docId = container.documents.createDocument(placeholder!!, null, DocType.UNKNOWN, false)
                }
                val original = container.fileStore.newOriginalFile()
                BitmapIO.saveJpeg(bmp, original, 95)
                val processed = container.fileStore.newProcessedFile()
                BitmapIO.saveJpeg(bmp, processed, quality)
                val thumbBmp = ImageEnhancer.makeThumbnail(bmp)
                val thumb = container.fileStore.newThumbFile()
                BitmapIO.saveJpeg(thumbBmp, thumb, 80)
                thumbBmp.recycle()
                bmp.recycle()
                container.documents.addPage(
                    docId!!,
                    PageModel(
                        0, docId!!, 0, original.absolutePath, processed.absolutePath, thumb.absolutePath,
                        Quad.full(), FilterType.ORIGINAL, 0f, 1f, 0, null, null
                    )
                )
            }
        }
        docId?.let { PostScan.launch(container, it, placeholder) }
        return docId
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container) }
        }
    }
}
