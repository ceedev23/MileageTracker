package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MileageSettings(
    val autoDetect: Boolean = true,
    val dwellMinutes: Int = 5,
    val unit: String = "km", // "km" or "mi"
    val reimbursementRate: Double = 0.70, // dollars per unit
    val vehicleName: String = "Primary Vehicle",
    val annualGoal: Double = 15000.0 // target annual distance in user's unit
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
            reimbursementRate = prefs.getFloat("reimbursement_rate", 0.70f).toDouble(),
            vehicleName = prefs.getString("vehicle_name", "Primary Vehicle") ?: "Primary Vehicle",
            annualGoal = prefs.getFloat("annual_goal", 15000f).toDouble()
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
    }

    fun setUnit(unit: String) {
        prefs.edit().putString("distance_unit", unit).apply()
        _settings.value = _settings.value.copy(unit = unit)
    }

    fun setReimbursementRate(rate: Double) {
        prefs.edit().putFloat("reimbursement_rate", rate.toFloat()).apply()
        _settings.value = _settings.value.copy(reimbursementRate = rate)
    }

    fun setVehicleName(name: String) {
        prefs.edit().putString("vehicle_name", name).apply()
        _settings.value = _settings.value.copy(vehicleName = name)
    }

    fun setAnnualGoal(goal: Double) {
        val positive = if (goal <= 0.0) 1000.0 else goal
        prefs.edit().putFloat("annual_goal", positive.toFloat()).apply()
        _settings.value = _settings.value.copy(annualGoal = positive)
    }
}
