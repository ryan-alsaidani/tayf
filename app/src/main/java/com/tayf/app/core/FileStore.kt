package com.tayf.app.core

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class FileStore(private val context: Context) {

    private fun dir(parent: File, name: String): File = File(parent, name).also { it.mkdirs() }
    private fun uuid(): String = UUID.randomUUID().toString()

    /** filesDir/scans/originals/<uuid>.jpg */
    fun newOriginalFile(): File = File(dir(context.filesDir, "scans/originals"), "${uuid()}.jpg")

    /** filesDir/scans/processed/<uuid>.jpg */
    fun newProcessedFile(): File = File(dir(context.filesDir, "scans/processed"), "${uuid()}.jpg")

    /** filesDir/scans/thumbs/<uuid>.jpg */
    fun newThumbFile(): File = File(dir(context.filesDir, "scans/thumbs"), "${uuid()}.jpg")

    /** cacheDir/tmp/<uuid>.<ext> */
    fun newTempFile(ext: String): File = File(dir(context.cacheDir, "tmp"), "${uuid()}.$ext")

    /** cacheDir/exports (shared through FileProvider) */
    fun exportDir(): File = dir(context.cacheDir, "exports")

    /** filesDir/vault */
    fun vaultDir(): File = dir(context.filesDir, "vault")

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "com.tayf.app.fileprovider", file)

    fun deleteQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun clearTemp() {
        runCatching { File(context.cacheDir, "tmp").deleteRecursively() }
    }
}
