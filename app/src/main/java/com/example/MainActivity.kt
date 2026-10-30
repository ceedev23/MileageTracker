package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.example.ui.AppTab
import com.example.ui.MileageViewModel
import com.example.ui.components.OdometerDialog
import com.example.ui.components.ReceiptScanDialog
import com.example.ui.components.TripEditDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReceiptsScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TripsScreen
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.MileageBlueContainer
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: MileageViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    setTheme(R.style.Theme_MyApplication)
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    )
    setContent {
      MyApplicationTheme {
        MileageTrackerApp(viewModel = viewModel)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageTrackerApp(viewModel: MileageViewModel) {
  val context = LocalContext.current
  val currentTab by viewModel.currentTab.collectAsState()
  val trips by viewModel.trips.collectAsState()
  val receipts by viewModel.receipts.collectAsState()
  val settings by viewModel.settings.collectAsState()
  val trackingState by viewModel.trackingState.collectAsState()
  val scanState by viewModel.receiptScanState.collectAsState()

  val unclassifiedCount = trips.count { it.type == null }

  var showQuickActionsSheet by remember { mutableStateOf(false) }
  var showManualTripDialog by remember { mutableStateOf(false) }
  var showOdometerDialog by remember { mutableStateOf(false) }
  val userProfile by viewModel.userProfile.collectAsState()
  val isAuthPromptVisible by viewModel.isAuthPromptVisible.collectAsState()

  if (isAuthPromptVisible) {
    AuthScreen(
      viewModel = viewModel,
      onDismiss = { viewModel.dismissAuthScreen() },
      canDismiss = userProfile != null
    )
    return
  }

  BackHandler(enabled = currentTab != AppTab.HOME) {
    viewModel.selectTab(AppTab.HOME)
  }

  val showLiveDrive = currentTab == AppTab.HOME && trackingState.showsLiveDrive()

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = if (showLiveDrive || currentTab == AppTab.HOME) {
      WindowInsets(0, 0, 0, 0)
    } else {
      ScaffoldDefaults.contentWindowInsets
    },
    bottomBar = {
      if (!showLiveDrive) {
      val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MileageBlue,
        selectedTextColor = MileageBlue,
        indicatorColor = MileageBlueContainer,
        unselectedIconColor = Color(0xFF94A3B8),
        unselectedTextColor = Color(0xFF64748B)
      )

      NavigationBar(
        modifier = Modifier.testTag("bottom_nav_bar"),
        containerColor = Color.White,
        tonalElevation = 8.dp
      ) {
        // 1. Home Tab
        NavigationBarItem(
          selected = currentTab == AppTab.HOME,
          onClick = { viewModel.selectTab(AppTab.HOME) },
          icon = {
            Icon(
              imageVector = Icons.Default.DirectionsCar,
              contentDescription = "Home"
            )
          },
          label = {
            Text(
              "Home",
              fontWeight = if (currentTab == AppTab.HOME) FontWeight.Bold else FontWeight.Normal
            )
          },
          colors = navItemColors,
          modifier = Modifier.testTag("nav_tab_home")
        )

        // 2. Trips Tab (with badge for unclassified count)
        NavigationBarItem(
          selected = currentTab == AppTab.TRIPS,
          onClick = { viewModel.selectTab(AppTab.TRIPS) },
          icon = {
            if (unclassifiedCount > 0) {
              BadgedBox(
                badge = {
                  Badge(
                    containerColor = Color(0xFFD97706),
                    contentColor = Color.White
                  ) {
                    Text(unclassifiedCount.toString())
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                  contentDescription = "Trips"
                )
              }
            } else {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = "Trips"
              )
            }
          },
          label = {
            Text(
              "Trips",
              fontWeight = if (currentTab == AppTab.TRIPS) FontWeight.Bold else FontWeight.Normal
            )
          },
          colors = navItemColors,
          modifier = Modifier.testTag("nav_tab_trips")
        )

        // 3. Central Big Circle with Plus Sign in the middle (Replaces the receipts button)
        Box(
          modifier = Modifier.weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Surface(
            onClick = { showQuickActionsSheet = true },
            shape = CircleShape,
            color = MileageBlue,
            shadowElevation = 0.dp,
            modifier = Modifier
              .size(65.dp)
              .testTag("nav_circle_plus_button")
          ) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Quick Add or Scan",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
              )
            }
          }
        }

        // 4. Report Tab
        NavigationBarItem(
          selected = currentTab == AppTab.REPORT,
          onClick = { viewModel.selectTab(AppTab.REPORT) },
          icon = {
            Icon(
              imageVector = Icons.Default.Assessment,
              contentDescription = "Report"
            )
          },
          label = {
            Text(
              "Report",
              fontWeight = if (currentTab == AppTab.REPORT) FontWeight.Bold else FontWeight.Normal
            )
          },
          colors = navItemColors,
          modifier = Modifier.testTag("nav_tab_report")
        )

        // 5. Settings Tab
        NavigationBarItem(
          selected = currentTab == AppTab.SETTINGS,
          onClick = { viewModel.selectTab(AppTab.SETTINGS) },
          icon = {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings"
            )
          },
          label = {
            Text(
              "Settings",
              fontWeight = if (currentTab == AppTab.SETTINGS) FontWeight.Bold else FontWeight.Normal
            )
          },
          colors = navItemColors,
          modifier = Modifier.testTag("nav_tab_settings")
        )
      }
      }
    }
  ) { innerPadding ->
    Crossfade(
      targetState = currentTab,
      label = "screen_crossfade",
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) { tab ->
      when (tab) {
        AppTab.HOME -> HomeScreen(viewModel = viewModel)
        AppTab.TRIPS -> TripsScreen(viewModel = viewModel)
        AppTab.RECEIPTS -> ReceiptsScreen(viewModel = viewModel)
        AppTab.REPORT -> ReportScreen(viewModel = viewModel)
        AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
      }
    }
  }

  // Quick Action Sheet opened by the Central Plus Circle Button
  if (showQuickActionsSheet) {
    ModalBottomSheet(
      onDismissRequest = { showQuickActionsSheet = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = Color.White,
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 8.dp)
          .padding(bottom = 24.dp)
      ) {
        Text(
          text = "Quick Actions",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF0F172A),
          modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. Live Drive Toggle Action
        QuickActionItem(
          title = if (trackingState.isTracking) "Stop Live Drive" else "Start Live Drive",
          subtitle = if (trackingState.isTracking) "Finish and review route" else "Real-time GPS mileage tracker",
          icon = if (trackingState.isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
          iconBgColor = if (trackingState.isTracking) Color(0xFFFEE2E2) else Color(0xFFEFF6FF),
          iconTint = if (trackingState.isTracking) Color(0xFFDC2626) else Color(0xFF2563EB),
          onClick = {
            showQuickActionsSheet = false
            if (trackingState.isTracking) {
              viewModel.stopManualTracking(context)
            } else {
              viewModel.startManualTracking(context)
            }
            viewModel.selectTab(AppTab.HOME)
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Add Manual Trip Action
        QuickActionItem(
          title = "Add Manual Trip",
          subtitle = "Log past drive, odometer or addresses",
          icon = Icons.Default.AddRoad,
          iconBgColor = MileageBlueContainer,
          iconTint = MileageBlue,
          onClick = {
            showQuickActionsSheet = false
            showManualTripDialog = true
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        val odometerSubtitle = settings.odometerKm?.let { km ->
          val shown = if (settings.unit == "mi") km * 0.621371 else km
          val unit = if (settings.unit == "mi") "mi" else "km"
          "Last ${String.format(Locale.US, "%,.1f", shown)} $unit"
        } ?: "Bluetooth OBD adapter, or type the dash reading"
        QuickActionItem(
          title = "Odometer",
          subtitle = odometerSubtitle,
          icon = Icons.Default.Bluetooth,
          iconBgColor = Color(0xFFECFDF5),
          iconTint = Color(0xFF059669),
          onClick = {
            showQuickActionsSheet = false
            showOdometerDialog = true
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Scan Gas Receipt Action
        QuickActionItem(
          title = "Scan Gas Receipt",
          subtitle = "Instant camera OCR for fuel & litres",
          icon = Icons.AutoMirrored.Filled.ReceiptLong,
          iconBgColor = Color(0xFFFEF3C7),
          iconTint = Color(0xFFD97706),
          onClick = {
            showQuickActionsSheet = false
            viewModel.openReceiptScanner()
          }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. View All Receipts Action
        QuickActionItem(
          title = "View All Receipts",
          subtitle = "${receipts.size} receipt${if (receipts.size == 1) "" else "s"} logged",
          icon = Icons.Default.Receipt,
          iconBgColor = Color(0xFFF1F5F9),
          iconTint = Color(0xFF475569),
          onClick = {
            showQuickActionsSheet = false
            viewModel.selectTab(AppTab.RECEIPTS)
          }
        )
      }
    }
  }

  if (showOdometerDialog) {
    OdometerDialog(
      viewModel = viewModel,
      onDismiss = { showOdometerDialog = false }
    )
  }

  // Manual Trip Dialog launched from Quick Actions
  if (showManualTripDialog) {
    TripEditDialog(
      trip = null,
      isMiles = settings.unit == "mi",
      onDismiss = { showManualTripDialog = false },
      onSave = { newTrip ->
        viewModel.saveTrip(newTrip)
        showManualTripDialog = false
      }
    )
  }

  // Global Receipt Scanner Dialog (if opened from quick actions or subscreen)
  if (scanState.isDialogVisible && currentTab != AppTab.HOME && currentTab != AppTab.RECEIPTS) {
    ReceiptScanDialog(
      viewModel = viewModel,
      onDismiss = { viewModel.dismissReceiptScanner() }
    )
  }
}

@Composable
private fun QuickActionItem(
  title: String,
  subtitle: String,
  icon: ImageVector,
  iconBgColor: Color,
  iconTint: Color,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFFF8FAFC),
    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Surface(
        shape = CircleShape,
        color = iconBgColor,
        modifier = Modifier.size(44.dp)
      ) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          color = Color(0xFF0F172A)
        )
        Text(
          text = subtitle,
          fontSize = 12.sp,
          color = Color(0xFF64748B)
        )
      }
    }
  }
}

