package com.tayf.app.export

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.tayf.app.core.AppDispatchers
import com.tayf.app.core.AppScope
import com.tayf.app.core.FileStore
import com.tayf.app.core.Outcome
import com.tayf.app.core.PageModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PrintHelper {

    /** Renders the pages to a PDF and hands it to the Android print framework. */
    fun printPages(activity: Activity, pages: List<PageModel>, jobName: String) {
        val appContext = activity.applicationContext
        AppScope.scope.launch {
            val file = File(FileStore(appContext).exportDir(), "print_${System.currentTimeMillis()}.pdf")
            val result = PdfExporter.export(pages, file, PdfOptions(searchable = false, jpegQuality = 85, maxSidePx = 2400))
            if (result is Outcome.Ok) {
                withContext(AppDispatchers.main) { start(activity, file, jobName) }
            }
        }
    }

    private fun start(activity: Activity, file: File, jobName: String) {
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?, callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { out -> input.copyTo(out, 64 * 1024) }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, null)
    }
}
