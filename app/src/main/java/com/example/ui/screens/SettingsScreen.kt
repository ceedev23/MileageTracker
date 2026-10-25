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
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.data.InputCheck
import com.example.data.supabase.SyncStatus
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
import com.example.ui.components.OdometerDialog
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.MileageBlueContainer
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.StopRed
import java.util.Locale

private const val CRA_RATE_PER_KM = 0.73
private const val IRS_RATE_PER_MILE = 0.76

@Composable
fun SettingsScreen(
    viewModel: MileageViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showOdometerDialog by remember { mutableStateOf(false) }
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

        // Account & Profile Section
        val profile = userProfile
        Card(
            modifier = Modifier.fillMaxWidth().testTag("settings_account_card"),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (profile?.isGuest == false) MileageBlue else Color(0xFF64748B)),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = profile?.takeIf { !it.isGuest }?.name?.firstOrNull()?.uppercase() ?: "G"
                            Text(
                                text = initial,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column {
                            val signedIn = profile != null && !profile.isGuest
                            Text(
                                text = if (signedIn) profile.name else "Guest",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )
                            Text(
                                text = if (signedIn) profile.email else "Not signed in",
                                fontSize = 12.5.sp,
                                color = SlateTextSecondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (profile != null && !profile.isGuest) {
                        OutlinedButton(
                            onClick = { viewModel.showAuthScreen() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Switch Account", fontSize = 12.5.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.signOut() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Text("Sign Out", fontSize = 12.5.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.showAuthScreen() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MileageBlue,
                                contentColor = Color.White
                            ),
                            elevation = null
                        ) {
                            Text("Sign In or Create Account", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        val syncState by viewModel.supabaseSyncState.collectAsState()
        val signedIn = profile != null && !profile.isGuest
        val backupAvailable = syncState.status != SyncStatus.NOT_CONFIGURED
        val (badgeBg, badgeFg, badgeText) = when {
            !backupAvailable -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), "Unavailable")
            !signedIn -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Sign in")
            syncState.status == SyncStatus.SUCCESS -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "Synced")
            syncState.status == SyncStatus.SYNCING -> Triple(Color(0xFFDBEAFE), Color(0xFF1D4ED8), "Syncing")
            syncState.status == SyncStatus.ERROR -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), "Sync failed")
            else -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), "Ready")
        }
        val backupDetail = when {
            !backupAvailable -> "Backup isn't available in this version."
            !signedIn -> "Sign in to back up trips and receipts to your account."
            syncState.status == SyncStatus.ERROR -> "The last backup didn't finish. Trips on this device are unchanged."
            syncState.status == SyncStatus.SUCCESS -> "Trips and receipts are backed up to your account."
            syncState.status == SyncStatus.SYNCING -> "Backing up trips and receipts."
            else -> "Back up trips and receipts to your account."
        }

        Card(
            modifier = Modifier.fillMaxWidth().testTag("settings_cloud_backup_card"),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cloud backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = backupDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )

                if (backupAvailable && signedIn) {
                    Button(
                        onClick = { viewModel.syncWithSupabase() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = syncState.status != SyncStatus.SYNCING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MileageBlue,
                            contentColor = Color.White
                        ),
                        elevation = null
                    ) {
                        Text(
                            text = if (syncState.status == SyncStatus.SYNCING) "Syncing..." else "Sync now",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
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
                                if (settings.reimbursementRate == IRS_RATE_PER_MILE) {
                                    viewModel.setReimbursementRate(CRA_RATE_PER_KM)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        UnitOptionButton(
                            label = "Miles (mi)",
                            isSelected = settings.unit == "mi",
                            onClick = {
                                viewModel.setUnit("mi")
                                if (settings.reimbursementRate == CRA_RATE_PER_KM) {
                                    viewModel.setReimbursementRate(IRS_RATE_PER_MILE)
                                }
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
                            onValueChange = { next ->
                                if (!InputCheck.decimalText(next, 2, 2)) return@OutlinedTextField
                                rateInput = next
                                val parsed = next.toDoubleOrNull() ?: return@OutlinedTextField
                                InputCheck.rate(parsed)?.let { viewModel.setReimbursementRate(it) }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        // Quick Presets
                        OutlinedButton(
                            onClick = {
                                val preset = if (settings.unit == "km") CRA_RATE_PER_KM else IRS_RATE_PER_MILE
                                rateInput = String.format(Locale.US, "%.2f", preset)
                                viewModel.setReimbursementRate(preset)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (settings.unit == "km") "2026 CRA (0.73)" else "2026 IRS (0.76)",
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = if (settings.unit == "km") {
                            "2026 CRA rate for the first 5,000 km. Each kilometre after that is $0.67."
                        } else {
                            "2026 IRS business rate for July 1 through December 31."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextMuted
                    )
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
                        onValueChange = { next ->
                            val typed = InputCheck.keepTyped(next, 40)
                            vehicleInput = typed
                            InputCheck.exactLabel(typed, 40)?.let { viewModel.setVehicleName(it) }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Odometer",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = settings.odometerKm?.let { km ->
                            val shown = if (settings.unit == "mi") km * 0.621371 else km
                            val unit = if (settings.unit == "mi") "mi" else "km"
                            val how = if (settings.odometerSource == "bluetooth") "synced from the car" else "entered manually"
                            "${String.format(Locale.US, "%,.1f", shown)} $unit, $how"
                        } ?: "Not set. Type the dash reading, or read it from a paired adapter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextMuted
                    )
                    OutlinedButton(
                        onClick = { showOdometerDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("settings_odometer"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Update odometer", fontSize = 13.sp)
                    }
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
                        onValueChange = { next ->
                            if (!InputCheck.intText(next, 6)) return@OutlinedTextField
                            goalInput = next
                            val parsed = next.toDoubleOrNull() ?: return@OutlinedTextField
                            InputCheck.annualGoal(parsed)?.let { viewModel.setAnnualGoal(it) }
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
                    text = "Your data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary
                )

                Text(
                    text = "Remove every trip saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )

                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StopRed,
                        contentColor = Color.White
                    ),
                    elevation = null
                ) {
                    Text("Clear all trips", fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }

    if (showOdometerDialog) {
        OdometerDialog(
            viewModel = viewModel,
            onDismiss = { showOdometerDialog = false }
        )
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StopRed,
                        contentColor = Color.White
                    ),
                    elevation = null
                ) {
                    Text("Delete Everything", color = Color.White)
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
