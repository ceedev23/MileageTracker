package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE syncDeleted = 0 ORDER BY startDate DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE syncDeleted = 0 AND (syncOwner IS NULL OR syncOwner = :owner) ORDER BY startDate DESC")
    fun visibleTrips(owner: String?): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE syncDeleted = 0 ORDER BY startDate DESC")
    suspend fun getAllTripsList(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE syncOwner = :owner")
    suspend fun syncRecords(owner: String): List<TripEntity>

    @Query("UPDATE trips SET syncOwner = :owner WHERE syncOwner IS NULL")
    suspend fun claimUnowned(owner: String)

    @Query("SELECT * FROM trips WHERE id = :id AND syncDeleted = 0 LIMIT 1")
    suspend fun getTripById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun findRecord(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE syncId = :syncId LIMIT 1")
    suspend fun findSynced(syncId: String): TripEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrip(record: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrips(records: List<TripEntity>)

    @Update
    suspend fun replaceTrip(record: TripEntity)

    @Transaction
    suspend fun updateTrip(record: TripEntity) {
        val current = getTripById(record.id) ?: return
        replaceTrip(record.copy(sync = current.sync.changed()))
    }

    @Transaction
    suspend fun classifyTrips(ids: List<Long>, type: String): List<TripEntity> {
        require(type == "Business" || type == "Personal")
        return ids.distinct().mapNotNull { id ->
            getTripById(id)?.let { trip ->
                trip.copy(type = type, sync = trip.sync.changed()).also { replaceTrip(it) }
            }
        }
    }

    @Transaction
    suspend fun deleteTrip(record: TripEntity) = deleteTripById(record.id)

    @Transaction
    suspend fun deleteTripById(id: Long) {
        val current = getTripById(id) ?: return
        replaceTrip(current.copy(sync = current.sync.changed(deleted = true)))
    }

    @Transaction
    suspend fun clearAllTrips(owner: String? = null) {
        getAllTripsList().filter { it.sync.syncOwner == null || it.sync.syncOwner == owner }
            .forEach { deleteTripById(it.id) }
    }

    @Transaction
    suspend fun attachSyncId(localId: Long, cloudId: String, owner: String, preserveClassification: Boolean) {
        val current = findRecord(localId) ?: return
        if (current.sync.syncId == null && current.sync.syncOwner == owner) {
            val sync = current.sync.copy(syncId = cloudId)
            val keepClassification = preserveClassification && !sync.syncDeleted && current.type != null && sync.syncUpdatedAt == 0L
            replaceTrip(current.copy(sync = if (keepClassification) sync.changed() else sync))
        }
    }

    @Transaction
    suspend fun mergeDownloaded(records: List<TripEntity>) {
        for (incoming in records) {
            val existing = findSynced(requireNotNull(incoming.sync.syncId))
            if (existing == null) {
                insertTrip(incoming.copy(id = 0))
            } else if (existing.sync.syncOwner == incoming.sync.syncOwner &&
                (incoming.sync > existing.sync || (incoming.sync == existing.sync && incoming.sync.syncUpdatedAt == 0L))
            ) {
                replaceTrip(incoming.copy(id = existing.id))
            }
        }
    }
}
