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
    val unit: String = "L",
    val pricePerUnit: Double? = null,
    val receiptDate: String? = null,
    val paymentMethod: String? = null,
    val rawExtraction: String? = null,
    val tripId: Long? = null
)

fun isGallonUnit(unit: String?): Boolean = unit.equals("gal", ignoreCase = true)

fun fuelVolumeLabel(unit: String?): String = if (isGallonUnit(unit)) "Gallons" else "Litres"

fun fuelUnitShort(unit: String?): String = if (isGallonUnit(unit)) "gal" else "L"

fun fuelPriceFieldLabel(unit: String?): String =
    if (isGallonUnit(unit)) "Price/Gal ($)" else "Price/L ($)"

fun fuelPriceDetailLabel(unit: String?): String =
    if (isGallonUnit(unit)) "Price per gallon" else "Price per litre"
