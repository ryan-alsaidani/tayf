package com.tayf.app.vision

import android.graphics.Bitmap
import com.tayf.app.AppContainer
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.BitmapIO
import com.tayf.app.core.FilterType
import com.tayf.app.core.PageModel
import com.tayf.app.core.Quad
import com.tayf.app.privacy.VaultCrypto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Shared page pipeline: original -> warp(quad) -> rotate -> filter. */
object PageRenderer {

    fun render(
        original: Bitmap, quad: Quad, filter: FilterType, brightness: Float, contrast: Float,
        rotation: Int, removeShadows: Boolean = false
    ): Bitmap {
        val warped = PerspectiveWarp.warp(original, quad)
        val rotated = PerspectiveWarp.rotate(warped, rotation)
        if (rotated !== warped) warped.recycle()
        val out = ImageEnhancer.apply(rotated, filter, brightness, contrast, removeShadows)
        rotated.recycle()
        return out
    }

    /** Re-renders [page] from its original image and stores the new processed image and thumbnail. */
    suspend fun renderAndSave(container: AppContainer, page: PageModel, removeShadows: Boolean = false): PageModel =
        withContext(AppDispatchers.cpu) {
            val original = BitmapIO.decodeFile(container.context, page.originalPath)
                ?: throw IllegalStateException("Original image missing")
            val rendered = render(original, page.quad, page.filter, page.brightness, page.contrast, page.rotation, removeShadows)
            original.recycle()
            saveProcessedBitmap(container, page, rendered)
        }

    /**
     * Writes [bitmap] as the new processed image (+ thumbnail) of [page], deletes the old files,
     * clears stale OCR data and keeps vault pages encrypted. The bitmap is recycled. Persist the result with updatePage.
     */
    suspend fun saveProcessedBitmap(container: AppContainer, page: PageModel, bitmap: Bitmap): PageModel =
        withContext(AppDispatchers.io) {
            val quality = container.settings.jpegQuality.first()
            val processed = container.fileStore.newProcessedFile()
            BitmapIO.saveJpeg(bitmap, processed, quality)
            val thumbBmp = ImageEnhancer.makeThumbnail(bitmap)
            val thumb = container.fileStore.newThumbFile()
            BitmapIO.saveJpeg(thumbBmp, thumb, 80)
            thumbBmp.recycle()
            bitmap.recycle()
            container.fileStore.deleteQuietly(page.processedPath)
            container.fileStore.deleteQuietly(page.thumbPath)
            var updated = page.copy(
                processedPath = processed.absolutePath,
                thumbPath = thumb.absolutePath,
                ocrText = null,
                ocrBlocksJson = null
            )
            if (page.originalPath.endsWith(VaultCrypto.EXT)) {
                val enc = container.vault.encryptPageFiles(updated)
                if (enc is com.tayf.app.core.Outcome.Ok) updated = enc.value
            }
            updated
        }
}
