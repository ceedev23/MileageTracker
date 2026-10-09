package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.LiveTrackingState
import com.example.ui.components.GoogleMapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LiveRouteGreen = Color(0xFF188038)
private val PersonalGreen = Color(0xFF16A34A)
private val BusinessBlue = Color(0xFF2563EB)
private val StopRed = Color(0xFFEF4444)

/**
 * Modern Live Tracking Screen styled to seamlessly match the design language of the rest of the app:
 * - Floating top tracking banner with live pulsing status indicator and crisp rounded card surface
 * - Compact live stats card (Distance & Amount) with high-contrast typography and subtle borders
 * - Elevated bottom sheet container with rounded top corners and drag handle indicator
 * - Start / Destination timeline card with clean markers and dashed connector
 * - Two-segment category toggle pill ([ Personal ] | [ Business ])
 * - Vehicle selector card with icon and chevron
 * - Note input card with edit iconography
 * - Pinned prominent Stop button with icon for quick and safe accessibility while driving
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

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    LaunchedEffect(category, vehicle, note, startPlaceName) {
        onDraftChange(category, vehicle, note, startPlaceName)
    }

    if (showPlaceDialog) {
        NameDialog(
            title = "Start Location",
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
            .background(Color(0xFFF8FAFC))
            .testTag("home_screen_driving_mode")
    ) {
        val mapHeight = when {
            maxHeight < 680.dp -> maxHeight * 0.22f
            maxHeight < 800.dp -> maxHeight * 0.26f
            else -> maxHeight * 0.30f
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // MAP & FLOATING OVERLAYS
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

                // 1. Sleek Floating Top Tracking Banner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("home_tracking_banner")
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = BusinessBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Tracking is starting",
                                        color = Color(0xFF0F172A),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFDCFCE7),
                                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF16A34A).copy(alpha = pulseAlpha))
                                            )
                                            Text(
                                                text = "LIVE",
                                                color = Color(0xFF15803D),
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = "We detected fast movement. Unless you stop,\nwe'll record this as a trip.",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // ELEVATED BOTTOM SHEET / DRAFT DETAILS
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = Color(0xFFF8FAFC),
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Drag Handle Indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Color(0xFFCBD5E1),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Box(modifier = Modifier.size(width = 36.dp, height = 3.5.dp))
                        }
                    }

                    // Trip configuration items
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        // 1. Compact Live Stats Card (Distance & Amount)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_tracking_stats"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "DISTANCE",
                                        color = Color(0xFF64748B),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "$distanceText $unitLabel",
                                        color = Color(0xFF0F172A),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .height(22.dp)
                                        .width(1.dp)
                                        .background(Color(0xFFE2E8F0))
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "AMOUNT",
                                        color = Color(0xFF64748B),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = amountText,
                                        color = if (amount > 0.0) BusinessBlue else Color(0xFF0F172A),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 2. Start & End Location Timeline Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                // Point A (Start)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showPlaceDialog = true }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEFF6FF))
                                            .border(1.5.dp, BusinessBlue, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "A",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BusinessBlue
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = placeTitle,
                                                color = if (startPlaceName != null) Color(0xFF0F172A) else BusinessBlue,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit start location",
                                                tint = Color(0xFF94A3B8),
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                        Text(
                                            text = addressLine,
                                            color = Color(0xFF64748B),
                                            fontSize = 11.5.sp,
                                            lineHeight = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = dateText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = timeText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }

                                // Timeline dashed connector
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(8.dp)
                                    ) {
                                        drawLine(
                                            color = Color(0xFFCBD5E1),
                                            start = Offset(size.width / 2, 0f),
                                            end = Offset(size.width / 2, size.height),
                                            strokeWidth = 2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                        )
                                    }
                                }

                                // Point B (Live Destination)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF1F5F9))
                                            .border(1.5.dp, Color(0xFF94A3B8), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "B",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "We'll add the location once the trip's over",
                                            color = Color(0xFF64748B),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Recording route continuously…",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.5.sp,
                                            lineHeight = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Classification Segmented Pill Toggle
                        val isPersonalSelected = category == "Personal"
                        val isBusinessSelected = category == "Business"

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Personal Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isPersonalSelected) PersonalGreen else Color.Transparent)
                                        .testTag("home_tracking_personal")
                                        .clickable { category = if (isPersonalSelected) null else "Personal" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = null,
                                            tint = if (isPersonalSelected) Color.White else PersonalGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Personal",
                                            fontSize = 13.sp,
                                            fontWeight = if (isPersonalSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPersonalSelected) Color.White else Color(0xFF64748B)
                                        )
                                    }
                                }

                                // Business Pill
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isBusinessSelected) BusinessBlue else Color.Transparent)
                                        .testTag("home_tracking_business")
                                        .clickable { category = if (isBusinessSelected) null else "Business" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BusinessCenter,
                                            contentDescription = null,
                                            tint = if (isBusinessSelected) Color.White else BusinessBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Business",
                                            fontSize = 13.sp,
                                            fontWeight = if (isBusinessSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isBusinessSelected) Color.White else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Vehicle Selector Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showVehicleDialog = true }
                                .testTag("home_tracking_vehicle"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFEFF6FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = BusinessBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Vehicle",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = vehicle,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Choose vehicle",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // 5. Note Input Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                BasicTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF0F172A)
                                    ),
                                    cursorBrush = SolidColor(BusinessBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("home_tracking_note"),
                                    decorationBox = { innerTextField ->
                                        Box {
                                            if (note.isEmpty()) {
                                                Text(
                                                    text = "Write a note",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 13.5.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Pinned Bottom Stop Action
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF8FAFC)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = {
                                    onDraftChange(category, vehicle, note, startPlaceName)
                                    onStop()
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StopRed,
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("home_tracking_stop")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stop",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
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
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BusinessBlue,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedLabelColor = BusinessBlue,
                    unfocusedLabelColor = Color(0xFF64748B)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(value.trim()) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BusinessBlue)
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
            }
        }
    )
}
