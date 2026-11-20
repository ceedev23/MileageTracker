package com.example.data

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips", indices = [Index(value = ["syncId"], unique = true)])
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startDate: Long,
    val endDate: Long,
    val distanceKm: Double,
    val type: String?, // "Business", "Personal", or null (Unclassified)
    val startAddress: String? = null,
    val endAddress: String? = null,
    val purpose: String? = null,
    val vehicle: String? = null,
    val pathJson: String = "[]",
    @Embedded val sync: SyncRevision = SyncRevision()
)
