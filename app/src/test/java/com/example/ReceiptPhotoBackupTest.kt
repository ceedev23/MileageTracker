package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.data.supabase.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReceiptPhotoBackupTest {
    private val user = "11111111-1111-4111-8111-111111111111"
    private val other = "22222222-2222-4222-8222-222222222222"
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 1, 2)
    private lateinit var app: Application
    private lateinit var config: SupabaseConfig
    private lateinit var client: SupabaseClient
    private lateinit var photos: ReceiptPhotoBackup
    private lateinit var image: File
    private val requests = CopyOnWriteArrayList<Request>()
    private var failUpload = false
    private var failDelete = false
    private var failDownload = false
    private var remoteReceipt: SupabaseReceiptDto? = null
    private var db: AppDatabase? = null

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("receipt_photo_backup", Context.MODE_PRIVATE).edit().clear().commit()
        config = SupabaseConfig(app)
        config.setAnonKey("eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiYW5vbiJ9.c2lnbmF0dXJlMTIzNDU2")
        signIn(user)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request()
            requests.add(r)
            val storage = r.url.encodedPath.contains("/storage/")
            val fails = storage && ((r.method == "POST" && failUpload) || (r.method == "DELETE" && failDelete) || (r.method == "GET" && failDownload))
            val body = if (storage && r.method == "GET" && !fails) jpeg.toResponseBody("image/jpeg".toMediaType())
                else (if (r.method == "GET" && r.url.encodedPath.endsWith("gas_receipts"))
                    JSONArray().apply { remoteReceipt?.let { put(it.toJsonObject()) } }.toString() else "[]")
                    .toResponseBody("application/json".toMediaType())
            Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(if (fails) 503 else 200)
                .message("Test").body(body).build()
        }.build()
        client = SupabaseClient(config, http)
        photos = ReceiptPhotoBackup(app, client, available = true, region = "Canada Central")
        image = File(File(app.filesDir, "receipts").apply { mkdirs() }, "receipt_123.jpg")
        image.writeBytes(jpeg)
    }
    private fun signIn(id: String) = config.saveSession(SupabaseSession("test-token", "test-refresh", SupabaseUser(id, "test@example.invalid")))
    private fun receipt() = GasReceiptEntity(id = 123, imagePath = image.absolutePath, totalAmount = 50.0)
    private fun dto() = SupabaseReceiptDto.fromGasReceiptEntity(receipt(), user).copy(imagePath = ReceiptPhotoBackup.PREFIX + "$user/123.jpg")
    @After fun cleanup() { db?.close(); app.filesDir.resolve("receipts").deleteRecursively() }

    @Test fun `photos stay local until explicit opt in`() = runBlocking {
        assertFalse(photos.enabled())
        assertNull(photos.upload(receipt()))
        assertTrue(requests.isEmpty())
        assertFalse(SupabaseReceiptDto.fromGasReceiptEntity(receipt()).toJsonObject().has("image_path"))
    }
    @Test fun `opt in is scoped to account and project`() {
        photos.setEnabled(true)
        signIn(other)
        assertFalse(photos.enabled())
        signIn(user)
        assertTrue(photos.enabled())
        config.setUrl("https://anotherproject.supabase.co")
        assertFalse(photos.enabled())
    }
    @Test fun `unavailable deployment cannot enable uploads`() {
        val disabled = ReceiptPhotoBackup(app, client, available = false)
        try { disabled.setEnabled(true); fail("Should reject") } catch (_: IllegalStateException) {}
        assertFalse(disabled.enabled())
    }
    @Test fun `upload sends bytes and own private object key`() = runBlocking {
        photos.setEnabled(true)
        assertEquals(ReceiptPhotoBackup.PREFIX + "$user/123.jpg", photos.upload(receipt()))
        val request = requests.single()
        assertEquals("/storage/v1/object/receipt-photos/$user/123.jpg", request.url.encodedPath)
        assertEquals("Bearer test-token", request.header("Authorization"))
        val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
        assertArrayEquals(jpeg, buffer.readByteArray())
    }
    @Test fun `failed upload preserves local photo`() = runBlocking {
        photos.setEnabled(true); failUpload = true
        try { photos.upload(receipt()); fail("Should reject") } catch (_: Exception) {}
        assertArrayEquals(jpeg, image.readBytes())
    }
    @Test fun `restore writes actual image into private receipt directory`() = runBlocking {
        photos.setEnabled(true); image.delete()
        val restored = photos.restore(dto(), null)
        val file = InputCheck.receiptFile(app.filesDir, restored.imagePath)
        assertNotNull(file)
        assertArrayEquals(jpeg, file!!.readBytes())
        assertFalse(restored.imagePath.contains("receipt-photo:"))
    }
    @Test fun `legacy remote local paths are not treated as photos on new phone`() = runBlocking {
        val restored = photos.restore(dto().copy(imagePath = "/data/other-phone/receipts/receipt_123.jpg"), null)
        assertEquals("", restored.imagePath)
        assertTrue(requests.isEmpty())
    }
    @Test fun `other user keys and traversal are rejected`() = runBlocking {
        photos.setEnabled(true)
        for (key in listOf("$other/123.jpg", "$user/../123.jpg", "https://evil.invalid/p.jpg")) {
            try { photos.restore(dto().copy(imagePath = ReceiptPhotoBackup.PREFIX + key), null); fail("Should reject") }
            catch (_: IllegalArgumentException) {}
        }
        assertTrue(requests.isEmpty())
    }
    @Test fun `missing local image cannot silently count as photo backup`() = runBlocking {
        photos.setEnabled(true); image.delete()
        try { photos.upload(receipt()); fail("Should reject") } catch (_: IllegalStateException) {}
        assertTrue(requests.isEmpty())
    }
    @Test fun `deletion survives recreation and retries even when backup is off`() = runBlocking {
        photos.queueDeletion(123); failDelete = true
        try { photos.drainDeletions(); fail("Should reject") } catch (_: Exception) {}
        assertEquals(1, requests.size)
        failDelete = false; requests.clear()
        ReceiptPhotoBackup(app, client, available = false).drainDeletions()
        assertEquals(2, requests.size)
        requests.clear(); photos.drainDeletions()
        assertTrue(requests.isEmpty())
    }
    @Test fun `photo upload failure prevents metadata writes and downloads`() = runBlocking {
        photos.setEnabled(true); failUpload = true
        val manager = manager(true)
        assertEquals(SyncStatus.ERROR, manager.syncAll().status)
        assertFalse(requests.any { it.url.encodedPath.startsWith("/rest/") })
    }
    @Test fun `restore failure never reports a successful full backup`() = runBlocking {
        photos.setEnabled(true); failDownload = true; remoteReceipt = dto()
        assertEquals(SyncStatus.ERROR, manager(false).syncAll().status)
    }
    @Test fun `successful sync restores photo into local database`() = runBlocking {
        photos.setEnabled(true); remoteReceipt = dto(); image.delete()
        assertEquals(SyncStatus.SUCCESS, manager(false).syncAll().status)
        val saved = db!!.gasReceiptDao().getReceiptById(123)!!
        assertArrayEquals(jpeg, File(saved.imagePath).readBytes())
    }
    private suspend fun manager(localReceipt: Boolean): SupabaseSyncManager {
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        if (localReceipt) db!!.gasReceiptDao().insertReceipt(receipt())
        return SupabaseSyncManager(TripRepository(db!!.tripDao()), GasReceiptRepository(db!!.gasReceiptDao()), client, photos = photos)
    }
}
