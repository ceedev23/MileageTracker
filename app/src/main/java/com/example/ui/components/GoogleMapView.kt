package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LatLngPoint
import com.example.ui.theme.DriveGreen
import com.example.ui.theme.LightBorder
import com.example.ui.theme.LightSurface
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.MileageBlueContainer
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.StopRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Modern vector-rendered GPS Map and Route Visualizer.
 * Provides interactive gestures (pan, zoom), dynamic route drawing,
 * live vehicle tracking with pulse aura, telemetry HUD, and satellite/street styling
 * without requiring Google Maps API keys or third-party service registration.
 */
@Composable
fun GoogleMapView(
    points: List<LatLngPoint>,
    isLive: Boolean,
    speedKmh: Float = 0f,
    distanceKm: Double = 0.0,
    isMiles: Boolean = false,
    hasLocationPermission: Boolean = true,
    showChrome: Boolean = true,
    routeColor: Color = MileageBlue,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Pulsing radar animation for live tracking vehicle
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    // Reset pan whenever live tracking updates significantly
    LaunchedEffect(isLive) {
        panOffset = Offset.Zero
        zoomScale = 1.0f
    }

    val mapShape = if (showChrome) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp)

    Box(
        modifier = modifier
            .clip(mapShape)
            .then(if (showChrome) Modifier.border(1.dp, LightBorder, mapShape) else Modifier)
            .background(Color(0xFFF1F5F9))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    panOffset += pan
                    zoomScale = (zoomScale * zoom).coerceIn(0.5f, 4.0f)
                }
            }
            .testTag("google_map_container")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f) + panOffset

            // 1. Draw stylized background map terrain (parks, roads, blocks)
            drawMapBase(
                width = width,
                height = height,
                center = center,
                zoom = zoomScale
            )

            // 2. Project Lat/Lng points into canvas space
            if (points.isNotEmpty()) {
                val projectedPoints = projectRoutePoints(
                    points = points,
                    canvasCenter = center,
                    canvasSize = size,
                    zoom = zoomScale,
                    isLive = isLive
                )

                // 3. Draw route line with smooth path & soft glow underlay
                if (projectedPoints.size >= 2) {
                    val path = Path().apply {
                        moveTo(projectedPoints.first().x, projectedPoints.first().y)
                        for (i in 1 until projectedPoints.size) {
                            lineTo(projectedPoints[i].x, projectedPoints[i].y)
                        }
                    }

                    val routeWidth = if (showChrome) 8f else 14f
                    // Soft glow under the route
                    drawPath(
                        path = path,
                        color = routeColor.copy(alpha = 0.28f),
                        style = Stroke(
                            width = (routeWidth + 10f) * zoomScale.coerceIn(0.8f, 1.6f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Sharp route
                    drawPath(
                        path = path,
                        color = routeColor,
                        style = Stroke(
                            width = routeWidth * zoomScale.coerceIn(0.8f, 1.6f),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 4. Start Point Pin
                val startPos = projectedPoints.first()
                if (!showChrome) {
                    drawCircle(color = Color.White, radius = 18f, center = startPos)
                    drawCircle(color = routeColor, radius = 15f, center = startPos)
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                        isAntiAlias = true
                        textSize = 13f * density
                    }
                    val baseline = startPos.y - (labelPaint.ascent() + labelPaint.descent()) / 2f
                    drawContext.canvas.nativeCanvas.drawText("A", startPos.x, baseline, labelPaint)
                } else {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.2f),
                        radius = 14f,
                        center = startPos + Offset(0f, 4f)
                    )
                    drawCircle(
                        color = PurpleAccent,
                        radius = 12f,
                        center = startPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = startPos
                    )
                }

                // 5. Current Vehicle Marker (Live vs Stationary)
                val latestPos = projectedPoints.last()
                if (isLive) {
                    // Pulsing radar ring
                    val maxRadius = 55f * zoomScale.coerceIn(0.8f, 1.5f)
                    val currentRadius = 14f + (maxRadius - 14f) * pulseProgress
                    val alpha = (1f - pulseProgress).coerceIn(0f, 1f)

                    drawCircle(
                        color = routeColor.copy(alpha = alpha * 0.45f),
                        radius = currentRadius,
                        center = latestPos
                    )
                    drawCircle(
                        color = routeColor.copy(alpha = alpha * 0.8f),
                        radius = currentRadius,
                        center = latestPos,
                        style = Stroke(width = 2.5f)
                    )

                    // Outer vehicle glow
                    drawCircle(
                        color = routeColor,
                        radius = 16f,
                        center = latestPos
                    )
                    // Inner white dot
                    drawCircle(
                        color = Color.White,
                        radius = 8f,
                        center = latestPos
                    )
                    // Center core
                    drawCircle(
                        color = routeColor,
                        radius = 4f,
                        center = latestPos
                    )
                } else if (projectedPoints.size >= 2) {
                    // Destination pin
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.2f),
                        radius = 14f,
                        center = latestPos + Offset(0f, 4f)
                    )
                    drawCircle(
                        color = StopRed,
                        radius = 12f,
                        center = latestPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5f,
                        center = latestPos
                    )
                }
            } else {
                // Empty state: draw a subtle center vehicle icon/pin at map center
                drawCircle(
                    color = MileageBlue.copy(alpha = 0.25f),
                    radius = 24f,
                    center = center
                )
                drawCircle(
                    color = MileageBlue,
                    radius = 12f,
                    center = center
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = center
                )
            }
        }

        if (showChrome) {
        // Top Left Status Badge (Pristine White Pill with soft shadow)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = LightSurface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                LightBorder
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isLive) DriveGreen else MileageBlue, CircleShape)
                )
                Text(
                    text = if (isLive) "LIVE GPS ROUTE" else "GPS MAP VIEW",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLive) DriveGreen else (TextPrimary),
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Top Right Map Controls
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom In
            IconButton(
                onClick = { zoomScale = (zoomScale * 1.3f).coerceAtMost(4.0f) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LightSurface)
                    .border(1.dp, LightBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Zoom Out
            IconButton(
                onClick = { zoomScale = (zoomScale / 1.3f).coerceAtLeast(0.5f) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LightSurface)
                    .border(1.dp, LightBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Recenter Camera Button
            IconButton(
                onClick = {
                    panOffset = Offset.Zero
                    zoomScale = 1.0f
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(LightSurface)
                    .border(1.dp, LightBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter Map",
                    tint = MileageBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Bottom Telemetry HUD when live tracking (White Card)
        AnimatedVisibility(
            visible = isLive,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            val dist = if (isMiles) distanceKm * 0.621371 else distanceKm
            val unit = if (isMiles) "mi" else "km"
            val spd = if (isMiles) speedKmh * 0.621371f else speedKmh
            val spdUnit = if (isMiles) "mph" else "km/h"

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = LightSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    LightBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MileageBlueContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = null,
                                tint = MileageBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CURRENT SPEED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${spd.toInt()} $spdUnit",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 1.dp, height = 30.dp)
                            .background(LightBorder)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "TRIP DISTANCE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = String.format(Locale.getDefault(), "%.1f %s", dist, unit),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MileageBlue
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Draws stylized vector city street map grid, avenues, rivers, and parks.
 */
private fun DrawScope.drawMapBase(
    width: Float,
    height: Float,
    center: Offset,
    zoom: Float
) {
    val roadColor = Color(0xFFFFFFFF)
    val roadBorder = Color(0xFFE2E8F0)
    val highwayColor = Color(0xFFF1F5F9)
    val parkColor = Color(0xFFE2F2E9)
    val waterColor = Color(0xFFDBEAFE)

    // Draw stylized water body curve on canvas corner
    val waterPath = Path().apply {
        moveTo(0f, height * 0.1f)
        cubicTo(
            width * 0.3f, height * 0.05f,
            width * 0.4f, height * 0.35f,
            width * 0.7f, height * 0.25f
        )
        cubicTo(
            width * 0.85f, height * 0.2f,
            width * 0.95f, height * 0.1f,
            width, height * 0.15f
        )
        lineTo(width, 0f)
        lineTo(0f, 0f)
        close()
    }
    drawPath(waterPath, waterColor)

    // Draw stylized green park block
    drawRoundRect(
        color = parkColor,
        topLeft = Offset(width * 0.08f, height * 0.65f),
        size = Size(width * 0.32f, height * 0.22f),
        cornerRadius = CornerRadius(16f, 16f)
    )

    // Draw regular city grid roads
    val gridSpacing = 65f * zoom.coerceIn(0.6f, 2.0f)
    val startX = (center.x % gridSpacing) - gridSpacing
    val startY = (center.y % gridSpacing) - gridSpacing

    // Secondary vertical & horizontal streets
    var x = startX
    while (x < width + gridSpacing) {
        drawLine(
            color = roadBorder,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 14f * zoom.coerceIn(0.7f, 1.4f)
        )
        drawLine(
            color = roadColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 10f * zoom.coerceIn(0.7f, 1.4f)
        )
        x += gridSpacing
    }

    var y = startY
    while (y < height + gridSpacing) {
        drawLine(
            color = roadBorder,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 14f * zoom.coerceIn(0.7f, 1.4f)
        )
        drawLine(
            color = roadColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 10f * zoom.coerceIn(0.7f, 1.4f)
        )
        y += gridSpacing
    }

    // Diagonal arterial expressways
    drawLine(
        color = roadBorder,
        start = Offset(0f, height * 0.85f),
        end = Offset(width, height * 0.25f),
        strokeWidth = 22f * zoom.coerceIn(0.8f, 1.5f)
    )
    drawLine(
        color = highwayColor,
        start = Offset(0f, height * 0.85f),
        end = Offset(width, height * 0.25f),
        strokeWidth = 16f * zoom.coerceIn(0.8f, 1.5f)
    )
    // Expressway dashed median
    drawLine(
        color = Color(0xFFCBD5E1),
        start = Offset(0f, height * 0.85f),
        end = Offset(width, height * 0.25f),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
    )
}

/**
 * Projects geographical coordinates (latitude/longitude) into canvas 2D pixel space.
 */
private fun projectRoutePoints(
    points: List<LatLngPoint>,
    canvasCenter: Offset,
    canvasSize: Size,
    zoom: Float,
    isLive: Boolean
): List<Offset> {
    if (points.isEmpty()) return emptyList()

    // Find bounds or center reference
    val lats = points.map { it.latitude }
    val lngs = points.map { it.longitude }
    val minLat = lats.minOrNull() ?: 0.0
    val maxLat = lats.maxOrNull() ?: 0.0
    val minLng = lngs.minOrNull() ?: 0.0
    val maxLng = lngs.maxOrNull() ?: 0.0

    val latSpan = (maxLat - minLat).coerceAtLeast(0.003)
    val lngSpan = (maxLng - minLng).coerceAtLeast(0.003)

    // Calculate scale so route comfortably fits canvas
    val usableWidth = canvasSize.width * 0.65f * zoom
    val usableHeight = canvasSize.height * 0.55f * zoom
    val scaleX = usableWidth / lngSpan.toFloat()
    val scaleY = usableHeight / latSpan.toFloat()
    val scale = minOf(scaleX, scaleY)

    val centerLat = if (isLive) points.last().latitude else (minLat + maxLat) / 2.0
    val centerLng = if (isLive) points.last().longitude else (minLng + maxLng) / 2.0

    return points.map { p ->
        val dx = (p.longitude - centerLng).toFloat() * scale
        val dy = -(p.latitude - centerLat).toFloat() * scale // Latitude increases upwards, canvas Y downwards
        Offset(canvasCenter.x + dx, canvasCenter.y + dy)
    }
}
