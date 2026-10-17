package com.tayf.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.tayf.app.privacy.VaultCrypto
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

/** Image decoding/encoding helpers: sampling, 4000px cap, EXIF rotation, transparent vault (.enc) support. */
object BitmapIO {
    const val MAX_SIDE = 4000

    fun decodeFile(context: Context, path: String, maxSide: Int = MAX_SIDE): Bitmap? {
        return try {
            val f = File(path)
            if (!f.exists()) return null
            if (path.endsWith(VaultCrypto.EXT)) {
                val bytes = VaultCrypto.readBytes(context, f)
                decode({ ByteArrayInputStream(bytes) }, maxSide)
            } else {
                decode({ FileInputStream(f) }, maxSide)
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun decodeUri(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? {
        return try {
            decode({ context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("no stream") }, maxSide)
        } catch (e: Throwable) {
            null
        }
    }

    private fun decode(open: () -> InputStream, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longSide = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = open().use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = try {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return fitAndOrient(bmp, orientation, maxSide)
    }

    private fun fitAndOrient(bmp: Bitmap, orientation: Int, maxSide: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        val longSide = max(bmp.width, bmp.height)
        if (longSide > maxSide) {
            val s = maxSide.toFloat() / longSide
            m.postScale(s, s)
        }
        if (m.isIdentity) return bmp
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 90) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), it) }
    }

    fun savePng(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
