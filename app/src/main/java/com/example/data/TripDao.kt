package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    suspend fun getAllTripsList(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: Long): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Transaction
    suspend fun mergeDownloadedTrips(downloaded: List<TripEntity>, uploaded: List<TripEntity>) {
        val uploadedById = uploaded.associateBy { it.id }
        val tripsToSave = downloaded.mapNotNull { remote ->
            val local = getTripById(remote.id)
            when {
                // A trip may have been edited, inserted, or deleted during the network calls.
                local != uploadedById[remote.id] -> null
                local?.type != null && remote.type == null -> remote.copy(type = local.type)
                else -> remote
            }
        }
        insertTrips(tripsToSave)
    }

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Transaction
    suspend fun classifyTrips(ids: List<Long>, type: String): List<TripEntity> {
        require(type == "Business" || type == "Personal")
        val updated = ids.distinct().mapNotNull { getTripById(it) }.map { it.copy(type = type) }
        updated.forEach { updateTrip(it) }
        return updated
    }

    @Delete
    suspend fun deleteTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: Long)

    @Query("DELETE FROM trips")
    suspend fun clearAllTrips()
}
