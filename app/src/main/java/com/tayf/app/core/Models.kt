package com.tayf.app.core

enum class FilterType { ORIGINAL, BLACK_WHITE, GRAY, ENHANCED_COLOR }
enum class DocType { UNKNOWN, ID_CARD, INVOICE, BOOK, HANDWRITTEN, WHITEBOARD }
enum class ScanMode { SINGLE, BATCH, ID_CARD, BOOK, WHITEBOARD, LONG }
enum class PageSize(val widthMm: Int, val heightMm: Int) {
    AUTO(0, 0), A3(297, 420), A4(210, 297), A5(148, 210), A6(105, 148), LETTER(216, 279), LEGAL(216, 356)
}
enum class ExportFormat { PDF, JPG, PNG, TXT, DOCX, XLSX }
enum class SortOrder { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC }

/** Normalized corner coordinates in 0f..1f relative to the ORIGINAL image. Order: topLeft, topRight, bottomRight, bottomLeft. */
data class Quad(
    val tlX: Float, val tlY: Float, val trX: Float, val trY: Float,
    val brX: Float, val brY: Float, val blX: Float, val blY: Float
) {
    companion object { fun full(): Quad = Quad(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f) }
}

data class PageModel(
    val id: Long,
    val documentId: Long,
    val position: Int,
    val originalPath: String,      // absolute path, camera/import image
    val processedPath: String,     // absolute path, after crop+filter (what the user sees)
    val thumbPath: String,
    val quad: Quad,
    val filter: FilterType,
    val brightness: Float,         // -1f..1f, default 0f
    val contrast: Float,           // 0.5f..2f, default 1f
    val rotation: Int,             // 0, 90, 180, 270
    val ocrText: String?,
    val ocrBlocksJson: String?     // JSON list of OcrBlock (see 4.6)
)

data class DocumentModel(
    val id: Long,
    val name: String,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val isVault: Boolean,
    val docType: DocType,
    val pageCount: Int,
    val expiryDate: Long?          // epoch millis or null
)

data class FolderModel(val id: Long, val name: String, val isVault: Boolean, val createdAt: Long)

data class SearchHit(
    val documentId: Long, val documentName: String, val pageId: Long, val pageIndex: Int,
    val snippet: String,
    val boxes: List<NormRect>      // highlight boxes on the processed page image
)
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) // 0..1
