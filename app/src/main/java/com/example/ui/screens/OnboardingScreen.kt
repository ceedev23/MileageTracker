package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class OnboardingPage(
    val kicker: String,
    val headline: String,
    val body: String
)

private val pages = listOf(
    OnboardingPage(
        kicker = "Welcome to",
        headline = "MileageTracker",
        body = "Welcome to MileageTracker, your guide to quick and reliable mileage tracking. Easily log the right trip, track its distance, and keep it ready for tax time."
    ),
    OnboardingPage(
        kicker = "Quick and Reliable",
        headline = "Live Tracking",
        body = "Live tracking records the route as you drive, so business and personal miles stay accurate. Start a drive, follow the distance, and save the trip when you stop."
    ),
    OnboardingPage(
        kicker = "Effortless",
        headline = "Tax Reports",
        body = "Scan a gas receipt, classify the drive, and turn your trips into a deduction report. See what you spent and what you can claim."
    )
)

private val BlobBlue = Color(0xFFE7F0FE)
private val Skyline = Color(0xFFD3E3F8)
private val Ink = Color(0xFF1E293B)
private val DotIdle = Color(0xFFD5DCE6)

/**
 * Three swipeable intro screens shown once, after the landing page.
 * Skip and the last Continue both finish into the action the user already chose.
 */
@Composable
fun OnboardingScreen(
    onBackToLanding: () -> Unit,
    onDone: () -> Unit
) {
    var page by rememberSaveable { mutableIntStateOf(0) }

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val insets = WindowCompat.getInsetsController(window, view)
        val previous = insets.isAppearanceLightStatusBars
        insets.isAppearanceLightStatusBars = true
        onDispose { insets.isAppearanceLightStatusBars = previous }
    }

    BackHandler {
        if (page == 0) onBackToLanding() else page -= 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("onboarding_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                IconButton(
                    onClick = { page -= 1 },
                    modifier = Modifier.testTag("onboarding_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Ink,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onDone,
                modifier = Modifier.testTag("onboarding_skip")
            ) {
                Text(
                    text = "Skip",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .pointerInput(page) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            when {
                                drag < -48f && page < pages.lastIndex -> page += 1
                                drag > 48f && page > 0 -> page -= 1
                            }
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            when (page) {
                0 -> WelcomeArt()
                1 -> LiveTrackingArt()
                else -> TaxReportArt()
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = pages[page].kicker,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = pages[page].headline,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = pages[page].body,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        TextButton(
            onClick = {
                if (page == pages.lastIndex) onDone() else page += 1
            },
            modifier = Modifier.testTag("onboarding_next")
        ) {
            Text(
                text = if (page == pages.lastIndex) "Continue" else "Next",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MileageBlue
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        ) {
            repeat(pages.size) { index ->
                val selected = index == page
                Box(
                    modifier = Modifier
                        .height(7.dp)
                        .width(if (selected) 18.dp else 7.dp)
                        .background(if (selected) MileageBlue else DotIdle, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ArtStage(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier.size(width = 300.dp, height = 230.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOval(
                color = BlobBlue,
                topLeft = Offset(size.width * 0.06f, size.height * 0.08f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.88f, size.height * 0.78f)
            )
        }
        content()
    }
}

@Composable
private fun WelcomeArt() {
    ArtStage {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            listOf(26, 42, 18, 34).forEach { height ->
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(height.dp)
                        .background(Skyline, RoundedCornerShape(3.dp))
                )
            }
        }
        Box(modifier = Modifier.width(228.dp).height(116.dp).offset(y = 22.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawOval(
                    color = Color(0x140F172A),
                    topLeft = Offset(size.width * 0.16f, size.height * 0.84f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.1f)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp)
                    .width(108.dp)
                    .height(42.dp)
                    .background(MileageBlue, RoundedCornerShape(topStart = 18.dp, topEnd = 22.dp))
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp)
                        .fillMaxWidth()
                        .height(22.dp)
                        .background(Color(0xFFBFDBFE), RoundedCornerShape(6.dp))
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(MileageBlue, RoundedCornerShape(12.dp))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                        .size(width = 10.dp, height = 6.dp)
                        .background(Color.White, CircleShape)
                )
            }
            Wheel(Modifier.align(Alignment.BottomStart).padding(start = 28.dp))
            Wheel(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp))
        }
    }
}

@Composable
private fun LiveTrackingArt() {
    ArtStage {
        Surface(
            modifier = Modifier
                .size(width = 118.dp, height = 176.dp)
                .offset(x = (-28).dp, y = 4.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(MileageBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(5.dp)
                            .background(Color.White.copy(alpha = 0.85f), CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFFF8FBFF))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val road = Path().apply {
                            moveTo(size.width * 0.15f, size.height * 0.2f)
                            quadraticTo(
                                size.width * 0.7f, size.height * 0.25f,
                                size.width * 0.45f, size.height * 0.55f
                            )
                            quadraticTo(
                                size.width * 0.2f, size.height * 0.8f,
                                size.width * 0.85f, size.height * 0.78f
                            )
                        }
                        drawPath(
                            road,
                            color = MileageBlue.copy(alpha = 0.35f),
                            style = Stroke(width = 10f, cap = StrokeCap.Round)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(16.dp)
                            .background(MileageBlue, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(26.dp)
                            .background(MileageBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        FlatPerson(
            modifier = Modifier.offset(x = 78.dp, y = 36.dp),
            shirt = Color.White,
            pants = Ink
        )
    }
}

@Composable
private fun TaxReportArt() {
    ArtStage {
        Surface(
            modifier = Modifier
                .width(200.dp)
                .offset(x = (-24).dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReportRow(icon = { Icon(Icons.Default.LocalGasStation, null, tint = Color.White, modifier = Modifier.size(16.dp)) })
                ReportRow(icon = { Icon(Icons.Default.DirectionsCar, null, tint = Color.White, modifier = Modifier.size(16.dp)) })
            }
        }
        FlatPerson(
            modifier = Modifier.offset(x = 96.dp, y = 18.dp),
            shirt = MileageBlue,
            pants = Ink
        )
    }
}

@Composable
private fun ReportRow(icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MileageBlue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(8.dp)
                    .background(Color(0xFFE2E8F0), CircleShape)
            )
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(8.dp)
                    .background(BlobBlue, CircleShape)
            )
        }
    }
}

@Composable
private fun Wheel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .background(Ink, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color(0xFFE2E8F0), CircleShape)
        )
    }
}

@Composable
private fun FlatPerson(modifier: Modifier = Modifier, shirt: Color, pants: Color) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(0xFFF3C7A5), CircleShape)
        )
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(32.dp)
                .offset(y = (-2).dp)
                .background(shirt, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(26.dp)
                    .background(pants, RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(26.dp)
                    .background(pants, RoundedCornerShape(4.dp))
            )
        }
    }
}
