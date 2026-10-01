package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gas_receipts")
data class GasReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String,
    val merchant: String? = null,
    val totalAmount: Double = 0.0,
    val currency: String = "$",
    val fuelType: String? = null,
    val gallonsOrLiters: Double? = null,
    val unit: String = "gal",
    val pricePerUnit: Double? = null,
    val receiptDate: String? = null,
    val paymentMethod: String? = null,
    val rawExtraction: String? = null,
    val tripId: Long? = null
)
