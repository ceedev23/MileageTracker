package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.LatLngPoint
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.MileageBlueLight

@Composable
fun RouteCanvasView(
    points: List<LatLngPoint>,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0D1527))
    ) {
        if (points.size < 2) {
            // Radar / Ready indicator when waiting for points
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)

                // Grid circles
                for (r in listOf(40f, 90f, 150f)) {
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                }

                // Grid crosshairs
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(center.x - 160f, center.y),
                    end = Offset(center.x + 160f, center.y),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(center.x, center.y - 160f),
                    end = Offset(center.x, center.y + 160f),
                    strokeWidth = 1f
                )

                // Center pulse
                if (isLive) {
                    drawCircle(
                        color = MileageBlue.copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = center
                    )
                }
                drawCircle(
                    color = MileageBlue,
                    radius = 8f,
                    center = center
                )
            }

            Text(
                text = if (isLive) "Recording live GPS route..." else "Live Route Preview",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val paddingPx = 48f
                val w = size.width - (paddingPx * 2)
                val h = size.height - (paddingPx * 2)

                // Compute bounding box of path
                var minLat = points.minOf { it.latitude }
                var maxLat = points.maxOf { it.latitude }
                var minLng = points.minOf { it.longitude }
                var maxLng = points.maxOf { it.longitude }

                // Avoid division by zero
                if (maxLat - minLat < 0.0001) {
                    maxLat += 0.0001
                    minLat -= 0.0001
                }
                if (maxLng - minLng < 0.0001) {
                    maxLng += 0.0001
                    minLng -= 0.0001
                }

                fun toOffset(pt: LatLngPoint): Offset {
                    val nx = ((pt.longitude - minLng) / (maxLng - minLng)).toFloat()
                    val ny = (1f - ((pt.latitude - minLat) / (maxLat - minLat))).toFloat() // invert Y
                    return Offset(
                        x = paddingPx + (nx * w),
                        y = paddingPx + (ny * h)
                    )
                }

                // Background subtle road grid
                val gridSpacing = 60f
                var gy = 0f
                while (gy < size.height) {
                    drawLine(
                        color = Color(0xFF131E35),
                        start = Offset(0f, gy),
                        end = Offset(size.width, gy),
                        strokeWidth = 1f
                    )
                    gy += gridSpacing
                }
                var gx = 0f
                while (gx < size.width) {
                    drawLine(
                        color = Color(0xFF131E35),
                        start = Offset(gx, 0f),
                        end = Offset(gx, size.height),
                        strokeWidth = 1f
                    )
                    gx += gridSpacing
                }

                // Draw route path
                val path = Path()
                val offsets = points.map { toOffset(it) }
                offsets.forEachIndexed { idx, offset ->
                    if (idx == 0) path.moveTo(offset.x, offset.y)
                    else path.lineTo(offset.x, offset.y)
                }

                // Glow path
                drawPath(
                    path = path,
                    color = MileageBlue.copy(alpha = 0.35f),
                    style = Stroke(
                        width = 12f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Foreground gradient path
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF38BDF8), MileageBlue, MileageBlueLight),
                        start = offsets.first(),
                        end = offsets.last()
                    ),
                    style = Stroke(
                        width = 6f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Start Marker (Blue Pin)
                val startPos = offsets.first()
                drawCircle(color = Color(0xFF2563EB), radius = 7f, center = startPos)
                drawCircle(color = Color.White, radius = 3f, center = startPos)

                // End / Current Marker (Blue / Pulsing)
                val endPos = offsets.last()
                if (isLive) {
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = endPos
                    )
                }
                drawCircle(color = Color(0xFF2563EB), radius = 8f, center = endPos)
                drawCircle(color = Color.White, radius = 4f, center = endPos)
            }
        }
    }
}
