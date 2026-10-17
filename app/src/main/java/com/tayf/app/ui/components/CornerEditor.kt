package com.tayf.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tayf.app.core.Quad
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfGoldLight
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun quadPoints(q: Quad): List<Offset> =
    listOf(Offset(q.tlX, q.tlY), Offset(q.trX, q.trY), Offset(q.brX, q.brY), Offset(q.blX, q.blY))

private fun withCorner(q: Quad, i: Int, x: Float, y: Float): Quad = when (i) {
    0 -> q.copy(tlX = x, tlY = y)
    1 -> q.copy(trX = x, trY = y)
    2 -> q.copy(brX = x, brY = y)
    else -> q.copy(blX = x, blY = y)
}

/** Where the image is drawn (letterboxed) inside the box. */
fun fitRect(box: IntSize, iw: Int, ih: Int): Rect {
    if (box.width <= 0 || box.height <= 0 || iw <= 0 || ih <= 0) return Rect(0f, 0f, 1f, 1f)
    val s = min(box.width.toFloat() / iw, box.height.toFloat() / ih)
    val w = iw * s
    val h = ih * s
    return Rect((box.width - w) / 2f, (box.height - h) / 2f, (box.width + w) / 2f, (box.height + h) / 2f)
}

/** Image with a draggable quadrilateral (4 corner handles) and a magnifier loupe while dragging. */
@Composable
fun CornerEditor(bitmap: Bitmap, quad: Quad, onQuadChange: (Quad) -> Unit, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var active by remember { mutableIntStateOf(-1) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val currentQuad by rememberUpdatedState(quad)
    val currentChange by rememberUpdatedState(onQuadChange)
    val rect = fitRect(box, bitmap.width, bitmap.height)
    val density = LocalDensity.current
    val grabPx = with(density) { 64.dp.toPx() }
    val loupeR = with(density) { 54.dp.toPx() }
    val handleR = with(density) { 11.dp.toPx() }

    Box(
        modifier = modifier
            .onSizeChanged { box = it }
            .pointerInput(rect) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val pts = quadPoints(currentQuad).map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
                        var best = -1
                        var bestD = Float.MAX_VALUE
                        pts.forEachIndexed { i, p ->
                            val d = hypot(p.x - pos.x, p.y - pos.y)
                            if (d < bestD) { bestD = d; best = i }
                        }
                        active = if (bestD <= grabPx) best else -1
                        touch = pos
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (active >= 0) {
                            touch = change.position
                            val nx = ((change.position.x - rect.left) / rect.width).coerceIn(0f, 1f)
                            val ny = ((change.position.y - rect.top) / rect.height).coerceIn(0f, 1f)
                            currentChange(withCorner(currentQuad, active, nx, ny))
                        }
                    },
                    onDragEnd = { active = -1 },
                    onDragCancel = { active = -1 }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                image,
                srcOffset = IntOffset.Zero, srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt())
            )
            val pts = quadPoints(quad).map { Offset(rect.left + it.x * rect.width, rect.top + it.y * rect.height) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                lineTo(pts[1].x, pts[1].y)
                lineTo(pts[2].x, pts[2].y)
                lineTo(pts[3].x, pts[3].y)
                close()
            }
            drawPath(path, Color(0x33C9A24B))
            drawPath(path, TayfGold, style = Stroke(width = 3f))
            pts.forEachIndexed { i, p ->
                drawCircle(Color(0x66000000), handleR + 3f, p)
                drawCircle(if (i == active) TayfGoldLight else TayfGold, handleR, p)
            }
            if (active >= 0) {
                val corner = quadPoints(quad)[active]
                val cx = corner.x * bitmap.width
                val cy = corner.y * bitmap.height
                val scale = rect.width / bitmap.width
                val zoom = 2.5f
                val srcSide = max(8f, min(min(bitmap.width, bitmap.height).toFloat(), 2f * loupeR / (scale * zoom)))
                val sx = (cx - srcSide / 2f).coerceIn(0f, bitmap.width - srcSide)
                val sy = (cy - srcSide / 2f).coerceIn(0f, bitmap.height - srcSide)
                var lc = Offset(touch.x, touch.y - loupeR * 2.2f)
                if (lc.y < loupeR) lc = Offset(lc.x, touch.y + loupeR * 2.2f)
                lc = Offset(lc.x.coerceIn(loupeR, size.width - loupeR), lc.y.coerceIn(loupeR, size.height - loupeR))
                val circle = Path().apply { addOval(Rect(lc.x - loupeR, lc.y - loupeR, lc.x + loupeR, lc.y + loupeR)) }
                clipPath(circle) {
                    drawRect(Color.Black, topLeft = Offset(lc.x - loupeR, lc.y - loupeR), size = Size(loupeR * 2, loupeR * 2))
                    drawImage(
                        image,
                        srcOffset = IntOffset(sx.roundToInt(), sy.roundToInt()),
                        srcSize = IntSize(srcSide.roundToInt(), srcSide.roundToInt()),
                        dstOffset = IntOffset((lc.x - loupeR).roundToInt(), (lc.y - loupeR).roundToInt()),
                        dstSize = IntSize((loupeR * 2).roundToInt(), (loupeR * 2).roundToInt())
                    )
                }
                drawCircle(TayfGold, loupeR, lc, style = Stroke(width = 4f))
                drawLine(TayfGold, Offset(lc.x - 12f, lc.y), Offset(lc.x + 12f, lc.y), strokeWidth = 2f)
                drawLine(TayfGold, Offset(lc.x, lc.y - 12f), Offset(lc.x, lc.y + 12f), strokeWidth = 2f)
            }
        }
    }
}
