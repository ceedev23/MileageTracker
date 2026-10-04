package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GasReceiptDao {
    @Query("SELECT * FROM gas_receipts ORDER BY timestamp DESC")
    fun getAllReceipts(): Flow<List<GasReceiptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: GasReceiptEntity): Long

    @Delete
    suspend fun deleteReceipt(receipt: GasReceiptEntity)

    @Query("SELECT SUM(totalAmount) FROM gas_receipts")
    fun getTotalGasExpenses(): Flow<Double?>
}
