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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SupabaseSyncManager(
    private val tripRepository: TripRepository,
    private val receiptRepository: GasReceiptRepository,
    val supabaseClient: SupabaseClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val photos: ReceiptPhotoBackup? = null
) {
    // Full syncs and individual changes must not race to publish their status.
    private val syncMutex = Mutex()
    private val _syncState = MutableStateFlow(
        if (supabaseClient.config.isConfigured()) {
            SupabaseSyncState(status = SyncStatus.IDLE)
        } else {
            SupabaseSyncState(status = SyncStatus.NOT_CONFIGURED, message = "Cloud backup isn't available.")
        }
    )
    val syncState: StateFlow<SupabaseSyncState> = _syncState.asStateFlow()

    fun triggerSync() {
        scope.launch { syncAll() }
    }

    suspend fun syncAll(): SupabaseSyncState = withContext(Dispatchers.IO) {
        syncMutex.withLock { syncAllLocked() }
    }

    private suspend fun syncAllLocked(): SupabaseSyncState {
        if (!supabaseClient.config.isConfigured()) {
            return SupabaseSyncState(
                status = SyncStatus.NOT_CONFIGURED,
                message = "Cloud backup isn't available."
            ).also { _syncState.value = it }
        }

        val lastSuccessfulSync = _syncState.value.lastSyncTime
        var uploadedTrips = 0
        var uploadedReceipts = 0
        _syncState.value = SupabaseSyncState(
            status = SyncStatus.SYNCING,
            lastSyncTime = lastSuccessfulSync,
            message = "Synchronizing with Supabase..."
        )
        try {
            val userId = supabaseClient.config.getSession()?.user?.id
            photos?.drainDeletions()
            val localTrips = tripRepository.getAllTripsList()
            val localReceipts = receiptRepository.getAllReceiptsList()

            // Upload ALL local records first. Result.failure is a value, not a thrown
            // exception: explicitly unwrap it before proceeding or counting an upload.
            for (trip in localTrips) {
                supabaseClient.upsertTrip(SupabaseTripDto.fromTripEntity(trip, userId)).getOrThrow()
                uploadedTrips++
            }
            for (receipt in localReceipts) {
                uploadReceipt(receipt, userId)
                uploadedReceipts++
            }

            // Do not pull older cloud data over an upload that failed. Fetch and
            // validate both collections before changing either local collection.
            val remoteTrips = supabaseClient.fetchTrips().getOrThrow().map {
                requireNotNull(InputCheck.storedTrip(it.toTripEntity(), strict = false)) {
                    "Cloud trip could not be validated"
                }
            }
            val remoteReceipts = supabaseClient.fetchReceipts().getOrThrow().map {
                requireNotNull(InputCheck.storedReceipt(
                    photos?.restore(it, receiptRepository.getReceiptById(it.clientId)) ?: it.toGasReceiptEntity(), strict = false)) {
                    "Cloud receipt could not be validated"
                }
            }
            if (remoteTrips.isNotEmpty()) tripRepository.insertTrips(remoteTrips)
            if (remoteReceipts.isNotEmpty()) receiptRepository.insertReceipts(remoteReceipts)

            return SupabaseSyncState(
                status = SyncStatus.SUCCESS,
                lastSyncTime = System.currentTimeMillis(),
                message = "Successfully synchronized with Supabase database.",
                syncedTripsCount = (localTrips.map { it.id } + remoteTrips.map { it.id }).toSet().size,
                syncedReceiptsCount = (localReceipts.map { it.id } + remoteReceipts.map { it.id }).toSet().size
            ).also { _syncState.value = it }
        } catch (error: CancellationException) {
            _syncState.value = SupabaseSyncState(
                status = SyncStatus.IDLE,
                lastSyncTime = lastSuccessfulSync,
                message = "Cloud sync was interrupted."
            )
            throw error
        } catch (error: Exception) {
            return syncFailed(error, lastSuccessfulSync, uploadedTrips, uploadedReceipts)
        }
    }

    private fun syncFailed(
        error: Exception,
        lastSuccessfulSync: Long?,
        uploadedTrips: Int = 0,
        uploadedReceipts: Int = 0
    ): SupabaseSyncState {
        Log.e("SupabaseSync", "sync failed", error)
        return SupabaseSyncState(
            status = SyncStatus.ERROR,
            message = "Cloud sync did not finish. Try syncing again.",
            lastSyncTime = lastSuccessfulSync,
            syncedTripsCount = uploadedTrips,
            syncedReceiptsCount = uploadedReceipts
        ).also { _syncState.value = it }
    }

    private fun enqueueChange(operation: suspend () -> Unit) {
        if (!supabaseClient.config.isConfigured()) return
        // A previous full sync does not certify a newly edited or deleted record.
        if (_syncState.value.status != SyncStatus.SYNCING) {
            _syncState.value = _syncState.value.copy(status = SyncStatus.IDLE, message = "Changes pending sync.")
        }
        scope.launch {
            syncMutex.withLock {
                val lastSuccessfulSync = _syncState.value.lastSyncTime
                try {
                    _syncState.value = _syncState.value.copy(status = SyncStatus.SYNCING, message = "Syncing changes...")
                    operation()
                    // One change succeeding is not proof that a full backup completed.
                    _syncState.value = SupabaseSyncState(
                        status = SyncStatus.IDLE,
                        lastSyncTime = lastSuccessfulSync,
                        message = "Change synced. Run a full sync to verify your backup."
                    )
                } catch (error: CancellationException) {
                    _syncState.value = SupabaseSyncState(status = SyncStatus.IDLE, lastSyncTime = lastSuccessfulSync)
                    throw error
                } catch (error: Exception) {
                    syncFailed(error, lastSuccessfulSync)
                }
            }
        }
    }

    fun onTripSaved(trip: TripEntity) = enqueueChange {
        // Read current local data when this queued operation runs, not an older edit.
        tripRepository.getTripById(trip.id)?.let { current ->
            val userId = supabaseClient.config.getSession()?.user?.id
            supabaseClient.upsertTrip(SupabaseTripDto.fromTripEntity(current, userId)).getOrThrow()
        }
    }

    fun onTripDeleted(trip: TripEntity) = enqueueChange {
        check(supabaseClient.deleteTrip(trip.id).getOrThrow()) { "Cloud trip deletion was not confirmed" }
    }

    fun onReceiptSaved(receipt: GasReceiptEntity) = enqueueChange {
        receiptRepository.getReceiptById(receipt.id)?.let { current ->
            val userId = supabaseClient.config.getSession()?.user?.id
            photos?.drainDeletions()
            uploadReceipt(current, userId)
        }
    }

    suspend fun deleteReceiptLocally(receipt: GasReceiptEntity) = syncMutex.withLock {
        photos?.queueDeletion(receipt.id)
        receiptRepository.deleteReceipt(receipt)
    }

    private suspend fun uploadReceipt(receipt: GasReceiptEntity, userId: String?) {
        val cloudPath = photos?.upload(receipt)
        val dto = SupabaseReceiptDto.fromGasReceiptEntity(receipt, userId).copy(imagePath = cloudPath.orEmpty())
        supabaseClient.upsertReceipt(dto).getOrThrow()
    }

    fun onReceiptDeleted(receipt: GasReceiptEntity) = enqueueChange {
        if (photos != null) photos.drainDeletions()
        else check(supabaseClient.deleteReceipt(receipt.id).getOrThrow()) { "Cloud receipt deletion was not confirmed" }
    }
}
