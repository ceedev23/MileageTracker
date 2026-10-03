package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.TripEntity
import com.example.service.DetectorState
import com.example.ui.AppTab
import com.example.ui.MileageViewModel
import com.example.ui.components.EndTripBottomSheet
import com.example.ui.components.ReceiptScanDialog
import com.example.ui.components.ReferenceTripCard
import com.example.ui.components.UndoToast
import com.example.ui.theme.BusinessBlue
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.PersonalOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
    val justCompletedTrip by viewModel.justCompletedTrip.collectAsState()

    // Receipt Scanner Dialog
    if (scanState.isDialogVisible) {
        ReceiptScanDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissReceiptScanner() }
        )
    }

    // End of Trip Review / Classification Bottom Sheet
    justCompletedTrip?.let { completedTrip ->
        EndTripBottomSheet(
            trip = completedTrip,
            isMiles = settings.unit == "mi",
            onClassify = { t, category -> viewModel.classifyTrip(t, category) },
            onDismiss = { viewModel.dismissJustCompletedTrip() }
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

    // Calculate total driven distance overall
    val totalLoggedKm = remember(trips) {
        trips.sumOf { it.distanceKm }
    }
    val currentDriveKm = if (trackingState.isTracking) trackingState.distanceKm else 0.0
    val totalCombinedKm = totalLoggedKm + currentDriveKm

    val displayDistance = if (isMiles) {
        totalCombinedKm * 0.621371
    } else {
        totalCombinedKm
    }
    val formattedDistance = String.format(Locale.US, "%.1f", displayDistance)

    // Calculate annual progress that automatically resets at the end of every year
    val currentCalendar = remember { Calendar.getInstance() }
    val currentYear = currentCalendar.get(Calendar.YEAR)
    val hourOfDay = currentCalendar.get(Calendar.HOUR_OF_DAY)
    val greetingText = remember(hourOfDay) {
        when (hourOfDay) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Start of current calendar year (Jan 1 00:00)
    val startOfYearMillis = remember(currentYear) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // End of current calendar year (Dec 31 23:59:59.999)
    val endOfYearMillis = remember(currentYear) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear + 1)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Distance in current calendar year only
    val yearTripsKm = remember(trips, startOfYearMillis, endOfYearMillis) {
        trips.filter { it.startDate in startOfYearMillis until endOfYearMillis }
            .sumOf { it.distanceKm }
    }
    val totalYearKm = yearTripsKm + (if (trackingState.isTracking) trackingState.distanceKm else 0.0)
    val yearDisplayDistance = if (isMiles) totalYearKm * 0.621371 else totalYearKm

    val annualGoal = settings.annualGoal.coerceAtLeast(100.0)
    val annualProgress = (yearDisplayDistance / annualGoal).toFloat().coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = annualProgress,
        animationSpec = tween(durationMillis = 800),
        label = "annual_progress"
    )

    // Unclassified trips waiting for user classification
    val unclassifiedTrips = remember(trips) {
        trips.filter { it.type == null || it.type.isEmpty() || it.type == "Unclassified" }
    }

    // Movement detection state: true when tracking is active or movement is detected
    val isDriverMoving = trackingState.isTracking ||
            trackingState.detectorState == DetectorState.MOVING ||
            trackingState.detectorState == DetectorState.RECORDING ||
            trackingState.speedKmh > 3.0f

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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val isCompactHeight = screenHeight < 640.dp

        // Dynamic responsive dimensions with generous circular dial size for perfect fit
        val dialSize = if (isCompactHeight) 176.dp else 206.dp
        val dialStrokeWidth = if (isCompactHeight) 11.dp else 13.dp
        val dialPaddingTop = if (isCompactHeight) 4.dp else 6.dp
        val dialPaddingBottom = if (isCompactHeight) 12.dp else 16.dp
        val distanceFontSize = if (isCompactHeight) 33.sp else 38.sp
        val titleFontSize = if (isCompactHeight) 24.sp else 27.sp
        val avatarSize = if (isCompactHeight) 38.dp else 42.dp

        Box(modifier = Modifier.fillMaxSize()) {
            if (isDriverMoving) {
                // When moving / driving: ONLY the trips should be on the home screen!
                // Shows the driving banner, active live drive card (if tracking), and unclassified trips waiting for classification.
                // Once classified, each trip is removed from the home screen.
                DrivingModeHomeScreen(
                    trackingState = trackingState,
                    unclassifiedTrips = unclassifiedTrips,
                    isMiles = isMiles,
                    unitLabel = unitLabel,
                    settings = settings,
                    pulseAlpha = pulseAlpha,
                    isCompactHeight = isCompactHeight,
                    hasLocationPermission = hasLocationPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onClassify = { targetTrip, classification ->
                        viewModel.classifyTrip(targetTrip, classification)
                    },
                    onDelete = { targetTrip ->
                        viewModel.deleteTrip(targetTrip)
                    },
                    onUpdateTrip = { updatedTrip ->
                        viewModel.updateTrip(updatedTrip)
                    },
                    onOpenDetail = { trip ->
                        viewModel.showCompletedTrip(trip)
                    },
                    onStopAndSave = { viewModel.stopManualTracking(context) },
                    onStopAndNext = { viewModel.recordDestinationStopAndStartNewTrip(context) }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFAFAFC))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = if (isCompactHeight) 8.dp else 12.dp)
                        .testTag("home_screen"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp)
                ) {
                    // 1. TOP HEADER: "Good evening" / "Your driving" + Profile Avatar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = greetingText,
                                    fontSize = if (isCompactHeight) 13.sp else 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "Your driving",
                                    fontSize = titleFontSize,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    letterSpacing = (-0.5).sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(avatarSize)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF))
                                    .clickable { viewModel.selectTab(AppTab.SETTINGS) }
                                    .testTag("home_profile_avatar"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(if (isCompactHeight) 22.dp else 24.dp)
                                )
                            }
                        }
                    }

                // 2. MAIN "YOUR DRIVING" CARD WITH CENTRAL DIAL & ANNUAL GOAL
                // Hidden when there are trips to be classified, returns once user has classified them.
                AnimatedVisibility(
                    visible = unclassifiedTrips.isEmpty(),
                    enter = fadeIn(tween(250)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(250))
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (isCompactHeight) 4.dp else 6.dp)
                            .testTag("home_total_driven_dial"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                top = dialPaddingTop,
                                bottom = dialPaddingBottom,
                                start = 12.dp,
                                end = 12.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Circular Dial dynamically proportioned to screen height
                        Box(
                            modifier = Modifier.size(dialSize),
                            contentAlignment = Alignment.Center
                        ) {
                            val trackColor = Color(0xFFE0EDFD)
                            val progressColor = Color(0xFF2563EB)

                            // Circular Progress Track
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                            ) {
                                val strokePx = dialStrokeWidth.toPx()
                                val arcSize = size.width - strokePx
                                val topLeftOffset = Offset(strokePx / 2f, strokePx / 2f)

                                // Background circular track
                                drawArc(
                                    color = trackColor,
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = topLeftOffset,
                                    size = Size(arcSize, arcSize),
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )

                                // Foreground annual progress arc
                                if (animatedProgress > 0f) {
                                    drawArc(
                                        color = progressColor,
                                        startAngle = -90f,
                                        sweepAngle = (animatedProgress * 360f).coerceIn(1.5f, 360f),
                                        useCenter = false,
                                        topLeft = topLeftOffset,
                                        size = Size(arcSize, arcSize),
                                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                    )
                                }
                            }

                            // Inner Center Content
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_launcher_asset),
                                    contentDescription = "App logo",
                                    modifier = Modifier
                                        .size(if (isCompactHeight) 34.dp else 40.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // "TOTAL DRIVEN"
                                Text(
                                    text = "TOTAL DRIVEN",
                                    fontSize = if (isCompactHeight) 10.5.sp else 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 1.sp
                                )

                                // Distance Number & Unit
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = formattedDistance,
                                        fontSize = distanceFontSize,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A),
                                        lineHeight = distanceFontSize
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = unitLabel,
                                        fontSize = if (isCompactHeight) 14.sp else 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                // "• Tracking is on" pill badge
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (trackingState.isTracking) Color(0xFFDBEAFE)
                                    else if (settings.autoDetect) Color(0xFFEFF6FF)
                                    else Color(0xFFF1F5F9)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(5.5.dp)
                                                .clip(CircleShape)
                                                .alpha(if (trackingState.isTracking) pulseAlpha else 1.0f)
                                                .background(
                                                    if (trackingState.isTracking) Color(0xFF2563EB)
                                                    else if (settings.autoDetect) Color(0xFF2563EB)
                                                    else Color(0xFF94A3B8)
                                                )
                                        )
                                        Text(
                                            text = if (trackingState.isTracking) "Driving"
                                            else if (settings.autoDetect) "Tracking is on"
                                            else "Tracking is off",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (trackingState.isTracking) Color(0xFF1E40AF)
                                            else if (settings.autoDetect) Color(0xFF1E40AF)
                                            else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 10.dp))

                        // Bottom Stats Row inside Card: "2026 • 6% / of 15,000 km" | "Resets Dec 31"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Progress towards goal
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$currentYear • ${String.format(Locale.US, "%.0f%%", annualProgress * 100f)}",
                                    fontSize = if (isCompactHeight) 13.5.sp else 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "of ${String.format(Locale.US, "%,.0f", annualGoal)} $unitLabel",
                                    fontSize = if (isCompactHeight) 11.sp else 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF64748B)
                                )
                            }

                            // Divider line
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(Color(0xFFE2E8F0))
                            )

                            // Right: Reset date
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Resets Dec 31",
                                    fontSize = if (isCompactHeight) 12.5.sp else 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }

                // 3. RECENT TRIPS SECTION (Optimized for Ended Trip after stopping)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Recent trips",
                        fontSize = if (isCompactHeight) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. Active live drive card if currently tracking
                    if (trackingState.isTracking) {
                        LiveDriveHomeCard(
                            trackingState = trackingState,
                            isMiles = isMiles,
                            pulseAlpha = pulseAlpha,
                            isCompact = isCompactHeight,
                            onStopAndSave = { viewModel.stopManualTracking(context) },
                            onStopAndNext = { viewModel.recordDestinationStopAndStartNewTrip(context) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 2. Recent trips listed in the reference format
                    val tripsToShow = unclassifiedTrips

                    if (tripsToShow.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (unclassifiedTrips.isNotEmpty()) {
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
                            }

                            // Render EACH trip using the reference image design
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
                                        },
                                        onOpenDetail = {
                                            viewModel.showCompletedTrip(trip)
                                        }
                                    )
                                }
                            }
                        }
                    } else if (!trackingState.isTracking) {
                        // Empty state: Car driving towards location icon
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_trips),
                                contentDescription = "Car driving towards destination pin",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isCompactHeight) 80.dp else 90.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Your trips will appear here",
                                fontSize = if (isCompactHeight) 14.5.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Drive as usual. Trips are recorded automatically.",
                                fontSize = if (isCompactHeight) 11.5.sp else 12.5.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Location permission request banner if not granted
                if (!hasLocationPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                        shape = RoundedCornerShape(10.dp),
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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

/**
 * Driving Mode layout displayed on the Home Screen when the user starts driving.
 * ONLY the trips are shown, using the reference trip card layout for each unclassified trip.
 * Once classified, each trip is removed from the home screen.
 */
@Composable
fun DrivingModeHomeScreen(
    trackingState: com.example.service.LiveTrackingState,
    unclassifiedTrips: List<TripEntity>,
    isMiles: Boolean,
    unitLabel: String,
    settings: com.example.data.MileageSettings,
    pulseAlpha: Float,
    isCompactHeight: Boolean,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onClassify: (TripEntity, String) -> Unit,
    onDelete: (TripEntity) -> Unit,
    onUpdateTrip: (TripEntity) -> Unit,
    onOpenDetail: (TripEntity) -> Unit,
    onStopAndSave: () -> Unit,
    onStopAndNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFC))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = if (isCompactHeight) 10.dp else 14.dp)
            .testTag("home_screen_driving_mode"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Sleek, minimal Driving Status Header (unobtrusive so trips remain the only focus)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_driving_mode_banner"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .alpha(pulseAlpha)
                            .background(Color(0xFF2563EB))
                    )
                    Column {
                        Text(
                            text = "DRIVING IN PROGRESS",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E3A8A),
                            letterSpacing = 0.5.sp
                        )
                        val displaySpeed = if (isMiles) trackingState.speedKmh * 0.621371f else trackingState.speedKmh
                        val speedText = if (trackingState.speedKmh > 1f) "${displaySpeed.toInt()} $unitLabel/h" else "Active GPS"
                        Text(
                            text = "$speedText • Trips waiting for review",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onStopAndSave,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFDC2626)
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("home_finish_drive_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Finish Drive",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Finish",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }
        }

        // 2. Unclassified Trips Section in the reference card format: ONLY shown until classified!
        // Once classified, each trip is immediately removed from the home screen.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_driving_trips_section"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recent trips",
                fontSize = if (isCompactHeight) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            if (unclassifiedTrips.isNotEmpty()) {
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

                unclassifiedTrips.forEach { trip ->
                    key(trip.id) {
                        ReferenceTripCard(
                            trip = trip,
                            isMiles = isMiles,
                            mileageRate = settings.reimbursementRate,
                            onClassify = onClassify,
                            onDelete = onDelete,
                            onUpdateTrip = onUpdateTrip,
                            onOpenDetail = { onOpenDetail(trip) }
                        )
                    }
                }
            } else {
                // When all trips are classified while moving
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_all_trips_classified_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "All trips classified ✓",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                            Text(
                                text = "Focus on the road. Trips will appear here until classified.",
                                fontSize = 12.sp,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }
                }
            }
        }

        // Location permission request banner if not granted
        if (!hasLocationPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestPermission() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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

/**
 * Live Drive card displayed under Recent trips on the Home Screen while tracking is active.
 */
@Composable
fun LiveDriveHomeCard(
    trackingState: com.example.service.LiveTrackingState,
    isMiles: Boolean,
    pulseAlpha: Float,
    isCompact: Boolean,
    onStopAndSave: () -> Unit,
    onStopAndNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unitLabel = if (isMiles) "mi" else "km"
    val displayDistance = if (isMiles) trackingState.distanceKm * 0.621371 else trackingState.distanceKm
    val hours = trackingState.elapsedSeconds / 3600
    val minutes = (trackingState.elapsedSeconds % 3600) / 60
    val seconds = trackingState.elapsedSeconds % 60
    val elapsedStr = if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_active_live_trip_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        border = BorderStroke(1.5.dp, Color(0xFF93C5FD)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (isCompact) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live status header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .alpha(pulseAlpha)
                            .background(Color(0xFF2563EB))
                    )
                    Text(
                        text = "Trip in progress",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White
                ) {
                    Text(
                        text = "⏱ $elapsedStr",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E40AF),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                    )
                }
            }

            // Distance & Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.2f", displayDistance),
                        fontSize = if (isCompact) 26.sp else 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        lineHeight = if (isCompact) 26.sp else 30.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unitLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (trackingState.speedKmh > 0f) {
                    val displaySpeed = if (isMiles) trackingState.speedKmh * 0.621371f else trackingState.speedKmh
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.0f", displaySpeed)} ${if (isMiles) "mph" else "km/h"}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }
            }

            // Stop / Finish buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStopAndSave,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    elevation = null,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isCompact) 34.dp else 38.dp)
                        .testTag("home_active_trip_finish_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Finish and Save",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Finish Drive", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = onStopAndNext,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF2563EB)),
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isCompact) 34.dp else 38.dp)
                        .testTag("home_active_trip_stop_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Stop and Next",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop & Next", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            }
        }
    }
}

/**
 * Compact swipeable and clickable card for pending unclassified trips shown under "Recent trips"
 * on the Home Screen. Optimized to fit perfectly on the user's device after stopping a trip.
 */
@Composable
fun CompactSwipeableHomeTripCard(
    trip: TripEntity,
    isMiles: Boolean,
    isCompact: Boolean,
    onOpenDetail: () -> Unit,
    onClassify: (TripEntity, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val thresholdPx = with(density) { 60.dp.toPx() }

    val distanceDisplay = if (isMiles) {
        val miles = trip.distanceKm * 0.621371
        String.format(Locale.US, "%.1f mi", miles)
    } else {
        String.format(Locale.US, "%.1f km", trip.distanceKm)
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val dateStr = remember(trip.startDate) { dateFormat.format(Date(trip.startDate)) }
    val startStr = remember(trip.startDate) { timeFormat.format(Date(trip.startDate)) }
    val endStr = remember(trip.endDate) { timeFormat.format(Date(trip.endDate)) }

    val origin = trip.startAddress?.ifBlank { "Trip Origin" } ?: "Trip Origin"
    val destination = trip.endAddress?.ifBlank { "Destination Stop" } ?: "Destination Stop"

    val isSwipingRight = offsetX > 0
    val progress = (abs(offsetX) / thresholdPx).coerceIn(0f, 1f)
    val isThresholdReached = abs(offsetX) >= thresholdPx

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .testTag("home_trip_card_${trip.id}")
    ) {
        // Background swipe indicators
        if (offsetX != 0f) {
            val bgColor = if (isSwipingRight) {
                BusinessBlue.copy(alpha = (0.25f + progress * 0.75f).coerceIn(0f, 1f))
            } else {
                PersonalOrange.copy(alpha = (0.25f + progress * 0.75f).coerceIn(0f, 1f))
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(bgColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isSwipingRight) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSwipingRight) {
                        Icon(
                            imageVector = Icons.Default.BusinessCenter,
                            contentDescription = "Business",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isThresholdReached) "Business ✓" else "Business",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    } else {
                        Text(
                            text = if (isThresholdReached) "Personal ✓" else "Personal",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Personal",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Foreground Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .clickable { onOpenDetail() }
                .pointerInput(trip.id) {
                    if (isAnimating) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (isThresholdReached) {
                                isAnimating = true
                                val targetX = if (offsetX > 0) size.width.toFloat() else -size.width.toFloat()
                                val category = if (offsetX > 0) "Business" else "Personal"
                                coroutineScope.launch {
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = targetX,
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                                    ) { value, _ -> offsetX = value }
                                    onClassify(trip, category)
                                    offsetX = 0f
                                    isAnimating = false
                                }
                            } else {
                                coroutineScope.launch {
                                    animate(
                                        initialValue = offsetX,
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f)
                                    ) { value, _ -> offsetX = value }
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                animate(
                                    initialValue = offsetX,
                                    targetValue = 0f
                                ) { value, _ -> offsetX = value }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isAnimating) {
                                offsetX = (offsetX + dragAmount).coerceIn(-400f, 400f)
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = if (isCompact) 7.dp else 9.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp)
            ) {
                // Header: Destination Stop Tag, Time & Distance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "📍 Stopped",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }

                        Text(
                            text = "$dateStr • $startStr - $endStr",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B)
                        )
                    }

                    Text(
                        text = distanceDisplay,
                        fontSize = if (isCompact) 13.5.sp else 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                // Route: Origin -> Destination
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                    )
                    Text(
                        text = origin,
                        fontSize = 11.5.sp,
                        color = Color(0xFF334155),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text("→", fontSize = 10.5.sp, color = Color(0xFF94A3B8))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Text(
                        text = destination,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Action Classification Buttons: [ Business ] & [ Personal ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onClassify(trip, "Business") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MileageBlue,
                            contentColor = Color.White
                        ),
                        elevation = null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isCompact) 30.dp else 34.dp)
                            .testTag("classify_business_${trip.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BusinessCenter,
                            contentDescription = "Business",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Business",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = { onClassify(trip, "Personal") },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isCompact) 30.dp else 34.dp)
                            .testTag("classify_personal_${trip.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Personal",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Personal",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }
    }
}
