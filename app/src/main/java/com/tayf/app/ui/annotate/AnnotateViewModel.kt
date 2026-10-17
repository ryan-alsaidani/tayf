package com.tayf.app.ui.annotate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tayf.app.AppContainer
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.PageModel
import com.tayf.app.data.PostScan
import com.tayf.app.vision.PageRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

enum class AnnotateTool { DRAW, HIGHLIGHT, TEXT, SIGNATURE }

/** All coordinates are normalized (0..1 of the page image); sizes are fractions of the image width. */
sealed class Annotation {
    data class Stroke(val points: List<Offset>, val color: Int, val width: Float, val highlight: Boolean) : Annotation()
    data class TextNote(val text: String, val pos: Offset, val color: Int, val size: Float) : Annotation()
    data class Signature(val center: Offset, val width: Float, val bitmap: Bitmap) : Annotation()
}

data class AnnotateUiState(
    val loading: Boolean = true,
    val bitmap: Bitmap? = null,
    val annotations: List<Annotation> = emptyList(),
    val redo: List<Annotation> = emptyList(),
    val tool: AnnotateTool = AnnotateTool.DRAW,
    val color: Int = 0xFF8B0000.toInt(),
    val width: Float = 0.006f,
    val savedSignature: Bitmap? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val failed: Boolean = false
)

class AnnotateViewModel(private val container: AppContainer, private val pageId: Long) : ViewModel() {
    private val _state = MutableStateFlow(AnnotateUiState())
    val state: StateFlow<AnnotateUiState> = _state.asStateFlow()
    private var page: PageModel? = null
    private val signatureFile = File(container.context.filesDir, "signature.png")

    init {
        viewModelScope.launch {
            val p = container.documents.getPage(pageId)
            page = p
            if (p == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val (bmp, sig) = withContext(AppDispatchers.io) {
                BitmapIO.decodeFile(container.context, p.processedPath, 2000) to
                    (if (signatureFile.exists()) BitmapFactory.decodeFile(signatureFile.absolutePath) else null)
            }
            _state.update { it.copy(loading = false, bitmap = bmp, savedSignature = sig, failed = bmp == null) }
        }
    }

    fun setTool(t: AnnotateTool) = _state.update { it.copy(tool = t) }
    fun setColor(c: Int) = _state.update { it.copy(color = c) }
    fun setWidth(w: Float) = _state.update { it.copy(width = w) }

    fun add(a: Annotation) = _state.update { it.copy(annotations = it.annotations + a, redo = emptyList()) }

    fun undo() = _state.update {
        if (it.annotations.isEmpty()) it else it.copy(annotations = it.annotations.dropLast(1), redo = it.redo + it.annotations.last())
    }

    fun redo() = _state.update {
        if (it.redo.isEmpty()) it else it.copy(annotations = it.annotations + it.redo.last(), redo = it.redo.dropLast(1))
    }

    /** Stores the drawn signature so it can be reused later. */
    fun rememberSignature(bmp: Bitmap) {
        viewModelScope.launch {
            withContext(AppDispatchers.io) { BitmapIO.savePng(bmp, signatureFile) }
            _state.update { it.copy(savedSignature = bmp) }
        }
    }

    /** Burns the annotations into the processed image of the page (flattened). */
    fun save() {
        val p = page ?: return
        val annotations = _state.value.annotations
        if (annotations.isEmpty()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val out = withContext(AppDispatchers.cpu) {
                    val base = BitmapIO.decodeFile(container.context, p.processedPath) ?: throw IllegalStateException("missing image")
                    val mutable = base.copy(Bitmap.Config.ARGB_8888, true)
                    base.recycle()
                    flatten(mutable, annotations)
                    mutable
                }
                val updated = PageRenderer.saveProcessedBitmap(container, p, out)
                container.documents.updatePage(updated)
                container.documents.clearOcr(updated.id)
                PostScan.launch(container, updated.documentId, null)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: Throwable) {
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    private fun flatten(bmp: Bitmap, list: List<Annotation>) {
        val canvas = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        for (a in list) {
            when (a) {
                is Annotation.Stroke -> {
                    if (a.points.isEmpty()) continue
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = max(1f, a.width * w)
                        color = a.color
                        if (a.highlight) alpha = 110
                    }
                    val path = Path()
                    a.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x * w, p.y * h) else path.lineTo(p.x * w, p.y * h) }
                    if (a.points.size == 1) canvas.drawPoint(a.points[0].x * w, a.points[0].y * h, paint) else canvas.drawPath(path, paint)
                }
                is Annotation.TextNote -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a.color; textSize = a.size * w }
                    var y = a.pos.y * h
                    for (line in a.text.lines()) {
                        canvas.drawText(line, a.pos.x * w, y, paint)
                        y += paint.textSize * 1.25f
                    }
                }
                is Annotation.Signature -> {
                    val tw = a.width * w
                    val th = tw * a.bitmap.height / a.bitmap.width
                    val dst = android.graphics.RectF(a.center.x * w - tw / 2, a.center.y * h - th / 2, a.center.x * w + tw / 2, a.center.y * h + th / 2)
                    canvas.drawBitmap(a.bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, pageId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { AnnotateViewModel(container, pageId) }
        }
    }
}
