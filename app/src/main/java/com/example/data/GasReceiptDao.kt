package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GasReceiptDao {
    @Query("SELECT * FROM gas_receipts WHERE syncDeleted = 0 ORDER BY timestamp DESC")
    fun getAllReceipts(): Flow<List<GasReceiptEntity>>

    @Query("SELECT * FROM gas_receipts WHERE syncDeleted = 0 AND (syncOwner IS NULL OR syncOwner = :owner) ORDER BY timestamp DESC")
    fun visibleReceipts(owner: String?): Flow<List<GasReceiptEntity>>

    @Query("SELECT * FROM gas_receipts WHERE syncDeleted = 0 ORDER BY timestamp DESC")
    suspend fun getAllReceiptsList(): List<GasReceiptEntity>

    @Query("SELECT * FROM gas_receipts WHERE syncOwner = :owner")
    suspend fun syncRecords(owner: String): List<GasReceiptEntity>

    @Query("UPDATE gas_receipts SET syncOwner = :owner WHERE syncOwner IS NULL")
    suspend fun claimUnowned(owner: String)

    @Query("SELECT * FROM gas_receipts WHERE id = :id AND syncDeleted = 0 LIMIT 1")
    suspend fun getReceiptById(id: Long): GasReceiptEntity?

    @Query("SELECT * FROM gas_receipts WHERE id = :id LIMIT 1")
    suspend fun findRecord(id: Long): GasReceiptEntity?

    @Query("SELECT * FROM gas_receipts WHERE syncId = :syncId LIMIT 1")
    suspend fun findSynced(syncId: String): GasReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(record: GasReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipts(records: List<GasReceiptEntity>)

    @Update
    suspend fun replaceReceipt(record: GasReceiptEntity)

    @Transaction
    suspend fun updateReceipt(record: GasReceiptEntity) {
        val current = getReceiptById(record.id) ?: return
        val linkedTrip = if (record.tripId == current.tripId) current.tripSyncId else null
        replaceReceipt(record.copy(tripSyncId = linkedTrip, sync = current.sync.changed()))
    }

    @Transaction
    suspend fun deleteReceipt(record: GasReceiptEntity) = deleteReceiptById(record.id)

    @Transaction
    suspend fun deleteReceiptById(id: Long) {
        val current = getReceiptById(id) ?: return
        replaceReceipt(current.copy(sync = current.sync.changed(deleted = true)))
    }

    @Transaction
    suspend fun clearAllReceipts(owner: String? = null) {
        getAllReceiptsList().filter { it.sync.syncOwner == null || it.sync.syncOwner == owner }
            .forEach { deleteReceiptById(it.id) }
    }

    @Transaction
    suspend fun attachSyncId(localId: Long, cloudId: String, owner: String) {
        val current = findRecord(localId) ?: return
        if (current.sync.syncId == null && current.sync.syncOwner == owner) {
            replaceReceipt(current.copy(sync = current.sync.copy(syncId = cloudId)))
        }
    }

    @Transaction
    suspend fun mergeDownloaded(records: List<GasReceiptEntity>) {
        for (incoming in records) {
            val existing = findSynced(requireNotNull(incoming.sync.syncId))
            if (existing == null) {
                insertReceipt(incoming.copy(id = 0))
            } else if (existing.sync.syncOwner == incoming.sync.syncOwner) {
                if (incoming.sync > existing.sync || (incoming.sync == existing.sync && incoming.sync.syncUpdatedAt == 0L)) {
                    replaceReceipt(incoming.copy(id = existing.id))
                } else if (incoming.sync == existing.sync && !incoming.sync.syncDeleted) {
                    // A receipt's backing photo and device-local FK can change without a metadata edit.
                    val linked = existing.copy(imagePath = incoming.imagePath, tripId = incoming.tripId, tripSyncId = incoming.tripSyncId)
                    if (linked != existing) replaceReceipt(linked)
                }
            }
        }
    }

    @Query("SELECT SUM(totalAmount) FROM gas_receipts WHERE syncDeleted = 0")
    fun getTotalGasExpenses(): Flow<Double?>
}
