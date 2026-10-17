package com.tayf.app.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tayf.app.core.FileStore
import com.tayf.app.core.Outcome
import java.io.File

object ShareHelper {

    fun share(context: Context, files: List<File>, mime: String) {
        if (files.isEmpty()) return
        val store = FileStore(context)
        val uris = ArrayList(files.map { store.uriFor(it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        intent.type = mime
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri(null, uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        intent.clipData = clip
        val chooser = Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun saveToUri(context: Context, file: File, target: Uri): Outcome<Unit> {
        return try {
            val out = context.contentResolver.openOutputStream(target, "w")
                ?: return Outcome.Fail("Cannot open the destination")
            out.use { o -> file.inputStream().use { it.copyTo(o, 64 * 1024) } }
            Outcome.Ok(Unit)
        } catch (e: Throwable) {
            Outcome.Fail(e.message ?: "Save failed", e)
        }
    }
}
