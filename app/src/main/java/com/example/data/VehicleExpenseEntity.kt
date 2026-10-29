package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "vehicle_expenses", primaryKeys = ["vehicle", "year"])
data class VehicleExpenseEntity(
    val vehicle: String, val year: Int,
    val openingKm: Double, val closingKm: Double,
    val fuel: Double, val maintenance: Double, val insurance: Double,
    val registration: Double, val allowableInterest: Double, val allowableLease: Double,
    val businessParking: Double, val supplementaryInsurance: Double
)

@Dao
interface VehicleExpenseDao {
    @Query("SELECT * FROM vehicle_expenses ORDER BY year DESC, vehicle")
    fun observe(): Flow<List<VehicleExpenseEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(expense: VehicleExpenseEntity)
    @Query("DELETE FROM vehicle_expenses WHERE vehicle = :vehicle AND year = :year")
    suspend fun delete(vehicle: String, year: Int)
}
