package com.example.ui.components

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
    var logTrip by remember { mutableStateOf(false) }
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

    deleteReadingId?.let { id ->
        AlertDialog(onDismissRequest = { if (!busy) deleteReadingId = null },
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

    AlertDialog(
        onDismissRequest = { if (!busy) dismiss() },
        title = { Text("Save odometer reading", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = latest?.readingKm?.let { km ->
                        val shown = if (isMiles) km * 0.621371 else km
                        "Last saved ${String.format(Locale.US, "%,.1f", shown)} $unitLabel"
                    } ?: "No reading saved yet",
                    color = SlateTextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = readingText,
                    onValueChange = { next ->
                        if (!InputCheck.decimalText(next, 7, 1)) return@OutlinedTextField
                        readingText = next
                        source = "manual"
                        message = null
                        photoReviewed = false
                    },
                    label = { Text("Odometer ($unitLabel)") },
                    placeholder = { Text("Enter odometer reading") },
                    trailingIcon = { Text(unitLabel, color = SlateTextSecondary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MileageBlue,
                        unfocusedBorderColor = MileageBlue,
                        disabledBorderColor = SlateTextSecondary,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    supportingText = { Text("Enter the number displayed on your dashboard. Check the unit before saving.") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )
                Text("Vehicle: ${settings.vehicleName}")
                OutlinedButton(onClick = { takeOdometerPhoto() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Take odometer photo") }
                OutlinedButton(onClick = { photoLauncher.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Choose existing photo") }
                if (photoPath != null) {
                    coil.compose.AsyncImage(model = photoPath, contentDescription = "Odometer photo for manual review", modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp))
                    Text("Photo stays on this device and is excluded from Android backup. OCR does not verify authenticity.")
                    Text(if (photoText.isBlank()) "No text detected. Check the photo manually." else "Detected text (check manually): ${photoText.take(300)}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = photoReviewed, onCheckedChange = { photoReviewed = it }, enabled = !busy)
                        Text("I checked the entered number and km/mi against the photo")
                    }
                }
                TextButton(onClick = { showHistory = !showHistory }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showHistory) "Hide reading history" else "View reading history")
                }
                if (showHistory) readings.filter { it.vehicle == settings.vehicleName }.take(20).forEach { reading ->
                    val shown = if (isMiles) reading.readingKm * 0.621371 else reading.readingKm
                    Text("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(reading.recordedAt))}: ${String.format(Locale.US, "%.1f", shown)} $unitLabel · ${reading.source}${if (reading.photoReviewed) " · photo reviewed" else ""}")
                    TextButton(enabled = !busy, onClick = {
                        deleteReadingId = reading.id
                    }) { Text("Delete reading and photo") }
                }
                if (deltaKm != null) {
                    val shownDelta = if (isMiles) deltaKm * 0.621371 else deltaKm
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = logTrip, onCheckedChange = { logTrip = it }, enabled = !busy)
                        Text(
                            text = "Log ${String.format(Locale.US, "%,.1f", shownDelta)} $unitLabel since the last reading",
                            fontSize = 13.sp
                        )
                    }
                }
                TextButton(onClick = { showAdapter = !showAdapter }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAdapter) "Hide Bluetooth adapter options" else "Use Bluetooth OBD-II adapter")
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val km = enteredKm?.let { InputCheck.odometerKm(it) }
                    if (km == null) {
                        messageIsError = true
                        message = "Enter the number on the dash."
                        return@Button
                    }
                    busy = true
                    viewModel.saveOdometerReading(km, source, logTrip && deltaKm != null, photoPath, photoReviewed) { result ->
                        busy = false
                        result.onSuccess { photoPath = null; onDismiss() }
                            .onFailure { messageIsError = true; message = it.message ?: "Could not save reading" }
                    }
                },
                enabled = !busy && enteredKm?.let { InputCheck.odometerKm(it) } != null && (photoPath == null || photoReviewed),
                colors = ButtonDefaults.buttonColors(containerColor = MileageBlue, contentColor = Color.White),
                elevation = null
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { dismiss() }, enabled = !busy) { Text("Cancel") }
        }
    )
}
