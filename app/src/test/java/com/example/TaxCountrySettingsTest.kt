package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TaxCountrySettingsTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("mileage_tracker_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun countryDefaultsPersistWithoutChangingOdometerOrRate() {
        val manager = SettingsManager(context)
        manager.setOdometer(12000.0, "manual")
        manager.setReimbursementRate(0.5)
        manager.setTaxCountry("US")
        val saved = SettingsManager(context).settings.value
        assertEquals("US", saved.taxCountry)
        assertEquals("mi", saved.unit)
        assertTrue(saved.taxCountrySelected)
        assertEquals(12000.0, saved.odometerKm!!, 0.0)
        assertEquals(0.5, saved.reimbursementRate, 0.00001)
        assertEquals(15000.0 / 1.609344, saved.annualGoal, 0.01)
        manager.setTaxCountry("CA")
        assertEquals(15000.0, manager.settings.value.annualGoal, 0.01)
    }
    @Test fun unitOverrideKeepsCountryAndPhysicalGoal() {
        val manager = SettingsManager(context)
        manager.setTaxCountry("US")
        manager.setUnit("km")
        assertEquals("US", manager.settings.value.taxCountry)
        assertEquals(15000.0, manager.settings.value.annualGoal, 0.01)
    }
    @Test fun invalidCountryDoesNotMutateSettings() {
        val manager = SettingsManager(context)
        val before = manager.settings.value
        manager.setTaxCountry("DE")
        assertEquals(before, manager.settings.value)
    }
    @Test fun upgradePreservesExistingUnitsUntilExplicitSelection() {
        context.getSharedPreferences("mileage_tracker_prefs", Context.MODE_PRIVATE).edit()
            .putString("distance_unit", "mi").putFloat("annual_goal", 8000f).commit()
        val settings = SettingsManager(context).settings.value
        assertFalse(settings.taxCountrySelected)
        assertEquals("mi", settings.unit)
        assertEquals(8000.0, settings.annualGoal, 0.0)
    }
}
