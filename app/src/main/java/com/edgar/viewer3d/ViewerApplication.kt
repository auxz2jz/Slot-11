package com.edgar.viewer3d

import android.app.Application

class ViewerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.initialize(this)
        DiagnosticLogger.event(
            category = "APP",
            event = "APPLICATION_CREATED"
        )
    }
}
