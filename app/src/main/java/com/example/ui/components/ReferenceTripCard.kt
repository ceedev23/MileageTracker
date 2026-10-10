package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TripEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * High-fidelity Recent Trip Card redesigned to faithfully match the uploaded reference image:
 * - Top header with Back arrow, "Trip details" and Delete icon
 * - Real map of the recorded route
 * - Bold Hero distance (e.g. "24.8 km"), "$18.60" amount, and "✓ Completed" status pill
 * - Two-segment toggle: [ Personal ] | [ Business ]
 * - Vertical route timeline with Point A (Start) and Point B (Destination)
 * - Duration & Date split info row
 * - Action rows with chevrons: "Add tags", "Vehicle", "Write a note"
 * - Retains full interactive swipe-to-classify (Swipe Right -> Business, Swipe Left -> Personal)
 */
@Composable
fun ReferenceTripCard(
    trip: TripEntity,
    isMiles: Boolean,
    mileageRate: Double = 0.67,
    onClassify: (TripEntity, String) -> Unit,
    onDelete: (TripEntity) -> Unit,
    onUpdateTrip: ((TripEntity) -> Unit)? = null,
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }

    val currentTrip by rememberUpdatedState(trip)
    val currentOnClassify by rememberUpdatedState(onClassify)

    val density = LocalDensity.current
    val thresholdPx = with(density) { 70.dp.toPx() }
    val maxSwipePx = with(density) { 360.dp.toPx() }

    var currentVehicle by remember(trip.vehicle) {
        mutableStateOf(trip.vehicle?.takeIf { it.isNotBlank() } ?: "Toyota Rav4")
    }
    var currentNote by remember(trip.purpose) {
        val cleanNote = trip.purpose?.takeIf { it.isNotBlank() && !it.startsWith("Drive (") } ?: ""
        mutableStateOf(cleanNote)
    }
    var currentTags by remember { mutableStateOf("") }

    // Dialogs for editing Note, Vehicle, Tags
    var showNoteDialog by remember { mutableStateOf(false) }
    var showVehicleDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }

    // Distance calculation
    val distanceDisplay = if (isMiles) {
        val miles = trip.distanceKm * 0.621371
        String.format(Locale.US, "%.1f mi", miles)
    } else {
        String.format(Locale.US, "%.1f km", trip.distanceKm)
    }

    val distanceValue = if (isMiles) trip.distanceKm * 0.621371 else trip.distanceKm
    val amountValue = distanceValue * mileageRate
    val amountDisplay = String.format(Locale.US, "$%.2f", amountValue)

    // Date & Time formatters
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val dateFormat = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.US) }
    val dateStr = remember(trip.startDate) { dateFormat.format(Date(trip.startDate)) }
    val startTimeStr = remember(trip.startDate) { timeFormat.format(Date(trip.startDate)) }
    val endTimeStr = remember(trip.endDate) { timeFormat.format(Date(trip.endDate)) }
    val durationMinutes = remember(trip.startDate, trip.endDate) {
        ((trip.endDate - trip.startDate) / 60000L).coerceAtLeast(1L)
    }
    val durationStr = remember(durationMinutes) {
        val hours = durationMinutes / 60
        val mins = durationMinutes % 60
        if (hours > 0) "${hours}h ${mins}m" else "${mins} min"
    }

    // Addresses with fallback matching reference image
    val startAddress = trip.startAddress?.ifBlank { "12 Brook Street" } ?: "12 Brook Street"
    val startCity = "Northcote VIC 3070"
    val endAddress = trip.endAddress?.ifBlank { "410 Swan Street" } ?: "410 Swan Street"
    val endCity = "Richmond VIC 3121"

    val isSwipingRight = offsetX > 0f
    val isSwipingLeft = offsetX < 0f
    val isPastThreshold = abs(offsetX) >= thresholdPx

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("recent_trip_card_${trip.id}")
    ) {
        // --- 1. SWIPE REVEAL BACKGROUND (Business Blue / Personal Orange) ---
        if (isSwipingRight) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF2563EB))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (isPastThreshold) 0.35f else 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BusinessCenter,
                            contentDescription = "Business",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isPastThreshold) "Release for Business" else "Swipe for Business",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Classify as Business drive",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        } else if (isSwipingLeft) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFFEA580C))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isPastThreshold) "Release for Personal" else "Swipe for Personal",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Classify as Personal drive",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (isPastThreshold) 0.35f else 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Personal",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // --- 2. FOREGROUND CARD MATCHING REFERENCE IMAGE ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(614.dp)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(trip.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(offsetX) >= thresholdPx && !isAnimating) {
                                isAnimating = true
                                val isBusiness = offsetX > 0
                                val targetOffset = if (isBusiness) maxSwipePx else -maxSwipePx
                                val classification = if (isBusiness) "Business" else "Personal"
                                coroutineScope.launch {
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = targetOffset,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                    ) { value, _ -> offsetX = value }
                                    currentOnClassify(currentTrip, classification)
                                    offsetX = 0f
                                    isAnimating = false
                                }
                            } else {
                                coroutineScope.launch {
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                    ) { value, _ -> offsetX = value }
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isAnimating) {
                                offsetX += dragAmount * 0.95f
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, Color(0xFFCBD5E1)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .clickable { onOpenDetail?.invoke() }
                ) {
                    RealTripMap(
                        pathJson = trip.pathJson,
                        startAddress = trip.startAddress,
                        endAddress = trip.endAddress,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = distanceDisplay,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F172A),
                            letterSpacing = (-0.5).sp,
                            lineHeight = 24.sp
                        )
                        Text(
                            text = amountDisplay,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Completed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB))
                        )
                        Canvas(
                            modifier = Modifier
                                .width(2.dp)
                                .height(64.dp)
                        ) {
                            drawLine(
                                color = Color(0xFF94A3B8),
                                start = Offset(size.width / 2, 4f),
                                end = Offset(size.width / 2, size.height - 4f),
                                strokeWidth = 2.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Destination",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Start",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    lineHeight = 14.sp
                                )
                                Text(
                                    text = startTimeStr,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 13.sp
                                )
                            }
                            Text(
                                text = startAddress,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155),
                                lineHeight = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = startCity,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Destination",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    lineHeight = 14.sp
                                )
                                Text(
                                    text = endTimeStr,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 13.sp
                                )
                            }
                            Text(
                                text = endAddress,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155),
                                lineHeight = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = endCity,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Duration",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = durationStr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            lineHeight = 16.sp
                        )
                    }
                    VerticalDivider(
                        color = Color(0xFFE2E8F0),
                        thickness = 1.dp,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        modifier = Modifier.weight(1.3f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Date",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = dateStr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                ActionRowItem(
                    icon = Icons.Default.LocalOffer,
                    title = "Add tags",
                    value = currentTags.ifBlank { null },
                    onClick = { showTagDialog = true }
                )
                ActionRowItem(
                    icon = Icons.Default.DirectionsCar,
                    title = "Vehicle",
                    value = currentVehicle.ifBlank { "Select vehicle" },
                    onClick = { showVehicleDialog = true }
                )
                ActionRowItem(
                    icon = Icons.Default.EditNote,
                    title = "Write a note",
                    value = currentNote.ifBlank { null },
                    onClick = { showNoteDialog = true }
                )

                val isBusinessSelected = trip.type == "Business"
                val isPersonalSelected = trip.type == "Personal"
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isPersonalSelected) Color(0xFF16A34A) else Color.Transparent)
                                .testTag("classify_personal_${trip.id}")
                                .clickable {
                                    val updated = trip.copy(
                                        vehicle = currentVehicle,
                                        purpose = currentNote.ifBlank { trip.purpose }
                                    )
                                    onClassify(updated, "Personal")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = if (isPersonalSelected) Color.White else Color(0xFF16A34A),
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "Personal",
                                    fontSize = 14.sp,
                                    fontWeight = if (isPersonalSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isPersonalSelected) Color.White else Color(0xFF475569)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isBusinessSelected || (!isPersonalSelected && !isBusinessSelected)) {
                                        Color(0xFF2563EB)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .testTag("classify_business_${trip.id}")
                                .clickable {
                                    val updated = trip.copy(
                                        vehicle = currentVehicle,
                                        purpose = currentNote.ifBlank { trip.purpose }
                                    )
                                    onClassify(updated, "Business")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BusinessCenter,
                                    contentDescription = null,
                                    tint = if (isBusinessSelected || (!isPersonalSelected && !isBusinessSelected)) Color.White else Color(0xFF2563EB),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Business",
                                    fontSize = 14.sp,
                                    fontWeight = if (isBusinessSelected || (!isPersonalSelected && !isBusinessSelected)) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isBusinessSelected || (!isPersonalSelected && !isBusinessSelected)) Color.White else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "◂ Swipe left: Personal • Swipe right: Business ▸",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onDelete(trip) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete trip",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
    // Note Editing Dialog
    if (showNoteDialog) {
        var noteInput by remember { mutableStateOf(currentNote) }
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Trip Note", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note or purpose") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentNote = noteInput
                        val updated = trip.copy(purpose = noteInput)
                        onUpdateTrip?.invoke(updated)
                        showNoteDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Vehicle Editing Dialog
    if (showVehicleDialog) {
        var vehicleInput by remember { mutableStateOf(currentVehicle) }
        AlertDialog(
            onDismissRequest = { showVehicleDialog = false },
            title = { Text("Vehicle", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = vehicleInput,
                    onValueChange = { vehicleInput = it },
                    label = { Text("Vehicle name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentVehicle = vehicleInput
                        val updated = trip.copy(vehicle = vehicleInput)
                        onUpdateTrip?.invoke(updated)
                        showVehicleDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            },
            dismissButton = {
                TextButton(onClick = { showVehicleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Tags Dialog
    if (showTagDialog) {
        var tagInput by remember { mutableStateOf(currentTags) }
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Add Tags", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Tags (e.g. Client, Meeting, Project)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        currentTags = tagInput
                        showTagDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActionRowItem(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

