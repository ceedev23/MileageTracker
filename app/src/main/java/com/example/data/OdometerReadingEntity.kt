package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "odometer_readings")
data class OdometerReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicle: String,
    val recordedAt: Long,
    val readingKm: Double,
    val source: String,
    val photoPath: String? = null,
    val photoReviewed: Boolean = false
)

@Dao
interface OdometerReadingDao {
    @Query("SELECT * FROM odometer_readings ORDER BY recordedAt DESC, id DESC")
    fun observe(): Flow<List<OdometerReadingEntity>>
    @Query("SELECT * FROM odometer_readings WHERE vehicle = :vehicle ORDER BY recordedAt DESC, id DESC LIMIT 1")
    suspend fun latest(vehicle: String): OdometerReadingEntity?
    @Insert suspend fun insert(reading: OdometerReadingEntity): Long
    @Query("SELECT * FROM odometer_readings WHERE id = :id")
    suspend fun find(id: Long): OdometerReadingEntity?
    @Query("DELETE FROM odometer_readings WHERE id = :id")
    suspend fun delete(id: Long)
}
