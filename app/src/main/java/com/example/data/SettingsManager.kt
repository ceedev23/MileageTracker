package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.service.TrackingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MileageSettings(
    val autoDetect: Boolean = true,
    val dwellMinutes: Int = 5,
    val taxCountry: String = "CA",
    val taxCountrySelected: Boolean = false,
    val unit: String = "km", // "km" or "mi"
    val reimbursementRate: Double = 0.73, // 2026 CRA rate for the first 5,000 km
    val vehicleName: String = "Primary Vehicle",
    val annualGoal: Double = 15000.0, // target annual distance in user's unit
    val odometerKm: Double? = null,
    val odometerAt: Long = 0L,
    val odometerSource: String = "",
    val onboardingSeen: Boolean = false
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mileage_tracker_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<MileageSettings> = _settings.asStateFlow()

    private fun loadSettings(): MileageSettings {
        return MileageSettings(
            autoDetect = prefs.getBoolean("auto_detect", true),
            dwellMinutes = prefs.getInt("dwell_minutes", 5),
            taxCountry = prefs.getString("tax_country", "CA").orEmpty().takeIf { it in setOf("CA", "US") } ?: "CA",
            taxCountrySelected = prefs.getBoolean("tax_country_selected", false),
            unit = prefs.getString("distance_unit", "km") ?: "km",
            reimbursementRate = prefs.getFloat("reimbursement_rate", 0.73f).toDouble(),
            vehicleName = prefs.getString("vehicle_name", "Primary Vehicle") ?: "Primary Vehicle",
            annualGoal = prefs.getFloat("annual_goal", 15000f).toDouble(),
            odometerKm = prefs.getString("odometer_km", null)?.toDoubleOrNull(),
            odometerAt = prefs.getLong("odometer_at", 0L),
            odometerSource = prefs.getString("odometer_source", "") ?: "",
            onboardingSeen = prefs.getBoolean("onboarding_seen", false)
        )
    }

    fun setAutoDetect(enabled: Boolean) {
        prefs.edit().putBoolean("auto_detect", enabled).apply()
        _settings.value = _settings.value.copy(autoDetect = enabled)
    }

    fun setDwellMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 30)
        prefs.edit().putInt("dwell_minutes", clamped).apply()
        _settings.value = _settings.value.copy(dwellMinutes = clamped)
        TrackingManager.setDwellMinutes(clamped)
    }

    /** Country is explicit; GPS/location never changes it. Stored trip/odometer km stay canonical. */
    fun setTaxCountry(country: String) {
        if (country != "CA" && country != "US") return
        val old = _settings.value
        val unit = if (country == "US") "mi" else "km"
        val goal = convertGoal(old.annualGoal, old.unit, unit)
        prefs.edit().putString("tax_country", country)
            .putBoolean("tax_country_selected", true)
            .putString("distance_unit", unit)
            .putFloat("annual_goal", goal.toFloat()).apply()
        _settings.value = old.copy(taxCountry = country, taxCountrySelected = true, unit = unit, annualGoal = goal)
    }

    private fun convertGoal(goal: Double, from: String, to: String): Double = when {
        from == to -> goal
        to == "mi" -> goal / 1.609344
        else -> goal * 1.609344
    }

    fun setUnit(unit: String) {
        val clean = InputCheck.distanceUnit(unit) ?: return
        val goal = convertGoal(_settings.value.annualGoal, _settings.value.unit, clean)
        prefs.edit().putString("distance_unit", clean).putFloat("annual_goal", goal.toFloat()).apply()
        _settings.value = _settings.value.copy(unit = clean, annualGoal = goal)
    }

    fun setReimbursementRate(rate: Double) {
        val clean = InputCheck.rate(rate) ?: return
        prefs.edit().putFloat("reimbursement_rate", clean.toFloat()).apply()
        _settings.value = _settings.value.copy(reimbursementRate = clean)
    }

    fun setVehicleName(name: String) {
        val clean = InputCheck.exactLabel(name, 40) ?: return
        prefs.edit().putString("vehicle_name", clean).apply()
        _settings.value = _settings.value.copy(vehicleName = clean)
    }

    fun setAnnualGoal(goal: Double) {
        val clean = InputCheck.annualGoal(goal) ?: return
        prefs.edit().putFloat("annual_goal", clean.toFloat()).apply()
        _settings.value = _settings.value.copy(annualGoal = clean)
    }

    fun setOdometer(km: Double, source: String) {
        val cleanKm = InputCheck.odometerKm(km) ?: return
        val cleanSource = InputCheck.odometerSource(source) ?: return
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString("odometer_km", cleanKm.toString())
            .putLong("odometer_at", now)
            .putString("odometer_source", cleanSource)
            .apply()
        _settings.value = _settings.value.copy(odometerKm = cleanKm, odometerAt = now, odometerSource = cleanSource)
    }

    fun setOnboardingSeen(seen: Boolean) {
        prefs.edit().putBoolean("onboarding_seen", seen).apply()
        _settings.value = _settings.value.copy(onboardingSeen = seen)
    }
}
