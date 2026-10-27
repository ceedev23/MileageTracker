package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.InputCheck
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
    val statusMessage: String = "Ready to track",
    val startAddress: String? = null,
    val tripStartedAtMs: Long = 0L
) {
    fun showsLiveDrive(): Boolean =
        isTracking ||
            detectorState == DetectorState.MOVING ||
            detectorState == DetectorState.RECORDING
}

/** Choices made on the live tracking screen, applied when the trip is saved. */
data class ActiveTripDraft(
    val category: String? = null,
    val vehicle: String = "",
    val note: String = "",
    val startPlaceName: String? = null
)

object TrackingManager {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var timerJob: Job? = null

    private val _trackingState = MutableStateFlow(LiveTrackingState())
    val trackingState: StateFlow<LiveTrackingState> = _trackingState.asStateFlow()

    private var tripDetector: TripDetector? = null
    private var appContext: Context? = null
    private var currentDwellMinutes: Int = 5
    private var startTimeMs: Long = 0L
    private var activeDraft = ActiveTripDraft()
    private var startGeocodeStarted = false

    var onTripSavedCallback: ((TripEntity) -> Unit)? = null

    fun init(context: Context) {
        if (tripDetector != null && appContext != null) return
        appContext = context.applicationContext
        val db = AppDatabase.getDatabase(context)

        val savedDwell = try {
            val prefs = context.getSharedPreferences("mileage_tracker_prefs", Context.MODE_PRIVATE)
            prefs.getInt("dwell_minutes", currentDwellMinutes)
        } catch (_: Exception) {
            currentDwellMinutes
        }
        currentDwellMinutes = savedDwell

        tripDetector = TripDetector(
            dwellMinutes = currentDwellMinutes,
            onTripStarted = { trip ->
                startTimeMs = trip.startTime
                val existingStart = _trackingState.value.startAddress
                if (existingStart == null) startGeocodeStarted = false
                _trackingState.value = _trackingState.value.copy(
                    isTracking = true,
                    detectorState = DetectorState.RECORDING,
                    distanceKm = trip.distanceKm,
                    path = trip.points.toList(),
                    statusMessage = "Driving detected - Recording Trip",
                    startAddress = existingStart,
                    tripStartedAtMs = trip.startTime
                )
                if (existingStart == null) maybeGeocodeStart(trip.points.firstOrNull())
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
                val draft = activeDraft
                val liveStart = _trackingState.value.startAddress
                scope.launch {
                    try {
                        val pathJson = serializePoints(trip.points)
                        val (startAddr, endAddr) = if (ctx != null && trip.points.isNotEmpty()) {
                            reverseGeocodeTrip(ctx, trip.points.first(), trip.points.last())
                        } else {
                            Pair(liveStart ?: "Trip Origin", "Trip Destination")
                        }

                        val namedStart = draft.startPlaceName?.let { InputCheck.finishLabel(it, 80) }
                        val entity = InputCheck.storedTrip(
                            TripEntity(
                                startDate = trip.startTime,
                                endDate = trip.endTime,
                                distanceKm = trip.distanceKm,
                                type = draft.category,
                                startAddress = namedStart ?: InputCheck.finishLabel(startAddr, 120),
                                endAddress = InputCheck.finishLabel(endAddr, 120),
                                purpose = InputCheck.finishLabel(draft.note, 160)
                                    ?: "Drive (${String.format(Locale.US, "%.1f", trip.distanceKm)} km)",
                                vehicle = InputCheck.finishLabel(draft.vehicle, 40) ?: "Primary Vehicle",
                                pathJson = pathJson
                            ),
                            strict = false
                        ) ?: return@launch
                        val insertedId = db.tripDao().insertTrip(entity)
                        val saved = entity.copy(id = insertedId)
                        withContext(Dispatchers.Main) {
                            onTripSavedCallback?.invoke(saved)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                stopTimer()
                clearLiveTripExtras()
                _trackingState.value = _trackingState.value.copy(
                    isTracking = false,
                    detectorState = DetectorState.IDLE,
                    distanceKm = 0.0,
                    speedKmh = 0f,
                    elapsedSeconds = 0L,
                    path = emptyList(),
                    startAddress = null,
                    tripStartedAtMs = 0L,
                    statusMessage = "Trip saved (${String.format(Locale.US, "%.1f", trip.distanceKm)} km)"
                )

                // If not in auto-detection monitoring mode, stop the foreground service
                if (!_trackingState.value.isMonitoringMovement) {
                    ctx?.let { c ->
                        try {
                            val intent = Intent(c, TrackingService::class.java).apply {
                                action = TrackingService.ACTION_STOP
                            }
                            c.stopService(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            },
            onTripDiscarded = { trip ->
                stopTimer()
                clearLiveTripExtras()
                _trackingState.value = _trackingState.value.copy(
                    isTracking = false,
                    detectorState = DetectorState.IDLE,
                    distanceKm = 0.0,
                    speedKmh = 0f,
                    elapsedSeconds = 0L,
                    path = emptyList(),
                    startAddress = null,
                    tripStartedAtMs = 0L,
                    statusMessage = "Short movement discarded (< 0.25 km)"
                )
            }
        )
    }

    fun autoDetectEnabled(context: Context): Boolean {
        return try {
            context.getSharedPreferences("mileage_tracker_prefs", Context.MODE_PRIVATE)
                .getBoolean("auto_detect", true)
        } catch (_: Exception) {
            true
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** Keep auto-detect armed after process start. No-op until location permission exists. */
    fun resumeAutoDetect(context: Context) {
        if (!autoDetectEnabled(context) || !hasLocationPermission(context)) return
        startMovementMonitoring(context)
    }

    fun noteMonitoring() {
        if (_trackingState.value.isMonitoringMovement) return
        _trackingState.value = _trackingState.value.copy(
            isMonitoringMovement = true,
            statusMessage = if (_trackingState.value.isTracking) {
                _trackingState.value.statusMessage
            } else {
                "Auto-detection active: Monitoring movement"
            }
        )
    }

    fun startMovementMonitoring(context: Context) {
        init(context)
        _trackingState.value = _trackingState.value.copy(
            isMonitoringMovement = true,
            statusMessage = if (_trackingState.value.isTracking) {
                _trackingState.value.statusMessage
            } else {
                "Auto-detection active: Monitoring movement"
            }
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
        startGeocodeStarted = false
        _trackingState.value = _trackingState.value.copy(
            isTracking = true,
            detectorState = DetectorState.RECORDING,
            distanceKm = 0.0,
            speedKmh = 0f,
            elapsedSeconds = 0L,
            path = emptyList(),
            startAddress = null,
            tripStartedAtMs = startTimeMs,
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
            clearLiveTripExtras()
            _trackingState.value = _trackingState.value.copy(
                isTracking = false,
                detectorState = DetectorState.IDLE,
                distanceKm = 0.0,
                speedKmh = 0f,
                elapsedSeconds = 0L,
                path = emptyList(),
                startAddress = null,
                tripStartedAtMs = 0L,
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

    fun updateActiveDraft(draft: ActiveTripDraft) {
        activeDraft = ActiveTripDraft(
            category = if (draft.category == "Business" || draft.category == "Personal") draft.category else null,
            vehicle = InputCheck.finishLabel(draft.vehicle, 40) ?: "",
            note = InputCheck.finishLabel(draft.note, 160) ?: "",
            startPlaceName = draft.startPlaceName?.let { InputCheck.finishLabel(it, 80) }
        )
    }

    fun resetForTesting() {
        stopTimer()
        tripDetector?.reset()
        clearLiveTripExtras()
        _trackingState.value = LiveTrackingState()
    }

    fun processGpsPoint(point: LatLngPoint) {
        tripDetector?.processPoint(point)
        val isTracking = _trackingState.value.isTracking
        val trip = tripDetector?.currentTrip
        val currentDetectorState = tripDetector?.state ?: DetectorState.IDLE

        if (isTracking || trip != null) {
            val startedAt = _trackingState.value.tripStartedAtMs.takeIf { it > 0L }
                ?: trip?.startTime
                ?: point.timestamp
            _trackingState.value = _trackingState.value.copy(
                detectorState = currentDetectorState,
                distanceKm = trip?.distanceKm ?: _trackingState.value.distanceKm,
                speedKmh = point.speedKmh,
                path = trip?.points?.toList() ?: (_trackingState.value.path + point),
                tripStartedAtMs = startedAt
            )
            maybeGeocodeStart(trip?.points?.firstOrNull() ?: point)
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
        currentDwellMinutes = minutes.coerceIn(1, 30)
        tripDetector?.updateDwellMinutes(currentDwellMinutes)
    }

    fun getDwellMinutes(): Int = tripDetector?.dwellMinutes ?: currentDwellMinutes

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - startTimeMs) / 1000L
                _trackingState.value = _trackingState.value.copy(
                    elapsedSeconds = elapsed.coerceAtLeast(0L)
                )
                tripDetector?.checkDwellTimeout(System.currentTimeMillis())
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun clearLiveTripExtras() {
        activeDraft = ActiveTripDraft()
        startGeocodeStarted = false
    }

    private fun maybeGeocodeStart(point: LatLngPoint?) {
        if (point == null || startGeocodeStarted || _trackingState.value.startAddress != null) return
        val ctx = appContext ?: return
        startGeocodeStarted = true
        scope.launch {
            val addr = withContext(Dispatchers.IO) { geocodePoint(ctx, point) }
                ?.let { InputCheck.finishLabel(it, 120) }
            if (!addr.isNullOrBlank() && (_trackingState.value.isTracking || _trackingState.value.showsLiveDrive())) {
                _trackingState.value = _trackingState.value.copy(startAddress = addr)
            }
        }
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
