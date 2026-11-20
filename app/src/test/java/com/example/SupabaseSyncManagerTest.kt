package com.example

import android.app.Application
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GasReceiptEntity
import com.example.data.GasReceiptRepository
import com.example.data.SyncRevision
import com.example.data.TripEntity
import com.example.data.TripRepository
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseReceiptDto
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseSyncManager
import com.example.data.supabase.SupabaseTripDto
import com.example.data.supabase.SupabaseUser
import com.example.data.supabase.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SupabaseSyncManagerTest {
    private val owner = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"
    private val cloud = Cloud()
    private val devices = mutableListOf<Device>()

    private data class Device(val db: AppDatabase, val sync: SupabaseSyncManager, val scope: CoroutineScope) {
        val trips get() = db.tripDao()
        val receipts get() = db.gasReceiptDao()
        suspend fun synchronize() = assertEquals(SyncStatus.SUCCESS, sync.syncAll().status)
    }

    private fun device(owner: String = this.owner): Device {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefix = "sync-test-${UUID.randomUUID()}-"
        val config = SupabaseConfig(object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefix + name, mode)
        })
        config.setAnonKey("eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.c2lnbmF0dXJlMTIzNDU2")
        config.saveSession(SupabaseSession(owner, "test-refresh", SupabaseUser(owner, "driver@example.invalid")))
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return Device(db, SupabaseSyncManager(TripRepository(db.tripDao()), GasReceiptRepository(db.gasReceiptDao()),
            SupabaseClient(config, cloud.http), scope), scope).also(devices::add)
    }

    @After fun closeDevices() = runBlocking {
        for (device in devices) {
            device.scope.coroutineContext[Job]?.cancelAndJoin()
            device.db.close()
        }
    }

    private fun trip(purpose: String = "Delivery") = TripEntity(
        startDate = 1_000, endDate = 2_000, distanceKm = 5.0, type = "Business", purpose = purpose
    )
    private fun receipt(tripId: Long? = null) =
        GasReceiptEntity(imagePath = "", merchant = "Shell", totalAmount = 70.0, tripId = tripId)

    @Test fun `two devices with the same Room IDs keep their own trips and linked receipts`() = runBlocking {
        val first = device()
        val second = device()
        val third = device(other)
        for ((index, d) in listOf(first, second, third).withIndex()) {
            val localId = d.trips.insertTrip(trip("Device $index"))
            assertEquals(1L, localId)
            assertEquals(1L, d.receipts.insertReceipt(receipt(localId).copy(merchant = "Receipt $index")))
        }

        first.synchronize()
        second.synchronize()
        first.synchronize()
        third.synchronize()
        first.synchronize()

        for (d in listOf(first, second)) {
            val byId = d.trips.getAllTripsList().associateBy { it.id }
            assertEquals(setOf("Device 0", "Device 1"), byId.values.map { it.purpose }.toSet())
            assertEquals(2, d.receipts.getAllReceiptsList().size)
            for (r in d.receipts.getAllReceiptsList()) {
                assertEquals(r.merchant!!.replace("Receipt", "Device"), byId[r.tripId]?.purpose)
            }
        }
        assertEquals("Device 2", third.trips.getAllTripsList().single().purpose)
        assertEquals("Receipt 2", third.receipts.getAllReceiptsList().single().merchant)
    }

    @Test fun `newest offline edits win on both devices regardless of upload order`() = runBlocking {
        val first = device()
        val second = device()
        val tripId = first.trips.insertTrip(trip())
        first.receipts.insertReceipt(receipt(tripId))
        first.synchronize()
        second.synchronize()

        val tripA = first.trips.getAllTripsList().single()
        val tripB = second.trips.getAllTripsList().single()
        val receiptA = first.receipts.getAllReceiptsList().single()
        val receiptB = second.receipts.getAllReceiptsList().single()
        first.trips.updateTrip(tripA.copy(type = "Personal", purpose = "Earlier offline edit"))
        first.receipts.updateReceipt(receiptA.copy(merchant = "Earlier receipt"))
        val newer = first.trips.getTripById(tripA.id)!!.sync.syncUpdatedAt + 500
        // Simulate a later device clock while both devices are offline.
        second.trips.replaceTrip(tripB.copy(type = "Business", purpose = "Later offline edit",
            sync = tripB.sync.changed().copy(syncUpdatedAt = newer)))
        second.receipts.replaceReceipt(receiptB.copy(merchant = "Later receipt",
            sync = receiptB.sync.changed().copy(syncUpdatedAt = newer)))

        second.synchronize()
        first.synchronize()
        second.synchronize()
        for (d in listOf(first, second)) {
            assertEquals("Later offline edit", d.trips.getAllTripsList().single().purpose)
            assertEquals("Business", d.trips.getAllTripsList().single().type)
            assertEquals("Later receipt", d.receipts.getAllReceiptsList().single().merchant)
        }
    }

    @Test fun `offline deletion survives retry and wins even over a later stale edit`() = runBlocking {
        val first = device()
        val second = device()
        val tripId = first.trips.insertTrip(trip())
        val receiptId = first.receipts.insertReceipt(receipt(tripId))
        first.synchronize()
        second.synchronize()

        first.trips.deleteTripById(tripId)
        first.receipts.deleteReceiptById(receiptId)
        cloud.failure = "POST" to "trips"
        assertEquals(SyncStatus.ERROR, first.sync.syncAll().status)
        assertTrue(first.trips.getAllTripsList().isEmpty())
        assertTrue(first.receipts.getAllReceiptsList().isEmpty())
        assertTrue(first.trips.findRecord(tripId)!!.sync.syncDeleted)
        assertTrue(first.receipts.findRecord(receiptId)!!.sync.syncDeleted)

        val staleTrip = second.trips.getAllTripsList().single()
        val staleReceipt = second.receipts.getAllReceiptsList().single()
        second.trips.updateTrip(staleTrip.copy(purpose = "Edit after first device deleted"))
        second.receipts.updateReceipt(staleReceipt.copy(merchant = "Stale receipt edit"))
        cloud.failure = null
        first.synchronize()
        second.synchronize()
        first.synchronize()
        for (d in listOf(first, second)) {
            assertTrue(d.trips.getAllTripsList().isEmpty())
            assertTrue(d.receipts.getAllReceiptsList().isEmpty())
        }
    }

    @Test fun `legacy copies adopt their cloud UUID while a different trip with the same ID survives`() = runBlocking {
        val legacyTrip = trip().copy(id = 1, type = null, sync = SyncRevision(syncUpdatedAt = 0, syncChangeId = ""))
        val legacyReceipt = receipt(1).copy(id = 1, sync = SyncRevision(syncUpdatedAt = 0, syncChangeId = ""))
        cloud.seed("trips", SupabaseTripDto.fromTripEntity(legacyTrip, owner).toJsonObject())
        cloud.seed("gas_receipts", SupabaseReceiptDto.fromGasReceiptEntity(legacyReceipt, owner).toJsonObject())
        val first = device()
        val second = device()
        val noIdentity = SyncRevision(syncId = null, syncUpdatedAt = 0, syncChangeId = "")
        first.trips.insertTrip(legacyTrip.copy(type = "Business", sync = noIdentity))
        first.receipts.insertReceipt(legacyReceipt.copy(sync = noIdentity))
        second.trips.insertTrip(trip("Unrelated older local drive").copy(id = 1, startDate = 10_000,
            endDate = 20_000, sync = noIdentity))

        first.synchronize()
        assertEquals(legacyTrip.sync.syncId, first.trips.getAllTripsList().single().sync.syncId)
        assertEquals("Business", first.trips.getAllTripsList().single().type)
        assertEquals(legacyReceipt.sync.syncId, first.receipts.getAllReceiptsList().single().sync.syncId)
        second.synchronize()
        assertEquals(2, second.trips.getAllTripsList().size)
        val linked = second.trips.getTripById(second.receipts.getAllReceiptsList().single().tripId!!)!!
        assertEquals(legacyTrip.sync.syncId, linked.sync.syncId)
    }

    @Test fun `a classification saved during download remains the local winner`() = runBlocking {
        val d = device()
        val id = d.trips.insertTrip(trip().copy(type = null))
        cloud.beforeGet = { collection, count ->
            if (collection == "trips" && count == 2) d.trips.classifyTrips(listOf(id), "Personal")
        }
        d.synchronize()
        assertEquals("Personal", d.trips.getTripById(id)!!.type)
        cloud.beforeGet = { _, _ -> }
        d.synchronize()
        assertEquals("Personal", d.trips.getAllTripsList().single().type)
    }

    @Test fun `failure retains local data and the previous successful sync time`() = runBlocking {
        val d = device()
        val id = d.trips.insertTrip(trip())
        d.synchronize()
        val successfulTime = d.sync.syncState.value.lastSyncTime
        d.trips.updateTrip(d.trips.getTripById(id)!!.copy(purpose = "Offline edit"))
        cloud.failure = "GET" to "gas_receipts"
        val state = d.sync.syncAll()
        assertEquals(SyncStatus.ERROR, state.status)
        assertEquals(successfulTime, state.lastSyncTime)
        assertEquals("Offline edit", d.trips.getTripById(id)!!.purpose)
        cloud.failure = null
        d.synchronize()
        assertEquals("Offline edit", d.trips.getAllTripsList().single().purpose)
    }

    @Test fun `an automatic sync posts a previously offline deletion`() = runBlocking {
        val first = device()
        val second = device()
        val id = first.trips.insertTrip(trip())
        first.synchronize()
        second.synchronize()
        first.trips.deleteTripById(id)
        cloud.failure = "POST" to "trips"
        first.sync.triggerSync()
        withTimeout(5_000) { first.sync.syncState.first { it.status == SyncStatus.ERROR } }
        cloud.failure = null
        first.sync.triggerSync()
        withTimeout(5_000) { first.sync.syncState.first { it.status == SyncStatus.SUCCESS } }
        second.synchronize()
        assertTrue(second.trips.getAllTripsList().isEmpty())
    }

    @Test fun `invalid downloaded record prevents success`() = runBlocking {
        val d = device()
        val invalid = trip().copy(distanceKm = 0.0)
        cloud.seed("trips", SupabaseTripDto.fromTripEntity(invalid, owner).toJsonObject())
        assertEquals(SyncStatus.ERROR, d.sync.syncAll().status)
        assertTrue(d.trips.getAllTripsList().isEmpty())
    }

    private class Cloud {
        private val rows = mutableMapOf<Pair<String, String>, JSONObject>()
        private val downloads = mutableMapOf<String, Int>()
        private var nextId = 1L
        var failure: Pair<String, String>? = null
        var beforeGet: suspend (String, Int) -> Unit = { _, _ -> }

        @Synchronized fun seed(table: String, json: JSONObject) {
            json.put("id", nextId++)
            rows[table to json.getString("sync_id")] = json
        }

        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val table = request.url.encodedPath.substringAfterLast('/')
            val owner = request.header("Authorization")!!.removePrefix("Bearer ")
            if (request.method == "GET") {
                val count = synchronized(this) { downloads.merge(table, 1, Int::plus)!! }
                runBlocking { beforeGet(table, count) }
            }
            val failed = (request.method to table) == failure
            val body = if (failed) "{}" else synchronized(this) {
                when (request.method) {
                    "GET" -> JSONArray(rows.filter { (key, value) -> key.first == table && value.getString("user_id") == owner }
                        .values.sortedBy { it.getLong("id") }).toString()
                    "POST" -> {
                        assertEquals("user_id,sync_id", request.url.queryParameter("on_conflict"))
                        val buffer = Buffer()
                        request.body!!.writeTo(buffer)
                        val edit = JSONObject(buffer.readUtf8())
                        assertFalse(edit.has("client_id"))
                        assertEquals(owner, edit.getString("user_id"))
                        val key = table to edit.getString("sync_id")
                        val old = rows[key]
                        if (old == null) seed(table, edit)
                        else if (!old.getBoolean("sync_deleted") && version(edit) > version(old)) {
                            edit.put("id", old.getLong("id"))
                            if (old.has("client_id")) edit.put("client_id", old.getLong("client_id"))
                            if (old.has("trip_id")) edit.put("trip_id", old.opt("trip_id"))
                            rows[key] = edit
                        }
                        "[]"
                    }
                    else -> error("Unrecognized sync request")
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (failed) 503 else 200).message("Test")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()

        private fun version(json: JSONObject) = SyncRevision(
            syncUpdatedAt = json.getLong("sync_updated_at"),
            syncChangeId = json.getString("sync_change_id"),
            syncDeleted = json.getBoolean("sync_deleted")
        )
    }
}
