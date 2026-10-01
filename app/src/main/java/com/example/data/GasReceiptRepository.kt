package com.example.data

import kotlinx.coroutines.flow.Flow

class GasReceiptRepository(private val dao: GasReceiptDao) {
    val allReceipts: Flow<List<GasReceiptEntity>> = dao.getAllReceipts()
    val totalGasExpenses: Flow<Double?> = dao.getTotalGasExpenses()

    suspend fun getReceiptById(id: Long): GasReceiptEntity? = dao.getReceiptById(id)

    suspend fun insertReceipt(receipt: GasReceiptEntity): Long = dao.insertReceipt(receipt)

    suspend fun updateReceipt(receipt: GasReceiptEntity) = dao.updateReceipt(receipt)

    suspend fun deleteReceipt(receipt: GasReceiptEntity) = dao.deleteReceipt(receipt)

    suspend fun clearAll() = dao.clearAllReceipts()
}
