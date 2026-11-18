package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.InputCheck
import com.example.service.ObdBluetoothReader
import com.example.service.ObdDevice
import com.example.service.Odometer
import com.example.service.OdometerTextParser
import com.example.ui.MileageViewModel
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.StopRed
import java.util.Locale

@Composable
fun OdometerDialog(
    viewModel: MileageViewModel,
    onDismiss: () -> Unit,
    openCameraOnStart: Boolean = false
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val isMiles = settings.unit == "mi"
    val unitLabel = if (isMiles) "mi" else "km"

    var readingText by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showAdapter by remember { mutableStateOf(false) }
    var deleteReadingId by remember { mutableStateOf<Long?>(null) }
    var source by remember { mutableStateOf("manual") }
    val readings by viewModel.odometerReadings.collectAsState()
    val latest = readings.firstOrNull { it.vehicle == settings.vehicleName }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var photoText by remember { mutableStateOf("") }
    var photoReviewed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<ObdDevice>?>(null) }
    var pendingCapture by remember { mutableStateOf<File?>(null) }

    fun dismiss() {
        photoPath?.let { viewModel.discardOdometerPhoto(it) }
        onDismiss()
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            viewModel.importOdometerPhoto(uri) { result ->
                busy = false
                result.onSuccess { (path, text) ->
                    photoPath?.let { viewModel.discardOdometerPhoto(it) }
                    photoPath = path; photoText = text; photoReviewed = false
                }.onFailure { messageIsError = true; message = it.message ?: "Could not read photo" }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val file = pendingCapture
        pendingCapture = null
        if (captured && file != null) {
            busy = true
            viewModel.importOdometerPhoto(Uri.fromFile(file)) { result ->
                busy = false
                file.delete()
                result.onSuccess { (path, text) ->
                    photoPath?.let { viewModel.discardOdometerPhoto(it) }
                    photoPath = path
                    photoText = text
                    photoReviewed = false
                    source = "camera"
                    val candidate = OdometerTextParser.candidate(text)
                    if (candidate != null) {
                        readingText = candidate
                        message = "Number detected. Confirm it matches the dashboard before saving."
                        messageIsError = false
                    } else {
                        message = "Check the photo and enter the odometer number manually."
                        messageIsError = false
                    }
                }.onFailure { messageIsError = true; message = it.message ?: "Could not read camera photo" }
            }
        } else file?.delete()
    }
    fun takeOdometerPhoto() {
        try {
            val dir = File(context.cacheDir, "odometer_captures").apply { mkdirs() }
            val file = File.createTempFile("odometer_", ".jpg", dir)
            pendingCapture = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraLauncher.launch(uri)
        } catch (error: Exception) {
            pendingCapture?.delete()
            pendingCapture = null
            messageIsError = true
            message = error.message ?: "Could not open camera"
        }
    }
    LaunchedEffect(openCameraOnStart) {
        if (openCameraOnStart) takeOdometerPhoto()
    }

    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var hasPermission by remember {
        mutableStateOf(
            !needsPermission || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) devices = ObdBluetoothReader.pairedDevices(context)
        else {
            messageIsError = true
            message = "Bluetooth permission is required to read the car. You can still type the reading."
        }
    }

    val entered = readingText.replace(",", "").toDoubleOrNull()
    val enteredKm = entered?.let { if (isMiles) it / 0.621371 else it }
    val deltaKm = enteredKm?.let { Odometer.tripKm(latest?.readingKm, it) }
    val trips by viewModel.trips.collectAsState()
    val intervalTrips = if (latest == null) emptyList() else trips.filter {
        (it.vehicle == settings.vehicleName || it.vehicle.isNullOrBlank()) &&
            it.endDate > latest.recordedAt && it.startDate <= System.currentTimeMillis()
    }
    val recordedKm = intervalTrips.sumOf { it.distanceKm }
    val hasCrossingTrip = latest != null && intervalTrips.any { it.startDate < latest.recordedAt }

    deleteReadingId?.let { id ->
        StyledAlertDialog(onDismissRequest = { if (!busy) deleteReadingId = null },
            title = { Text("Delete odometer reading?") },
            text = { Text("This removes the saved reading and its local photo. Existing trip records will remain.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                viewModel.deleteOdometerReading(id) { result ->
                    busy = false; deleteReadingId = null
                    result.onFailure { messageIsError = true; message = "Could not delete reading" }
                }
            }) { Text("Delete") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleteReadingId = null }) { Text("Cancel") } })
    }

    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(30.dp),
        color = Color.White,
        shadowElevation = 12.dp
      ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Outlined.Speed, contentDescription = null, tint = MileageBlue, modifier = Modifier.size(28.dp))
            Text(
              "Save Odometer Reading",
              fontWeight = FontWeight.Bold,
              fontSize = 21.sp,
              color = Color(0xFF091638),
              textAlign = TextAlign.Center
            )
          }
          Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Outlined.DirectionsCar, contentDescription = null, tint = SlateTextSecondary, modifier = Modifier.size(22.dp))
              Text(settings.vehicleName, color = SlateTextSecondary, fontSize = 16.sp)
            }
            if (latest == null) {
              Text("No reading saved yet", modifier = Modifier.padding(top = 8.dp), color = SlateTextSecondary, fontSize = 13.sp)
            } else {
              val shown = if (isMiles) latest.readingKm * 0.621371 else latest.readingKm
              Box(
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .clickable(
                    enabled = !busy,
                    role = Role.Button,
                    onClickLabel = if (showHistory) "Hide reading history" else "View reading history"
                  ) { showHistory = !showHistory }
                  .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  "Last saved ${String.format(Locale.US, "%,.1f", shown)} $unitLabel",
                  color = SlateTextSecondary,
                  fontSize = 13.sp,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = readingText,
                    onValueChange = { next ->
                        if (!InputCheck.decimalText(next, 7, 1)) return@OutlinedTextField
                        readingText = next
                        source = "manual"
                        message = null
                        photoReviewed = false
                    },
                    placeholder = { Text("Odometer reading", color = SlateTextSecondary) },
                    trailingIcon = {
                        Text(unitLabel, modifier = Modifier.padding(end = 4.dp), color = Color(0xFF091638), fontWeight = FontWeight.Bold)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MileageBlue,
                        unfocusedBorderColor = SlateTextSecondary,
                        disabledBorderColor = SlateTextSecondary,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    OdometerActionRow("Take photo", Icons.Outlined.CameraAlt, !busy) { takeOdometerPhoto() }
                    HorizontalDivider(color = SlateBorder)
                    OdometerActionRow("Choose photo", Icons.Outlined.Image, !busy) { photoLauncher.launch("image/*") }
                    HorizontalDivider(color = SlateBorder)
                    OdometerActionRow(
                        if (showAdapter) "Hide Bluetooth Options" else "Bluetooth OBD-II",
                        Icons.Outlined.Bluetooth,
                        !busy
                    ) { showAdapter = !showAdapter }
                }
                if (photoPath != null) {
                    coil.compose.AsyncImage(model = photoPath, contentDescription = "Odometer photo for manual review", modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp))
                    Text("Photo stays on this device and is excluded from Android backup. OCR does not verify authenticity.")
                    Text(if (photoText.isBlank()) "No text detected. Check the photo manually." else "Detected text (check manually): ${photoText.take(300)}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = photoReviewed, onCheckedChange = { photoReviewed = it }, enabled = !busy)
                        Text("I checked the entered number and km/mi against the photo")
                    }
                }
                if (showHistory) readings.filter { it.vehicle == settings.vehicleName }.take(20).forEach { reading ->
                    val shown = if (isMiles) reading.readingKm * 0.621371 else reading.readingKm
                    Text("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(reading.recordedAt))}: ${String.format(Locale.US, "%.1f", shown)} $unitLabel · ${reading.source}${if (reading.photoReviewed) " · photo reviewed" else ""}")
                    Button(
                        onClick = { deleteReadingId = reading.id },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFDECEC),
                            contentColor = Color(0xFF9F2424)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            "Delete reading and photo",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (deltaKm != null) {
                    val shownDelta = if (isMiles) deltaKm * 0.621371 else deltaKm
                    val remaining = deltaKm - recordedKm
                    val shownRemaining = if (isMiles) remaining * 0.621371 else remaining
                    Text("Odometer distance: ${String.format(Locale.US, "%,.1f", shownDelta)} $unitLabel", fontSize = 13.sp)
                    Text("Recorded trip distance: ${String.format(Locale.US, "%,.1f", if (isMiles) recordedKm * 0.621371 else recordedKm)} $unitLabel", fontSize = 13.sp)
                    Text(
                        if (hasCrossingTrip) "Some trips cross the reading boundary. Review your logbook to reconcile the difference."
                        else if (remaining < -0.1) "Recorded trips exceed the odometer difference. Review the records."
                        else if (remaining > 0.1) "${String.format(Locale.US, "%,.1f", shownRemaining)} $unitLabel is not accounted for. Save this reading, then add any missing journey in Trips with its date, destination and purpose. Unexplained distance is not a business trip."
                        else "Odometer and recorded trips agree within 0.1 km. No additional trip is needed.",
                        fontSize = 13.sp, color = SlateTextSecondary
                    )
                }
                if (showAdapter && devices == null) {
                    OutlinedButton(
                        onClick = {
                            if (!hasPermission) {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else {
                                message = null
                                devices = ObdBluetoothReader.pairedDevices(context)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Read from paired adapter")
                    }
                } else if (showAdapter) {
                    val problem = ObdBluetoothReader.unavailableReason(context)
                    when {
                        problem != null -> {
                            Text(problem, color = StopRed, fontSize = 13.sp)
                            TextButton(onClick = { devices = ObdBluetoothReader.pairedDevices(context) }, enabled = !busy) {
                                Text("Try again")
                            }
                        }
                        devices.orEmpty().isEmpty() -> {
                            Text(
                                "Pair an OBD-II adapter in Android Bluetooth settings, then try again.",
                                color = SlateTextSecondary,
                                fontSize = 13.sp
                            )
                            TextButton(
                                onClick = { devices = ObdBluetoothReader.pairedDevices(context) },
                                enabled = !busy
                            ) { Text("Try again") }
                        }
                        else -> devices.orEmpty().forEach { device ->
                            TextButton(
                                onClick = {
                                    busy = true
                                    messageIsError = false
                                    message = "Reading ${device.name}…"
                                    viewModel.readCarOdometer(device.address) { result ->
                                        busy = false
                                        result.onSuccess { km ->
                                            val shown = if (isMiles) km * 0.621371 else km
                                            readingText = String.format(Locale.US, "%.1f", shown)
                                            source = "bluetooth"
                                            photoReviewed = false
                                            messageIsError = false
                                            message = "Read from ${device.name}. Save to keep it."
                                        }.onFailure { error ->
                                            messageIsError = true
                                            message = error.message ?: "Could not read the odometer."
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(device.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                message?.let {
                    Text(it, color = if (messageIsError) StopRed else SlateTextSecondary, fontSize = 13.sp)
                }
            }
          Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { dismiss() }, enabled = !busy, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE4E8EF), contentColor = Color(0xFF091638))) {
              Text("Cancel", fontWeight = FontWeight.Bold)
            }
            Button(
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    val km = enteredKm?.let { InputCheck.odometerKm(it) }
                    if (km == null) {
                        messageIsError = true
                        message = "Enter the number on the dash."
                        return@Button
                    }
                    busy = true
                    viewModel.saveOdometerReading(km, source, false, photoPath, photoReviewed) { result ->
                        busy = false
                        result.onSuccess { photoPath = null; onDismiss() }
                            .onFailure { messageIsError = true; message = it.message ?: "Could not save reading" }
                    }
                },
                enabled = !busy && enteredKm?.let { InputCheck.odometerKm(it) } != null && (photoPath == null || photoReviewed),
                colors = ButtonDefaults.buttonColors(containerColor = MileageBlue, contentColor = Color.White)
            ) { Text("Save", fontWeight = FontWeight.Bold, color = LocalContentColor.current) }
          }
        }
      }
    }
}

@Composable
private fun OdometerActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val actionColor = if (enabled) Color(0xFF091638) else SlateTextSecondary
    val iconColor = if (enabled) MileageBlue else SlateTextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(23.dp))
        Text(title, modifier = Modifier.weight(1f).padding(start = 14.dp), color = actionColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = actionColor)
    }
}
