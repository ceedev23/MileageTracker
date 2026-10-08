package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.LiveTrackingState
import com.example.ui.components.GoogleMapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LiveRouteGreen = Color(0xFF188038)
private val PersonalRed = Color(0xFFE53935)
private val BusinessBlue = Color(0xFF2563EB)
private val RowMuted = Color(0xFF9CA3AF)
private val AddressGray = Color(0xFF6B7280)
private val DividerColor = Color(0xFFE6E6E6)

/**
 * Live tracking screen shown as soon as movement is detected.
 * Replaces the previous "trip in progress" home layout.
 */
@Composable
fun LiveDriveScreen(
    trackingState: LiveTrackingState,
    isMiles: Boolean,
    reimbursementRate: Double,
    vehicleName: String,
    onVehicleChange: (String) -> Unit,
    onStop: () -> Unit,
    onDraftChange: (category: String?, vehicle: String, note: String, startPlaceName: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val unitLabel = if (isMiles) "mi" else "km"
    val displayDistance = if (isMiles) trackingState.distanceKm * 0.621371 else trackingState.distanceKm
    val distanceText = String.format(Locale.US, "%.2f", displayDistance)

    var category by remember { mutableStateOf<String?>(null) }
    var vehicle by remember(vehicleName) { mutableStateOf(vehicleName.ifBlank { "Toyota Rav4" }) }
    var note by remember { mutableStateOf("") }
    var startPlaceName by remember { mutableStateOf<String?>(null) }
    var showPlaceDialog by remember { mutableStateOf(false) }
    var showVehicleDialog by remember { mutableStateOf(false) }
    val fallbackStart by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val startedAt = trackingState.tripStartedAtMs.takeIf { it > 0L }
        ?: trackingState.path.firstOrNull()?.timestamp?.takeIf { it > 0L }
        ?: fallbackStart
    val dateText = remember(startedAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(startedAt))
    }
    val timeText = remember(startedAt) {
        SimpleDateFormat("h:mm a", Locale.US).format(Date(startedAt))
    }

    val amount = if (category == "Business") displayDistance * reimbursementRate else 0.0
    val amountText = if (amount < 0.005) "$0" else String.format(Locale.US, "$%.2f", amount)
    val addressLine = trackingState.startAddress?.takeIf { it.isNotBlank() } ?: "Locating start address…"
    val placeTitle = startPlaceName?.takeIf { it.isNotBlank() } ?: "Select a location"

    LaunchedEffect(category, vehicle, note, startPlaceName) {
        onDraftChange(category, vehicle, note, startPlaceName)
    }

    if (showPlaceDialog) {
        NameDialog(
            title = "Start location",
            label = "Place name",
            initialValue = startPlaceName.orEmpty(),
            onDismiss = { showPlaceDialog = false },
            onSave = {
                startPlaceName = it.ifBlank { null }
                showPlaceDialog = false
            }
        )
    }
    if (showVehicleDialog) {
        NameDialog(
            title = "Vehicle",
            label = "Vehicle name",
            initialValue = vehicle,
            onDismiss = { showVehicleDialog = false },
            onSave = {
                val saved = it.ifBlank { vehicle }
                vehicle = saved
                onVehicleChange(saved)
                showVehicleDialog = false
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("home_screen_driving_mode")
    ) {
        val mapHeight = maxHeight * 0.40f
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeight)
            ) {
                GoogleMapView(
                    points = trackingState.path,
                    isLive = true,
                    speedKmh = trackingState.speedKmh,
                    distanceKm = trackingState.distanceKm,
                    isMiles = isMiles,
                    showChrome = false,
                    routeColor = LiveRouteGreen,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .testTag("home_tracking_banner")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(22.dp)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Tracking is starting",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "We detected fast movement. Unless you stop,\nwe'll record this as a trip.",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 16.dp)
                        .background(Color(0xE6323232), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("home_tracking_stats"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Distance",
                        color = Color(0xFFD1D5DB),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$distanceText $unitLabel",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Amount",
                        color = Color(0xFFD1D5DB),
                        fontSize = 12.sp
                    )
                    Text(
                        text = amountText,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                LocationRow(
                    marker = "A",
                    title = placeTitle,
                    titleColor = LiveRouteGreen,
                    subtitle = addressLine,
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = dateText,
                                fontSize = 13.sp,
                                color = Color(0xFF374151)
                            )
                            Text(
                                text = timeText,
                                fontSize = 13.sp,
                                color = Color(0xFF374151)
                            )
                        }
                    },
                    onClick = { showPlaceDialog = true }
                )
                HorizontalDivider(color = DividerColor, thickness = 1.dp)
                LocationRow(
                    marker = "B",
                    title = "We'll add the location once the trip's over",
                    titleColor = AddressGray,
                    subtitle = null,
                    trailing = null,
                    onClick = null
                )
                HorizontalDivider(color = DividerColor, thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryChoice(
                        label = "Personal",
                        selected = category == "Personal",
                        selectedColor = PersonalRed,
                        icon = Icons.Default.Home,
                        onClick = { category = if (category == "Personal") null else "Personal" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_tracking_personal")
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(DividerColor)
                    )
                    CategoryChoice(
                        label = "Business",
                        selected = category == "Business",
                        selectedColor = BusinessBlue,
                        icon = Icons.Default.BusinessCenter,
                        onClick = { category = if (category == "Business") null else "Business" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_tracking_business")
                    )
                }
                HorizontalDivider(color = DividerColor, thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showVehicleDialog = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .testTag("home_tracking_vehicle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = vehicle,
                        fontSize = 16.sp,
                        color = Color(0xFF111827)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Choose vehicle",
                        tint = RowMuted
                    )
                }
                HorizontalDivider(color = DividerColor, thickness = 1.dp)

                Spacer(modifier = Modifier.height(14.dp))
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = Color(0xFF111827)
                    ),
                    cursorBrush = SolidColor(BusinessBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, DividerColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                        .testTag("home_tracking_note"),
                    decorationBox = { inner ->
                        Box {
                            if (note.isEmpty()) {
                                Text(
                                    text = "Write a note",
                                    color = RowMuted,
                                    fontSize = 15.sp
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp, top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedButton(
                    onClick = {
                        onDraftChange(category, vehicle, note, startPlaceName)
                        onStop()
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFE57373)),
                    modifier = Modifier
                        .width(188.dp)
                        .height(48.dp)
                        .testTag("home_tracking_stop")
                ) {
                    Text(
                        text = "Stop",
                        color = Color(0xFFE57373),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationRow(
    marker: String,
    title: String,
    titleColor: Color,
    subtitle: String?,
    trailing: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(28.dp)
                .border(1.5.dp, Color(0xFFD0D0D0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = marker,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = RowMuted
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AddressGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun CategoryChoice(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) selectedColor else RowMuted
    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }) {
                Text("Save", fontWeight = FontWeight.Bold, color = BusinessBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
