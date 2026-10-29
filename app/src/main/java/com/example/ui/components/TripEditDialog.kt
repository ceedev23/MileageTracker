package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.InputCheck
import com.example.data.TripEntity
import com.example.ui.theme.BusinessBg
import com.example.ui.theme.BusinessBlue
import com.example.ui.theme.BusinessBorder
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.PersonalBg
import com.example.ui.theme.PersonalBorder
import com.example.ui.theme.PersonalOrange
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.UnclassifiedAmber
import com.example.ui.theme.UnclassifiedBg

@Composable
fun TripEditDialog(
    trip: TripEntity?,
    isMiles: Boolean,
    onDismiss: () -> Unit,
    onSave: (TripEntity) -> Unit,
    workProfile: com.example.data.WorkProfile = com.example.data.WorkProfile.GENERAL
) {
    val initialDist = if (trip != null) {
        val d = if (isMiles) trip.distanceKm * 0.621371 else trip.distanceKm
        String.format("%.1f", d)
    } else {
        ""
    }

    var distanceText by remember { mutableStateOf(initialDist) }
    var selectedType by remember { mutableStateOf(trip?.type) } // "Business", "Personal", or null
    var startAddress by remember { mutableStateOf(trip?.startAddress ?: "") }
    var endAddress by remember { mutableStateOf(trip?.endAddress ?: "") }
    var purpose by remember { mutableStateOf(trip?.purpose ?: "") }
    var vehicle by remember { mutableStateOf(trip?.vehicle ?: "Primary Vehicle") }
    var formError by remember { mutableStateOf<String?>(null) }

    val unitLabel = if (isMiles) "mi" else "km"

    val routePoints = remember(trip?.pathJson) {
        val jsonStr = trip?.pathJson
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = org.json.JSONArray(jsonStr)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    com.example.data.LatLngPoint(
                        latitude = obj.getDouble("lat"),
                        longitude = obj.getDouble("lng"),
                        timestamp = obj.optLong("t", 0L),
                        speedKmh = obj.optDouble("s", 0.0).toFloat()
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (trip == null) "Log Manual Trip" else "Edit Trip",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (routePoints.isNotEmpty()) {
                    Text(
                        text = "Trip Route Map",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    GoogleMapView(
                        points = routePoints,
                        isLive = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )
                }

                // Distance input
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = {
                        if (InputCheck.decimalText(it, 5, 1)) distanceText = it
                        formError = null
                    },
                    label = { Text("Distance ($unitLabel)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Classification selection
                Text(
                    text = "Classification",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypeSelectorChip(
                        label = "💼 Business",
                        isSelected = selectedType == "Business",
                        selectedBg = MileageBlue,
                        selectedBorder = MileageBlue,
                        selectedColor = Color.White,
                        onClick = { selectedType = "Business" },
                        modifier = Modifier.weight(1f)
                    )

                    TypeSelectorChip(
                        label = "🏠 Personal",
                        isSelected = selectedType == "Personal",
                        selectedBg = PersonalOrange,
                        selectedBorder = PersonalOrange,
                        selectedColor = Color.White,
                        onClick = { selectedType = "Personal" },
                        modifier = Modifier.weight(1f)
                    )

                    TypeSelectorChip(
                        label = "Pending",
                        isSelected = selectedType == null,
                        selectedBg = UnclassifiedAmber,
                        selectedBorder = UnclassifiedAmber,
                        selectedColor = Color.White,
                        onClick = { selectedType = null },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Locations
                OutlinedTextField(
                    value = startAddress,
                    onValueChange = {
                        startAddress = InputCheck.keepTyped(it, 120)
                        formError = null
                    },
                    label = { Text("Start Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endAddress,
                    onValueChange = {
                        endAddress = InputCheck.keepTyped(it, 120)
                        formError = null
                    },
                    label = { Text("Destination") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = purpose,
                    onValueChange = {
                        purpose = InputCheck.keepTyped(it, 160)
                        formError = null
                    },
                    label = { Text(workProfile.purposeLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(workProfile.purposeHint, style = MaterialTheme.typography.bodySmall)
                if (purpose.isBlank()) {
                    workProfile.quickPurposes.forEach { suggestion ->
                        androidx.compose.material3.TextButton(onClick = { purpose = suggestion }) { Text(suggestion) }
                    }
                }

                OutlinedTextField(
                    value = vehicle,
                    onValueChange = {
                        vehicle = InputCheck.keepTyped(it, 40)
                        formError = null
                    },
                    label = { Text("Vehicle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                formError?.let { message ->
                    Text(text = message, color = Color(0xFFDC2626), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawDist = distanceText.toDoubleOrNull()
                    val distKm = rawDist?.let { if (isMiles) it / 0.621371 else it }
                    if (distKm == null || InputCheck.tripKm(distKm) == null) {
                        formError = "Enter a distance greater than 0, up to 10,000 km."
                        return@Button
                    }
                    if (selectedType != null && selectedType != "Business" && selectedType != "Personal") {
                        formError = "Choose Business, Personal, or leave the trip pending."
                        return@Button
                    }
                    val start = labelOrBlank(startAddress, 120)
                    val end = labelOrBlank(endAddress, 120)
                    val note = labelOrBlank(purpose, 160)
                    val car = labelOrBlank(vehicle, 40)
                    if (start == INVALID || end == INVALID || note == INVALID || car == INVALID) {
                        formError = "Use letters, numbers, and simple punctuation."
                        return@Button
                    }
                    val now = System.currentTimeMillis()
                    val result = (trip ?: TripEntity(
                        startDate = now,
                        endDate = now,
                        distanceKm = distKm,
                        type = selectedType
                    )).copy(
                        distanceKm = distKm,
                        type = selectedType,
                        startAddress = start,
                        endAddress = end,
                        purpose = note,
                        vehicle = car
                    )
                    onSave(result)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MileageBlue,
                    contentColor = Color.White
                ),
                elevation = null
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private const val INVALID = "\u0000"

private fun labelOrBlank(raw: String, max: Int): String? {
    if (raw.isBlank()) return null
    return InputCheck.exactLabel(raw, max) ?: INVALID
}

@Composable
private fun TypeSelectorChip(
    label: String,
    isSelected: Boolean,
    selectedBg: Color,
    selectedBorder: Color,
    selectedColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) selectedBg else Color.Transparent)
            .border(
                1.5.dp,
                if (isSelected) selectedBorder else SlateBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) selectedColor else SlateTextSecondary
        )
    }
}
