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
    val unit: String = "km", // "km" or "mi"
    val reimbursementRate: Double = 0.73, // 2026 CRA rate for the first 5,000 km
    val vehicleName: String = "Primary Vehicle",
    val annualGoal: Double = 15000.0, // target annual distance in user's unit
    val odometerKm: Double? = null,
    val odometerAt: Long = 0L,
    val odometerSource: String = "",
    val workProfile: WorkProfile = WorkProfile.GENERAL,
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
            unit = prefs.getString("distance_unit", "km") ?: "km",
            reimbursementRate = prefs.getFloat("reimbursement_rate", 0.73f).toDouble(),
            vehicleName = prefs.getString("vehicle_name", "Primary Vehicle") ?: "Primary Vehicle",
            annualGoal = prefs.getFloat("annual_goal", 15000f).toDouble(),
            odometerKm = prefs.getString("odometer_km", null)?.toDoubleOrNull(),
            odometerAt = prefs.getLong("odometer_at", 0L),
            odometerSource = prefs.getString("odometer_source", "") ?: "",
            workProfile = WorkProfile.fromId(prefs.getString("work_profile", null)),
            onboardingSeen = prefs.getBoolean("onboarding_seen", false)
        )
    }

    fun setWorkProfile(profile: WorkProfile) {
        prefs.edit().putString("work_profile", profile.id).apply()
        _settings.value = _settings.value.copy(workProfile = profile)
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

    fun setUnit(unit: String) {
        val clean = InputCheck.distanceUnit(unit) ?: return
        prefs.edit().putString("distance_unit", clean).apply()
        _settings.value = _settings.value.copy(unit = clean)
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
