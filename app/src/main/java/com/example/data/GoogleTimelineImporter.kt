package com.example.data

import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/** Parses user-exported Google Maps Timeline JSON on-device. No Google API or upload required. */
object GoogleTimelineImporter {
    data class Result(val trips: List<TripEntity>, val skipped: Int)

    fun parse(json: String): Result {
        val root = JSONObject(json)
        val output = mutableListOf<TripEntity>()
        var skipped = 0
        fun add(start: String?, end: String?, meters: Double, mode: String?) {
            val driving = mode.orEmpty().uppercase()
            if (driving !in setOf("IN_PASSENGER_VEHICLE", "IN_VEHICLE", "DRIVING", "IN_CAR")) {
                skipped++; return
            }
            val from = parseTime(start)
            val to = parseTime(end)
            if (from == null || to == null || to <= from || meters <= 0.0 || !meters.isFinite()) {
                skipped++; return
            }
            output.add(TripEntity(
                startDate = from, endDate = to, distanceKm = meters / 1000.0,
                type = null, purpose = "Imported from Google Maps Timeline"
            ))
        }

        val semantic = root.optJSONArray("semanticSegments")
        if (semantic != null) {
            for (i in 0 until semantic.length()) {
                val item = semantic.optJSONObject(i) ?: continue
                val activity = item.optJSONObject("activity") ?: continue
                val candidate = activity.optJSONArray("topCandidate")?.optJSONObject(0)
                    ?: activity.optJSONObject("topCandidate")
                add(item.optString("startTime"), item.optString("endTime"),
                    activity.optDouble("distanceMeters", Double.NaN),
                    candidate?.optString("type") ?: activity.optString("activityType"))
            }
        }
        val legacy = root.optJSONArray("timelineObjects")
        if (legacy != null) {
            for (i in 0 until legacy.length()) {
                val activity = legacy.optJSONObject(i)?.optJSONObject("activitySegment") ?: continue
                val mode = activity.optString("activityType")
                val duration = activity.optJSONObject("duration")
                add(duration?.optString("startTimestamp") ?: duration?.optString("startTimestampMs"),
                    duration?.optString("endTimestamp") ?: duration?.optString("endTimestampMs"),
                    activity.optDouble("distance", Double.NaN), mode)
            }
        }
        if (semantic == null && legacy == null) {
            throw IllegalArgumentException("This file does not contain supported Google Maps Timeline segments.")
        }
        return Result(output.distinctBy { Pair(it.startDate, it.endDate) }.sortedBy { it.startDate }, skipped)
    }

    private fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            value.toLongOrNull()?.let { if (it < 100000000000L) it * 1000 else it }
                ?: runCatching { Instant.parse(value).toEpochMilli() }
                    .getOrElse { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        }.getOrNull()
    }
}
