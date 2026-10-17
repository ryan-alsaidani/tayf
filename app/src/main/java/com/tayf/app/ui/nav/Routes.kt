package com.tayf.app.ui.nav

import com.tayf.app.core.ScanMode

object Routes {
    const val LIBRARY = "library"
    const val CAMERA = "camera/{mode}?docId={docId}"        // mode = ScanMode.name, docId optional (add pages to existing doc)
    const val CROP = "crop/{pageId}"
    const val EDITOR = "editor/{pageId}"
    const val DOCUMENT = "document/{docId}"
    const val SEARCH = "search"
    const val EXPIRING = "expiring"
    const val ANNOTATE = "annotate/{pageId}"
    const val PROTECTED_COPY = "protected/{pageId}"
    const val VAULT = "vault"
    const val LOCK = "lock"
    const val SETTINGS = "settings"
    const val TRANSLATE = "translate/{docId}"
    const val LANGUAGE_PACKS = "languagepacks"

    fun camera(mode: ScanMode, docId: Long? = null): String = "camera/${mode.name}?docId=${docId ?: -1L}"
    fun crop(pageId: Long): String = "crop/$pageId"
    fun editor(pageId: Long): String = "editor/$pageId"
    fun document(docId: Long): String = "document/$docId"
    fun annotate(pageId: Long): String = "annotate/$pageId"
    fun protectedCopy(pageId: Long): String = "protected/$pageId"
    fun translate(docId: Long): String = "translate/$docId"
}
