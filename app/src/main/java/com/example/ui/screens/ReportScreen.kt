package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TripEntity
import com.example.ui.AppTab
import com.example.ui.MileageViewModel
import com.example.ui.theme.BusinessBlue
import com.example.ui.theme.DriveGreen
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.PersonalOrange
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import java.util.Calendar
import java.util.Locale

enum class ReportPeriod(val label: String) {
    ALL("All Time"),
    THIS_MONTH("This Month"),
    THIS_YEAR("This Year")
}

@Composable
fun ReportScreen(
    viewModel: MileageViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val trips by viewModel.trips.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val totalGasExpenses by viewModel.totalGasExpenses.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedPeriod by remember { mutableStateOf(ReportPeriod.ALL) }
    var showCsvPreview by remember { mutableStateOf(false) }

    val isMiles = settings.unit == "mi"
    val unitLabel = if (isMiles) "mi" else "km"
    val rate = settings.reimbursementRate

    // Filter trips by period
    val now = Calendar.getInstance()
    val currentMonth = now.get(Calendar.MONTH)
    val currentYear = now.get(Calendar.YEAR)

    val filteredTrips = trips.filter { trip ->
        val tripCal = Calendar.getInstance().apply { timeInMillis = trip.startDate }
        when (selectedPeriod) {
            ReportPeriod.ALL -> true
            ReportPeriod.THIS_MONTH -> tripCal.get(Calendar.MONTH) == currentMonth && tripCal.get(Calendar.YEAR) == currentYear
            ReportPeriod.THIS_YEAR -> tripCal.get(Calendar.YEAR) == currentYear
        }
    }

    val totalKm = filteredTrips.sumOf { it.distanceKm }
    val businessKm = filteredTrips.filter { it.type == "Business" }.sumOf { it.distanceKm }
    val personalKm = filteredTrips.filter { it.type == "Personal" }.sumOf { it.distanceKm }

    val totalDist = if (isMiles) totalKm * 0.621371 else totalKm
    val businessDist = if (isMiles) businessKm * 0.621371 else businessKm
    val personalDist = if (isMiles) personalKm * 0.621371 else personalKm

    val estimatedTaxValue = businessDist * rate
    val businessRatio = if (totalDist > 0) (businessDist / totalDist).toFloat() else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Tax & Mileage Report",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SlateTextPrimary
            )
            Text(
                text = "CRA & IRS standard mileage deduction calculations",
                style = MaterialTheme.typography.bodySmall,
                color = SlateTextSecondary
            )
        }

        // Period Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportPeriod.values().forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period },
                    label = { Text(period.label) }
                )
            }
        }

        // Primary Hero Card (Driversnote / CRA report style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MileageBlue),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ESTIMATED TAX DEDUCTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = String.format(Locale.getDefault(), "$%.2f", estimatedTaxValue),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                val businessDistStr = String.format(Locale.getDefault(), "%.1f", businessDist)
                val rateStr = String.format(Locale.getDefault(), "$%.2f", rate)
                Text(
                    text = "Based on $businessDistStr $unitLabel business @ $rateStr/unit",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Stats strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Distance", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(
                            "${String.format(Locale.getDefault(), "%.1f", totalDist)} $unitLabel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Trips", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(
                            "${filteredTrips.size}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Business vs Personal Breakdown Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Mileage Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary
                )

                // Progress Bar
                LinearProgressIndicator(
                    progress = { businessRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = BusinessBlue,
                    trackColor = PersonalOrange
                )

                // Breakdown Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(BusinessBlue, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Business", fontSize = 12.sp, color = SlateTextSecondary)
                            Text(
                                "${String.format(Locale.getDefault(), "%.1f", businessDist)} $unitLabel (${(businessRatio * 100).toInt()}%)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(PersonalOrange, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Personal", fontSize = 12.sp, color = SlateTextSecondary)
                            Text(
                                "${String.format(Locale.getDefault(), "%.1f", personalDist)} $unitLabel (${((1f - businessRatio) * 100).toInt()}%)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Tracked Fuel & Gas Expenses Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = null,
                            tint = DriveGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Tracked Fuel Receipts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextPrimary
                        )
                    }

                    Text(
                        text = "$${String.format(Locale.US, "%.2f", totalGasExpenses ?: 0.0)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                    )
                }

                Text(
                    text = "${receipts.size} receipts captured with AI-extracted totals. Keep digital photo copies for tax deductions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )

                OutlinedButton(
                    onClick = { viewModel.selectTab(AppTab.RECEIPTS) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View & Scan Gas Receipts")
                }
            }
        }

        // Export Actions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MileageBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Official Log Export (CSV)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )
                }

                Text(
                    text = "Generate a spreadsheet with date, start address, destination, distances, purpose, and reimbursement calculations ready for tax filing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showCsvPreview = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Preview CSV", fontSize = 13.sp)
                    }

                    Button(
                        onClick = { viewModel.shareCsvReport(context) },
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("export_csv_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MileageBlue,
                            contentColor = Color.White
                        ),
                        elevation = null
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export & Share", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // CSV Preview Dialog
    if (showCsvPreview) {
        val previewData = viewModel.generateCsv(filteredTrips, isMiles, rate)
        AlertDialog(
            onDismissRequest = { showCsvPreview = false },
            title = { Text("CSV Report Preview") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = previewData,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFE2E8F0)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCsvPreview = false
                        viewModel.shareCsvReport(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MileageBlue,
                        contentColor = Color.White
                    ),
                    elevation = null
                ) {
                    Text("Share", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCsvPreview = false }) {
                    Text("Close")
                }
            }
        )
    }
}
