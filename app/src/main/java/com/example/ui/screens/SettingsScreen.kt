package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.MileageViewModel
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.MileageBlueContainer
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.StopRed
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MileageViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var rateInput by remember(settings.reimbursementRate) {
        mutableStateOf(String.format(Locale.US, "%.2f", settings.reimbursementRate))
    }
    var vehicleInput by remember(settings.vehicleName) {
        mutableStateOf(settings.vehicleName)
    }
    var goalInput by remember(settings.annualGoal) {
        mutableStateOf(String.format(Locale.US, "%.0f", settings.annualGoal))
    }

    var hasBackgroundLocation by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )
            Text(
                text = "Tracking sensitivity, units & tax rates",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary
            )
        }

        // Automatic Detection Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Trip Detection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Record trips automatically when driving movement is detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )
                    }

                    Switch(
                        checked = settings.autoDetect,
                        onCheckedChange = { enabled ->
                            viewModel.setAutoDetect(enabled)
                            if (enabled && !hasBackgroundLocation && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                permissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MileageBlue),
                        modifier = Modifier.testTag("auto_detect_switch")
                    )
                }

                if (settings.autoDetect) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SlateBorder)
                    )

                    // Dwell time stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "End trip after stopping",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SlateTextPrimary
                            )
                            Text(
                                text = "Stationary delay before completing trip",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextMuted
                            )
                        }

                        // Stepper UI [ - ]  5 min  [ + ]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateSurfaceVariant)
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { viewModel.changeDwell(-1) }
                                    .testTag("dwell_decrease_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease stationary delay",
                                    tint = MileageBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            Text(
                                text = "${settings.dwellMinutes} min",
                                modifier = Modifier.padding(horizontal = 6.dp),
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary,
                                fontSize = 13.sp
                            )

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { viewModel.changeDwell(1) }
                                    .testTag("dwell_increase_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase stationary delay",
                                    tint = MileageBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Units and Rates Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Units & Rates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary
                )

                // Distance Unit
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Distance Unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UnitOptionButton(
                            label = "Kilometers (km)",
                            isSelected = settings.unit == "km",
                            onClick = {
                                viewModel.setUnit("km")
                                if (settings.reimbursementRate == 0.67) viewModel.setReimbursementRate(0.70)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        UnitOptionButton(
                            label = "Miles (mi)",
                            isSelected = settings.unit == "mi",
                            onClick = {
                                viewModel.setUnit("mi")
                                if (settings.reimbursementRate == 0.70) viewModel.setReimbursementRate(0.67)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Reimbursement Rate
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tax Reimbursement Rate ($ / ${settings.unit})",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = rateInput,
                            onValueChange = {
                                rateInput = it
                                it.toDoubleOrNull()?.let { r -> viewModel.setReimbursementRate(r) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        // Quick Presets
                        OutlinedButton(
                            onClick = {
                                val preset = if (settings.unit == "km") 0.70 else 0.67
                                rateInput = String.format(Locale.US, "%.2f", preset)
                                viewModel.setReimbursementRate(preset)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (settings.unit == "km") "2024 CRA (0.70)" else "2024 IRS (0.67)", fontSize = 12.sp)
                        }
                    }
                }

                // Vehicle Name
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Default Vehicle Name",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = vehicleInput,
                        onValueChange = {
                            vehicleInput = it
                            viewModel.setVehicleName(it)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Annual Driving Target
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Annual Driving Target (${settings.unit})",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = {
                            goalInput = it
                            it.toDoubleOrNull()?.let { g -> viewModel.setAnnualGoal(g) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        supportingText = {
                            Text("Progress bar on Home resets automatically at the end of every year (Dec 31)")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Goal Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10000.0, 15000.0, 20000.0, 25000.0).forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    goalInput = String.format(Locale.US, "%.0f", preset)
                                    viewModel.setAnnualGoal(preset)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "${(preset / 1000).toInt()}k",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Data Management Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Data & Testing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.seedSampleTrips() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Seed Sample Trips", fontSize = 13.sp)
                    }

                    Button(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StopRed)
                    ) {
                        Text("Clear All Trips", fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // Confirmation dialog for clearing all data
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StopRed) },
            title = { Text("Clear All Trips?") },
            text = { Text("This will permanently delete all recorded and manual trips from the database.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTrips()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StopRed)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UnitOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MileageBlue else Color.Transparent)
            .border(
                1.5.dp,
                if (isSelected) MileageBlue else SlateBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else SlateTextSecondary
        )
    }
}
