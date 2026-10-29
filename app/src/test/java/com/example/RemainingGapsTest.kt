package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.service.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RemainingGapsTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private fun time(year: Int, month: Int = 6, day: Int = 1) = Calendar.getInstance(zone).apply {
        clear(); set(year, month - 1, day, 12, 0)
    }.timeInMillis
    private fun trip(id: Long = 1, start: Long = time(2026), km: Double = 27000.0,
        vehicle: String = "Van", type: String? = "Business") = TripEntity(id, start, start + 1000, km, type, vehicle = vehicle)
    private fun expenses() = VehicleExpenseEntity("Van", 2026, 10000.0, 40000.0,
        2400.0, 200.0, 1900.0, 100.0, 800.0, 0.0, 40.0, 100.0)

    @Test fun craWorkedExampleIncludesDirectBusinessCostsOnce() {
        val r = CanadianVehicleExpenses.calculate(expenses(), listOf(trip()), zone)
        assertEquals(0.9, r.businessShare, 0.0001)
        assertEquals(5400.0, r.operatingCosts, 0.001)
        assertEquals(5000.0, r.estimatedDeduction, 0.001)
    }
    @Test fun vehicleYearAndClassificationAreIsolated() {
        val r = CanadianVehicleExpenses.calculate(expenses(), listOf(trip(km = 1000.0),
            trip(2, vehicle = "Other"), trip(3, start = time(2025)),
            trip(4, type = "Personal"), trip(5, type = null)), zone)
        assertEquals(1000.0, r.businessKm, 0.0)
    }
    @Test fun zeroBusinessDoesNotAllocateOperatingCosts() {
        val r = CanadianVehicleExpenses.calculate(expenses(), emptyList(), zone)
        assertEquals(140.0, r.estimatedDeduction, 0.0)
    }
    @Test fun zeroDistanceRejected() { assertFails { CanadianVehicleExpenses.calculate(expenses().copy(closingKm = 10000.0), emptyList(), zone) } }
    @Test fun reversedReadingsRejected() { assertFails { CanadianVehicleExpenses.calculate(expenses().copy(closingKm = 9000.0), emptyList(), zone) } }
    @Test fun businessAboveOdometerRejected() { assertFails { CanadianVehicleExpenses.calculate(expenses(), listOf(trip(km = 31000.0)), zone) } }
    @Test fun nonFiniteExpenseRejected() { assertFails { CanadianVehicleExpenses.calculate(expenses().copy(fuel = Double.NaN), emptyList(), zone) } }
    @Test fun negativeExpenseRejected() { assertFails { CanadianVehicleExpenses.calculate(expenses().copy(maintenance = -1.0), emptyList(), zone) } }
    @Test fun yearBoundaryBusinessTripMustBeSplit() {
        val start = Calendar.getInstance(zone).apply { clear(); set(2026, 0, 1) }.timeInMillis
        assertFails { CanadianVehicleExpenses.calculate(expenses(), listOf(trip(start = start - 500)), zone) }
    }
    @Test fun csvContainsInputsResultAndSource() {
        val e = expenses().copy(vehicle = "=SUM(1)")
        val csv = CanadianVehicleExpenses.csv(e, CanadianVehicleExpenses.calculate(e, emptyList(), zone))
        assertTrue(csv.contains("\"'=SUM(1)\""))
        assertTrue(csv.contains("Estimated deduction CAD"))
        assertTrue(csv.contains(CanadianVehicleExpenses.SOURCE))
        assertTrue(csv.contains("Excludes CCA"))
    }
    @Test fun shiftCanCrossMidnight() {
        val midnight = Calendar.getInstance(zone).apply { clear(); set(2026, 5, 2) }.timeInMillis
        assertEquals(1, TripShifts.group(listOf(trip(start = midnight - 3600000), trip(2, start = midnight + 3600000))).size)
    }
    @Test fun shiftsSplitByVehicleAndLongBreak() {
        assertEquals(3, TripShifts.group(listOf(trip(), trip(2, vehicle = "Other"), trip(3, start = time(2026) + TripShifts.BREAK_MS + 1001))).size)
    }
    @Test fun shiftsHaveMaximumSpan() {
        val trips = (0..17).map { trip(it.toLong(), time(2026) + it * 3600000L) }
        assertEquals(2, TripShifts.group(trips).size)
        assertTrue(TripShifts.group(trips).all { it.end - it.start <= TripShifts.MAX_SHIFT_MS })
    }
    @Test fun emptyShiftListIsEmpty() { assertTrue(TripShifts.group(emptyList()).isEmpty()) }

    @Test fun bulkClassificationUpdatesOnlySelectedRecordsAndSurvivesMissingIds() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.tripDao().insertTrips(listOf(trip(km = 10.0), trip(2, km = 20.0)))
            val updated = db.tripDao().classifyTrips(listOf(1, 1, 999), "Personal")
            assertEquals(1, updated.size)
            assertEquals("Personal", db.tripDao().getTripById(1)?.type)
            assertEquals(10.0, db.tripDao().getTripById(1)!!.distanceKm, 0.0)
            assertEquals("Business", db.tripDao().getTripById(2)?.type)
        } finally { db.close() }
    }
    @Test fun bulkInvalidClassificationLeavesRecordsUnchanged() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.tripDao().insertTrip(trip())
            try { db.tripDao().classifyTrips(listOf(1), "Other"); fail("Must reject") } catch (_: IllegalArgumentException) { }
            assertEquals("Business", db.tripDao().getTripById(1)?.type)
        } finally { db.close() }
    }
    @Test fun odometerHistoryKeepsBothReadingsAndVehicleIsolation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.odometerReadingDao()
            val first = dao.insert(OdometerReadingEntity(vehicle = "Van", recordedAt = 1, readingKm = 100.0, source = "manual"))
            val second = dao.insert(OdometerReadingEntity(vehicle = "Van", recordedAt = 2, readingKm = 200.0, source = "manual"))
            dao.insert(OdometerReadingEntity(vehicle = "Other", recordedAt = 3, readingKm = 999.0, source = "manual"))
            assertEquals(200.0, dao.latest("Van")!!.readingKm, 0.0)
            assertNotNull(dao.find(first))
            dao.delete(second)
            assertEquals(100.0, dao.latest("Van")!!.readingKm, 0.0)
        } finally { db.close() }
    }
    @Test fun migrationAddsTablesWithoutChangingOldTripData() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY, note TEXT)")
                        db.execSQL("INSERT INTO trips VALUES (1, 'preserved')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_3_4.migrate(db)
            db.query("SELECT note FROM trips WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
            db.query("SELECT * FROM odometer_readings").use { assertEquals(0, it.count) }
            db.query("SELECT * FROM vehicle_expenses").use { assertEquals(0, it.count) }
        } finally { helper.close() }
    }
    private fun assertFails(block: () -> Unit) { try { block(); fail("Invalid input accepted") } catch (_: IllegalArgumentException) {} }
}
