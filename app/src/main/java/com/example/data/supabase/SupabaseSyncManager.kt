package com.example.data.supabase

import android.util.Log
import com.example.data.GasReceiptEntity
import com.example.data.GasReceiptRepository
import com.example.data.InputCheck
import com.example.data.TripEntity
import com.example.data.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class SupabaseSyncManager(
    private val tripRepository: TripRepository,
    private val receiptRepository: GasReceiptRepository,
    val supabaseClient: SupabaseClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val photos: ReceiptPhotoBackup? = null
) {
    private val trips = tripRepository.tripDao
    private val receipts = receiptRepository.dao
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val syncMutex = Mutex()
    private val _syncState = MutableStateFlow(
        if (supabaseClient.config.isConfigured()) SupabaseSyncState(SyncStatus.IDLE)
        else SupabaseSyncState(SyncStatus.NOT_CONFIGURED, message = "Cloud backup isn't available.")
    )
    val syncState: StateFlow<SupabaseSyncState> = _syncState.asStateFlow()

    init {
        scope.launch {
            for (change in changes) syncAll()
        }
    }

    fun triggerSync() {
        if (supabaseClient.config.getSession() != null) changes.trySend(Unit)
    }

    suspend fun syncAll(): SupabaseSyncState = withContext(Dispatchers.IO) {
        syncMutex.withLock { syncAllLocked() }
    }

    private suspend fun syncAllLocked(): SupabaseSyncState {
        if (!supabaseClient.config.isConfigured()) {
            return publish(SupabaseSyncState(SyncStatus.NOT_CONFIGURED, message = "Cloud backup isn't available."))
        }
        val previous = _syncState.value.lastSyncTime
        val owner = supabaseClient.config.getSession()?.user?.id
            ?: return publish(SupabaseSyncState(SyncStatus.IDLE, lastSyncTime = previous, message = "Sign in to sync."))
        publish(SupabaseSyncState(SyncStatus.SYNCING, lastSyncTime = previous))

        try {
            // Bind existing and guest records once to the account that backs them up.
            checkAccount(owner)
            trips.claimUnowned(owner)
            receipts.claimUnowned(owner)
            val cloudTrips = fetchTrips(owner)
            val cloudReceipts = fetchReceipts(owner)
            val tripVersions = cloudTrips.associateBy { it.sync?.syncId }
            val receiptVersions = cloudReceipts.associateBy { it.sync?.syncId }
            val legacyTrips = cloudTrips.filter { it.clientId > 0 }.associateBy { it.clientId }
            val legacyReceipts = cloudReceipts.filter { it.clientId > 0 }.associateBy { it.clientId }

            // The old app kept receipt deletions in preferences. Convert those to durable cloud tombstones.
            for (id in photos?.pendingLegacyDeletions(owner).orEmpty()) {
                val remote = legacyReceipts[id]
                if (remote != null && !requireNotNull(remote.sync).syncDeleted) {
                    supabaseClient.upsertReceipt(remote.copy(id = null, sync = remote.sync.changed(deleted = true))).getOrThrow()
                }
                photos?.deleteLegacyPhoto(id, owner)
                photos?.acknowledgeLegacyDeletion(id, owner)
            }

            for (local in trips.syncRecords(owner)) {
                if (local.sync.syncId != null) continue
                val match = legacyTrips[local.id]?.takeIf {
                    it.startDate == local.startDate && it.endDate == local.endDate
                }
                val legacy = match?.sync
                trips.attachSyncId(
                    local.id, legacy?.syncId ?: UUID.randomUUID().toString(), owner,
                    preserveClassification = legacy != null && legacy.syncUpdatedAt == 0L && !legacy.syncDeleted && match.type == null
                )
            }
            for (local in receipts.syncRecords(owner)) {
                if (local.sync.syncId != null) continue
                val match = legacyReceipts[local.id]?.takeIf { it.timestamp == local.timestamp }
                receipts.attachSyncId(local.id, match?.sync?.syncId ?: UUID.randomUUID().toString(), owner)
            }

            val localTrips = trips.syncRecords(owner)
            val localReceipts = receipts.syncRecords(owner)
            val localTripsById = localTrips.associateBy { it.id }
            for (trip in localTrips) {
                val remote = tripVersions[trip.sync.syncId]?.sync
                if (remote == null || trip.sync > remote) {
                    supabaseClient.upsertTrip(SupabaseTripDto.fromTripEntity(trip, owner)).getOrThrow()
                }
            }
            for (receipt in localReceipts) {
                val remote = receiptVersions[receipt.sync.syncId]
                val version = remote?.sync
                if (version != null && receipt.sync < version) continue
                val cloudPhoto = if (!receipt.sync.syncDeleted &&
                    !remote?.imagePath.orEmpty().startsWith(ReceiptPhotoBackup.PREFIX)
                ) photos?.upload(receipt) else null
                if (version == null || receipt.sync > version || cloudPhoto != null) {
                    val tripSyncId = localTripsById[receipt.tripId]?.sync?.syncId ?: receipt.tripSyncId
                    val dto = SupabaseReceiptDto.fromGasReceiptEntity(receipt, owner)
                        .copy(imagePath = cloudPhoto.orEmpty(), tripSyncId = tripSyncId)
                    supabaseClient.upsertReceipt(dto).getOrThrow()
                }
            }

            val downloadedTrips = fetchTrips(owner)
            val downloadedReceipts = fetchReceipts(owner)
            val validTrips = downloadedTrips.map { dto ->
                val trip = dto.toTripEntity()
                if (trip.sync.syncDeleted) trip else requireNotNull(InputCheck.storedTrip(trip, strict = false))
            }
            val legacyTripIds = downloadedTrips.filter { it.clientId > 0 }.associate { it.clientId to it.sync?.syncId }
            val validReceipts = downloadedReceipts.mapNotNull { dto ->
                val remote = dto.toGasReceiptEntity()
                val local = receipts.findSynced(requireNotNull(remote.sync.syncId))
                if (remote.sync.syncDeleted) {
                    photos?.deleteBackedUpPhoto(dto)
                    remote.copy(imagePath = "")
                } else if (local != null && local.sync > remote.sync) {
                    null
                } else {
                    val restored = photos?.restore(dto, local) ?: remote.copy(imagePath = local?.imagePath.orEmpty())
                    requireNotNull(InputCheck.storedReceipt(restored, strict = false)).copy(
                        tripSyncId = dto.tripSyncId ?: legacyTripIds[dto.tripId].takeIf {
                            remote.sync.syncUpdatedAt == 0L
                        }
                    )
                }
            }

            // Validate both downloads before replacing local records. DAO transactions recheck revisions
            // so a local edit or deletion during the network calls cannot be overwritten.
            checkAccount(owner)
            trips.mergeDownloaded(validTrips)
            val liveTripIds = trips.syncRecords(owner)
                .filterNot { it.sync.syncDeleted }.associate { it.sync.syncId to it.id }
            receipts.mergeDownloaded(validReceipts.map { receipt ->
                receipt.copy(tripId = liveTripIds[receipt.tripSyncId])
            })
            return publish(SupabaseSyncState(
                status = SyncStatus.SUCCESS,
                lastSyncTime = System.currentTimeMillis(),
                message = "Cloud backup is up to date.",
                syncedTripsCount = validTrips.count { !it.sync.syncDeleted },
                syncedReceiptsCount = validReceipts.count { !it.sync.syncDeleted }
            ))
        } catch (error: CancellationException) {
            publish(SupabaseSyncState(SyncStatus.IDLE, lastSyncTime = previous))
            throw error
        } catch (error: Exception) {
            Log.e("SupabaseSync", "sync failed", error)
            return publish(SupabaseSyncState(
                SyncStatus.ERROR, lastSyncTime = previous, message = "Cloud sync did not finish. Try syncing again."
            ))
        }
    }

    private suspend fun fetchTrips(owner: String): List<SupabaseTripDto> =
        supabaseClient.fetchTrips(owner).getOrThrow().onEach { require(it.sync?.syncOwner == owner) }

    private suspend fun fetchReceipts(owner: String): List<SupabaseReceiptDto> =
        supabaseClient.fetchReceipts(owner).getOrThrow().onEach { require(it.sync?.syncOwner == owner) }

    private fun checkAccount(owner: String) {
        check(supabaseClient.config.getSession()?.user?.id == owner) { "Cloud account changed during sync" }
    }

    private fun publish(state: SupabaseSyncState): SupabaseSyncState = state.also { _syncState.value = it }
}
