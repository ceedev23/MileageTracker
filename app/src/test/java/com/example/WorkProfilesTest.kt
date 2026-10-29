package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.service.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkProfilesTest {
    private lateinit var app: Application
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("mileage_tracker_prefs", 0).edit().clear().commit()
    }
    @Test fun existingUsersKeepGeneralProfile() {
        assertEquals(WorkProfile.GENERAL, SettingsManager(app).settings.value.workProfile)
    }
    @Test fun profileChoiceSurvivesRestart() {
        SettingsManager(app).setWorkProfile(WorkProfile.CARE)
        assertEquals(WorkProfile.CARE, SettingsManager(app).settings.value.workProfile)
        SettingsManager(app).setWorkProfile(WorkProfile.DELIVERY)
        assertEquals(WorkProfile.DELIVERY, SettingsManager(app).settings.value.workProfile)
    }
    @Test fun unknownPreferenceSafelyFallsBack() {
        app.getSharedPreferences("mileage_tracker_prefs", 0).edit().putString("work_profile", "unknown-new-profile").commit()
        assertEquals(WorkProfile.GENERAL, SettingsManager(app).settings.value.workProfile)
    }
    @Test fun switchingProfilesKeepsCustomRateUnitsVehicleAndOdometer() {
        val settings = SettingsManager(app)
        settings.setUnit("mi"); settings.setReimbursementRate(.55); settings.setVehicleName("Car A")
        settings.setOdometer(10000.0, "manual")
        val before = settings.settings.value
        settings.setWorkProfile(WorkProfile.CARE); settings.setWorkProfile(WorkProfile.DELIVERY)
        assertEquals(before, settings.settings.value.copy(workProfile = before.workProfile))
    }
    private fun trip(id: Long, purpose: String?, type: String? = "Business") = TripEntity(id, 1000, 2000, 10.0, type, purpose = purpose)
    private fun snapshot(profile: WorkProfile, purpose: String? = null): ReportSnapshot = ReportSnapshot.create(
        listOf(trip(1, "Agency A"), trip(2, "DoorDash"), trip(3, null, "Personal")),
        listOf(GasReceiptEntity(1, imagePath = "", tripId = 1), GasReceiptEntity(2, imagePath = "", tripId = 2), GasReceiptEntity(3, imagePath = "")),
        ReportPeriod.ALL, false, .55, workProfile = profile, workPurpose = purpose)
    @Test fun profilesNeverFilterTripsOrChangeClassificationsAutomatically() {
        val care = snapshot(WorkProfile.CARE)
        val delivery = snapshot(WorkProfile.DELIVERY)
        assertEquals(care.trips, delivery.trips)
        assertEquals(3, care.trips.size)
        assertEquals("Personal", care.trips.last().type)
        assertEquals(care.receipts, delivery.receipts)
    }
    @Test fun reportFilterKeepsOnlyExactPurposeAndLinkedReceipts() {
        val filtered = snapshot(WorkProfile.CARE, "Agency A")
        assertEquals(listOf(1L), filtered.trips.map { it.id })
        assertEquals(listOf(1L), filtered.receipts.map { it.id })
        assertEquals("Agency A", filtered.workPurpose)
    }
    @Test fun unmatchedPurposeDoesNotLeakOtherTripsOrUnlinkedReceipts() {
        val filtered = snapshot(WorkProfile.CARE, "Agency")
        assertTrue(filtered.trips.isEmpty()); assertTrue(filtered.receipts.isEmpty())
    }
    @Test fun exportsPreserveNotesAndUseSelectedProfileLabel() {
        val s = snapshot(WorkProfile.CARE)
        val csv = AccountantReportExporter.tripCsv(s)
        assertTrue(csv.contains("Agency-Assignment-Purpose"))
        assertTrue(csv.contains("\"Agency A\"")); assertTrue(csv.contains("\"DoorDash\""))
    }
    @Test fun reportSnapshotRetainsProfileAfterPreferenceChange() {
        val settings = SettingsManager(app)
        settings.setWorkProfile(WorkProfile.CARE)
        val s = snapshot(settings.settings.value.workProfile)
        settings.setWorkProfile(WorkProfile.DELIVERY)
        assertEquals(WorkProfile.CARE, s.workProfile)
    }
    @Test fun businessOnlyReportExcludesPersonalPendingAndUnlinkedReceipts() {
        val s = ReportSnapshot.create(listOf(trip(1, "Agency A"), trip(2, "Agency A", "Personal"), trip(3, "Agency A", null)),
            listOf(GasReceiptEntity(1, imagePath = "", tripId = 1), GasReceiptEntity(2, imagePath = "", tripId = 2), GasReceiptEntity(3, imagePath = "")),
            ReportPeriod.ALL, false, .55, workProfile = WorkProfile.CARE, businessOnly = true)
        assertEquals(listOf(1L), s.trips.map { it.id })
        assertEquals(listOf(1L), s.receipts.map { it.id })
    }
    @Test fun carePromptsAvoidPatientAndClinicalDetails() {
        assertTrue(WorkProfile.CARE.purposeHint.contains("omit patient names and clinical information"))
        assertTrue(WorkProfile.DELIVERY.purposeHint.contains("Omit customer names"))
    }
}
