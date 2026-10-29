package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** Local receipt for a checkpoint import; committed in the same transaction as its trip. */
@Entity(tableName = "trip_recoveries")
data class TripRecoveryEntity(@PrimaryKey val recoveryKey: String, val tripId: Long)

@Dao
interface TripRecoveryDao {
    @Query("SELECT * FROM trip_recoveries WHERE recoveryKey = :key LIMIT 1")
    suspend fun find(key: String): TripRecoveryEntity?

    @Insert
    suspend fun insert(entry: TripRecoveryEntity)
}
