package com.example.service

import android.util.AtomicFile
import com.example.data.TripEntity
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TripCheckpoint(val key: String, val trip: TripEntity, val completed: Boolean = false)

/** One atomic file per trip: finishing one trip cannot erase a newer active checkpoint. */
class TripCheckpointStore(private val directory: File) {
    private var lastWritten: TripCheckpoint? = null
    @Synchronized
    fun write(checkpoint: TripCheckpoint): Boolean {
        // Only byte-for-byte equivalent logical snapshots are skipped. A new end
        // time, route, distance, note or completion marker must still be persisted.
        if (lastWritten == checkpoint && File(directory, "${checkpoint.key}.json").isFile) return false
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create trip checkpoint directory" }
        val trip = checkpoint.trip
        val json = JSONObject().apply {
            put("version", 1)
            put("key", checkpoint.key)
            put("completed", checkpoint.completed)
            put("start", trip.startDate)
            put("end", trip.endDate)
            put("km", trip.distanceKm)
            put("type", trip.type ?: JSONObject.NULL)
            put("origin", trip.startAddress ?: JSONObject.NULL)
            put("destination", trip.endAddress ?: JSONObject.NULL)
            put("purpose", trip.purpose ?: JSONObject.NULL)
            put("vehicle", trip.vehicle ?: JSONObject.NULL)
            put("path", trip.pathJson)
        }
        val file = atomicFile(checkpoint.key)
        val stream = file.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            lastWritten = checkpoint
            return true
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    @Synchronized
    fun readAll(): List<TripCheckpoint> {
        // AtomicFile may leave a .bak after an interrupted write. Include it so openRead
        // can restore the last complete version even if the base file is missing.
        val keys = directory.listFiles().orEmpty().mapNotNull { file ->
            val name = file.name.removeSuffix(".bak")
            if (!name.endsWith(".json")) return@mapNotNull null
            name.removeSuffix(".json").takeIf { runCatching { UUID.fromString(it) }.isSuccess }
        }.distinct()
        return keys.mapNotNull { key ->
            runCatching {
                val json = atomicFile(key).openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                require(json.getInt("version") == 1 && json.getString("key") == key)
                val trip = TripEntity(
                    startDate = json.getLong("start"),
                    endDate = json.getLong("end"),
                    distanceKm = json.getDouble("km"),
                    type = json.optionalString("type"),
                    startAddress = json.optionalString("origin"),
                    endAddress = json.optionalString("destination"),
                    purpose = json.optionalString("purpose"),
                    vehicle = json.optionalString("vehicle"),
                    pathJson = json.getString("path")
                )
                require(trip.distanceKm.isFinite() && trip.distanceKm >= 0 && trip.endDate >= trip.startDate)
                TripCheckpoint(key, trip, json.getBoolean("completed"))
            }.getOrNull() // Leave an unreadable file intact; one bad file must not block other trips.
        }
    }

    @Synchronized
    fun remove(key: String) {
        atomicFile(key).delete()
        if (lastWritten?.key == key) lastWritten = null
    }

    private fun atomicFile(key: String): AtomicFile {
        require(UUID.fromString(key).toString() == key)
        return AtomicFile(File(directory, "$key.json"))
    }

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else getString(key)
}
