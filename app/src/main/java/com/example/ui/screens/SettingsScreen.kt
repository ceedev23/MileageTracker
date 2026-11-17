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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.InputCheck
import com.example.data.supabase.SyncStatus
import com.example.ui.MileageViewModel
import com.example.ui.components.OdometerDialog
import com.example.ui.components.StyledAlertDialog
import com.example.ui.theme.DriveGreen
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.SlateBorder
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
    val userProfile by viewModel.userProfile.collectAsState()

    var showReceiptBackupConsent by remember { mutableStateOf(false) }
    var photoBackupEnabled by remember(userProfile) { mutableStateOf(viewModel.receiptPhotoBackup.enabled()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deletingAccount by remember { mutableStateOf(false) }
    var accountDeleteError by remember { mutableStateOf<String?>(null) }
    var showOdometerDialog by remember { mutableStateOf(false) }
    var legalDoc by remember { mutableStateOf<LegalDoc?>(null) }
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

    val scrollState = rememberScrollState()
    val openLegal = legalDoc
    if (openLegal != null) {
        LegalScreen(
            doc = openLegal,
            onBack = { legalDoc = null },
            modifier = modifier
        )
        return
    }

    val syncState by viewModel.supabaseSyncState.collectAsState()
    val profile = userProfile?.takeUnless { it.isGuest }
    val backupAvailable = syncState.status != SyncStatus.NOT_CONFIGURED
    val backupStatus = when {
        !backupAvailable -> "Unavailable"
        profile == null -> "Sign in"
        syncState.status == SyncStatus.SUCCESS -> "Synced"
        syncState.status == SyncStatus.SYNCING -> "Syncing"
        syncState.status == SyncStatus.ERROR -> "Sync failed"
        else -> "Ready"
    }
    val odometer = settings.odometerKm?.let { km ->
        val distance = if (settings.unit == "mi") km * 0.621371 else km
        String.format(Locale.US, "%,.1f %s", distance, settings.unit)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F4F8))
    ) {
        Text(
            text = "Settings",
            modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 16.dp)
                .semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = SlateTextPrimary
        )
        HorizontalDivider(color = SlateBorder)

        Column(
            modifier = Modifier.weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            SettingsGroup("Tracking") {
                SettingsRow("Auto-tracking", Icons.Outlined.Navigation) {
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
                            .semantics { contentDescription = "Auto-tracking" }
                    )
                }
                if (settings.autoDetect) {
                    SettingsDivider()
                    SettingsDetailsRow(
                        title = "End trip after stopping",
                        icon = Icons.Outlined.Schedule,
                        value = "${settings.dwellMinutes} min"
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.changeDwell(-1) },
                                modifier = Modifier.testTag("dwell_decrease_button")
                            ) {
                                Icon(Icons.Default.Remove, "Decrease stationary delay", tint = DriveGreen)
                            }
                            Text("${settings.dwellMinutes} min", color = SlateTextPrimary)
                            IconButton(
                                onClick = { viewModel.changeDwell(1) },
                                modifier = Modifier.testTag("dwell_increase_button")
                            ) {
                                Icon(Icons.Default.Add, "Increase stationary delay", tint = DriveGreen)
                            }
                        }
                    }
                }
                SettingsDivider()
                SettingsRow(
                    title = "Odometer readings",
                    icon = Icons.Outlined.Speed,
                    value = odometer,
                    modifier = Modifier.testTag("settings_odometer"),
                    onClick = { showOdometerDialog = true }
                )
            }

            SettingsGroup("Vehicles & reports") {
                SettingsDetailsRow("Vehicle", Icons.Outlined.DirectionsCar) {
                    OutlinedTextField(
                        value = vehicleInput,
                        onValueChange = { next ->
                            val typed = InputCheck.keepTyped(next, 40)
                            vehicleInput = typed
                            InputCheck.exactLabel(typed, 40)?.let { viewModel.setVehicleName(it) }
                        },
                        label = { Text("Default vehicle name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SettingsDivider()
                SettingsDetailsRow("Units & rates", Icons.Outlined.Tune) {
                    SettingsNote("Tax country")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UnitOptionButton("Canada", settings.taxCountry == "CA", { viewModel.setTaxCountry("CA") }, Modifier.weight(1f))
                        UnitOptionButton("United States", settings.taxCountry == "US", { viewModel.setTaxCountry("US") }, Modifier.weight(1f))
                    }
                    SettingsNote("Country sets the default unit. US deduction methods are unavailable.")
                    SettingsNote("Distance unit")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UnitOptionButton("Kilometres (km)", settings.unit == "km", { viewModel.setUnit("km") }, Modifier.weight(1f))
                        UnitOptionButton("Miles (mi)", settings.unit == "mi", { viewModel.setUnit("mi") }, Modifier.weight(1f))
                    }
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { next ->
                            if (!InputCheck.decimalText(next, 2, 2)) return@OutlinedTextField
                            rateInput = next
                            val parsed = next.toDoubleOrNull() ?: return@OutlinedTextField
                            InputCheck.rate(parsed)?.let { viewModel.setReimbursementRate(it) }
                        },
                        label = { Text("Client billing rate ($/${settings.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsNote("Optional rate for client billing only. Review it after changing units or country.")
                }
                SettingsDivider()
                SettingsDetailsRow("Annual driving target", Icons.Outlined.Speed) {
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { next ->
                            if (!InputCheck.intText(next, 6)) return@OutlinedTextField
                            goalInput = next
                            val parsed = next.toDoubleOrNull() ?: return@OutlinedTextField
                            InputCheck.annualGoal(parsed)?.let { viewModel.setAnnualGoal(it) }
                        },
                        label = { Text("Annual target (${settings.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsNote("Home progress resets each calendar year.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10000.0, 15000.0, 20000.0, 25000.0).forEach { preset ->
                            UnitOptionButton(
                                label = "${(preset / 1000).toInt()}k",
                                isSelected = settings.annualGoal == preset,
                                onClick = {
                                    goalInput = String.format(Locale.US, "%.0f", preset)
                                    viewModel.setAnnualGoal(preset)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            SettingsGroup("Account", Modifier.testTag("settings_account_card")) {
                if (profile == null) {
                    SettingsRow(
                        title = "Sign in or create account",
                        icon = Icons.Outlined.Person,
                        onClick = { viewModel.showAuthScreen() }
                    )
                } else {
                    SettingsDetailsRow("Profile", Icons.Outlined.Person) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold, color = SlateTextPrimary)
                        SettingsNote(profile.email)
                        OutlinedButton(onClick = { viewModel.showAuthScreen() }) {
                            Text("Switch account")
                        }
                    }
                }
                SettingsDivider()
                SettingsDetailsRow(
                    title = "Cloud backup",
                    icon = Icons.Outlined.Cloud,
                    value = backupStatus,
                    modifier = Modifier.testTag("settings_cloud_backup_card")
                ) {
                    when {
                        !backupAvailable -> SettingsNote("Cloud backup is unavailable in this version.")
                        profile == null -> SettingsNote("Sign in to back up trips and receipts.")
                        syncState.status == SyncStatus.ERROR -> SettingsNote("Some changes may still need backup. Try syncing again.")
                    }
                    if (profile != null) {
                        val photoBackupAvailable = viewModel.receiptPhotoBackup.available
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Receipt photo backup", modifier = Modifier.weight(1f), color = SlateTextPrimary)
                            Switch(
                                checked = photoBackupEnabled,
                                enabled = photoBackupAvailable && syncState.status != SyncStatus.SYNCING,
                                onCheckedChange = { checked ->
                                    if (checked) showReceiptBackupConsent = true
                                    else {
                                        viewModel.setReceiptPhotoBackup(false)
                                        photoBackupEnabled = false
                                    }
                                },
                                modifier = Modifier.semantics { contentDescription = "Receipt photo backup" }
                            )
                        }
                        if (!photoBackupAvailable) {
                            SettingsNote("Photo backup is unavailable. Photos stay on this device.")
                        } else {
                            SettingsNote("Turning backup off stops photo uploads and restores. To remove a cloud photo, delete its receipt and sync.")
                        }
                    }
                    if (backupAvailable && profile != null) {
                        Button(
                            onClick = { viewModel.syncWithSupabase() },
                            enabled = syncState.status != SyncStatus.SYNCING
                        ) {
                            Text(if (syncState.status == SyncStatus.SYNCING) "Syncing…" else "Sync now")
                        }
                    }
                }
                SettingsDivider()
                SettingsDetailsRow("Import trips", Icons.Outlined.FileDownload) {
                    GoogleMapsTripImport(viewModel)
                }
            }

            SettingsGroup("Data & privacy") {
                SettingsRow(
                    title = "Terms of service",
                    icon = Icons.Outlined.Description,
                    modifier = Modifier.testTag("settings_terms"),
                    onClick = { legalDoc = LegalDoc.TERMS }
                )
                SettingsDivider()
                SettingsRow(
                    title = "Privacy policy",
                    icon = Icons.Outlined.Shield,
                    modifier = Modifier.testTag("settings_privacy"),
                    onClick = { legalDoc = LegalDoc.PRIVACY }
                )
                SettingsDivider()
                SettingsDetailsRow("Other policies", Icons.Outlined.Description, inset = false) {
                    listOf(
                        LegalDoc.REFUNDS to "settings_refunds",
                        LegalDoc.SUBSCRIPTIONS to "settings_subscriptions",
                        LegalDoc.DATA to "settings_data_policy",
                        LegalDoc.LOCATION to "settings_location_policy",
                        LegalDoc.ACCEPTABLE to "settings_acceptable_use",
                        LegalDoc.SECURITY to "settings_security_policy",
                        LegalDoc.COOKIES to "settings_cookies",
                        LegalDoc.DISCLAIMER to "settings_disclaimer"
                    ).forEach { (doc, tag) ->
                        SettingsDivider()
                        SettingsRow(
                            title = doc.title,
                            icon = Icons.Outlined.Description,
                            modifier = Modifier.testTag(tag),
                            onClick = { legalDoc = doc }
                        )
                    }
                }
            }

            SettingsGroup {
                if (profile != null) {
                    SettingsFooterAction("Log out", onClick = { viewModel.signOut() })
                    HorizontalDivider(color = SlateBorder)
                }
                SettingsFooterAction(
                    "Clear all trips",
                    color = MaterialTheme.colorScheme.error,
                    onClick = { showClearDialog = true }
                )
                if (profile != null) {
                    HorizontalDivider(color = SlateBorder)
                    SettingsFooterAction(
                        "Delete account",
                        modifier = Modifier.testTag("settings_delete_account"),
                        color = MaterialTheme.colorScheme.error,
                        onClick = {
                            accountDeleteError = null
                            showDeleteAccountDialog = true
                        }
                    )
                }
                accountDeleteError?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    if (showDeleteAccountDialog) {
        StyledAlertDialog(
            onDismissRequest = { if (!deletingAccount) showDeleteAccountDialog = false },
            title = { Text("Permanently delete account?") },
            text = {
                Text("This permanently deletes your sign-in account and associated cloud records, including synced trips and receipts. This cannot be undone. Local records on this device are not deleted. Export anything you need first. Closing the account is permanent; there is no temporary deactivation option.")
            },
            confirmButton = {
                Button(
                    enabled = !deletingAccount,
                    onClick = {
                        deletingAccount = true
                        viewModel.deleteAccount { result ->
                            deletingAccount = false
                            showDeleteAccountDialog = false
                            accountDeleteError = result.exceptionOrNull()?.message
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StopRed)
                ) { Text(if (deletingAccount) "Deleting..." else "Delete permanently", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !deletingAccount,
                    onClick = { showDeleteAccountDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    if (showOdometerDialog) {
        OdometerDialog(
            viewModel = viewModel,
            onDismiss = { showOdometerDialog = false }
        )
    }

    // Confirmation dialog for clearing all data
    if (showReceiptBackupConsent) {
        StyledAlertDialog(
            onDismissRequest = { showReceiptBackupConsent = false },
            title = { Text("Back up receipt photos?") },
            text = { Text("Upload your receipt photos to private Supabase storage to restore them on another device. Storage region: ${viewModel.receiptPhotoBackup.region}. Supabase and its providers may process information elsewhere under their data processing terms. Only your signed-in account can access photos through the app. Photos may contain personal or payment details. Backup is optional; you can turn it off anytime. Deleting a receipt queues removal of its cloud copy when online. Keep copies for your tax obligations.") },
            confirmButton = { Button(onClick = {
                viewModel.setReceiptPhotoBackup(true)
                photoBackupEnabled = true
                showReceiptBackupConsent = false
            }) { Text("Enable photo backup") } },
            dismissButton = { OutlinedButton(onClick = { showReceiptBackupConsent = false }) { Text("Keep off") } }
        )
    }

    if (showClearDialog) {
        StyledAlertDialog(
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
private fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SlateTextPrimary
            )
        }
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    value: String? = null,
    arrow: ImageVector = Icons.Outlined.KeyboardArrowRight,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val action = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).then(action)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(22.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = SlateTextPrimary
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(arrow, contentDescription = null, tint = DriveGreen, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingsDetailsRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    value: String? = null,
    inset: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = title,
        icon = icon,
        modifier = modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        value = value,
        arrow = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
        onClick = { expanded = !expanded }
    )
    if (expanded) {
        Column(
            modifier = if (inset) Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                else Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (inset) 8.dp else 0.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 50.dp), color = SlateBorder)
}

@Composable
private fun SettingsNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SlateTextSecondary)
}

@Composable
private fun SettingsFooterAction(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = SlateTextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
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
