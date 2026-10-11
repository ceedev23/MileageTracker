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

    @Query("SELECT * FROM gas_receipts ORDER BY timestamp DESC")
    suspend fun getAllReceiptsList(): List<GasReceiptEntity>

    @Query("SELECT * FROM gas_receipts WHERE id = :id LIMIT 1")
    suspend fun getReceiptById(id: Long): GasReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: GasReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipts(receipts: List<GasReceiptEntity>)

    @Delete
    suspend fun deleteReceipt(receipt: GasReceiptEntity)

    @Query("DELETE FROM gas_receipts WHERE id = :id")
    suspend fun deleteReceiptById(id: Long)

    @Query("DELETE FROM gas_receipts")
    suspend fun clearAllReceipts()

    @Query("SELECT SUM(totalAmount) FROM gas_receipts")
    fun getTotalGasExpenses(): Flow<Double?>
}
