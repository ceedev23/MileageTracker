package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.AppTab
import com.example.ui.MileageViewModel
import com.example.ui.components.ReceiptScanDialog
import com.example.ui.components.ReferenceTripCard
import com.example.ui.components.UndoToast
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    viewModel: MileageViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val trackingState by viewModel.trackingState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val scanState by viewModel.receiptScanState.collectAsState()
    val undoAction by viewModel.undoAction.collectAsState()

    // Receipt Scanner Dialog
    if (scanState.isDialogVisible) {
        ReceiptScanDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissReceiptScanner() }
        )
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // Auto-start movement monitoring when enabled and permitted
    LaunchedEffect(settings.autoDetect, hasLocationPermission) {
        if (settings.autoDetect && hasLocationPermission) {
            viewModel.startMovementMonitoring(context)
        }
    }

    val isMiles = settings.unit == "mi"
    val unitLabel = if (isMiles) "mi" else "km"

    val currentCalendar = remember { Calendar.getInstance() }
    val currentYear = currentCalendar.get(Calendar.YEAR)
    var selectedYear by remember { mutableIntStateOf(currentYear) }
    var showYearMenu by remember { mutableStateOf(false) }

    // Start of selected calendar year (Jan 1 00:00)
    val startOfYearMillis = remember(selectedYear) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // End of selected calendar year (Dec 31 23:59:59.999)
    val endOfYearMillis = remember(selectedYear) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear + 1)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Calculate total driven distance
    val totalLoggedKm = remember(trips) {
        trips.sumOf { it.distanceKm }
    }
    val currentDriveKm = if (trackingState.isTracking) trackingState.distanceKm else 0.0
    val totalCombinedKm = totalLoggedKm + currentDriveKm

    // Distance in selected calendar year
    val yearTripsKm = remember(trips, startOfYearMillis, endOfYearMillis) {
        trips.filter { it.startDate in startOfYearMillis until endOfYearMillis }
            .sumOf { it.distanceKm }
    }
    val totalYearKm = if (selectedYear == currentYear) {
        val yr = yearTripsKm + currentDriveKm
        if (yr > 0.0) yr else totalCombinedKm
    } else {
        yearTripsKm
    }

    val displayDistance = if (isMiles) {
        totalYearKm * 0.621371
    } else {
        totalYearKm
    }
    val formattedDistance = String.format(Locale.US, "%.1f", displayDistance)

    val annualGoal = settings.annualGoal.coerceAtLeast(100.0)
    val annualProgress = (displayDistance / annualGoal).toFloat().coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = annualProgress,
        animationSpec = tween(durationMillis = 800),
        label = "annual_progress"
    )

    // Unclassified trips waiting for user classification
    val unclassifiedTrips = remember(trips) {
        trips.filter { it.type == null || it.type.isEmpty() || it.type == "Unclassified" }
    }

    val isDriverMoving = trackingState.showsLiveDrive()

    // Pulse animation for driving indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val isCompactHeight = screenHeight < 680.dp

        // U-shaped Hero Card dimensions proportioned to match the reference design
        val heroHorizontalMargin = if (isCompactHeight) 36.dp else 40.dp
        val heroCardWidth = (screenWidth - heroHorizontalMargin * 2).coerceAtLeast(240.dp)
        val heroCardHeight = if (isCompactHeight) {
            (heroCardWidth * 1.24f).coerceAtMost(380.dp)
        } else {
            (heroCardWidth * 1.35f).coerceAtMost(445.dp)
        }
        val arcGap = if (isCompactHeight) 19.dp else 23.dp
        val thumbOuterRadius = 13.dp
        val heroSectionHeight = heroCardHeight + arcGap + thumbOuterRadius + 4.dp

        Box(modifier = Modifier.fillMaxSize()) {
            if (isDriverMoving) {
                LiveDriveScreen(
                    trackingState = trackingState,
                    isMiles = isMiles,
                    reimbursementRate = settings.reimbursementRate,
                    vehicleName = settings.vehicleName,
                    onVehicleChange = { viewModel.setVehicleName(it) },
                    onStop = { viewModel.stopManualTracking(context) },
                    onDraftChange = { category, vehicle, note, startPlaceName ->
                        viewModel.updateActiveTripDraft(category, vehicle, note, startPlaceName)
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF5F6F8))
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp)
                        .testTag("home_screen"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. TOP U-SHAPED HERO CARD + CONCENTRIC CURVED PROGRESS ARC
                    AnimatedVisibility(
                        visible = unclassifiedTrips.isEmpty(),
                        enter = fadeIn(tween(250)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroSectionHeight),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                val heroShape = RoundedCornerShape(
                                    topStartPercent = 0,
                                    topEndPercent = 0,
                                    bottomStartPercent = 50,
                                    bottomEndPercent = 50
                                )

                                // Concentric Curved Progress Arc + Thumb around the bottom semicircle
                                Canvas(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    val cardWidthPx = heroCardWidth.toPx()
                                    val cardHeightPx = heroCardHeight.toPx()
                                    val cardRadiusPx = cardWidthPx / 2f
                                    val centerX = size.width / 2f
                                    val circleCenterY = cardHeightPx - cardRadiusPx
                                    val arcRadiusPx = cardRadiusPx + arcGap.toPx()
                                    val strokeWidthPx = 6.dp.toPx()

                                    val arcTopLeft = Offset(
                                        x = centerX - arcRadiusPx,
                                        y = circleCenterY - arcRadiusPx
                                    )
                                    val arcSize = Size(arcRadiusPx * 2f, arcRadiusPx * 2f)

                                    val startAngleDeg = 155f
                                    val totalSweepDeg = -130f

                                    // Background track arc (light slate grey)
                                    drawArc(
                                        color = Color(0xFFDDE1E8),
                                        startAngle = startAngleDeg,
                                        sweepAngle = totalSweepDeg,
                                        useCenter = false,
                                        topLeft = arcTopLeft,
                                        size = arcSize,
                                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                                    )

                                    // Active progress arc (deep midnight navy from left toward right)
                                    val activeSweepDeg = totalSweepDeg * animatedProgress
                                    if (animatedProgress > 0.002f) {
                                        drawArc(
                                            color = Color(0xFF07162C),
                                            startAngle = startAngleDeg,
                                            sweepAngle = activeSweepDeg,
                                            useCenter = false,
                                            topLeft = arcTopLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                                        )
                                    }

                                    // Circular thumb handle positioned on the arc
                                    val thumbAngleRad = Math.toRadians((startAngleDeg + activeSweepDeg).toDouble())
                                    val thumbCenter = Offset(
                                        x = centerX + (arcRadiusPx * cos(thumbAngleRad)).toFloat(),
                                        y = circleCenterY + (arcRadiusPx * sin(thumbAngleRad)).toFloat()
                                    )

                                    // Subtle soft shadow behind thumb
                                    drawCircle(
                                        color = Color(0x1F0F172A),
                                        radius = thumbOuterRadius.toPx() + 2.dp.toPx(),
                                        center = Offset(thumbCenter.x, thumbCenter.y + 1.5.dp.toPx())
                                    )
                                    // Crisp white outer thumb circle
                                    drawCircle(
                                        color = Color.White,
                                        radius = thumbOuterRadius.toPx(),
                                        center = thumbCenter
                                    )
                                    // Deep navy inner dot
                                    drawCircle(
                                        color = Color(0xFF07162C),
                                        radius = 6.dp.toPx(),
                                        center = thumbCenter
                                    )
                                }

                                // U-Shaped Mountain Road Hero Card
                                Box(
                                    modifier = Modifier
                                        .width(heroCardWidth)
                                        .height(heroCardHeight)
                                        .shadow(
                                            elevation = 16.dp,
                                            shape = heroShape,
                                            ambientColor = Color(0x400F172A),
                                            spotColor = Color(0x400F172A)
                                        )
                                        .clip(heroShape)
                                        .background(Color(0xFF071426))
                                        .testTag("home_total_driven_dial")
                                ) {
                                    // Background Twilight Mountain Highway Image
                                    Image(
                                        painter = painterResource(id = R.drawable.img_home_hero),
                                        contentDescription = "Mountain road background",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Subtle dark vignette at bottom-left for text legibility
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    0.0f to Color(0x18040C18),
                                                    0.45f to Color.Transparent,
                                                    0.75f to Color(0x33030B16),
                                                    1.0f to Color(0x66020812)
                                                )
                                            )
                                    )

                                    // Top Row inside Hero Card: "2026 v" selector & Profile Avatar Button
                                    val topContentPadding = (statusBarTopPadding + 16.dp).coerceAtLeast(32.dp)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                top = topContentPadding,
                                                start = 22.dp,
                                                end = 18.dp
                                            ),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Year Selector ("2026 v")
                                        Box {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { showYearMenu = true }
                                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = selectedYear.toString(),
                                                    fontSize = 21.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    letterSpacing = (-0.3).sp
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Select year",
                                                    tint = Color.White.copy(alpha = 0.9f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = showYearMenu,
                                                onDismissRequest = { showYearMenu = false }
                                            ) {
                                                listOf(currentYear, currentYear - 1, currentYear - 2).forEach { yr ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = yr.toString(),
                                                                fontWeight = if (yr == selectedYear) FontWeight.Bold else FontWeight.Normal
                                                            )
                                                        },
                                                        onClick = {
                                                            selectedYear = yr
                                                            showYearMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // Translucent Glass Profile Button
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF475B74).copy(alpha = 0.52f))
                                                .border(
                                                    width = 1.dp,
                                                    color = Color.White.copy(alpha = 0.14f),
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.selectTab(AppTab.SETTINGS) }
                                                .testTag("home_profile_avatar"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Person,
                                                contentDescription = "Profile",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    // Lower-Left Content inside Hero Card: TOTAL DRIVEN, Distance, Tracking Pill
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(
                                                start = 26.dp,
                                                end = 20.dp,
                                                bottom = if (isCompactHeight) 82.dp else 98.dp
                                            )
                                    ) {
                                        Text(
                                            text = "TOTAL DRIVEN",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.72f),
                                            letterSpacing = 1.8.sp
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            Text(
                                                text = formattedDistance,
                                                fontSize = if (isCompactHeight) 36.sp else 42.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                lineHeight = if (isCompactHeight) 38.sp else 44.sp,
                                                letterSpacing = (-0.5).sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = unitLabel,
                                                fontSize = if (isCompactHeight) 17.sp else 20.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // "• Tracking is on" Dark Navy Pill Badge
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color(0xFF0E1F38).copy(alpha = 0.88f),
                                            border = BorderStroke(
                                                1.dp,
                                                Color(0xFF25426B).copy(alpha = 0.45f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 12.dp,
                                                    vertical = 6.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .alpha(if (trackingState.isTracking) pulseAlpha else 1.0f)
                                                        .background(
                                                            if (trackingState.isTracking || settings.autoDetect) {
                                                                Color(0xFF1D63FF)
                                                            } else {
                                                                Color(0xFF94A3B8)
                                                            }
                                                        )
                                                )
                                                Text(
                                                    text = if (trackingState.isTracking) {
                                                        "Driving"
                                                    } else if (settings.autoDetect) {
                                                        "Tracking is on"
                                                    } else {
                                                        "Tracking is off"
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.92f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(if (isCompactHeight) 14.dp else 20.dp))

                            // 2. STATS SUMMARY ROW: "2026 • 27% / of 15,000 km" | "[Calendar] Resets Dec 31"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Column: Year & Percentage + Annual Goal
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$selectedYear • ${String.format(Locale.US, "%.0f%%", annualProgress * 100f)}",
                                        fontSize = if (isCompactHeight) 17.sp else 19.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A),
                                        letterSpacing = (-0.3).sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "of ${String.format(Locale.US, "%,.0f", annualGoal)} $unitLabel",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                // Vertical Divider Line
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(34.dp)
                                        .background(Color(0xFFDCDEE4))
                                )

                                // Right Column: Calendar Icon + "Resets Dec 31"
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CalendarToday,
                                            contentDescription = "Reset date",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Text(
                                            text = "Resets Dec 31",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Header fallback when unclassified trips are being reviewed
                    if (unclassifiedTrips.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = (statusBarTopPadding + 12.dp).coerceAtLeast(16.dp),
                                    start = 18.dp,
                                    end = 18.dp,
                                    bottom = 8.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your driving",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF))
                                    .clickable { viewModel.selectTab(AppTab.SETTINGS) }
                                    .testTag("home_profile_avatar"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = "Profile",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 16.dp else 22.dp))

                    // 3. RECENT TRIPS / EMPTY STATE CARD SECTION
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                    ) {
                        val tripsToShow = unclassifiedTrips

                        if (tripsToShow.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFEFF6FF)
                                    ) {
                                        Text(
                                            text = "${unclassifiedTrips.size} ${if (unclassifiedTrips.size == 1) "trip" else "trips"} to review",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "Review and classify below",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                tripsToShow.forEach { trip ->
                                    key(trip.id) {
                                        ReferenceTripCard(
                                            trip = trip,
                                            isMiles = isMiles,
                                            mileageRate = settings.reimbursementRate,
                                            onClassify = { targetTrip, classification ->
                                                viewModel.classifyTrip(targetTrip, classification)
                                            },
                                            onDelete = { targetTrip ->
                                                viewModel.deleteTrip(targetTrip)
                                            },
                                            onUpdateTrip = { updatedTrip ->
                                                viewModel.updateTrip(updatedTrip)
                                            }
                                        )
                                    }
                                }
                            }
                        } else if (!trackingState.isTracking) {
                            // Rounded White Empty State Card matching the reference image
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFBFD)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                                border = BorderStroke(1.dp, Color(0xFFEDEFF3))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 24.dp,
                                            vertical = if (isCompactHeight) 22.dp else 30.dp
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isCompactHeight) 58.dp else 66.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF1F3F7)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = "Trips",
                                            tint = Color(0xFF6B788E),
                                            modifier = Modifier.size(if (isCompactHeight) 28.dp else 32.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 16.dp))

                                    Text(
                                        text = "Your trips will appear here",
                                        fontSize = if (isCompactHeight) 15.5.sp else 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "Drive as usual. Trips are recorded automatically.",
                                        fontSize = if (isCompactHeight) 12.sp else 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFF64748B),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Location permission request banner if not granted
                        if (!hasLocationPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Location required",
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Tap to grant location permission for tracking",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Undo Classification Toast at bottom
            if (undoAction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    UndoToast(
                        action = undoAction,
                        onUndo = { viewModel.undoClassification() }
                    )
                }
            }
        }
    }
}


