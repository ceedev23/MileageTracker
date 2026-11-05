package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.TripEntity
import com.example.ui.MileageViewModel
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/** Imports driving activities from Google Maps Timeline's on-device JSON export.
 * The user explicitly chooses a file; no Google account access or background upload occurs.
 */
@Composable
fun GoogleMapsTripImport(viewModel: MileageViewModel) {
    val context = LocalContext.current
    val existing by viewModel.trips.collectAsState()
    var candidates by remember { mutableStateOf<List<TripEntity>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    it.readText()
                } ?: error("Unable to open the selected file")
                val parsed = parseGoogleTimelineTrips(content)
                candidates = parsed.distinctBy { Pair(it.startDate, it.endDate) }
                    .filterNot { candidate ->
                        existing.any { it.startDate == candidate.startDate &&
                            kotlin.math.abs(it.endDate - candidate.endDate) < 60_000L }
                    }
                error = if (parsed.isEmpty()) "No supported driving activities were found. Select a Google Maps Timeline JSON export containing semanticSegments." else null
                preview = candidates.isNotEmpty()
                imported = false
            } catch (e: Exception) {
                candidates = emptyList()
                preview = false
                error = "Could not read this Timeline JSON file: ${e.message ?: "invalid format"}"
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Import from Google Maps", style = MaterialTheme.typography.titleMedium)
        Text("Import past driving trips from a Google Maps Timeline JSON export. Your file stays on this device; review trips before saving.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { picker.launch("application/json") }, modifier = Modifier.fillMaxWidth()) {
            Text("Choose Timeline JSON file")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (imported) Text("Trips added. Review and classify them in your trip history.")
        if (preview) {
            Text("${candidates.size} new driving trips found (existing matches skipped).", style = MaterialTheme.typography.bodyMedium)
            Text("Distances are taken from Timeline where available, otherwise estimated from endpoints. Check them before using for tax reporting.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(candidates) { trip ->
                    Text("${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(trip.startDate))} · ${String.format(Locale.US, "%.1f", trip.distanceKm)} km",
                        modifier = Modifier.padding(vertical = 5.dp))
                }
            }
            Button(onClick = {
                // Prevent repeat taps and accidental double-import.
                val saving = candidates
                candidates = emptyList()
                preview = false
                saving.forEach(viewModel::saveTrip)
                imported = true
            }, modifier = Modifier.fillMaxWidth()) { Text("Import ${candidates.size} trips") }
        }
    }
}

internal fun parseGoogleTimelineTrips(json: String): List<TripEntity> {
    val root = JSONObject(json)
    val segments = root.optJSONArray("semanticSegments") ?: return emptyList()
    val trips = mutableListOf<TripEntity>()
    for (i in 0 until segments.length()) {
        val segment = segments.optJSONObject(i) ?: continue
        val activity = segment.optJSONObject("activity") ?: continue
        val mode = activity.optString("topCandidate")
        if (mode != "IN_PASSENGER_VEHICLE" && mode != "DRIVING" &&
            mode != "IN_VEHICLE" && mode != "MOTORCYCLING") continue
        val start = parseTimelineTime(segment.optString("startTime")) ?: continue
        val end = parseTimelineTime(segment.optString("endTime")) ?: continue
        if (end <= start) continue
        val startPoint = activity.optJSONObject("start")?.optString("latLng").orEmpty()
        val endPoint = activity.optJSONObject("end")?.optString("latLng").orEmpty()
        val meters = activity.optDouble("distanceMeters", Double.NaN)
        val distanceKm = if (meters.isFinite() && meters > 0) meters / 1000.0
            else distanceBetween(startPoint, endPoint) ?: continue
        if (!distanceKm.isFinite() || distanceKm <= 0 || distanceKm > 2000) continue
        trips.add(TripEntity(startDate = start, endDate = end, distanceKm = distanceKm,
            type = null, startAddress = startPoint.ifBlank { null },
            endAddress = endPoint.ifBlank { null }, purpose = "Imported from Google Maps Timeline"))
    }
    return trips
}

private fun parseTimelineTime(value: String): Long? = try {
    Instant.parse(value).toEpochMilli()
} catch (_: Exception) {
    try { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
    catch (_: Exception) { null }
}

private fun distanceBetween(start: String, end: String): Double? {
    fun coords(s: String): Pair<Double, Double>? {
        val p = s.removePrefix("geo:").trim().split(Regex("[,\\s]+"))
        if (p.size < 2) return null
        val lat = p[0].toDoubleOrNull() ?: return null
        val lon = p[1].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }
    val a = coords(start) ?: return null
    val b = coords(end) ?: return null
    val dLat = Math.toRadians(b.first - a.first)
    val dLon = Math.toRadians(b.second - a.second)
    val h = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(Math.toRadians(a.first)) *
        kotlin.math.cos(Math.toRadians(b.first)) *
        kotlin.math.sin(dLon / 2).let { it * it }
    return 6371.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
}
