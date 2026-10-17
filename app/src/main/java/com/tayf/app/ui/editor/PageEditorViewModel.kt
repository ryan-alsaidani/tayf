package com.tayf.app.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tayf.app.AppContainer
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.FilterType
import com.tayf.app.core.PageModel
import com.tayf.app.data.PostScan
import com.tayf.app.vision.ImageEnhancer
import com.tayf.app.vision.PageRenderer
import com.tayf.app.vision.PerspectiveWarp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val loading: Boolean = true,
    val page: PageModel? = null,
    val preview: Bitmap? = null,
    val filter: FilterType = FilterType.ORIGINAL,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val rotation: Int = 0,
    val rendering: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false,
    val dirty: Boolean = false
)

class PageEditorViewModel(private val container: AppContainer, private val pageId: Long) : ViewModel() {
    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var warpedBase: Bitmap? = null   // original warped by the quad, downscaled for fast preview
    private var previewJob: Job? = null
    private var bakedShadows = false

    init {
        viewModelScope.launch {
            val p = container.documents.getPage(pageId)
            if (p == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            _state.update { it.copy(page = p, filter = p.filter, brightness = p.brightness, contrast = p.contrast, rotation = p.rotation) }
            val base = withContext(AppDispatchers.cpu) {
                val original = BitmapIO.decodeFile(container.context, p.originalPath, 1600)
                if (original == null) null else {
                    val w = PerspectiveWarp.warp(original, p.quad)
                    original.recycle()
                    w
                }
            }
            warpedBase = base
            _state.update { it.copy(loading = false, failed = base == null) }
            renderPreview(immediate = true)
        }
    }

    fun setFilter(f: FilterType) { _state.update { it.copy(filter = f, dirty = true) }; renderPreview() }
    fun setBrightness(v: Float) { _state.update { it.copy(brightness = v, dirty = true) }; renderPreview() }
    fun setContrast(v: Float) { _state.update { it.copy(contrast = v, dirty = true) }; renderPreview() }
    fun rotate(delta: Int) {
        _state.update { it.copy(rotation = ((it.rotation + delta) % 360 + 360) % 360, dirty = true) }
        renderPreview()
    }

    /** Shadow removal is baked into the saved image (changing the filter later re-renders from the original). */
    fun removeShadows() {
        bakedShadows = true
        _state.update { it.copy(dirty = true) }
        renderPreview(immediate = true)
    }

    private fun renderPreview(immediate: Boolean = false) {
        val base = warpedBase ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (!immediate) delay(120)
            _state.update { it.copy(rendering = true) }
            val s = _state.value
            val bmp = withContext(AppDispatchers.cpu) {
                val rotated = PerspectiveWarp.rotate(base, s.rotation)
                val out = ImageEnhancer.apply(rotated, s.filter, s.brightness, s.contrast, bakedShadows)
                if (rotated !== base) rotated.recycle()
                out
            }
            _state.update { it.copy(preview = bmp, rendering = false) }
        }
    }

    fun save() {
        val p = _state.value.page ?: return
        val s = _state.value
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val edited = p.copy(filter = s.filter, brightness = s.brightness, contrast = s.contrast, rotation = s.rotation)
                val updated = PageRenderer.renderAndSave(container, edited, bakedShadows)
                container.documents.updatePage(updated)
                container.documents.clearOcr(updated.id)
                PostScan.launch(container, updated.documentId, null)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Throwable) {
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    override fun onCleared() {
        warpedBase?.recycle()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer, pageId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { PageEditorViewModel(container, pageId) }
        }
    }
}
