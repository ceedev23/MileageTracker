package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.UnclassifiedAmber
import com.example.ui.theme.UnclassifiedBg
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableTripCard(
    trip: TripEntity,
    isMiles: Boolean,
    onClassify: (TripEntity, String) -> Unit,
    onEdit: (TripEntity) -> Unit,
    onDelete: (TripEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    val currentTrip by rememberUpdatedState(trip)
    val currentOnClassify by rememberUpdatedState(onClassify)

    val density = LocalDensity.current
    val thresholdPx = with(density) { 60.dp.toPx() }

    val distanceDisplay = if (isMiles) {
        val miles = trip.distanceKm * 0.621371
        String.format(Locale.getDefault(), "%.1f mi", miles)
    } else {
        String.format(Locale.getDefault(), "%.1f km", trip.distanceKm)
    }

    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault()) }
    val dateText = dateFormatter.format(Date(trip.startDate))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
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

        // Front interactive Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(trip.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                isAnimating = true
                                val width = size.width.toFloat()
                                if (offsetX > thresholdPx) {
                                    // Swipe right = Business
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = width,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                    ) { value, _ ->
                                        offsetX = value
                                    }
                                    currentOnClassify(currentTrip, "Business")
                                    offsetX = 0f
                                } else if (offsetX < -thresholdPx) {
                                    // Swipe left = Personal
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = -width,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                    ) { value, _ ->
                                        offsetX = value
                                    }
                                    currentOnClassify(currentTrip, "Personal")
                                    offsetX = 0f
                                } else {
                                    // Snap back
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    ) { value, _ ->
                                        offsetX = value
                                    }
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
                                ) { value, _ ->
                                    offsetX = value
                                }
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
                .testTag("trip_card_${trip.id}"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header: Date & Classification Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = SlateTextSecondary,
                        fontWeight = FontWeight.Medium
                    )

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
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Distance display
                Text(
                    text = distanceDisplay,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary
                )

                // Start and End Address
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        trip.endAddress?.let { end ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MileageBlue, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = end,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Purpose Note if present
                if (!trip.purpose.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = trip.purpose,
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateTextMuted
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Row (manual classification fallbacks + edit + delete)
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
                                containerColor = if (trip.type == "Business") BusinessBg else Color.Transparent
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = if (trip.type == "Business") BusinessBorder else SlateBorder
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
                                containerColor = if (trip.type == "Personal") PersonalBg else Color.Transparent
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = if (trip.type == "Personal") PersonalBorder else SlateBorder
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

                // Bottom swipe hint
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
