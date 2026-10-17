package com.tayf.app.ui.theme

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},${cy}a$r,$r 0 1 0 ${2 * r},0a$r,$r 0 1 0 ${-2 * r},0"

private fun icon(name: String, vararg paths: String, autoMirror: Boolean = false): ImageVector {
    val builder = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f, autoMirror = autoMirror
    )
    for (p in paths) {
        builder.addPath(
            pathData = PathParser().parsePathString(p).toNodes(),
            fill = null,
            stroke = SolidColor(TayfGold),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return builder.build()
}

/** Hand-drawn gold line icons (24x24, stroke only). */
object TayfIcons {
    val Scan = icon("Scan", "M4,8V5a1,1 0 0 1 1,-1h3", "M16,4h3a1,1 0 0 1 1,1v3", "M20,16v3a1,1 0 0 1 -1,1h-3", "M8,20H5a1,1 0 0 1 -1,-1v-3", "M3,12h18")
    val Library = icon("Library", "M4,4h7v7H4z", "M13,4h7v7h-7z", "M4,13h7v7H4z", "M13,13h7v7h-7z")
    val Folder = icon("Folder", "M3,7a2,2 0 0 1 2,-2h4l2,2h8a2,2 0 0 1 2,2v8a2,2 0 0 1 -2,2H5a2,2 0 0 1 -2,-2z")
    val Star = icon("Star", "M12,3l2.7,5.6 6.1,0.9 -4.4,4.3 1,6.1L12,17l-5.4,2.9 1,-6.1L3.2,9.5l6.1,-0.9z")
    val Search = icon("Search", circle(10.5f, 10.5f, 6.5f), "M15.5,15.5L20,20")
    val Lock = icon("Lock", "M6,11h12v9H6z", "M8,11V8a4,4 0 0 1 8,0v3")
    val Vault = icon("Vault", "M4,4h16v16H4z", circle(12f, 12f, 3.5f), "M12,12l2,-2", "M8,20v1.5", "M16,20v1.5")
    val Share = icon("Share", circle(18f, 5f, 2.5f), circle(6f, 12f, 2.5f), circle(18f, 19f, 2.5f), "M8.2,10.8l7.6,-4.4", "M8.2,13.2l7.6,4.4")
    val Print = icon("Print", "M7,9V4h10v5", "M7,17H5a1,1 0 0 1 -1,-1v-5a2,2 0 0 1 2,-2h12a2,2 0 0 1 2,2v5a1,1 0 0 1 -1,1h-2", "M7,14h10v6H7z")
    val Export = icon("Export", "M12,15V3", "M8,7l4,-4 4,4", "M5,12v7a1,1 0 0 0 1,1h12a1,1 0 0 0 1,-1v-7")
    val Crop = icon("Crop", "M7,3v14h14", "M3,7h14v14")
    val Rotate = icon("Rotate", "M4,12a8,8 0 1 0 2.3,-5.7", "M4,4v5h5")
    val Filter = icon("Filter", "M4,7h9", "M17,7h3", "M13,5v4", "M4,17h3", "M11,17h9", "M7,15v4")
    val Sign = icon("Sign", "M3,17c3,-6 4,-10 6,-10s0,8 3,6 3,-5 5,-3 1,4 4,1", "M3,21h18")
    val Text = icon("Text", "M5,6V4h14v2", "M12,4v16", "M9,20h6")
    val Highlight = icon("Highlight", "M14.5,4.5l5,5 -8,8 -5,-5z", "M9,14l-4,5h5l1,-1")
    val Draw = icon("Draw", "M4,20l1,-4L16.5,4.5a2,2 0 0 1 3,3L8,19z", "M14,7l3,3")
    val Blur = icon("Blur", "M12,3C9,8 6,11 6,14.5a6,6 0 0 0 12,0C18,11 15,8 12,3z", "M9,15a3,3 0 0 0 3,3")
    val Watermark = icon("Watermark", "M4,19L12,5l8,14", "M7.5,14h9")
    val Compress = icon("Compress", "M4,14h6v6", "M20,10h-6V4", "M14,10l7,-7", "M3,21l7,-7")
    val Settings = icon("Settings", circle(12f, 12f, 3f), "M12,3v3", "M12,18v3", "M3,12h3", "M18,12h3", "M5.6,5.6l2.1,2.1", "M16.3,16.3l2.1,2.1", "M5.6,18.4l2.1,-2.1", "M16.3,7.7l2.1,-2.1")
    val Camera = icon("Camera", "M4,8h3l1.5,-2h7L17,8h3v11H4z", circle(12f, 13f, 3.5f))
    val Gallery = icon("Gallery", "M4,4h16v16H4z", "M4,16l5,-5 4,4 3,-3 4,4", circle(9f, 8.5f, 1.5f))
    val Delete = icon("Delete", "M5,7h14", "M9,7V4h6v3", "M7,7l1,13h8l1,-13", "M10,11v6", "M14,11v6")
    val Add = icon("Add", "M12,5v14", "M5,12h14")
    val Back = icon("Back", "M15,5l-7,7 7,7", autoMirror = true)
    val More = icon("More", circle(12f, 5f, 1.2f), circle(12f, 12f, 1.2f), circle(12f, 19f, 1.2f))
    val Expiry = icon("Expiry", "M4,6h16v14H4z", "M4,10h16", "M8,3v4", "M16,3v4", "M12,13v3l2,1")
}
