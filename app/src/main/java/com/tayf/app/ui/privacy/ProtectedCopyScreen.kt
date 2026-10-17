package com.tayf.app.ui.privacy

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.DocType
import com.tayf.app.core.Formatters
import com.tayf.app.core.FilterType
import com.tayf.app.core.NormRect
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import com.tayf.app.core.Quad
import com.tayf.app.export.ShareHelper
import com.tayf.app.privacy.RedactionRegion
import com.tayf.app.privacy.RedactionStyle
import com.tayf.app.privacy.Redactor
import com.tayf.app.privacy.Watermarker
import com.tayf.app.data.PostScan
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfChip
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.components.fitRect
import com.tayf.app.ui.components.rememberPageBitmap
import com.tayf.app.ui.components.toast
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfDanger
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfTextDim
import com.tayf.app.vision.ImageEnhancer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

/** Builds an irreversible, flattened copy: regions are blacked out / pixelated, then a watermark is burned in. */
@Composable
fun ProtectedCopyScreen(container: AppContainer, navController: NavHostController, pageId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val page by produceState<PageModel?>(null, pageId) { value = container.documents.getPage(pageId) }
    val preview by rememberPageBitmap(page?.processedPath.orEmpty(), 1600)
    val hijri by container.settings.hijriDates.collectAsState(initial = false)

    val regions = remember { mutableStateListOf<RedactionRegion>() }
    var style by remember { mutableStateOf(RedactionStyle.BLACK_BOX) }
    var watermark by remember { mutableStateOf("") }
    var withDate by remember { mutableStateOf(true) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun buildResult(p: PageModel): Bitmap? = withContext(AppDispatchers.cpu) {
        val base = BitmapIO.decodeFile(container.context, p.processedPath) ?: return@withContext null
        val red = Redactor.apply(base, regions.toList())
        base.recycle()
        if (watermark.isBlank()) red else {
            val text = if (withDate) watermark.trim() + " - " + Formatters.formatDate(System.currentTimeMillis(), hijri) else watermark.trim()
            val wm = Watermarker.apply(red, text)
            red.recycle()
            wm
        }
    }

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(stringResource(R.string.protected_title), onBack = { navController.popBackStack() })
        val b = preview
        val p = page
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).onSizeChanged { box = it }) {
            if (b == null || p == null) LoadingBox() else {
                val rect = fitRect(box, b.width, b.height)
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(
                    Modifier.fillMaxSize().pointerInput(rect, style) {
                        detectDragGestures(
                            onDragStart = { dragStart = it; dragNow = it },
                            onDrag = { change, _ -> change.consume(); dragNow = change.position },
                            onDragEnd = {
                                val s = dragStart
                                val e = dragNow
                                if (s != null && e != null && rect.width > 0f && abs(e.x - s.x) > 12f && abs(e.y - s.y) > 12f) {
                                    fun nx(v: Float) = ((v - rect.left) / rect.width).coerceIn(0f, 1f)
                                    fun ny(v: Float) = ((v - rect.top) / rect.height).coerceIn(0f, 1f)
                                    regions.add(RedactionRegion(NormRect(min(nx(s.x), nx(e.x)), min(ny(s.y), ny(e.y)), maxOf(nx(s.x), nx(e.x)), maxOf(ny(s.y), ny(e.y))), style))
                                }
                                dragStart = null
                                dragNow = null
                            },
                            onDragCancel = { dragStart = null; dragNow = null }
                        )
                    }
                ) {
                    for (r in regions) {
                        val tl = Offset(rect.left + r.rect.left * rect.width, rect.top + r.rect.top * rect.height)
                        val sz = Size((r.rect.right - r.rect.left) * rect.width, (r.rect.bottom - r.rect.top) * rect.height)
                        drawRect(if (r.style == RedactionStyle.BLACK_BOX) Color.Black else Color(0xCC555555), tl, sz)
                        drawRect(TayfGold, tl, sz, style = Stroke(2f))
                    }
                    val s = dragStart
                    val e = dragNow
                    if (s != null && e != null) {
                        drawRect(TayfGold, Offset(min(s.x, e.x), min(s.y, e.y)), Size(abs(e.x - s.x), abs(e.y - s.y)), style = Stroke(3f))
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.protected_hint), color = TayfTextDim)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TayfChip(stringResource(R.string.protected_black_box), style == RedactionStyle.BLACK_BOX, { style = RedactionStyle.BLACK_BOX })
                TayfChip(stringResource(R.string.protected_blur), style == RedactionStyle.HEAVY_BLUR, { style = RedactionStyle.HEAVY_BLUR })
                TextButton(onClick = { if (regions.isNotEmpty()) regions.removeAt(regions.lastIndex) }, enabled = regions.isNotEmpty()) {
                    Text(stringResource(R.string.protected_undo), color = if (regions.isNotEmpty()) TayfDanger else TayfTextDim)
                }
            }
            OutlinedTextField(
                value = watermark, onValueChange = { watermark = it }, singleLine = true,
                label = { Text(stringResource(R.string.protected_watermark)) }, modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TayfChip(stringResource(R.string.protected_add_date), withDate, { withDate = !withDate })
            }
            if (busy) LoadingBox(Modifier.height(48.dp)) else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TayfButton(stringResource(R.string.protected_save), {
                    val pg = page ?: return@TayfButton
                    busy = true
                    scope.launch {
                        try {
                            val out = buildResult(pg)
                            if (out == null) toast(context, R.string.error_generic) else {
                                val id = saveAsNewDocument(container, pg, out)
                                navController.navigate(Routes.document(id))
                            }
                        } catch (e: Throwable) {
                            toast(context, R.string.error_generic)
                        } finally {
                            busy = false
                        }
                    }
                }, Modifier.weight(1f), enabled = page != null)
                TayfButton(stringResource(R.string.export_share), {
                    val pg = page ?: return@TayfButton
                    busy = true
                    scope.launch {
                        try {
                            val out = buildResult(pg)
                            if (out == null) toast(context, R.string.error_generic) else {
                                val file = container.fileStore.exportDir().resolve("protected_${System.currentTimeMillis()}.jpg")
                                withContext(AppDispatchers.io) { BitmapIO.saveJpeg(out, file, 90) }
                                out.recycle()
                                ShareHelper.share(context, listOf(file), "image/jpeg")
                            }
                        } catch (e: Throwable) {
                            toast(context, R.string.error_generic)
                        } finally {
                            busy = false
                        }
                    }
                }, Modifier.weight(1f), outlined = true, enabled = page != null)
            }
        }
    }
}

private suspend fun saveAsNewDocument(container: AppContainer, source: PageModel, bitmap: Bitmap): Long {
    val srcDoc = container.documents.getDocument(source.documentId)
    val name = (srcDoc?.name ?: "") + " - " + container.context.getString(R.string.protected_suffix)
    val newId = container.documents.createDocument(name, srcDoc?.folderId, srcDoc?.docType ?: DocType.UNKNOWN, srcDoc?.isVault ?: false)
    val quality = container.settings.jpegQuality.first()
    val model = withContext(AppDispatchers.io) {
        val original = container.fileStore.newOriginalFile()
        BitmapIO.saveJpeg(bitmap, original, 95)
        val processed = container.fileStore.newProcessedFile()
        BitmapIO.saveJpeg(bitmap, processed, quality)
        val thumbBmp = ImageEnhancer.makeThumbnail(bitmap)
        val thumb = container.fileStore.newThumbFile()
        BitmapIO.saveJpeg(thumbBmp, thumb, 80)
        thumbBmp.recycle()
        bitmap.recycle()
        PageModel(0, newId, 0, original.absolutePath, processed.absolutePath, thumb.absolutePath, Quad.full(), FilterType.ORIGINAL, 0f, 1f, 0, null, null)
    }
    var finalModel = model
    if (srcDoc?.isVault == true) {
        val enc = container.vault.encryptPageFiles(model)
        if (enc is Outcome.Ok) finalModel = enc.value
    }
    container.documents.addPage(newId, finalModel)
    PostScan.launch(container, newId, null)
    return newId
}
