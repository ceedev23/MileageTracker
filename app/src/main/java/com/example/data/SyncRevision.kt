package com.example.data

import androidx.room.ColumnInfo
import java.util.UUID

data class SyncRevision(
    val syncId: String? = UUID.randomUUID().toString(),
    val syncOwner: String? = null,
    @ColumnInfo(defaultValue = "0") val syncUpdatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val syncChangeId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val syncDeleted: Boolean = false
) : Comparable<SyncRevision> {
    fun changed(deleted: Boolean = false) = copy(
        syncUpdatedAt = maxOf(System.currentTimeMillis(), syncUpdatedAt + 1),
        syncChangeId = UUID.randomUUID().toString(),
        syncDeleted = deleted
    )

    override fun compareTo(other: SyncRevision): Int =
        compareValuesBy(this, other, { it.syncDeleted }, { it.syncUpdatedAt }, { it.syncChangeId })
}
