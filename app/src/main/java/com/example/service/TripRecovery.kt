package com.example.service

import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.InputCheck
import com.example.data.TripEntity
import com.example.data.TripRecoveryEntity

class TripRecovery(private val database: AppDatabase, private val store: TripCheckpointStore) {
    /** A restart preserves only observed driving. It never joins GPS fixes across the outage. */
    suspend fun save(checkpoint: TripCheckpoint): TripEntity? {
        val original = checkpoint.trip
        // A confirmed manual trip can still have no GPS distance at the time of interruption.
        if (original.distanceKm == 0.0) {
            store.remove(checkpoint.key)
            return null
        }
        val purpose = if (checkpoint.completed) original.purpose else {
            "Recovered interrupted drive - review. ${original.purpose.orEmpty()}"
        }
        val entity = requireNotNull(InputCheck.storedTrip(original.copy(purpose = purpose), strict = false))
        val saved = database.withTransaction {
            if (database.tripRecoveryDao().find(checkpoint.key) != null) {
                null // Also prevents resurrecting a trip the user subsequently deleted.
            } else {
                val id = database.tripDao().insertTrip(entity)
                database.tripRecoveryDao().insert(TripRecoveryEntity(checkpoint.key, id))
                entity.copy(id = id)
            }
        }
        // Keep the checkpoint until BOTH the trip and import receipt commit successfully.
        store.remove(checkpoint.key)
        return saved
    }
}
