package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.LatLngPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "mileage_tracking_v2"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
    }

    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isLocationTrackingActive: Boolean = false
    private var wasTracking: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                wasTracking = true
                val notification = buildNotification(
                    title = "Current trip",
                    text = "Recording your drive",
                    alert = true
                )
                startForeground(NOTIFICATION_ID, notification)
                startLocationUpdates()
            }
            else -> {
                // Monitoring start, or the system restarted this sticky service after the process died.
                if (!TrackingManager.autoDetectEnabled(this) || !TrackingManager.hasLocationPermission(this)) {
                    stopLocationUpdates()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                TrackingManager.noteMonitoring()
                wasTracking = false
                val notification = buildNotification(
                    title = "MileageTracker • Motion Detection Active",
                    text = "Monitoring driving movement & GPS to auto-record trips",
                    alert = true
                )
                startForeground(NOTIFICATION_ID, notification)
                startLocationUpdates()
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        if (isLocationTrackingActive) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val now = System.currentTimeMillis()
                for (location in result.locations) {
                    val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
                    val locTime = location.time
                    val timestamp = if (locTime > 0 && kotlin.math.abs(now - locTime) < 30_000L) locTime else now
                    val point = LatLngPoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = timestamp,
                        speedKmh = speedKmh
                    )
                    TrackingManager.processGpsPoint(point)
                    updateNotification(point)
                }
            }
        }

        try {
            locationCallback?.let {
                fusedLocationClient?.requestLocationUpdates(locationRequest, it, Looper.getMainLooper())
                isLocationTrackingActive = true
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
        isLocationTrackingActive = false
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Tracking",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows the current trip while MileageTracker is recording or watching for a drive"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String, alert: Boolean = false): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isActivelyRecording = TrackingManager.trackingState.value.isTracking

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)

        if (isActivelyRecording) {
            builder.addAction(android.R.drawable.ic_media_pause, "Stop & Save Trip", stopPendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(point: LatLngPoint) {
        val state = TrackingManager.trackingState.value
        val justStarted = state.isTracking && !wasTracking
        wasTracking = state.isTracking
        val (title, text) = if (state.isTracking) {
            currentTripText(state.distanceKm, state.speedKmh)
        } else if (state.isMonitoringMovement) {
            Pair(
                "MileageTracker • Auto-Detect Active",
                if (state.speedKmh > 3f) "Movement detected (${state.speedKmh.toInt()} km/h)..." else "Monitoring for vehicle movement"
            )
        } else {
            Pair("MileageTracker", "Ready to track")
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, text, alert = justStarted))
    }

    override fun onDestroy() {
        stopLocationUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
