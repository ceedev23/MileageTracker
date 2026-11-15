package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.VehicleExpenseEntity
import com.example.service.CanadianVehicleExpenses
import com.example.ui.MileageViewModel
import com.example.ui.theme.DriveGreen
import com.example.ui.theme.LightBorder
import com.example.ui.theme.MileageBlueContainer
import com.example.ui.theme.SlateTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

private val ExpenseDialogBlue = DriveGreen
private val ExpenseDialogButtonShape = RoundedCornerShape(14.dp)

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
    var showGuidance by remember { mutableStateOf(false) }
    var operatingItc by remember(vehicle, year) { mutableStateOf("") }
    var directItc by remember(vehicle, year) { mutableStateOf("") }
    var verifiedCca by remember(vehicle, year) { mutableStateOf("") }
    var ccaBasis by remember(vehicle, year) { mutableStateOf("") }
    var ownershipReviewed by remember(vehicle, year) { mutableStateOf(false) }
    var showOdometerHistory by remember { mutableStateOf(false) }
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
    val calculation = runCatching {
        val e = expense()
        fun amount(raw: String) = if (raw.isBlank()) 0.0 else raw.toDoubleOrNull() ?: error("Enter a valid tax adjustment")
        val adjustments = com.example.service.CanadianTaxAdjustments(
            operatingItcCad = amount(operatingItc), directExpenseItcCad = amount(directItc),
            verifiedCcaClaimCad = amount(verifiedCca), ccaClassAndBasis = ccaBasis,
            ownershipPeriodReviewed = ownershipReviewed
        )
        Triple(e, CanadianVehicleExpenses.calculate(e, trips),
            com.example.service.CanadianTaxWorksheet.calculate(e, trips, adjustments))
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            contentColor = SlateTextPrimary
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Canadian vehicle expenses",
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Enter annual expenses in CAD. Required odometer readings help calculate your business-use share.")
                    OutlinedTextField(vehicle, { vehicle = it.take(40); message = null }, label = { Text("Vehicle") }, supportingText = { Text("Use the same vehicle name as your saved trips.") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    val vehicles = (trips.mapNotNull { it.vehicle } + records.map { it.vehicle } + settings.vehicleName).distinct()
                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vehicles.forEach { name ->
                            ExpenseVehicleOption(
                                name = name,
                                selected = name == vehicle,
                                enabled = !busy,
                                onClick = { vehicle = name }
                            )
                        }
                    }
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4); message = null }, label = { Text("Calendar year") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    Text("Opening/closing readings must cover this calendar year, or the vehicle ownership period within it. Use recorded boundary readings; do not substitute the first and last tracked trip.")
                    val annual = year.toIntOrNull()?.let {
                        com.example.service.CraAnnualLogbook.summarize(it, readings, trips).firstOrNull { summary -> summary.vehicle == vehicle }
                    }
                    if (annual != null) {
                        Text("Saved odometer evidence: ${annual.openingKm?.toString() ?: "missing"} km to ${annual.closingKm?.toString() ?: "missing"} km")
                        if (annual.completeYearBoundaries) {
                            Text("Opening and closing year boundary readings found; verify dates and amounts.")
                        } else {
                            Text(
                                "Year boundary evidence is incomplete. Verify and enter actual opening and closing readings before filing.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFF7E3), ExpenseDialogButtonShape)
                                    .padding(12.dp)
                            )
                        }
                        if (annual.unclassifiedTrips > 0) Text("${annual.unclassifiedTrips} trips still require classification. They are excluded from business kilometres.")
                    }

                    ExpenseGuidanceButton(
                        label = if (showOdometerHistory) "Hide saved odometer readings" else "Show saved odometer readings",
                        icon = Icons.Outlined.Speed,
                        trailingIcon = if (showOdometerHistory) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        onClick = { showOdometerHistory = !showOdometerHistory }
                    )
                    if (showOdometerHistory) readings.filter { it.vehicle == vehicle }.take(8).forEach {
                        Text("Recorded ${java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(it.recordedAt))}: ${it.readingKm} km")
                    }
                    val labels = listOf("Opening odometer (km)", "Closing odometer (km)", "Fuel, charging and oil (CAD)",
                        "Maintenance and repairs (CAD)", "Regular insurance (CAD)", "Licence and registration (CAD)",
                        "Interest AFTER applicable limits (CAD)", "Lease costs AFTER applicable limits (CAD)",
                        "Business parking only (CAD)", "Supplementary business insurance only (CAD)")
                    Text("Required: opening and closing odometer readings")
                    Text("Optional: leave an expense blank if it does not apply.")
                    labels.forEachIndexed { i, label ->
                        OutlinedTextField(fields[i], { value ->
                            fields = fields.toMutableList().also { it[i] = value.take(16) }; message = null
                        }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    }
                    Text("T2125 adjustments (CAD)", style = MaterialTheme.typography.titleSmall)
                    Text("Enter only GST/HST input tax credits actually claimed or claimable and attributable to the amounts above. Do not subtract ITCs twice.")
                    OutlinedTextField(operatingItc, { operatingItc = it.take(16) }, label = { Text("Operating-expense ITCs to subtract (CAD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(directItc, { directItc = it.take(16) }, label = { Text("Direct-cost ITCs to subtract (CAD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Text("CCA must be calculated separately from the correct CRA class/UCC schedule. Enter the reviewed BUSINESS-USE CCA amount, not the vehicle purchase price or full depreciation.")
                    OutlinedTextField(verifiedCca, { verifiedCca = it.take(16) }, label = { Text("Verified business-use CCA claim (CAD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ccaBasis, { ccaBasis = it.take(160) }, label = { Text("CCA class, opening UCC and calculation reference") },
                        modifier = Modifier.fillMaxWidth())
                    Row { Checkbox(ownershipReviewed, { ownershipReviewed = it }); Text("I verified the tax-year or ownership-period odometer boundaries") }
                    Text("Passenger vehicle limits vary by acquisition year. For new 2026 passenger vehicles, Class 10.1 cost ceiling is CAD 39,000 before tax; interest ceiling CAD 350/month and lease ceiling CAD 1,100/month before tax are not standalone eligibility tests.")
                    ExpenseGuidanceButton(
                        label = if (showGuidance) "Hide expense limits guidance" else "View expense limits guidance",
                        icon = Icons.Outlined.Description,
                        onClick = { showGuidance = !showGuidance }
                    )
                    if (showGuidance) Text(CanadianVehicleExpenses.LIMITS)
                    val unclassified = trips.count { it.vehicle == vehicle && it.type == null && Calendar.getInstance().apply { timeInMillis = it.startDate }.get(Calendar.YEAR).toString() == year }
                    Text("$unclassified unclassified trips for this vehicle/year are excluded from business kilometres. Trips without a matching vehicle are excluded.")
                    Row { Checkbox(checked, { checked = it }, enabled = !busy); Text("I checked the vehicle, period, CAD amounts, expense eligibility and applicable limits") }
                    calculation.onSuccess { (_, result, tax) ->
                        Text(String.format(Locale.CANADA, "Business %.1f / total %.1f km (%.2f%%). T2125 vehicle expenses after ITCs: CAD %.2f. Separate reviewed CCA: CAD %.2f. Combined worksheet estimate: CAD %.2f", result.businessKm, result.totalKm, result.businessShare * 100, tax.t2125VehicleExpenses, tax.t2125CcaSeparate, tax.combinedEstimate))
                        tax.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }.onFailure { Text(it.message ?: "Enter readings to calculate") }
                    ExpenseGuidanceButton(
                        label = "CRA calculation guidance",
                        icon = Icons.Outlined.Description,
                        onClick = {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(CanadianVehicleExpenses.SOURCE)))
                        }
                    )
                    Button(
                        enabled = calculation.isSuccess && checked && ownershipReviewed && !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = ExpenseDialogButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseDialogBlue, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        onClick = {
                            val (e, result, tax) = calculation.getOrThrow()
                            exportText = CanadianVehicleExpenses.csv(e, result) +
                                "T2125 review,Amount CAD" + System.lineSeparator() +
                                "Vehicle expenses after ITCs,${tax.t2125VehicleExpenses}" + System.lineSeparator() +
                                "Separate verified CCA,${tax.t2125CcaSeparate}" + System.lineSeparator() +
                                "Combined estimate,${tax.combinedEstimate}" + System.lineSeparator()
                            export.launch("Canadian_Vehicle_Expenses_${e.year}.csv")
                        }
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export this calculation as CSV")
                    }
                    if (record != null) TextButton(enabled = !busy, onClick = {
                        busy = true
                        viewModel.deleteVehicleExpenses(record.vehicle, record.year) { result ->
                            busy = false; message = if (result.isSuccess) "Saved calculation deleted" else "Could not delete calculation"
                        }
                    }) { Text("Delete saved calculation") }
                    message?.let { Text(it) }
                }
                HorizontalDivider(color = LightBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        enabled = !busy,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = ExpenseDialogButtonShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateTextPrimary)
                    ) { Text("Close") }
                    Button(
                        enabled = !busy && checked && ownershipReviewed && calculation.isSuccess,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = ExpenseDialogButtonShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseDialogBlue, contentColor = Color.White),
                        onClick = {
                            val e = calculation.getOrThrow().first
                            busy = true
                            viewModel.saveVehicleExpenses(e) { result ->
                                busy = false
                                message = if (result.isSuccess) "Calculation saved on this device" else result.exceptionOrNull()?.message ?: "Save failed"
                            }
                        }
                    ) { Text(if (busy) "Working…" else "Save") }
                }
            }
        }
    }
}

@Composable
private fun ExpenseVehicleOption(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = ExpenseDialogButtonShape,
        color = if (selected) MileageBlueContainer else Color.White,
        contentColor = SlateTextPrimary,
        border = BorderStroke(1.dp, if (selected) ExpenseDialogBlue else LightBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tint = if (enabled) ExpenseDialogBlue else SlateTextPrimary.copy(alpha = 0.38f)
            Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = tint)
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                color = if (enabled) SlateTextPrimary else SlateTextPrimary.copy(alpha = 0.38f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = tint)
        }
    }
}

@Composable
private fun ExpenseGuidanceButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailingIcon: ImageVector? = null
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = ExpenseDialogButtonShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MileageBlueContainer,
            contentColor = ExpenseDialogBlue
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f))
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null)
    }
}
