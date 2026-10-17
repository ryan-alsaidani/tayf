package com.tayf.app.ui.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tayf.app.AppContainer
import com.tayf.app.core.DocumentModel
import com.tayf.app.core.PageModel
import com.tayf.app.core.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DocumentViewModel(private val container: AppContainer, private val docId: Long) : ViewModel() {

    val document: StateFlow<DocumentModel?> =
        container.documents.observeDocument(docId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pages: StateFlow<List<PageModel>?> =
        container.documents.observePages(docId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Other (non-vault) documents, candidates for merging. */
    val mergeCandidates: StateFlow<List<DocumentModel>> =
        container.documents.observeDocuments(null, SortOrder.DATE_DESC, false)
            .map { list -> list.filter { it.id != docId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    fun toggleSelect(pageId: Long) = _selection.update { if (pageId in it) it - pageId else it + pageId }
    fun clearSelection() { _selection.value = emptySet() }
    fun selectAll() { _selection.value = pages.value.orEmpty().map { it.id }.toSet() }

    fun rename(name: String) = viewModelScope.launch { container.documents.renameDocument(docId, name) }
    fun toggleFavorite() = viewModelScope.launch {
        val d = document.value ?: return@launch
        container.documents.setFavorite(docId, !d.isFavorite)
    }
    fun setExpiry(millis: Long?) = viewModelScope.launch { container.documents.setExpiry(docId, millis) }

    fun deleteSelected() = viewModelScope.launch {
        container.documents.deletePages(_selection.value.toList())
        _selection.value = emptySet()
    }

    /** Moves the single selected page one step earlier (-1) or later (+1). */
    fun moveSelected(delta: Int) = viewModelScope.launch {
        val sel = _selection.value
        if (sel.size != 1) return@launch
        val list = pages.value.orEmpty().map { it.id }.toMutableList()
        val from = list.indexOf(sel.first())
        val to = from + delta
        if (from < 0 || to < 0 || to >= list.size) return@launch
        val id = list.removeAt(from)
        list.add(to, id)
        container.documents.reorderPages(docId, list)
    }

    fun splitSelected(name: String, onDone: (Long) -> Unit) = viewModelScope.launch {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return@launch
        val newId = container.documents.splitDocument(docId, ids, name)
        _selection.value = emptySet()
        if (newId > 0) onDone(newId)
    }

    fun mergeFrom(sourceId: Long) = viewModelScope.launch { container.documents.mergeInto(docId, sourceId) }

    fun deleteDocument(onDone: () -> Unit) = viewModelScope.launch {
        container.documents.deleteDocument(docId)
        onDone()
    }

    companion object {
        fun factory(container: AppContainer, docId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { DocumentViewModel(container, docId) }
        }
    }
}
