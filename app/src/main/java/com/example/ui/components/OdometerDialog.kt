package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.InputCheck
import com.example.service.ObdBluetoothReader
import com.example.service.ObdDevice
import com.example.service.Odometer
import com.example.ui.MileageViewModel
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.StopRed
import java.util.Locale

@Composable
fun OdometerDialog(
    viewModel: MileageViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val isMiles = settings.unit == "mi"
    val unitLabel = if (isMiles) "mi" else "km"

    var readingText by remember {
        val saved = settings.odometerKm
        val shown = saved?.let { if (isMiles) it * 0.621371 else it }
        mutableStateOf(shown?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var source by remember { mutableStateOf("manual") }
    var logTrip by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<ObdDevice>?>(null) }

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
    val deltaKm = enteredKm?.let { Odometer.tripKm(settings.odometerKm, it) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Odometer", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = settings.odometerKm?.let { km ->
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
                    },
                    label = { Text("Odometer ($unitLabel)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )
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
                if (devices == null) {
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
                } else {
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
                    viewModel.saveOdometerReading(km, source, logTrip && deltaKm != null)
                    onDismiss()
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = MileageBlue, contentColor = Color.White),
                elevation = null
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        }
    )
}
