package com.example.data

import kotlinx.coroutines.flow.Flow

class GasReceiptRepository(private val dao: GasReceiptDao) {
    val allReceipts: Flow<List<GasReceiptEntity>> = dao.getAllReceipts()
    val totalGasExpenses: Flow<Double?> = dao.getTotalGasExpenses()

    suspend fun getAllReceiptsList(): List<GasReceiptEntity> = dao.getAllReceiptsList()

    suspend fun getReceiptById(id: Long): GasReceiptEntity? = dao.getReceiptById(id)

    suspend fun insertReceipt(receipt: GasReceiptEntity): Long = dao.insertReceipt(receipt)

    suspend fun insertReceipts(receipts: List<GasReceiptEntity>) = dao.insertReceipts(receipts)

    suspend fun deleteReceipt(receipt: GasReceiptEntity) = dao.deleteReceipt(receipt)

    suspend fun deleteReceiptById(id: Long) = dao.deleteReceiptById(id)

    suspend fun clearAll() = dao.clearAllReceipts()
}
