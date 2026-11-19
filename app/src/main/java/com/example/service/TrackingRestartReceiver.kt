package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TrackingRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (TrackingManager.hasBackgroundLocationPermission(context)) {
                    TrackingManager.resumeAutoDetect(context)
                }
            }
        }
    }
}
