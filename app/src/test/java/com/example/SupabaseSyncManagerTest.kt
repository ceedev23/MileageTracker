package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GasReceiptEntity
import com.example.data.GasReceiptRepository
import com.example.data.TripEntity
import com.example.data.TripRepository
import com.example.data.supabase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SupabaseSyncManagerTest {
    private lateinit var database: AppDatabase
    private lateinit var manager: SupabaseSyncManager
    private lateinit var scope: CoroutineScope
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    @Volatile private var failMethod: String? = null
    @Volatile private var failCollection: String? = null
    @Volatile private var tripDownload = "[]"
    @Volatile private var receiptDownload = "[]"
    private val trip = TripEntity(
        id = 1, startDate = 1000, endDate = 2000, distanceKm = 5.0,
        type = "Business", purpose = "Latest local edit"
    )
    private val receipt = GasReceiptEntity(
        id = 1, imagePath = "receipt_123.jpg", merchant = "Shell",
        totalAmount = 70.0, unit = "L"
    )

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        runBlocking {
            database.tripDao().insertTrip(trip)
            database.gasReceiptDao().insertReceipt(receipt)
        }
        val config = SupabaseConfig(app)
        config.clearSession()
        config.setAnonKey("eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.c2lnbmF0dXJlMTIzNDU2")
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val collection = request.url.encodedPath.substringAfterLast('/')
            requests.add(request.method to collection)
            val fails = request.method == failMethod && collection == failCollection
            val body = when {
                fails -> "{}"
                request.method == "GET" && collection == "trips" -> tripDownload
                request.method == "GET" && collection == "gas_receipts" -> receiptDownload
                else -> "[]"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (fails) 503 else 200).message(if (fails) "Unavailable" else "OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        manager = SupabaseSyncManager(
            TripRepository(database.tripDao()), GasReceiptRepository(database.gasReceiptDao()),
            SupabaseClient(config, http), scope
        )
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        database.close()
    }

    private fun fail(method: String, collection: String) {
        failMethod = method
        failCollection = collection
    }

    @Test
    fun `failed trip upload reports error without downloading or losing local edit`() = runBlocking {
        fail("POST", "trips")
        tripDownload = JSONArray().put(SupabaseTripDto.fromTripEntity(trip.copy(purpose = "Older cloud edit")).toJsonObject()).toString()
        val state = manager.syncAll()
        assertEquals(SyncStatus.ERROR, state.status)
        assertNull(state.lastSyncTime)
        assertEquals(0, state.syncedTripsCount)
        assertEquals(0, state.syncedReceiptsCount)
        assertFalse(requests.any { it.first == "GET" })
        assertEquals(trip, database.tripDao().getTripById(trip.id))
    }

    @Test
    fun `receipt upload failure reports only confirmed trip upload and prevents downloads`() = runBlocking {
        fail("POST", "gas_receipts")
        val state = manager.syncAll()
        assertEquals(SyncStatus.ERROR, state.status)
        assertEquals(1, state.syncedTripsCount)
        assertEquals(0, state.syncedReceiptsCount)
        assertFalse(requests.any { it.first == "GET" })
        assertEquals(receipt, database.gasReceiptDao().getReceiptById(receipt.id))
    }

    @Test
    fun `failed trip download cannot produce success`() = runBlocking {
        fail("GET", "trips")
        val state = manager.syncAll()
        assertEquals(SyncStatus.ERROR, state.status)
        assertNull(state.lastSyncTime)
        assertEquals(1, state.syncedTripsCount)
        assertEquals(1, state.syncedReceiptsCount)
    }

    @Test
    fun `receipt download failure does not apply already fetched cloud trip`() = runBlocking {
        tripDownload = JSONArray().put(SupabaseTripDto.fromTripEntity(trip.copy(purpose = "Older cloud edit")).toJsonObject()).toString()
        fail("GET", "gas_receipts")
        assertEquals(SyncStatus.ERROR, manager.syncAll().status)
        assertEquals(trip, database.tripDao().getTripById(trip.id))
    }

    @Test
    fun `failure after a successful sync preserves last successful timestamp`() = runBlocking {
        val success = manager.syncAll()
        assertEquals(SyncStatus.SUCCESS, success.status)
        assertNotNull(success.lastSyncTime)
        fail("POST", "trips")
        val error = manager.syncAll()
        assertEquals(SyncStatus.ERROR, error.status)
        assertEquals(success.lastSyncTime, error.lastSyncTime)
        assertEquals(0, error.syncedTripsCount)
    }

    @Test
    fun `retained local records can be retried successfully`() = runBlocking {
        fail("POST", "trips")
        assertEquals(SyncStatus.ERROR, manager.syncAll().status)
        failMethod = null
        failCollection = null
        val success = manager.syncAll()
        assertEquals(SyncStatus.SUCCESS, success.status)
        assertEquals(1, success.syncedTripsCount)
        assertEquals(1, success.syncedReceiptsCount)
        assertNotNull(success.lastSyncTime)
        assertEquals(1, database.tripDao().getAllTripsList().size)
        assertEquals(1, database.gasReceiptDao().getAllReceiptsList().size)
        assertEquals(2, requests.count { it == ("POST" to "trips") })
    }

    @Test
    fun `invalid downloaded record cannot silently count as complete sync`() = runBlocking {
        tripDownload = JSONArray().put(SupabaseTripDto.fromTripEntity(trip.copy(distanceKm = 0.0)).toJsonObject()).toString()
        assertEquals(SyncStatus.ERROR, manager.syncAll().status)
        assertNull(manager.syncState.value.lastSyncTime)
        assertEquals(trip, database.tripDao().getTripById(trip.id))
    }

    @Test
    fun `successful empty sync has zero confirmed records`() = runBlocking {
        database.tripDao().clearAllTrips()
        database.gasReceiptDao().clearAllReceipts()
        val state = manager.syncAll()
        assertEquals(SyncStatus.SUCCESS, state.status)
        assertEquals(0, state.syncedTripsCount)
        assertEquals(0, state.syncedReceiptsCount)
        assertNotNull(state.lastSyncTime)
    }

    @Test
    fun `success counts distinct confirmed local and downloaded records`() = runBlocking {
        tripDownload = JSONArray().put(SupabaseTripDto.fromTripEntity(trip.copy(id = 2)).toJsonObject()).toString()
        receiptDownload = JSONArray().put(SupabaseReceiptDto.fromGasReceiptEntity(receipt.copy(id = 2)).toJsonObject()).toString()
        val state = manager.syncAll()
        assertEquals(SyncStatus.SUCCESS, state.status)
        assertEquals(2, state.syncedTripsCount)
        assertEquals(2, state.syncedReceiptsCount)
        assertEquals(2, database.tripDao().getAllTripsList().size)
        assertEquals(2, database.gasReceiptDao().getAllReceiptsList().size)
    }

    @Test
    fun `individual upload failure replaces previous success status`() = runBlocking {
        assertEquals(SyncStatus.SUCCESS, manager.syncAll().status)
        val successfulTime = manager.syncState.value.lastSyncTime
        fail("POST", "trips")
        manager.onTripSaved(trip)
        val error = withTimeout(5000) { manager.syncState.first { it.status == SyncStatus.ERROR } }
        assertEquals(successfulTime, error.lastSyncTime)
        assertEquals(trip, database.tripDao().getTripById(trip.id))
    }

    @Test
    fun `individual delete failure is visible`() = runBlocking {
        fail("DELETE", "trips")
        manager.onTripDeleted(trip)
        val error = withTimeout(5000) { manager.syncState.first { it.status == SyncStatus.ERROR } }
        assertNull(error.lastSyncTime)
    }

    @Test
    fun `individual receipt upload failure is visible and retains local receipt`() = runBlocking {
        fail("POST", "gas_receipts")
        manager.onReceiptSaved(receipt)
        withTimeout(5000) { manager.syncState.first { it.status == SyncStatus.ERROR } }
        assertEquals(receipt, database.gasReceiptDao().getReceiptById(receipt.id))
    }

    @Test
    fun `individual receipt deletion failure is visible`() = runBlocking {
        fail("DELETE", "gas_receipts")
        manager.onReceiptDeleted(receipt)
        val error = withTimeout(5000) { manager.syncState.first { it.status == SyncStatus.ERROR } }
        assertNull(error.lastSyncTime)
    }
}
