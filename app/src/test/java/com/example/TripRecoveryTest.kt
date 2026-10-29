package com.example

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TripEntity
import com.example.service.TripCheckpoint
import com.example.service.TripCheckpointStore
import com.example.service.TripRecovery
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TripRecoveryTest {
    private lateinit var database: AppDatabase
    private lateinit var directory: File
    private lateinit var store: TripCheckpointStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        directory = File(context.noBackupFilesDir, "recovery-test-${UUID.randomUUID()}")
        store = TripCheckpointStore(directory)
    }

    @After
    fun tearDown() {
        database.close()
        directory.deleteRecursively()
    }

    private fun checkpoint(completed: Boolean = false) = TripCheckpoint(
        UUID.randomUUID().toString(),
        TripEntity(
            startDate = 1000L,
            endDate = 25000L,
            distanceKm = 2.35,
            type = "Business",
            startAddress = "Oakville",
            endAddress = "Last recorded location",
            purpose = "Delivery shift",
            vehicle = "My Car",
            pathJson = "[{\"lat\":43.45,\"lng\":-79.68,\"t\":25000,\"s\":35}]"
        ),
        completed
    )

    @Test
    fun `new store instance restores distance route times and driver choices`() {
        val checkpoint = checkpoint()
        store.write(checkpoint)
        assertEquals(checkpoint, TripCheckpointStore(directory).readAll().single())
    }

    @Test
    fun `interrupted trip imports only recorded distance and asks for review`() = runBlocking {
        val checkpoint = checkpoint()
        store.write(checkpoint)
        val saved = TripRecovery(database, TripCheckpointStore(directory)).save(store.readAll().single())!!
        assertEquals(2.35, saved.distanceKm, 0.00001)
        assertEquals(25000L, saved.endDate)
        assertEquals("Business", saved.type)
        assertEquals("My Car", saved.vehicle)
        assertEquals(checkpoint.trip.pathJson, saved.pathJson)
        assertTrue(saved.purpose!!.contains("Recovered interrupted drive - review"))
        assertTrue(store.readAll().isEmpty())
        assertEquals(1, database.tripDao().getAllTripsList().size)
    }

    @Test
    fun `crash between database commit and file removal does not duplicate or resurrect trip`() = runBlocking {
        val checkpoint = checkpoint(completed = true)
        store.write(checkpoint)
        val saved = TripRecovery(database, store).save(checkpoint)!!
        // Simulate finding the old checkpoint after Room committed, but file cleanup did not.
        store.write(checkpoint)
        assertNull(TripRecovery(database, TripCheckpointStore(directory)).save(checkpoint))
        assertEquals(1, database.tripDao().getAllTripsList().size)
        assertEquals("Delivery shift", database.tripDao().getTripById(saved.id)!!.purpose)
        database.tripDao().deleteTripById(saved.id)
        store.write(checkpoint)
        assertNull(TripRecovery(database, store).save(checkpoint))
        assertTrue(database.tripDao().getAllTripsList().isEmpty())
    }

    @Test
    fun `failed database write retains checkpoint for next restart`() = runBlocking {
        val checkpoint = checkpoint()
        store.write(checkpoint)
        // Room can reopen a closed database. Reject the actual INSERT instead, so
        // this exercises the transaction failure and checkpoint-retention path.
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_trip_save BEFORE INSERT ON trips " +
                "BEGIN SELECT RAISE(ABORT, 'Simulated trip write failure'); END"
        )
        try {
            val failure = runCatching { TripRecovery(database, store).save(checkpoint) }.exceptionOrNull()
            assertNotNull("The trip INSERT must fail", failure)
            assertTrue(failure!!.message.orEmpty().contains("Simulated trip write failure"))
            assertTrue(database.tripDao().getAllTripsList().isEmpty())
            assertNull(database.tripRecoveryDao().find(checkpoint.key))
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_trip_save")
        }
        assertEquals(checkpoint, TripCheckpointStore(directory).readAll().single())
        // Once storage works again, the retained checkpoint can be recovered once.
        assertNotNull(TripRecovery(database, TripCheckpointStore(directory)).save(checkpoint))
        assertEquals(1, database.tripDao().getAllTripsList().size)
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `cleaning an earlier trip leaves the next active checkpoint intact`() = runBlocking {
        val earlier = checkpoint(completed = true)
        val active = checkpoint()
        store.write(earlier)
        store.write(active)
        TripRecovery(database, store).save(earlier)
        assertEquals(active, store.readAll().single())
    }

    @Test
    fun `corrupt file does not prevent recovery of other trips`() {
        val valid = checkpoint()
        store.write(valid)
        val corrupt = File(directory, "${UUID.randomUUID()}.json")
        corrupt.writeText("{incomplete")
        assertEquals(valid, store.readAll().single())
        assertTrue(corrupt.exists())
    }

    @Test
    fun `empty interrupted recording does not invent mileage`() = runBlocking {
        val empty = checkpoint().let { it.copy(trip = it.trip.copy(distanceKm = 0.0)) }
        store.write(empty)
        assertNull(TripRecovery(database, store).save(empty))
        assertTrue(database.tripDao().getAllTripsList().isEmpty())
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `version two upgrade preserves existing trips`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val name = "migration-${UUID.randomUUID()}.db"
        try {
            val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            val id = before.tripDao().insertTrip(checkpoint().trip)
            before.close()
            // Recreate v2, before the recovery table and v4 history/expense tables.
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, 0).use {
                it.execSQL("DROP TABLE trip_recoveries")
                it.execSQL("DROP TABLE odometer_readings")
                it.execSQL("DROP TABLE vehicle_expenses")
                it.execSQL("DROP TABLE room_master_table")
                it.version = 2
            }
            val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4).build()
            try {
                assertEquals(2.35, after.tripDao().getTripById(id)!!.distanceKm, 0.00001)
                assertNull(after.tripRecoveryDao().find("not-imported"))
                assertNull(after.odometerReadingDao().latest("My Car"))
            } finally {
                after.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `identical checkpoint avoids another write but changed end time is saved`() {
        val original = checkpoint()
        assertTrue(store.write(original))
        assertFalse(store.write(original))
        val updated = original.copy(trip = original.trip.copy(endDate = original.trip.endDate + 10000))
        assertTrue(store.write(updated))
        assertEquals(updated, store.readAll().single())
        assertTrue(store.write(updated.copy(completed = true)))
        assertTrue(store.readAll().single().completed)
    }

    @Test
    fun `removed checkpoint is written again even if snapshot is identical`() {
        val original = checkpoint()
        assertTrue(store.write(original))
        store.remove(original.key)
        assertTrue(store.write(original))
        assertEquals(original, store.readAll().single())
    }

    @Test
    fun `failed checkpoint write remains eligible for retry`() {
        val blocked = File(directory, "blocked").apply { parentFile!!.mkdirs(); writeText("not a directory") }
        val failing = TripCheckpointStore(blocked)
        val original = checkpoint()
        try { failing.write(original); fail("Should reject unwritable directory") } catch (_: IllegalStateException) {}
        blocked.delete()
        assertTrue(failing.write(original))
        assertEquals(original, failing.readAll().single())
    }
}
