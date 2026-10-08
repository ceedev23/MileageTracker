package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LatLngPoint
import com.example.data.TripEntity
import com.example.ui.theme.BusinessBg
import com.example.ui.theme.BusinessBlue
import com.example.ui.theme.BusinessBorder
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.MileageBlueContainer
import com.example.ui.theme.PersonalBg
import com.example.ui.theme.PersonalBorder
import com.example.ui.theme.PersonalOrange
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.UnclassifiedAmber
import com.example.ui.theme.UnclassifiedBg
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableTripCard(
    trip: TripEntity,
    isMiles: Boolean,
    reimbursementRate: Double = 0.70,
    onClassify: (TripEntity, String) -> Unit,
    onEdit: (TripEntity) -> Unit,
    onDelete: (TripEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var isExpanded by rememberSaveable(trip.id) { mutableStateOf(false) }

    val isClassified = trip.type != null
    val currentTrip by rememberUpdatedState(trip)
    val currentOnClassify by rememberUpdatedState(onClassify)

    val density = LocalDensity.current
    val thresholdPx = with(density) { 60.dp.toPx() }

    val distanceValue = if (isMiles) trip.distanceKm * 0.621371 else trip.distanceKm
    val unitLabel = if (isMiles) "mi" else "km"
    val distanceDisplay = String.format(Locale.getDefault(), "%.1f %s", distanceValue, unitLabel)
    val amountValue = distanceValue * reimbursementRate
    val amountDisplay = String.format(Locale.getDefault(), "$%.2f", amountValue)

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val fullDateFormat = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()) }

    val startTimeText = remember(trip.startDate) { timeFormat.format(Date(trip.startDate)) }
    val endTimeText = remember(trip.endDate) { timeFormat.format(Date(trip.endDate)) }
    val fullDateText = remember(trip.startDate) { fullDateFormat.format(Date(trip.startDate)) }
    val shortDateText = remember(trip.startDate) { shortDateFormat.format(Date(trip.startDate)) }

    val durationMinutes = remember(trip.startDate, trip.endDate) {
        val diffMs = (trip.endDate - trip.startDate).coerceAtLeast(60000L)
        val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        if (mins < 1) 1 else mins
    }
    val durationText = remember(durationMinutes) {
        val hours = durationMinutes / 60
        val mins = durationMinutes % 60
        if (hours > 0) "${hours}h ${mins}m" else "${mins} min"
    }

    // Parse route points if available
    val routePoints = remember(trip.pathJson) {
        val jsonStr = trip.pathJson
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    LatLngPoint(
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        // Swipe Reveal Backgrounds: ONLY active for unclassified trips
        if (!isClassified && !isExpanded) {
            val isSwipingRight = offsetX > 0f
            val isSwipingLeft = offsetX < 0f
            val isPastThreshold = abs(offsetX) >= thresholdPx

            if (isSwipingRight) {
                // Background reveal for Business (Left side -> swipe right)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(if (isPastThreshold) Color(0xFF1D4ED8) else MileageBlue)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BusinessCenter,
                            contentDescription = "Save as Business",
                            tint = Color.White,
                            modifier = Modifier.size(if (isPastThreshold) 28.dp else 24.dp)
                        )
                        Text(
                            text = if (isPastThreshold) "Release for Business ✓" else "Swipe for Business",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = if (isPastThreshold) 16.sp else 15.sp
                        )
                    }
                }
            } else if (isSwipingLeft) {
                // Background reveal for Personal (Right side -> swipe left)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(if (isPastThreshold) Color(0xFFD97706) else PersonalOrange)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isPastThreshold) "Release for Personal ✓" else "Swipe for Personal",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = if (isPastThreshold) 16.sp else 15.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Save as Personal",
                            tint = Color.White,
                            modifier = Modifier.size(if (isPastThreshold) 28.dp else 24.dp)
                        )
                    }
                }
            }
        }

        // Front interactive Card:
        // - For classified trips: SWIPING IS DISABLED. Clickable toggles expansion.
        // - For unclassified trips: Swiping classifies (when collapsed). Clickable toggles expansion.
        val dragModifier = if (!isClassified && !isExpanded) {
            Modifier.pointerInput(trip.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            isAnimating = true
                            val width = size.width.toFloat()
                            if (offsetX > thresholdPx) {
                                // Swipe right = Business
                                currentOnClassify(currentTrip, "Business")
                                animate(
                                    initialValue = offsetX,
                                    targetValue = width,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                ) { value, _ -> offsetX = value }
                                offsetX = 0f
                            } else if (offsetX < -thresholdPx) {
                                // Swipe left = Personal
                                currentOnClassify(currentTrip, "Personal")
                                animate(
                                    initialValue = offsetX,
                                    targetValue = -width,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                ) { value, _ -> offsetX = value }
                                offsetX = 0f
                            } else {
                                // Snap back
                                animate(
                                    initialValue = offsetX,
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                ) { value, _ -> offsetX = value }
                            }
                            isAnimating = false
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            isAnimating = true
                            animate(
                                initialValue = offsetX,
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            ) { value, _ -> offsetX = value }
                            isAnimating = false
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!isAnimating) {
                            change.consume()
                            offsetX += dragAmount
                        }
                    }
                )
            }
        } else {
            Modifier
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(if (!isClassified && !isExpanded) offsetX.roundToInt() else 0, 0) }
                .clip(RoundedCornerShape(16.dp))
                .clickable { isExpanded = !isExpanded }
                .then(dragModifier)
                .animateContentSize()
                .testTag("trip_card_${trip.id}"),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = if (isExpanded) 3.dp else 1.dp,
            border = BorderStroke(
                if (isExpanded) 1.5.dp else 1.dp,
                if (isExpanded) MileageBlue.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row: Date & Classification Badge & Expand Chevron
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shortDateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Classification Badge
                        when (trip.type) {
                            "Business" -> {
                                BadgeItem(
                                    label = "💼 Business",
                                    bgColor = BusinessBg,
                                    borderColor = BusinessBorder,
                                    textColor = BusinessBlue
                                )
                            }
                            "Personal" -> {
                                BadgeItem(
                                    label = "🏠 Personal",
                                    bgColor = PersonalBg,
                                    borderColor = PersonalBorder,
                                    textColor = PersonalOrange
                                )
                            }
                            else -> {
                                BadgeItem(
                                    label = "⚠️ To Classify",
                                    bgColor = UnclassifiedBg,
                                    borderColor = UnclassifiedAmber,
                                    textColor = UnclassifiedAmber
                                )
                            }
                        }

                        // Expand/Collapse Chevron
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse trip details" else "Expand trip details",
                            tint = SlateTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Metrics Row (Distance + Deduction Preview)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = distanceDisplay,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )

                    if (trip.type == "Business" || isExpanded) {
                        Text(
                            text = "$amountDisplay deduction",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BusinessBlue,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                // Locations preview (Collapsed or standard view)
                if (!trip.startAddress.isNullOrBlank() || !trip.endAddress.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        trip.startAddress?.let { start ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF2563EB), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = start,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary,
                                    maxLines = if (isExpanded) 2 else 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        trip.endAddress?.let { end ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(PersonalOrange, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = end,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary,
                                    maxLines = if (isExpanded) 2 else 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Notes / Purpose preview when collapsed
                if (!isExpanded && !trip.purpose.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = trip.purpose,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // -------------------------------------------------------------
                // EXPANDED COMPLETE DETAILS SECTION
                // -------------------------------------------------------------
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // 1. Route Map View (Interactive GoogleMapView if points exist, stylized snapshot canvas otherwise)
                        if (routePoints.isNotEmpty()) {
                            GoogleMapView(
                                points = routePoints,
                                isLive = false,
                                distanceKm = trip.distanceKm,
                                isMiles = isMiles,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            ) {
                                TripRouteSnapshotCanvas(modifier = Modifier.fillMaxSize())
                            }
                        }

                        // 2. Full Route Timeline with Timestamps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(0xFF2563EB), CircleShape)
                                )
                                Canvas(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(52.dp)
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
                                    tint = PersonalOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "START",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateTextSecondary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "· $startTimeText",
                                            fontSize = 11.sp,
                                            color = SlateTextMuted
                                        )
                                    }
                                    Text(
                                        text = trip.startAddress?.ifBlank { "Start location not recorded" } ?: "Start location not recorded",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = SlateTextPrimary
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "DESTINATION",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SlateTextSecondary,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "· $endTimeText",
                                            fontSize = 11.sp,
                                            color = SlateTextMuted
                                        )
                                    }
                                    Text(
                                        text = trip.endAddress?.ifBlank { "Destination not recorded" } ?: "Destination not recorded",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = SlateTextPrimary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // 3. Complete Stats Grid: 4 Metric Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TripMetricCard(
                                title = "DISTANCE",
                                value = distanceDisplay,
                                icon = Icons.Default.DirectionsCar,
                                modifier = Modifier.weight(1f)
                            )
                            TripMetricCard(
                                title = "DURATION",
                                value = durationText,
                                icon = Icons.Default.AccessTime,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TripMetricCard(
                                title = "EST. VALUE",
                                value = amountDisplay,
                                icon = Icons.Default.AttachMoney,
                                modifier = Modifier.weight(1f)
                            )
                            TripMetricCard(
                                title = "CATEGORY",
                                value = trip.type ?: "To Classify",
                                icon = if (trip.type == "Personal") Icons.Default.Home else Icons.Default.BusinessCenter,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // 4. Vehicle & Purpose Details List
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TripDetailRow(
                                icon = Icons.Default.DirectionsCar,
                                label = "Vehicle",
                                value = trip.vehicle?.ifBlank { "Primary Vehicle" } ?: "Primary Vehicle"
                            )
                            TripDetailRow(
                                icon = Icons.AutoMirrored.Filled.Notes,
                                label = "Purpose / Notes",
                                value = trip.purpose?.ifBlank { "No notes provided" } ?: "No notes provided"
                            )
                            TripDetailRow(
                                icon = Icons.Default.CalendarToday,
                                label = "Full Date",
                                value = fullDateText
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                        // 5. Expanded Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onEdit(trip) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MileageBlue,
                                    contentColor = Color.White
                                ),
                                elevation = null,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("edit_trip_expanded_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Edit Trip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { onDelete(trip) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFEF4444)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // Collapse Prompt
                        Text(
                            text = "Tap card to collapse ▲",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            fontSize = 11.sp
                        )
                    }
                }

                // -------------------------------------------------------------
                // COLLAPSED ACTION ROW
                // -------------------------------------------------------------
                if (!isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isClassified) {
                        // FOR ALREADY CLASSIFIED TRIPS:
                        // - NO swipe left or right to classify
                        // - NO classification toggle chips on card
                        // - User CAN ONLY USE THE EDIT ICON to edit the trip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Tap for details",
                                    fontSize = 12.sp,
                                    color = SlateTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = SlateTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onEdit(trip) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Trip",
                                        tint = MileageBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDelete(trip) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Trip",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // FOR UNCLASSIFIED TRIPS:
                        // - AssistChips for quick classification
                        // - Edit & delete icons
                        // - Bottom swipe hint
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { onClassify(trip, "Business") },
                                    label = { Text("Business", fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.BusinessCenter,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = BusinessBlue
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        enabled = true,
                                        borderColor = SlateBorder
                                    )
                                )

                                AssistChip(
                                    onClick = { onClassify(trip, "Personal") },
                                    label = { Text("Personal", fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = PersonalOrange
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = AssistChipDefaults.assistChipBorder(
                                        enabled = true,
                                        borderColor = SlateBorder
                                    )
                                )
                            }

                            Row {
                                IconButton(
                                    onClick = { onEdit(trip) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Trip",
                                        tint = MileageBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDelete(trip) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Trip",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Bottom swipe hint for unclassified trips
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "← Swipe left for Personal · Swipe right for Business →",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateTextMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeItem(
    label: String,
    bgColor: Color,
    borderColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Composable
private fun TripMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SlateTextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextSecondary,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )
        }
    }
}

@Composable
private fun TripDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SlateTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = SlateTextSecondary
            )
        }
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = SlateTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Clean vector map canvas snapshot for trips that do not have recorded GPS coordinates.
 */
@Composable
private fun TripRouteSnapshotCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Terrain
        drawRect(color = Color(0xFFF8FAFC))

        // Green park area
        val parkColor = Color(0xFFDCFCE7)
        val park = Path().apply {
            moveTo(w * 0.08f, h * 0.15f)
            lineTo(w * 0.32f, h * 0.12f)
            lineTo(w * 0.28f, h * 0.65f)
            lineTo(w * 0.06f, h * 0.55f)
            close()
        }
        drawPath(path = park, color = parkColor)

        // Water ribbon
        val waterColor = Color(0xFFBAE6FD)
        val river = Path().apply {
            moveTo(w * 0.85f, 0f)
            cubicTo(w * 0.78f, h * 0.35f, w * 0.94f, h * 0.65f, w * 0.82f, h)
            lineTo(w, h)
            lineTo(w, 0f)
            close()
        }
        drawPath(path = river, color = waterColor)

        // Street grid
        val strokeW = 2.5f
        for (f in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(
                color = Color.White,
                start = Offset(0f, h * f),
                end = Offset(w, h * f),
                strokeWidth = strokeW
            )
            drawLine(
                color = Color.White,
                start = Offset(w * f, 0f),
                end = Offset(w * f, h),
                strokeWidth = strokeW
            )
        }

        // Route line
        val startPoint = Offset(w * 0.22f, h * 0.68f)
        val endPoint = Offset(w * 0.72f, h * 0.30f)

        val routePath = Path().apply {
            moveTo(startPoint.x, startPoint.y)
            cubicTo(w * 0.35f, h * 0.65f, w * 0.45f, h * 0.35f, endPoint.x, endPoint.y)
        }

        // Route glow
        drawPath(
            path = routePath,
            color = Color(0xFF3B72FF).copy(alpha = 0.25f),
            style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Route core line
        drawPath(
            path = routePath,
            color = Color(0xFF3B72FF),
            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Start point
        drawCircle(color = Color(0xFF2563EB), radius = 7f, center = startPoint)
        drawCircle(color = Color.White, radius = 2.5f, center = startPoint)

        // End point
        drawCircle(color = Color(0xFFF59E0B), radius = 7f, center = endPoint)
        drawCircle(color = Color.White, radius = 2.5f, center = endPoint)
    }
}
