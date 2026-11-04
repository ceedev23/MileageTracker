package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.GoogleTimelineImporter
import com.example.data.TripEntity
import com.example.ui.MileageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/** Import only explicitly approved driving segments; raw Timeline never leaves the device. */
@Composable
fun GoogleTimelineImportDialog(viewModel: MileageViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing by viewModel.trips.collectAsState()
    var candidates by remember { mutableStateOf<List<TripEntity>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var status by remember { mutableStateOf("Select a Google Maps Timeline JSON export. Only driving segments with recorded distances can be imported.") }
    var loading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            loading = true
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                            GoogleTimelineImporter.parse(reader.readText())
                        } ?: error("Unable to read the selected file.")
                    }
                }
                result.onSuccess { parsed ->
                    candidates = parsed.trips.filter { candidate ->
                        existing.none { saved ->
                            abs(saved.startDate - candidate.startDate) < 60_000L &&
                                abs(saved.endDate - candidate.endDate) < 60_000L
                        }
                    }
                    selected = candidates.indices.toSet()
                    status = "${candidates.size} new driving trips found. ${parsed.skipped} non-driving or incomplete segments skipped. Review before importing."
                }.onFailure { status = it.message ?: "Could not read Timeline JSON."; candidates = emptyList(); selected = emptySet() }
                loading = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Import Google Maps trips") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(status, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !loading, onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Text("Choose Timeline JSON")
                }
                if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (candidates.isNotEmpty()) {
                    Text("Selected: ${selected.size} / ${candidates.size}")
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        itemsIndexed(candidates) { index, trip ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Checkbox(checked = index in selected, onCheckedChange = { checked ->
                                    selected = if (checked) selected + index else selected - index
                                })
                                Column {
                                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(trip.startDate)))
                                    Text(String.format(java.util.Locale.US, "%.1f km · Unclassified", trip.distanceKm),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading && selected.isNotEmpty(), onClick = {
                candidates.forEachIndexed { index, trip -> if (index in selected) viewModel.saveTrip(trip) }
                onDismiss()
            }) { Text("Import ${selected.size} trips") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
