package com.tayf.app.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

object AppDispatchers {
    val io = kotlinx.coroutines.Dispatchers.IO
    val cpu = kotlinx.coroutines.Dispatchers.Default
    val main = kotlinx.coroutines.Dispatchers.Main
}

/** Application-wide scope for background work that must survive screen changes (OCR post-processing). */
object AppScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
}
