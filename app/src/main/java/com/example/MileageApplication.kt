package com.example

import android.app.Application
import com.example.service.TrackingManager

class MileageApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TrackingManager.init(this)
    }
}
