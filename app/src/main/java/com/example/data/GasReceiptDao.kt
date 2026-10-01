package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GasReceiptDao {
    @Query("SELECT * FROM gas_receipts ORDER BY timestamp DESC")
    fun getAllReceipts(): Flow<List<GasReceiptEntity>>

    @Query("SELECT * FROM gas_receipts WHERE id = :id")
    suspend fun getReceiptById(id: Long): GasReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: GasReceiptEntity): Long

    @Update
    suspend fun updateReceipt(receipt: GasReceiptEntity)

    @Delete
    suspend fun deleteReceipt(receipt: GasReceiptEntity)

    @Query("DELETE FROM gas_receipts")
    suspend fun clearAllReceipts()

    @Query("SELECT SUM(totalAmount) FROM gas_receipts")
    fun getTotalGasExpenses(): Flow<Double?>
}
