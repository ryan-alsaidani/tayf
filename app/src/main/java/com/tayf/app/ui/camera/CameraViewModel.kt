package com.tayf.app.ui.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.SystemClock
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
import com.tayf.app.core.Formatters
import com.tayf.app.core.FilterType
import com.tayf.app.core.PageModel
import com.tayf.app.core.Quad
import com.tayf.app.core.ScanMode
import com.tayf.app.data.PostScan
import com.tayf.app.vision.BookSplitter
import com.tayf.app.vision.DocTypeClassifier
import com.tayf.app.vision.EdgeDetector
import com.tayf.app.vision.ImageEnhancer
import com.tayf.app.vision.LongScanStitcher
import com.tayf.app.vision.PerspectiveWarp
import com.tayf.app.vision.QualityAnalyzer
import com.tayf.app.vision.QualityReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class CameraUiState(
    val quad: Quad? = null,
    val confidence: Float = 0f,
    val quality: QualityReport? = null,
    val capturedCount: Int = 0,
    val busy: Boolean = false,
    val stableProgress: Float = 0f,
    val idStep: Int = 0,
    val longActive: Boolean = false,
    val longFrames: Int = 0,
    val autoCaptureTick: Int = 0,
    val finishedDocId: Long? = null,
    val errorRes: Int? = null,
    val qualityGuard: Boolean = true
)

class CameraViewModel(
    private val container: AppContainer,
    private val mode: ScanMode,
    existingDocId: Long?
) : ViewModel() {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private var docId: Long? = existingDocId
    private var placeholderName: String? = null
    @Volatile private var autoCapture = true
    @Volatile private var qualityGuard = true
    @Volatile private var defaultFilter = FilterType.ENHANCED_COLOR
    @Volatile private var jpegQuality = 90

    private var frameCounter = 0
    private var stableSince = 0L
    private var lastQuad: Quad? = null
    private var armed = true
    private var idFront: Bitmap? = null
    private val longFrames = ArrayList<Bitmap>()
    private var lastLongMs = 0L

    init {
        viewModelScope.launch { container.settings.autoCapture.collect { autoCapture = it } }
        viewModelScope.launch { container.settings.qualityGuard.collect { qualityGuard = it; _state.update { s -> s.copy(qualityGuard = it) } } }
        viewModelScope.launch { container.settings.defaultFilter.collect { defaultFilter = it } }
        viewModelScope.launch { container.settings.jpegQuality.collect { jpegQuality = it } }
    }

    // ---------------- live analysis (called on the analyzer thread) ----------------

    fun onFrame(frame: Bitmap) {
        val current = _state.value
        if (current.busy) return
        frameCounter++
        val det = EdgeDetector.detectWithConfidence(frame)
        val conf = det?.second ?: 0f
        val quad = if (det != null && conf >= 0.40f) det.first else null
        var quality = current.quality
        if (frameCounter % 4 == 1) quality = runCatching { QualityAnalyzer.analyze(frame) }.getOrNull()

        if (mode == ScanMode.LONG) {
            if (current.longActive) addLongFrame(frame)
            _state.update { it.copy(quad = null, confidence = 0f, quality = quality) }
            return
        }

        val now = SystemClock.elapsedRealtime()
        var progress = 0f
        var tick = current.autoCaptureTick
        val prev = lastQuad
        if (quad != null && conf >= 0.55f) {
            if (prev != null && maxShift(prev, quad) < 0.03f) {
                if (stableSince == 0L) stableSince = now
                progress = ((now - stableSince) / 1400f).coerceIn(0f, 1f)
            } else {
                stableSince = 0L
            }
            val qualityOk = !qualityGuard || quality?.isGood != false
            if (armed && autoCapture && progress >= 1f && qualityOk) {
                armed = false
                stableSince = 0L
                tick++
            }
        } else {
            stableSince = 0L
            if (conf < 0.35f) armed = true
        }
        lastQuad = quad
        _state.update { it.copy(quad = quad, confidence = conf, quality = quality, stableProgress = progress, autoCaptureTick = tick) }
    }

    private fun maxShift(a: Quad, b: Quad): Float = maxOf(
        abs(a.tlX - b.tlX), abs(a.tlY - b.tlY), abs(a.trX - b.trX), abs(a.trY - b.trY),
        abs(a.brX - b.brX), abs(a.brY - b.brY), abs(a.blX - b.blX), abs(a.blY - b.blY)
    )

    // ---------------- long receipt mode ----------------

    fun startLong() {
        synchronized(longFrames) {
            longFrames.forEach { it.recycle() }
            longFrames.clear()
        }
        lastLongMs = 0L
        _state.update { it.copy(longActive = true, longFrames = 0) }
    }

    private fun addLongFrame(frame: Bitmap) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLongMs < 450L) return
        lastLongMs = now
        val w = 900
        val h = max(1, (frame.height * w.toFloat() / frame.width).roundToInt())
        val copy = Bitmap.createScaledBitmap(frame, w, h, true)
        val stop: Boolean
        synchronized(longFrames) {
            longFrames.add(if (copy === frame) frame.copy(Bitmap.Config.ARGB_8888, false) else copy)
            stop = longFrames.size >= 24
            _state.update { it.copy(longFrames = longFrames.size) }
        }
        if (stop) viewModelScope.launch { stopLong() }
    }

    fun stopLong() {
        if (!_state.value.longActive) return
        _state.update { it.copy(longActive = false, busy = true) }
        viewModelScope.launch {
            try {
                withContext(AppDispatchers.cpu) {
                    val frames = synchronized(longFrames) { ArrayList(longFrames) }
                    val stitched = if (frames.size >= 2) LongScanStitcher.stitch(frames) else frames.firstOrNull()?.copy(Bitmap.Config.ARGB_8888, true)
                    synchronized(longFrames) {
                        longFrames.forEach { it.recycle() }
                        longFrames.clear()
                    }
                    if (stitched == null) {
                        _state.update { it.copy(errorRes = R.string.camera_error_capture) }
                    } else {
                        val type = DocTypeClassifier.classify(stitched, null)
                        val filter = DocTypeClassifier.suggestedFilter(if (type == DocType.UNKNOWN) DocType.INVOICE else type)
                        addProcessedPage(stitched, filter, DocType.INVOICE)
                        finish()
                    }
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    // ---------------- capture / import ----------------

    fun onCaptured(file: File) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                withContext(AppDispatchers.cpu) {
                    val bmp = BitmapIO.decodeFile(container.context, file.absolutePath)
                    file.delete()
                    if (bmp == null) {
                        _state.update { it.copy(errorRes = R.string.camera_error_capture) }
                    } else {
                        handleBitmap(bmp)
                    }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(errorRes = R.string.camera_error_capture) }
            } finally {
                lastQuad = null
                stableSince = 0L
                _state.update { it.copy(busy = false, stableProgress = 0f) }
            }
        }
    }

    fun importUris(uris: List<Uri>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                withContext(AppDispatchers.cpu) {
                    var added = 0
                    for (uri in uris) {
                        val bmp = BitmapIO.decodeUri(container.context, uri) ?: continue
                        addPageFromOriginal(bmp, null, null, DocType.UNKNOWN)
                        added++
                    }
                    if (added == 0) _state.update { it.copy(errorRes = R.string.camera_error_import) } else finish()
                }
            } catch (e: Throwable) {
                _state.update { it.copy(errorRes = R.string.camera_error_import) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorRes = null) }

    /** Ends the session: starts background OCR/naming and signals navigation to the document. */
    fun finish() {
        val id = docId ?: return
        PostScan.launch(container, id, placeholderName)
        _state.update { it.copy(finishedDocId = id) }
    }

    private suspend fun handleBitmap(bmp: Bitmap) {
        when (mode) {
            ScanMode.SINGLE -> { addPageFromOriginal(bmp, null, null, DocType.UNKNOWN); finish() }
            ScanMode.BATCH -> addPageFromOriginal(bmp, null, null, DocType.UNKNOWN)
            ScanMode.WHITEBOARD -> {
                addPageFromOriginal(bmp, null, FilterType.ENHANCED_COLOR, DocType.WHITEBOARD)
                finish()
            }
            ScanMode.ID_CARD -> handleIdCard(bmp)
            ScanMode.BOOK -> handleBook(bmp)
            ScanMode.LONG -> addPageFromOriginal(bmp, null, null, DocType.UNKNOWN)
        }
    }

    private suspend fun handleIdCard(bmp: Bitmap) {
        val quad = EdgeDetector.detect(bmp) ?: insetQuad()
        val warped = PerspectiveWarp.warp(bmp, quad)
        bmp.recycle()
        val enhanced = ImageEnhancer.apply(warped, FilterType.ENHANCED_COLOR, 0f, 1f, false)
        warped.recycle()
        val front = idFront
        if (front == null) {
            idFront = enhanced
            _state.update { it.copy(idStep = 1) }
            return
        }
        val combined = combineIdSides(front, enhanced)
        front.recycle()
        enhanced.recycle()
        idFront = null
        addProcessedPage(combined, FilterType.ORIGINAL, DocType.ID_CARD)
        _state.update { it.copy(idStep = 0) }
        finish()
    }

    private fun combineIdSides(front: Bitmap, back: Bitmap): Bitmap {
        val pageW = 1654
        val pageH = 2339
        val out = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val maxW = 1300f
        val maxH = 900f
        var y = 280f
        for (side in listOf(front, back)) {
            val s = minOf(maxW / side.width, maxH / side.height)
            val w = side.width * s
            val h = side.height * s
            val m = Matrix().apply { postScale(s, s); postTranslate((pageW - w) / 2f, y) }
            canvas.drawBitmap(side, m, null)
            y += h + 160f
        }
        return out
    }

    private suspend fun handleBook(bmp: Bitmap) {
        val halves = BookSplitter.split(bmp)
        if (halves == null) {
            addPageFromOriginal(bmp, null, DocTypeClassifier.suggestedFilter(DocType.BOOK), DocType.BOOK)
        } else {
            bmp.recycle()
            for (half in halves) addProcessedPage(half, DocTypeClassifier.suggestedFilter(DocType.BOOK), DocType.BOOK)
        }
        finish()
    }

    // ---------------- page creation ----------------

    private fun insetQuad(): Quad = Quad(0.04f, 0.04f, 0.96f, 0.04f, 0.96f, 0.96f, 0.04f, 0.96f)

    private suspend fun ensureDocument(type: DocType): Long {
        docId?.let { return it }
        val hijri = container.settings.hijriDates.first()
        val name = container.context.getString(R.string.doc_default_name) + " - " + Formatters.formatDate(System.currentTimeMillis(), hijri)
        placeholderName = name
        val id = container.documents.createDocument(name, null, type, false)
        docId = id
        return id
    }

    /** Camera/gallery image: detect the edges, straighten, filter, save original + processed + thumbnail. */
    private suspend fun addPageFromOriginal(original: Bitmap, quadIn: Quad?, filterOverride: FilterType?, type: DocType) {
        val quad = quadIn ?: EdgeDetector.detect(original) ?: insetQuad()
        val originalFile = container.fileStore.newOriginalFile()
        BitmapIO.saveJpeg(original, originalFile, 95)
        val warped = PerspectiveWarp.warp(original, quad)
        original.recycle()
        var filter = filterOverride
        if (filter == null) {
            val guess = DocTypeClassifier.classify(warped, null)
            filter = if (guess != DocType.UNKNOWN) DocTypeClassifier.suggestedFilter(guess) else defaultFilter
        }
        val processed = ImageEnhancer.apply(warped, filter, 0f, 1f, false)
        warped.recycle()
        savePage(originalFile, processed, quad, filter, type)
    }

    /** Image that is already final (stitched receipt, book half, ID sheet): stored as its own original. */
    private suspend fun addProcessedPage(bitmap: Bitmap, filter: FilterType, type: DocType) {
        val originalFile = container.fileStore.newOriginalFile()
        BitmapIO.saveJpeg(bitmap, originalFile, 95)
        val processed = if (filter == FilterType.ORIGINAL) bitmap else ImageEnhancer.apply(bitmap, filter, 0f, 1f, false)
        if (processed !== bitmap) bitmap.recycle()
        savePage(originalFile, processed, Quad.full(), filter, type)
    }

    private suspend fun savePage(originalFile: File, processed: Bitmap, quad: Quad, filter: FilterType, type: DocType) {
        val processedFile = container.fileStore.newProcessedFile()
        BitmapIO.saveJpeg(processed, processedFile, jpegQuality)
        val thumb = ImageEnhancer.makeThumbnail(processed)
        val thumbFile = container.fileStore.newThumbFile()
        BitmapIO.saveJpeg(thumb, thumbFile, 80)
        thumb.recycle()
        processed.recycle()
        val id = ensureDocument(type)
        container.documents.addPage(
            id,
            PageModel(
                id = 0, documentId = id, position = 0,
                originalPath = originalFile.absolutePath, processedPath = processedFile.absolutePath,
                thumbPath = thumbFile.absolutePath, quad = quad, filter = filter,
                brightness = 0f, contrast = 1f, rotation = 0, ocrText = null, ocrBlocksJson = null
            )
        )
        _state.update { it.copy(capturedCount = it.capturedCount + 1) }
    }

    override fun onCleared() {
        idFront?.recycle()
        synchronized(longFrames) {
            longFrames.forEach { it.recycle() }
            longFrames.clear()
        }
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer, mode: ScanMode, docId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer { CameraViewModel(container, mode, docId) }
        }
    }
}
