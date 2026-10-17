package com.tayf.app.vision

import org.opencv.android.OpenCVLoader

object OpenCvInit {
    @Volatile private var loaded = false

    /** Loads the bundled OpenCV native library once; safe to call many times. */
    fun ensure() {
        if (loaded) return
        synchronized(this) {
            if (!loaded) loaded = OpenCVLoader.initLocal()
        }
    }
}
