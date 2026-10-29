package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TripEntity
import com.example.ui.MileageViewModel
import com.example.ui.TripFilter
import com.example.ui.components.SwipeableTripCard
import com.example.ui.components.TripEditDialog
import com.example.ui.components.UndoToast
import com.example.ui.theme.BusinessBlue
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.MileageBlueContainer
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import com.example.ui.theme.UnclassifiedAmber
import com.example.ui.theme.UnclassifiedBg
import java.util.Locale

@Composable
fun TripsScreen(
    viewModel: MileageViewModel,
    modifier: Modifier = Modifier
) {
    val trips by viewModel.trips.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val undoAction by viewModel.undoAction.collectAsState()

    var tripToEdit by remember { mutableStateOf<TripEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var tripToDelete by remember { mutableStateOf<TripEntity?>(null) }

    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var selectMode by remember { mutableStateOf(false) }
    var showShifts by remember { mutableStateOf(false) }
    var bulkType by remember { mutableStateOf<String?>(null) }
    var bulkBusy by remember { mutableStateOf(false) }
    var bulkMessage by remember { mutableStateOf<String?>(null) }

    val isMiles = settings.unit == "mi"
    val unitLabel = if (isMiles) "mi" else "km"

    // Filter calculations
    val unclassified = trips.filter { it.type == null }
    val businessTrips = trips.filter { it.type == "Business" }
    val personalTrips = trips.filter { it.type == "Personal" }

    val businessDistance = businessTrips.sumOf { it.distanceKm }
    val displayBusinessDist = if (isMiles) businessDistance * 0.621371 else businessDistance

    val filteredTrips = when (activeFilter) {
        TripFilter.ALL -> trips
        TripFilter.UNCLASSIFIED -> unclassified
        TripFilter.BUSINESS -> businessTrips
        TripFilter.PERSONAL -> personalTrips
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    tripToEdit = null
                    showEditDialog = true
                },
                containerColor = MileageBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                shape = CircleShape,
                modifier = Modifier.testTag("add_trip_fab")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Manual Trip", tint = Color.White)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                // Top Summary Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "💼 ${String.format(Locale.getDefault(), "%.1f %s", displayBusinessDist, unitLabel)} business",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BusinessBlue
                            )
                            Text(
                                text = " · ",
                                fontSize = 14.sp,
                                color = SlateTextMuted
                            )
                            Text(
                                text = "${unclassified.size} to classify",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (unclassified.isNotEmpty()) Color(0xFFD97706) else SlateTextSecondary
                            )
                        }

                        Text(
                            text = "${trips.size} total",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = SlateTextSecondary
                        )
                    }
                }

                // Filter Chips Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = activeFilter == TripFilter.ALL,
                            onClick = { viewModel.setFilter(TripFilter.ALL) },
                            label = { Text("All (${trips.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == TripFilter.UNCLASSIFIED,
                            onClick = { viewModel.setFilter(TripFilter.UNCLASSIFIED) },
                            label = { Text("To Classify (${unclassified.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = UnclassifiedBg,
                                selectedLabelColor = UnclassifiedAmber
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == TripFilter.BUSINESS,
                            onClick = { viewModel.setFilter(TripFilter.BUSINESS) },
                            label = { Text("Business (${businessTrips.size})") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = activeFilter == TripFilter.PERSONAL,
                            onClick = { viewModel.setFilter(TripFilter.PERSONAL) },
                            label = { Text("Personal (${personalTrips.size})") }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectMode = !selectMode; selectedIds = emptySet() }, enabled = !bulkBusy) {
                        Text(if (selectMode) "Cancel selection" else "Select trips")
                    }
                    TextButton(onClick = { showShifts = true }, enabled = !bulkBusy && trips.isNotEmpty()) { Text("Select shift") }
                    if (selectMode) TextButton(onClick = { selectedIds = filteredTrips.map { it.id }.toSet() }, enabled = !bulkBusy) { Text("Select visible") }
                }
                if (selectedIds.isNotEmpty()) {
                    Text("${selectedIds.size} selected", modifier = Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        listOf("Business", "Personal").forEach { type ->
                            TextButton(onClick = { bulkType = type }, enabled = !bulkBusy) { Text("Mark $type") }
                        }
                    }
                }
                bulkMessage?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp)) }

                // Trips List or Empty State
                if (filteredTrips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MileageBlueContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MileageBlue
                                )
                            }

                            Text(
                                text = if (trips.isEmpty()) "No trips logged yet" else "No trips match this filter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )

                            Text(
                                text = if (trips.isEmpty())
                                    "Start tracking on the Home screen or seed sample trips to test the swipe-to-classify feature."
                                else
                                    "Try switching filters or swipe to classify other trips.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = SlateTextSecondary,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            if (trips.isEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { viewModel.seedSampleTrips() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MileageBlue,
                                        contentColor = Color.White
                                    ),
                                    elevation = null,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Seed Sample Trips", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = filteredTrips,
                            key = { it.id }
                        ) { trip ->
                            if (selectMode) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = trip.id in selectedIds, enabled = !bulkBusy, onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + trip.id else selectedIds - trip.id
                                })
                                Text("Select trip ${trip.id}")
                            }
                            SwipeableTripCard(
                                trip = trip,
                                isMiles = isMiles,
                                reimbursementRate = settings.reimbursementRate,
                                onClassify = { t, type -> viewModel.classifyTrip(t, type) },
                                onEdit = { t ->
                                    tripToEdit = t
                                    showEditDialog = true
                                },
                                onDelete = { t -> tripToDelete = t }
                            )
                        }
                    }
                }
            }

            // Undo Toast Bar positioned at bottom
            UndoToast(
                action = undoAction,
                onUndo = { viewModel.undoClassification() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    }

    if (showShifts) {
        AlertDialog(onDismissRequest = { showShifts = false }, title = { Text("Suggested shifts") },
            text = {
                LazyColumn(Modifier.height(320.dp)) {
                    item { Text("Same vehicle, breaks up to 4 hours, maximum 16 hours. Review selected trips before applying.") }
                    items(com.example.service.TripShifts.group(trips)) { shift ->
                        TextButton(onClick = {
                            selectedIds = shift.trips.map { it.id }.toSet()
                            viewModel.setFilter(TripFilter.ALL)
                            selectMode = true; showShifts = false
                        }) {
                            val format = java.text.SimpleDateFormat("MMM d HH:mm", Locale.getDefault())
                            Text("${format.format(java.util.Date(shift.start))} – ${format.format(java.util.Date(shift.end))} · ${shift.trips.size} trips · ${shift.trips.first().vehicle.orEmpty()}")
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showShifts = false }) { Text("Close") } })
    }
    bulkType?.let { type ->
        AlertDialog(onDismissRequest = { if (!bulkBusy) bulkType = null },
            title = { Text("Mark ${selectedIds.size} trips $type?") },
            text = { Text("This replaces the classification of every selected trip. Individual trip records stay separate.") },
            confirmButton = { TextButton(enabled = !bulkBusy, onClick = {
                bulkBusy = true
                viewModel.classifyTrips(selectedIds, type) { result ->
                    bulkBusy = false; bulkType = null
                    result.onSuccess { count ->
                        selectedIds = emptySet(); selectMode = false
                        bulkMessage = "$count trips saved as $type. Cloud sync status is shown in Settings."
                    }.onFailure { bulkMessage = "Could not save classifications. Try again." }
                }
            }) { Text(if (bulkBusy) "Saving…" else "Apply") } },
            dismissButton = { TextButton(enabled = !bulkBusy, onClick = { bulkType = null }) { Text("Cancel") } })
    }

    // Edit/Add Dialog
    if (showEditDialog) {
        TripEditDialog(
            trip = tripToEdit,
            isMiles = isMiles,
            workProfile = settings.workProfile,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                viewModel.saveTrip(updated)
                showEditDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (tripToDelete != null) {
        val target = tripToDelete!!
        val distText = if (isMiles) {
            String.format(Locale.getDefault(), "%.1f mi", target.distanceKm * 0.621371)
        } else {
            String.format(Locale.getDefault(), "%.1f km", target.distanceKm)
        }
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            title = {
                Text(
                    text = "Delete Trip?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this $distText trip? This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTrip(target)
                        tripToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFEF4444)
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
