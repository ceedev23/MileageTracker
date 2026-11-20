package com.example.ui.screens

import com.example.ui.components.StyledAlertDialog

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
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Route
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.InputCheck
import com.example.service.CanadianMileageEstimate
import com.example.service.LiveTrackingState
import com.example.ui.components.GoogleMapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TrackingBackground = Color(0xFFF7F8FD)
private val TrackingTile = Color(0xFFF1F5FD)
private val TrackingBorder = Color(0xFFE9ECF4)
private val TrackingInk = Color(0xFF101538)
private val TrackingMuted = Color(0xFF535D79)
private val TrackingAccent = Color(0xFF075DDC)
private val TrackingLiveGreen = Color(0xFF0B9A45)
private val PersonalGreen = Color(0xFF16A34A)
private val BusinessBlue = Color(0xFF2563EB)
private val StopRed = Color(0xFFEC2A2E)

@Composable
fun LiveDriveScreen(
    trackingState: LiveTrackingState,
    isMiles: Boolean,
    reimbursementRate: Double,
    vehicleName: String,
    onVehicleChange: (String) -> Unit,
    onStop: () -> Unit,
    onDraftChange: (category: String?, vehicle: String, note: String, startPlaceName: String?) -> Unit,
    modifier: Modifier = Modifier,
    canadianMileageEstimate: CanadianMileageEstimate? = null
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

    val benchmark = canadianMileageEstimate?.forLiveTrip(startedAt, trackingState.distanceKm, category)
    val amount = benchmark ?: if (category == "Personal") 0.0 else displayDistance * reimbursementRate
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
            maxChars = 80,
            initialValue = startPlaceName.orEmpty(),
            onDismiss = { showPlaceDialog = false },
            onSave = {
                startPlaceName = InputCheck.finishLabel(it, 80)
                showPlaceDialog = false
            }
        )
    }

    if (showVehicleDialog) {
        NameDialog(
            title = "Vehicle",
            label = "Vehicle name",
            maxChars = 40,
            initialValue = vehicle,
            onDismiss = { showVehicleDialog = false },
            onSave = {
                val saved = InputCheck.exactLabel(it, 40) ?: vehicle
                vehicle = saved
                onVehicleChange(saved)
                showVehicleDialog = false
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TrackingBackground)
            .testTag("home_screen_driving_mode")
    ) {
        val compact = maxHeight < 680.dp
        val narrow = maxWidth < 360.dp
        val curveDepth = if (compact) 40.dp else 56.dp
        val sheetHeight = maxHeight * (if (compact) 0.48f else 0.43f)
        val curveDepthPx = with(LocalDensity.current) { curveDepth.toPx() }
        val headerShape = remember(curveDepthPx) {
            GenericShape { size, _ ->
                val bottom = size.height
                val shoulder = (bottom - curveDepthPx).coerceAtLeast(0f)
                val controlY = shoulder + (bottom - shoulder) * 0.57f
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, shoulder)
                cubicTo(
                    size.width * 0.90f, controlY,
                    size.width * 0.71f, bottom,
                    size.width / 2f, bottom
                )
                cubicTo(
                    size.width * 0.29f, bottom,
                    size.width * 0.10f, controlY,
                    0f, shoulder
                )
                close()
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy((-12).dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(-curveDepth)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f)
                        .testTag("home_tracking_banner"),
                    shape = headerShape,
                    color = TrackingBackground,
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(top = if (compact) 16.dp else 24.dp, bottom = curveDepth),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(TrackingLiveGreen.copy(alpha = pulseAlpha), CircleShape)
                            )
                            Text(
                                text = "Tracking live",
                                color = TrackingLiveGreen,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (narrow) 16.dp else 20.dp)
                                .testTag("home_tracking_stats"),
                            horizontalArrangement = Arrangement.spacedBy(if (narrow) 9.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackingMetric(
                                label = "Distance",
                                value = "$distanceText $unitLabel",
                                icon = Icons.Outlined.Route,
                                compact = narrow,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(Color(0xFFD5D9E7))
                            )
                            TrackingMetric(
                                label = if (benchmark != null) "Mileage estimate" else "Billing estimate",
                                value = amountText,
                                icon = Icons.Outlined.AccountBalanceWallet,
                                compact = narrow,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                GoogleMapView(
                    points = trackingState.path,
                    isLive = true,
                    speedKmh = trackingState.speedKmh,
                    distanceKm = trackingState.distanceKm,
                    isMiles = isMiles,
                    showChrome = false,
                    routeColor = TrackingAccent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            // Keep the map visible above the scrollable trip details.
            Surface(
                modifier = Modifier
                    .height(sheetHeight)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = TrackingBackground,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Color(0xFFB7BFD1),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Box(modifier = Modifier.size(width = 32.dp, height = 4.dp))
                        }
                    }

                    // Trip configuration items
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
                    ) {
                        // Start and destination
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, TrackingBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 11.dp)
                            ) {
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
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, TrackingAccent, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "A",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TrackingAccent
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
                                                modifier = Modifier.weight(1f, fill = false),
                                                color = if (startPlaceName != null) TrackingInk else TrackingAccent,
                                                fontSize = if (narrow) 13.sp else 15.sp,
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
                                            color = TrackingMuted,
                                            fontSize = 12.sp,
                                            lineHeight = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = dateText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TrackingMuted
                                        )
                                        Text(
                                            text = timeText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TrackingInk
                                        )
                                    }
                                }

                                // Timeline dashed connector
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(9.dp)
                                    ) {
                                        drawLine(
                                            color = Color(0xFFB7BFD1),
                                            start = Offset(size.width / 2, 0f),
                                            end = Offset(size.width / 2, size.height),
                                            strokeWidth = 2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, Color(0xFF94A3B8), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "B",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TrackingMuted
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Destination",
                                            color = TrackingMuted,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 18.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Added when the trip ends",
                                            color = TrackingMuted,
                                            fontSize = 12.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Trip classification
                        val isPersonalSelected = category == "Personal"
                        val isBusinessSelected = category == "Business"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TrackingCategoryButton(
                                label = "Personal",
                                icon = Icons.Default.Home,
                                accent = PersonalGreen,
                                selected = isPersonalSelected,
                                onClick = { category = if (isPersonalSelected) null else "Personal" },
                                modifier = Modifier.weight(1f).testTag("home_tracking_personal")
                            )
                            TrackingCategoryButton(
                                label = "Business",
                                icon = Icons.Default.BusinessCenter,
                                accent = TrackingAccent,
                                selected = isBusinessSelected,
                                onClick = { category = if (isBusinessSelected) null else "Business" },
                                modifier = Modifier.weight(1f).testTag("home_tracking_business")
                            )
                        }

                        // Vehicle selector
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showVehicleDialog = true }
                                .testTag("home_tracking_vehicle"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, TrackingBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TrackingTile),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = TrackingAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "Vehicle",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TrackingMuted
                                        )
                                        Text(
                                            text = vehicle,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TrackingInk
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

                        // Trip note
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, TrackingBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 11.dp),
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
                                        tint = TrackingMuted,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                BasicTextField(
                                    value = note,
                                    onValueChange = { note = InputCheck.keepTyped(it, 160) },
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TrackingInk
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
                        color = TrackingBackground
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(top = 8.dp, bottom = if (compact) 10.dp else 18.dp),
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
                                    .fillMaxWidth(0.49f)
                                    .height(48.dp)
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
                                    fontSize = 19.sp,
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
private fun TrackingMetric(
    label: String,
    value: String,
    icon: ImageVector,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 44.dp)
                .background(TrackingTile, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TrackingAccent,
                modifier = Modifier.size(if (compact) 22.dp else 28.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TrackingMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                color = TrackingInk,
                fontSize = if (compact) 21.sp else 24.sp,
                lineHeight = if (compact) 23.sp else 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TrackingCategoryButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent else TrackingTile)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) Color.White else accent,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else TrackingMuted
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    maxChars: Int,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    StyledAlertDialog(
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
                onValueChange = { value = InputCheck.keepTyped(it, maxChars) },
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
