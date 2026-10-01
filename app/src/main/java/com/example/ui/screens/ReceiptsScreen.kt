package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.GasReceiptEntity
import com.example.ui.MileageViewModel
import com.example.ui.components.ReceiptScanDialog
import com.example.ui.theme.DriveGreen
import com.example.ui.theme.DriveGreenLight
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.MileageBlueContainer
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurfaceVariant
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptsScreen(
    viewModel: MileageViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val receipts by viewModel.receipts.collectAsState()
    val totalGasExpenses by viewModel.totalGasExpenses.collectAsState()
    val scanState by viewModel.receiptScanState.collectAsState()

    var selectedReceiptForDetail by remember { mutableStateOf<GasReceiptEntity?>(null) }
    var receiptToDelete by remember { mutableStateOf<GasReceiptEntity?>(null) }

    // Scanner / Photo Dialog
    if (scanState.isDialogVisible) {
        ReceiptScanDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissReceiptScanner() }
        )
    }

    // Detail Dialog
    selectedReceiptForDetail?.let { receipt ->
        ReceiptDetailModal(
            receipt = receipt,
            onDismiss = { selectedReceiptForDetail = null },
            onDelete = {
                viewModel.deleteReceipt(receipt)
                selectedReceiptForDetail = null
            }
        )
    }

    // Delete Confirmation Dialog
    receiptToDelete?.let { receipt ->
        AlertDialog(
            onDismissRequest = { receiptToDelete = null },
            title = { Text("Delete Gas Receipt?") },
            text = { Text("Are you sure you want to delete this receipt from ${receipt.merchant ?: "Gas Station"} for $${String.format(Locale.US, "%.2f", receipt.totalAmount)}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReceipt(receipt)
                        receiptToDelete = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { receiptToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("receipts_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Screen Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gas Receipts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SlateTextPrimary
                        )
                        Text(
                            text = "Track fuel purchases & extracted totals",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.seedSampleReceipts(context) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add Samples", fontSize = 12.sp)
                    }
                }
            }

            // Total Fuel Expenses Summary Card
            item {
                val total = totalGasExpenses ?: 0.0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("total_gas_expenses_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
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
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(DriveGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalGasStation,
                                        contentDescription = null,
                                        tint = DriveGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "TOTAL FUEL EXPENSES",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateTextMuted,
                                    letterSpacing = 0.8.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MileageBlueContainer)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${receipts.size} Receipts",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MileageBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Large Total Amount
                        Text(
                            text = String.format(Locale.US, "$%.2f", total),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = SlateTextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val avgPerReceipt = if (receipts.isNotEmpty()) total / receipts.size else 0.0
                        Text(
                            text = "Average: $${String.format(Locale.US, "%.2f", avgPerReceipt)} per fill-up",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateTextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Primary Action Button
                        Button(
                            onClick = { viewModel.openReceiptScanner() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("scan_receipt_main_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MileageBlue,
                                contentColor = Color.White
                            ),
                            elevation = null
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Take Picture of Gas Receipt", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            // Receipts List Section
            if (receipts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(MileageBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = MileageBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = "No Gas Receipts Saved Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )

                            Text(
                                text = "Snap a picture of your gas pump or paper receipt. The app automatically extracts the station name, fuel amount, gallons, and grand total.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.openReceiptScanner() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MileageBlue,
                                        contentColor = Color.White
                                    ),
                                    elevation = null
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Take Picture", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.processSampleReceipt(context) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test Sample", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                items(receipts, key = { it.id }) { receipt ->
                    ReceiptCardItem(
                        receipt = receipt,
                        onClick = { selectedReceiptForDetail = receipt },
                        onDelete = { receiptToDelete = receipt }
                    )
                }
            }

            // Bottom Spacing for navigation bar
            item {
                Spacer(modifier = Modifier.height(70.dp))
            }
        }

        // Floating Action Button to quickly take a picture
        FloatingActionButton(
            onClick = { viewModel.openReceiptScanner() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 20.dp, end = 20.dp)
                .testTag("scan_receipt_fab"),
            containerColor = MileageBlue,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = "Take Picture of Receipt",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ReceiptCardItem(
    receipt: GasReceiptEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("receipt_item_${receipt.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Receipt Image Thumbnail Preview
            val imageFile = File(receipt.imagePath)
            if (imageFile.exists()) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = "Receipt Image",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .background(SlateSurfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MileageBlueContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = MileageBlue,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Middle: Merchant, Date, Gallons
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = receipt.merchant ?: "Gas Station",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = SlateTextPrimary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                val dateStr = receipt.receiptDate ?: SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(receipt.timestamp))
                Text(
                    text = dateStr,
                    fontSize = 12.sp,
                    color = SlateTextMuted
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Fuel details row
                val fuelDetails = buildString {
                    if (!receipt.fuelType.isNullOrBlank()) append(receipt.fuelType)
                    if (receipt.gallonsOrLiters != null) {
                        if (isNotEmpty()) append(" • ")
                        append("${String.format(Locale.US, "%.2f", receipt.gallonsOrLiters)} ${receipt.unit}")
                    }
                }
                if (fuelDetails.isNotBlank()) {
                    Text(
                        text = fuelDetails,
                        fontSize = 11.sp,
                        color = SlateTextSecondary,
                        maxLines = 1
                    )
                }
            }

            // Right: PROMINENT TOTAL AMOUNT
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DriveGreenLight)
                        .border(1.dp, DriveGreen, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "$%.2f", receipt.totalAmount),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1D4ED8)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = SlateTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReceiptDetailModal(
    receipt: GasReceiptEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Receipt Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SlateTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Full Image Preview
                val imageFile = File(receipt.imagePath)
                if (imageFile.exists()) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = "Full Receipt",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Total Amount Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DriveGreenLight),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DriveGreen)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOTAL AMOUNT PAID",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6EE7B7),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = String.format(Locale.US, "$%.2f", receipt.totalAmount),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = SlateTextPrimary
                        )
                        receipt.merchant?.let {
                            Text(text = it, fontSize = 13.sp, color = Color(0xFFA7F3D0))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Metadata Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow(label = "Merchant", value = receipt.merchant ?: "N/A")
                    DetailRow(label = "Date", value = receipt.receiptDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(receipt.timestamp)))
                    if (!receipt.fuelType.isNullOrBlank()) {
                        DetailRow(label = "Fuel Grade", value = receipt.fuelType)
                    }
                    if (receipt.gallonsOrLiters != null) {
                        DetailRow(label = "Volume", value = "${String.format(Locale.US, "%.3f", receipt.gallonsOrLiters)} ${receipt.unit}")
                    }
                    if (receipt.pricePerUnit != null) {
                        DetailRow(label = "Price per ${receipt.unit}", value = "$${String.format(Locale.US, "%.3f", receipt.pricePerUnit)}")
                    }
                    if (!receipt.paymentMethod.isNullOrBlank()) {
                        DetailRow(label = "Payment", value = receipt.paymentMethod)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Delete Receipt Button
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Receipt")
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = SlateTextSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SlateTextPrimary)
    }
}
