package com.tayf.app.ui.annotate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.ui.components.ColorPickerRow
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.InputDialog
import com.tayf.app.ui.components.LoadingBox
import com.tayf.app.ui.components.TayfButton
import com.tayf.app.ui.components.TayfChip
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.components.fitRect
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfIcons

@Composable
fun AnnotateScreen(container: AppContainer, navController: NavHostController, pageId: Long) {
    val vm: AnnotateViewModel = viewModel(key = "annotate-$pageId", factory = AnnotateViewModel.factory(container, pageId))
    val s by vm.state.collectAsState()
    LaunchedEffect(s.saved) { if (s.saved) navController.popBackStack() }

    var box by remember { mutableStateOf(IntSize.Zero) }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var pendingText by remember { mutableStateOf<Offset?>(null) }
    var padOpen by remember { mutableStateOf(false) }
    var sigBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var sigCenter by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var sigWidth by remember { mutableStateOf(0.3f) }

    val bmp = s.bitmap
    val rect = if (bmp != null) fitRect(box, bmp.width, bmp.height) else androidx.compose.ui.geometry.Rect.Zero
    val currentState by rememberUpdatedState(s)

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(
            stringResource(R.string.annotate_title), onBack = { navController.popBackStack() },
            actions = {
                IconButton(onClick = vm::undo, enabled = s.annotations.isNotEmpty()) { Text("↶", color = if (s.annotations.isNotEmpty()) TayfGold else Color.Gray) }
                IconButton(onClick = vm::redo, enabled = s.redo.isNotEmpty()) { Text("↷", color = if (s.redo.isNotEmpty()) TayfGold else Color.Gray) }
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).onSizeChanged { box = it }) {
            when {
                s.loading || s.saving -> LoadingBox()
                bmp == null || s.failed -> EmptyState(stringResource(R.string.error_generic))
                else -> {
                    Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(s.tool, rect) {
                            when (s.tool) {
                                AnnotateTool.DRAW, AnnotateTool.HIGHLIGHT -> detectDragGestures(
                                    onDragStart = { p -> live = listOf(p) },
                                    onDrag = { change, _ -> change.consume(); live = live + change.position },
                                    onDragEnd = {
                                        val st = currentState
                                        val pts = live.map { Offset(((it.x - rect.left) / rect.width).coerceIn(0f, 1f), ((it.y - rect.top) / rect.height).coerceIn(0f, 1f)) }
                                        if (pts.isNotEmpty()) {
                                            val hl = st.tool == AnnotateTool.HIGHLIGHT
                                            vm.add(Annotation.Stroke(pts, st.color, if (hl) 0.03f else st.width, hl))
                                        }
                                        live = emptyList()
                                    },
                                    onDragCancel = { live = emptyList() }
                                )
                                AnnotateTool.TEXT -> detectTapGestures { p ->
                                    pendingText = Offset(((p.x - rect.left) / rect.width).coerceIn(0f, 1f), ((p.y - rect.top) / rect.height).coerceIn(0f, 1f))
                                }
                                AnnotateTool.SIGNATURE -> detectTransformGestures { _, pan, zoom, _ ->
                                    if (sigBitmap != null && rect.width > 0f) {
                                        sigCenter = Offset(
                                            (sigCenter.x + pan.x / rect.width).coerceIn(0f, 1f),
                                            (sigCenter.y + pan.y / rect.height).coerceIn(0f, 1f)
                                        )
                                        sigWidth = (sigWidth * zoom).coerceIn(0.08f, 0.9f)
                                    }
                                }
                            }
                        }
                    ) {
                        fun px(o: Offset) = Offset(rect.left + o.x * rect.width, rect.top + o.y * rect.height)
                        for (a in s.annotations) {
                            when (a) {
                                is Annotation.Stroke -> drawStroke(a.points.map { px(it) }, a.color, a.width * rect.width, a.highlight)
                                is Annotation.TextNote -> drawContext.canvas.nativeCanvas.drawText(
                                    a.text.lines().firstOrNull().orEmpty(), px(a.pos).x, px(a.pos).y,
                                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = a.color; textSize = a.size * rect.width }
                                )
                                is Annotation.Signature -> drawImage(
                                    a.bitmap.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset(
                                        (px(a.center).x - a.width * rect.width / 2).toInt(),
                                        (px(a.center).y - a.width * rect.width * a.bitmap.height / a.bitmap.width / 2).toInt()
                                    ),
                                    dstSize = IntSize((a.width * rect.width).toInt(), (a.width * rect.width * a.bitmap.height / a.bitmap.width).toInt())
                                )
                            }
                        }
                        if (live.isNotEmpty()) {
                            val hl = s.tool == AnnotateTool.HIGHLIGHT
                            drawStroke(live, s.color, (if (hl) 0.03f else s.width) * rect.width, hl)
                        }
                        val sb = sigBitmap
                        if (sb != null) {
                            val w = sigWidth * rect.width
                            val h = w * sb.height / sb.width
                            val c = px(sigCenter)
                            drawImage(sb.asImageBitmap(), dstOffset = androidx.compose.ui.unit.IntOffset((c.x - w / 2).toInt(), (c.y - h / 2).toInt()), dstSize = IntSize(w.toInt(), h.toInt()))
                            drawRect(TayfGold, androidx.compose.ui.geometry.Offset(c.x - w / 2, c.y - h / 2), androidx.compose.ui.geometry.Size(w, h), style = Stroke(width = 3f))
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TayfChip(stringResource(R.string.annotate_draw), s.tool == AnnotateTool.DRAW, { vm.setTool(AnnotateTool.DRAW) })
                TayfChip(stringResource(R.string.annotate_highlight), s.tool == AnnotateTool.HIGHLIGHT, { vm.setTool(AnnotateTool.HIGHLIGHT) })
                TayfChip(stringResource(R.string.annotate_text), s.tool == AnnotateTool.TEXT, { vm.setTool(AnnotateTool.TEXT) })
                TayfChip(stringResource(R.string.annotate_signature), s.tool == AnnotateTool.SIGNATURE, { vm.setTool(AnnotateTool.SIGNATURE); if (sigBitmap == null) padOpen = true })
            }
            ColorPickerRow(s.color, vm::setColor, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (s.tool == AnnotateTool.DRAW) {
                Slider(
                    value = s.width, onValueChange = vm::setWidth, valueRange = 0.002f..0.02f,
                    modifier = Modifier.padding(horizontal = 16.dp), colors = SliderDefaults.colors(thumbColor = TayfGold, activeTrackColor = TayfGold)
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sigBitmap != null) {
                    TayfButton(stringResource(R.string.annotate_place), {
                        sigBitmap?.let { vm.add(Annotation.Signature(sigCenter, sigWidth, it)) }
                        sigBitmap = null
                    }, Modifier.weight(1f), outlined = true)
                }
                TayfButton(stringResource(R.string.common_save), vm::save, Modifier.weight(1f), enabled = s.annotations.isNotEmpty() && !s.saving)
            }
        }
    }

    if (padOpen) {
        SignaturePad(
            saved = s.savedSignature,
            onConfirm = { b, isNew -> if (isNew) vm.rememberSignature(b); sigBitmap = b; sigCenter = Offset(0.5f, 0.8f); padOpen = false },
            onDismiss = { padOpen = false }
        )
    }
    val pt = pendingText
    if (pt != null) {
        InputDialog(
            stringResource(R.string.annotate_text), stringResource(R.string.annotate_text_hint), stringResource(R.string.common_ok),
            onConfirm = { vm.add(Annotation.TextNote(it, pt, s.color, 0.04f)); pendingText = null },
            onDismiss = { pendingText = null }
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(points: List<Offset>, color: Int, width: Float, highlight: Boolean) {
    if (points.isEmpty()) return
    val c = Color(color).copy(alpha = if (highlight) 0.42f else 1f)
    if (points.size == 1) {
        drawCircle(c, width / 2f, points[0])
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
    }
    drawPath(path, c, style = Stroke(width = width.coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
