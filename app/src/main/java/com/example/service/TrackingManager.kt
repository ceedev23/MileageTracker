package com.example.service

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.LatLngPoint
import com.example.data.TripEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LiveTrackingState(
    val isTracking: Boolean = false,
    val isMonitoringMovement: Boolean = false,
    val detectorState: DetectorState = DetectorState.IDLE,
    val distanceKm: Double = 0.0,
    val speedKmh: Float = 0f,
    val elapsedSeconds: Long = 0L,
    val path: List<LatLngPoint> = emptyList(),
    val statusMessage: String = "Ready to track"
)

object TrackingManager {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var timerJob: Job? = null

    private val _trackingState = MutableStateFlow(LiveTrackingState())
    val trackingState: StateFlow<LiveTrackingState> = _trackingState.asStateFlow()

    private var tripDetector: TripDetector? = null
    private var appContext: Context? = null
    private var startTimeMs: Long = 0L

    var onTripSavedCallback: ((TripEntity) -> Unit)? = null

    fun init(context: Context) {
        if (tripDetector != null && appContext != null) return
        appContext = context.applicationContext
        val db = AppDatabase.getDatabase(context)

        tripDetector = TripDetector(
            dwellMinutes = 5,
            onTripStarted = { trip ->
                startTimeMs = trip.startTime
                _trackingState.value = _trackingState.value.copy(
                    isTracking = true,
                    detectorState = DetectorState.RECORDING,
                    distanceKm = trip.distanceKm,
                    path = trip.points.toList(),
                    statusMessage = "Driving detected - Recording Trip"
                )
                startTimer()
            },
            onTripUpdated = { trip ->
                _trackingState.value = _trackingState.value.copy(
                    distanceKm = trip.distanceKm,
                    path = trip.points.toList(),
                    statusMessage = "Recording Drive (${String.format("%.1f", trip.distanceKm)} km)"
                )
            },
            onTripEnded = { trip ->
                val ctx = appContext
                scope.launch {
                    val pathJson = serializePoints(trip.points)
                    val (startAddr, endAddr) = if (ctx != null && trip.points.isNotEmpty()) {
                        reverseGeocodeTrip(ctx, trip.points.first(), trip.points.last())
                    } else {
                        Pair("Trip Origin", "Trip Destination")
                    }

                    val entity = TripEntity(
                        startDate = trip.startTime,
                        endDate = trip.endTime,
                        distanceKm = trip.distanceKm,
                        type = null, // unclassified by default
                        startAddress = startAddr,
                        endAddress = endAddr,
                        purpose = "Drive (${String.format("%.1f", trip.distanceKm)} km)",
                        vehicle = "Primary Vehicle",
                        pathJson = pathJson
                    )
                    val insertedId = db.tripDao().insertTrip(entity)
                    val saved = entity.copy(id = insertedId)
                    withContext(Dispatchers.Main) {
                        onTripSavedCallback?.invoke(saved)
                    }
                }
                stopTimer()
                _trackingState.value = _trackingState.value.copy(
                    isTracking = false,
                    detectorState = DetectorState.IDLE,
                    distanceKm = 0.0,
                    speedKmh = 0f,
                    elapsedSeconds = 0L,
                    path = emptyList(),
                    statusMessage = "Trip saved (${String.format("%.1f", trip.distanceKm)} km)"
                )
            },
            onTripDiscarded = { trip ->
                stopTimer()
                _trackingState.value = _trackingState.value.copy(
                    isTracking = false,
                    detectorState = DetectorState.IDLE,
                    distanceKm = 0.0,
                    speedKmh = 0f,
                    elapsedSeconds = 0L,
                    path = emptyList(),
                    statusMessage = "Short movement discarded (< 0.25 km)"
                )
            }
        )
    }

    fun startMovementMonitoring(context: Context) {
        init(context)
        _trackingState.value = _trackingState.value.copy(
            isMonitoringMovement = true,
            statusMessage = "Auto-detection active: Monitoring movement"
        )

        try {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopMovementMonitoring(context: Context) {
        _trackingState.value = _trackingState.value.copy(
            isMonitoringMovement = false,
            statusMessage = "Auto-detection paused"
        )

        if (!_trackingState.value.isTracking) {
            try {
                val intent = Intent(context, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_STOP
                }
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startManualTracking(context: Context) {
        init(context)
        startTimeMs = System.currentTimeMillis()
        val detector = tripDetector ?: return
        detector.startManualRecording()
        _trackingState.value = _trackingState.value.copy(
            isTracking = true,
            detectorState = DetectorState.RECORDING,
            distanceKm = 0.0,
            speedKmh = 0f,
            elapsedSeconds = 0L,
            path = emptyList(),
            statusMessage = "Manual trip recording active"
        )
        startTimer()

        // Start or promote Foreground Service
        try {
            val intent = Intent(context, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopManualTracking(context: Context) {
        val detector = tripDetector
        stopTimer()

        val active = detector?.currentTrip
        if (active != null && active.distanceKm > 0.05) {
            detector.endTrip()
        } else {
            detector?.reset()
            _trackingState.value = _trackingState.value.copy(
                isTracking = false,
                detectorState = DetectorState.IDLE,
                distanceKm = 0.0,
                speedKmh = 0f,
                elapsedSeconds = 0L,
                path = emptyList(),
                statusMessage = if (_trackingState.value.isMonitoringMovement) {
                    "Auto-detection active: Monitoring movement"
                } else {
                    "Ready to track"
                }
            )
        }

        // If not in auto-detection monitoring mode, stop the foreground service
        if (!_trackingState.value.isMonitoringMovement) {
            try {
                val intent = Intent(context, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_STOP
                }
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetForTesting() {
        stopTimer()
        tripDetector?.reset()
        _trackingState.value = LiveTrackingState()
    }

    fun recordDestinationStopAndStartNewTrip(context: Context) {
        init(context)
        val detector = tripDetector ?: return
        val currentTrip = detector.currentTrip
        val lastPoint = currentTrip?.lastPoint ?: currentTrip?.points?.lastOrNull()

        // End and save the completed leg
        detector.endTrip()

        // Immediately start recording the new trip leg from this stop
        startTimeMs = System.currentTimeMillis()
        detector.startManualRecording(lastPoint)
        _trackingState.value = _trackingState.value.copy(
            isTracking = true,
            detectorState = DetectorState.RECORDING,
            distanceKm = 0.0,
            speedKmh = 0f,
            elapsedSeconds = 0L,
            path = if (lastPoint != null) listOf(lastPoint) else emptyList(),
            statusMessage = "Destination stop recorded. New trip started."
        )
        startTimer()
    }

    fun processGpsPoint(point: LatLngPoint) {
        tripDetector?.processPoint(point)
        val isTracking = _trackingState.value.isTracking
        val trip = tripDetector?.currentTrip
        val currentDetectorState = tripDetector?.state ?: DetectorState.IDLE

        if (isTracking || trip != null) {
            _trackingState.value = _trackingState.value.copy(
                detectorState = currentDetectorState,
                distanceKm = trip?.distanceKm ?: _trackingState.value.distanceKm,
                speedKmh = point.speedKmh,
                path = trip?.points?.toList() ?: (_trackingState.value.path + point)
            )
        } else {
            // Idle movement monitoring telemetry update
            _trackingState.value = _trackingState.value.copy(
                detectorState = currentDetectorState,
                speedKmh = point.speedKmh,
                path = listOf(point)
            )
        }
    }

    fun setDwellMinutes(minutes: Int) {
        tripDetector?.updateDwellMinutes(minutes)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000L
                _trackingState.value = _trackingState.value.copy(
                    elapsedSeconds = elapsed.coerceAtLeast(0L)
                )
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun serializePoints(points: List<LatLngPoint>): String {
        val array = JSONArray()
        for (p in points) {
            val obj = JSONObject().apply {
                put("lat", p.latitude)
                put("lng", p.longitude)
                put("t", p.timestamp)
                put("s", p.speedKmh)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private suspend fun reverseGeocodeTrip(
        context: Context,
        startPt: LatLngPoint,
        endPt: LatLngPoint
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val startAddress = geocodePoint(context, startPt) ?: String.format(
            Locale.US, "Start: %.4f, %.4f", startPt.latitude, startPt.longitude
        )
        val endAddress = geocodePoint(context, endPt) ?: String.format(
            Locale.US, "End: %.4f, %.4f", endPt.latitude, endPt.longitude
        )
        Pair(startAddress, endAddress)
    }

    private fun geocodePoint(context: Context, pt: LatLngPoint): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val results: List<Address>? = geocoder.getFromLocation(pt.latitude, pt.longitude, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                val feature = addr.thoroughfare ?: addr.featureName
                val locality = addr.locality ?: addr.subAdminArea
                if (feature != null && locality != null) {
                    "$feature, $locality"
                } else {
                    addr.getAddressLine(0) ?: "$locality"
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
