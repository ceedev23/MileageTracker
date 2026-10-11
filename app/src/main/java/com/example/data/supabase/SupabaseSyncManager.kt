package com.example.data.supabase

import com.example.data.GasReceiptEntity
import com.example.data.GasReceiptRepository
import com.example.data.TripEntity
import com.example.data.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SupabaseSyncManager(
    private val tripRepository: TripRepository,
    private val receiptRepository: GasReceiptRepository,
    val supabaseClient: SupabaseClient,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _syncState = MutableStateFlow(
        if (supabaseClient.config.isConfigured()) {
            SupabaseSyncState(status = SyncStatus.IDLE)
        } else {
            SupabaseSyncState(
                status = SyncStatus.NOT_CONFIGURED,
                message = "Supabase Anon Key is required to enable cloud database sync."
            )
        }
    )
    val syncState: StateFlow<SupabaseSyncState> = _syncState.asStateFlow()

    fun triggerSync() {
        scope.launch {
            syncAll()
        }
    }

    suspend fun syncAll(): SupabaseSyncState = withContext(Dispatchers.IO) {
        if (!supabaseClient.config.isConfigured()) {
            val state = SupabaseSyncState(
                status = SyncStatus.NOT_CONFIGURED,
                message = "Supabase Anon Key is not configured."
            )
            _syncState.value = state
            return@withContext state
        }

        _syncState.value = _syncState.value.copy(status = SyncStatus.SYNCING, message = "Synchronizing with Supabase...")

        try {
            val currentUserId = supabaseClient.config.getSession()?.user?.id

            // 1. Synchronize Trips
            val localTrips = tripRepository.getAllTripsList()
            for (local in localTrips) {
                try {
                    val dto = SupabaseTripDto.fromTripEntity(local, currentUserId)
                    supabaseClient.upsertTrip(dto)
                } catch (_: Exception) {}
            }

            // Pull latest remote trips
            val remoteTripsResult = supabaseClient.fetchTrips()
            var tripsSynced = localTrips.size
            if (remoteTripsResult.isSuccess) {
                val remoteTrips = remoteTripsResult.getOrNull() ?: emptyList()
                val entitiesToInsert = remoteTrips.map { it.toTripEntity() }
                if (entitiesToInsert.isNotEmpty()) {
                    tripRepository.insertTrips(entitiesToInsert)
                    tripsSynced = maxOf(tripsSynced, remoteTrips.size)
                }
            }

            // 2. Synchronize Gas Receipts
            val localReceipts = receiptRepository.getAllReceiptsList()
            for (local in localReceipts) {
                try {
                    val dto = SupabaseReceiptDto.fromGasReceiptEntity(local, currentUserId)
                    supabaseClient.upsertReceipt(dto)
                } catch (_: Exception) {}
            }

            // Pull latest remote receipts
            val remoteReceiptsResult = supabaseClient.fetchReceipts()
            var receiptsSynced = localReceipts.size
            if (remoteReceiptsResult.isSuccess) {
                val remoteReceipts = remoteReceiptsResult.getOrNull() ?: emptyList()
                val entitiesToInsert = remoteReceipts.map { it.toGasReceiptEntity() }
                if (entitiesToInsert.isNotEmpty()) {
                    receiptRepository.insertReceipts(entitiesToInsert)
                    receiptsSynced = maxOf(receiptsSynced, remoteReceipts.size)
                }
            }

            val successState = SupabaseSyncState(
                status = SyncStatus.SUCCESS,
                lastSyncTime = System.currentTimeMillis(),
                message = "Successfully synchronized with Supabase database.",
                syncedTripsCount = tripsSynced,
                syncedReceiptsCount = receiptsSynced
            )
            _syncState.value = successState
            return@withContext successState
        } catch (e: Exception) {
            val errorState = SupabaseSyncState(
                status = SyncStatus.ERROR,
                message = e.message ?: "Cloud sync failed. Local data remains safe.",
                lastSyncTime = _syncState.value.lastSyncTime
            )
            _syncState.value = errorState
            return@withContext errorState
        }
    }

    fun onTripSaved(trip: TripEntity) {
        if (!supabaseClient.config.isConfigured()) return
        scope.launch {
            try {
                val userId = supabaseClient.config.getSession()?.user?.id
                val dto = SupabaseTripDto.fromTripEntity(trip, userId)
                supabaseClient.upsertTrip(dto)
            } catch (_: Exception) {}
        }
    }

    fun onTripDeleted(trip: TripEntity) {
        if (!supabaseClient.config.isConfigured()) return
        scope.launch {
            try {
                supabaseClient.deleteTrip(trip.id)
            } catch (_: Exception) {}
        }
    }

    fun onReceiptSaved(receipt: GasReceiptEntity) {
        if (!supabaseClient.config.isConfigured()) return
        scope.launch {
            try {
                val userId = supabaseClient.config.getSession()?.user?.id
                val dto = SupabaseReceiptDto.fromGasReceiptEntity(receipt, userId)
                supabaseClient.upsertReceipt(dto)
            } catch (_: Exception) {}
        }
    }

    fun onReceiptDeleted(receipt: GasReceiptEntity) {
        if (!supabaseClient.config.isConfigured()) return
        scope.launch {
            try {
                supabaseClient.deleteReceipt(receipt.id)
            } catch (_: Exception) {}
        }
    }
}
