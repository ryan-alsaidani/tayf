package com.tayf.app.ui.crop

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tayf.app.AppContainer
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.PageModel
import com.tayf.app.core.Quad
import com.tayf.app.data.PostScan
import com.tayf.app.vision.EdgeDetector
import com.tayf.app.vision.PageRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CropUiState(
    val loading: Boolean = true,
    val bitmap: Bitmap? = null,
    val quad: Quad = Quad.full(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false
)

class CropViewModel(private val container: AppContainer, private val pageId: Long) : ViewModel() {
    private val _state = MutableStateFlow(CropUiState())
    val state: StateFlow<CropUiState> = _state.asStateFlow()
    private var page: PageModel? = null

    init {
        viewModelScope.launch {
            val p = container.documents.getPage(pageId)
            page = p
            if (p == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val bmp = withContext(AppDispatchers.io) { BitmapIO.decodeFile(container.context, p.originalPath, 2400) }
            _state.update { it.copy(loading = false, bitmap = bmp, quad = p.quad, failed = bmp == null) }
        }
    }

    fun setQuad(q: Quad) = _state.update { it.copy(quad = q) }

    fun reset() = _state.update { it.copy(quad = Quad.full()) }

    fun autoDetect() {
        val bmp = _state.value.bitmap ?: return
        viewModelScope.launch {
            val q = withContext(AppDispatchers.cpu) { EdgeDetector.detect(bmp) }
            if (q != null) _state.update { it.copy(quad = q) }
        }
    }

    fun applyCrop() {
        val p = page ?: return
        val quad = _state.value.quad
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val updated = PageRenderer.renderAndSave(container, p.copy(quad = quad))
                container.documents.updatePage(updated)
                container.documents.clearOcr(updated.id)
                PostScan.launch(container, updated.documentId, null)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Throwable) {
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, pageId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { CropViewModel(container, pageId) }
        }
    }
}
