package com.tayf.app.ui.annotate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tayf.app.R
import com.tayf.app.ui.theme.TayfGold

/** Dialog with a finger-drawing pad; confirms with a transparent PNG-ready bitmap. */
@Composable
fun SignaturePad(saved: Bitmap?, onConfirm: (Bitmap, Boolean) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.annotate_signature)) },
        text = {
            Column {
                ComposeCanvas(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(ComposeColor.White)
                        .onSizeChanged { size = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDrag = { change, _ -> change.consume(); current = current + change.position },
                                onDragEnd = { if (current.isNotEmpty()) strokes.add(current); current = emptyList() },
                                onDragCancel = { current = emptyList() }
                            )
                        }
                ) {
                    for (s in strokes + listOf(current)) {
                        if (s.size < 2) continue
                        val path = Path().apply {
                            moveTo(s[0].x, s[0].y)
                            for (i in 1 until s.size) lineTo(s[i].x, s[i].y)
                        }
                        drawPath(path, ComposeColor(0xFF10205A), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { strokes.clear(); current = emptyList() }) { Text(stringResource(R.string.annotate_clear)) }
                    if (saved != null) {
                        TextButton(onClick = { onConfirm(saved, false) }) { Text(stringResource(R.string.annotate_use_saved), color = TayfGold) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.isNotEmpty() && size.width > 0,
                onClick = { render(strokes.toList(), size)?.let { onConfirm(it, true) } }
            ) { Text(stringResource(R.string.common_ok), color = TayfGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** Renders the strokes into a transparent bitmap cropped to the drawn area. */
private fun render(strokes: List<List<Offset>>, size: IntSize): Bitmap? {
    val pts = strokes.flatten()
    if (pts.isEmpty()) return null
    val pad = 10f
    val minX = (pts.minOf { it.x } - pad).coerceAtLeast(0f)
    val minY = (pts.minOf { it.y } - pad).coerceAtLeast(0f)
    val maxX = (pts.maxOf { it.x } + pad).coerceAtMost(size.width.toFloat())
    val maxY = (pts.maxOf { it.y } + pad).coerceAtMost(size.height.toFloat())
    val w = (maxX - minX).toInt().coerceAtLeast(8)
    val h = (maxY - minY).toInt().coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(16, 32, 90)
    }
    for (s in strokes) {
        if (s.size < 2) continue
        val path = android.graphics.Path()
        path.moveTo(s[0].x - minX, s[0].y - minY)
        for (i in 1 until s.size) path.lineTo(s[i].x - minX, s[i].y - minY)
        canvas.drawPath(path, paint)
    }
    return bmp
}
