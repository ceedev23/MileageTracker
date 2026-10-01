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
    onSave: (TripEntity) -> Unit
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
                    onValueChange = { distanceText = it },
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
                    onValueChange = { startAddress = it },
                    label = { Text("Start Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endAddress,
                    onValueChange = { endAddress = it },
                    label = { Text("Destination") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose / Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vehicle,
                    onValueChange = { vehicle = it },
                    label = { Text("Vehicle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawDist = distanceText.toDoubleOrNull() ?: 0.0
                    val distKm = if (isMiles) rawDist / 0.621371 else rawDist
                    val now = System.currentTimeMillis()
                    val result = (trip ?: TripEntity(
                        startDate = now,
                        endDate = now,
                        distanceKm = distKm,
                        type = selectedType
                    )).copy(
                        distanceKm = distKm,
                        type = selectedType,
                        startAddress = startAddress.ifBlank { null },
                        endAddress = endAddress.ifBlank { null },
                        purpose = purpose.ifBlank { null },
                        vehicle = vehicle.ifBlank { null }
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
