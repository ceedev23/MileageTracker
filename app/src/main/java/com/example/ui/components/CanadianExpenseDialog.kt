package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.VehicleExpenseEntity
import com.example.service.CanadianVehicleExpenses
import com.example.ui.MileageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

@Composable
fun CanadianExpenseDialog(viewModel: MileageViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val records by viewModel.vehicleExpenses.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val readings by viewModel.odometerReadings.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var vehicle by remember { mutableStateOf(settings.vehicleName) }
    var year by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
    val record = records.firstOrNull { it.vehicle == vehicle && it.year.toString() == year }
    var fields by remember(vehicle, year, record) { mutableStateOf(record?.let {
        listOf(it.openingKm, it.closingKm, it.fuel, it.maintenance, it.insurance, it.registration,
            it.allowableInterest, it.allowableLease, it.businessParking, it.supplementaryInsurance).map { n -> n.toString() }
    } ?: List(10) { "" }) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var checked by remember(vehicle, year, fields) { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val snapshot = exportText
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(snapshot) }
                        ?: error("Cannot write file")
                } }
                busy = false
                message = if (result.isSuccess) "CSV exported" else "CSV export failed. Try again."
            }
        }
    }
    fun expense(): VehicleExpenseEntity {
        val values = fields.mapIndexed { i, value ->
            if (value.isBlank() && i >= 2) 0.0 else value.toDoubleOrNull() ?: error("Enter valid numbers; odometers are required")
        }
        return VehicleExpenseEntity(vehicle, year.toIntOrNull() ?: error("Enter a valid year"),
            values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8], values[9])
    }
    val calculation = runCatching { val e = expense(); e to CanadianVehicleExpenses.calculate(e, trips) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Canadian vehicle expenses") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calendar year · CAD · each vehicle calculated separately · saved on this device")
                OutlinedTextField(vehicle, { vehicle = it.take(40); message = null }, label = { Text("Vehicle (exact trip vehicle name)") }, enabled = !busy)
                val vehicles = (trips.mapNotNull { it.vehicle } + records.map { it.vehicle } + settings.vehicleName).distinct()
                vehicles.forEach { name -> TextButton(onClick = { vehicle = name }, enabled = !busy) { Text(name) } }
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4); message = null }, label = { Text("Calendar year") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
                Text("Opening/closing readings must cover this calendar year, or the vehicle ownership period within it. Use recorded boundary readings; do not substitute the first and last tracked trip.")
                readings.filter { it.vehicle == vehicle }.take(8).forEach {
                    Text("Recorded ${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(it.recordedAt))}: ${it.readingKm} km")
                }
                val labels = listOf("Opening odometer (km)", "Closing odometer (km)", "Fuel, charging and oil (CAD)",
                    "Maintenance and repairs (CAD)", "Regular insurance (CAD)", "Licence and registration (CAD)",
                    "Interest AFTER applicable limits (CAD)", "Lease costs AFTER applicable limits (CAD)",
                    "Business parking only (CAD)", "Supplementary business insurance only (CAD)")
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(fields[i], { value ->
                        fields = fields.toMutableList().also { it[i] = value.take(16) }; message = null
                    }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !busy)
                }
                Text(CanadianVehicleExpenses.LIMITS)
                val unclassified = trips.count { it.vehicle == vehicle && it.type == null && Calendar.getInstance().apply { timeInMillis = it.startDate }.get(Calendar.YEAR).toString() == year }
                Text("$unclassified unclassified trips for this vehicle/year are excluded from business kilometres. Trips without a matching vehicle are excluded.")
                Row { Checkbox(checked, { checked = it }, enabled = !busy); Text("I checked the vehicle, period, CAD amounts, expense eligibility and applicable limits") }
                calculation.onSuccess { (_, result) ->
                    Text(String.format(Locale.CANADA, "Business %.1f / total %.1f km (%.2f%%). Estimated expense deduction: CAD %.2f", result.businessKm, result.totalKm, result.businessShare * 100, result.estimatedDeduction))
                }.onFailure { Text(it.message ?: "Enter readings to calculate") }
                TextButton(onClick = { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(CanadianVehicleExpenses.SOURCE))) }) { Text("CRA calculation guidance") }
                TextButton(enabled = calculation.isSuccess && checked && !busy, onClick = {
                    val (e, result) = calculation.getOrThrow()
                    exportText = CanadianVehicleExpenses.csv(e, result)
                    export.launch("Canadian_Vehicle_Expenses_${e.year}.csv")
                }) { Text("Export this calculation as CSV") }
                if (record != null) TextButton(enabled = !busy, onClick = {
                    busy = true
                    viewModel.deleteVehicleExpenses(record.vehicle, record.year) { result ->
                        busy = false; message = if (result.isSuccess) "Saved calculation deleted" else "Could not delete calculation"
                    }
                }) { Text("Delete saved calculation") }
                message?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(enabled = !busy && checked && calculation.isSuccess, onClick = {
            val e = calculation.getOrThrow().first
            busy = true
            viewModel.saveVehicleExpenses(e) { result ->
                busy = false; message = if (result.isSuccess) "Calculation saved on this device" else result.exceptionOrNull()?.message ?: "Save failed"
            }
        }) { Text(if (busy) "Working…" else "Save") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Close") } })
}
