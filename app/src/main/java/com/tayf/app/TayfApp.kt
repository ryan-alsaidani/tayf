package com.tayf.app

import android.app.Application
import com.tayf.app.vision.OpenCvInit

class TayfApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        OpenCvInit.ensure()
    }

    companion object {
        /** Application context holder for helpers that have no Context parameter (exporters). */
        lateinit var instance: TayfApp
            private set
    }
}
