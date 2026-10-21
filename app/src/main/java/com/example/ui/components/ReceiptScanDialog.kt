package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.ReceiptCaptureTarget
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.MileageViewModel
import com.example.ui.theme.DriveGreen
import com.example.ui.theme.DriveGreenLight
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateTextMuted
import com.example.ui.theme.SlateTextPrimary
import com.example.ui.theme.SlateTextSecondary
import java.util.Locale

@Composable
fun ReceiptScanDialog(
    viewModel: MileageViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scanState by viewModel.receiptScanState.collectAsState()
    val pendingCapture = remember { mutableStateOf<ReceiptCaptureTarget?>(null) }

    // Full-resolution capture. The preview contract only returns a tiny thumbnail,
    // which is too small to read station, gallons, and total off a receipt.
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val target = pendingCapture.value
        if (target != null && target.file.exists() && target.file.length() > 0L) {
            viewModel.processReceiptFromUri(context, target.uri)
        } else if (success) {
            viewModel.reportReceiptCaptureError("The photo didn't save. Try taking it again.")
        }
    }

    val openCamera = {
        val target = viewModel.createReceiptCaptureTarget(context)
        pendingCapture.value = target
        takePhotoLauncher.launch(target.uri)
    }

    // Photo picker launcher (gallery)
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.processReceiptFromUri(context, uri)
        }
    }

    val extracted = scanState.extractedData

    var merchantInput by remember(extracted) { mutableStateOf(extracted?.merchant ?: "") }
    var totalAmountInput by remember(extracted) {
        mutableStateOf(if (extracted != null && extracted.totalAmount > 0) String.format(Locale.US, "%.2f", extracted.totalAmount) else "")
    }
    var fuelTypeInput by remember(extracted) { mutableStateOf(extracted?.fuelType ?: "") }
    var gallonsInput by remember(extracted) {
        mutableStateOf(if (extracted?.gallonsOrLiters != null) String.format(Locale.US, "%.3f", extracted.gallonsOrLiters) else "")
    }
    var pricePerUnitInput by remember(extracted) {
        mutableStateOf(if (extracted?.pricePerUnit != null) String.format(Locale.US, "%.3f", extracted.pricePerUnit) else "")
    }
    var dateInput by remember(extracted) { mutableStateOf(extracted?.date ?: "") }
    var paymentMethodInput by remember(extracted) { mutableStateOf(extracted?.paymentMethod ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("receipt_scan_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Dialog Header
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MileageBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                tint = MileageBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Gas Receipt",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SlateTextPrimary
                            )
                            Text(
                                text = "Photo & Expense Details",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SlateTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                scanState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // If no image is captured yet, show capture options
                if (scanState.currentBitmap == null && !scanState.isScanningOrExtracting) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MileageBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = MileageBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Take a Picture of Your Receipt",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Hold your gas receipt flat in good light. Save the receipt image and record your total amount and fuel purchase details.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            // Capture Buttons
                            Button(
                                onClick = { openCamera() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("take_receipt_photo_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MileageBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Picture with Camera", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        pickPhotoLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("choose_receipt_gallery_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("From Gallery", fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.processSampleReceipt(context)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("sample_receipt_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Receipt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sample Receipt", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Receipt is being analyzed or has been extracted
                    if (scanState.isScanningOrExtracting) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(44.dp),
                                    color = MileageBlue,
                                    strokeWidth = 4.dp
                                )
                                Text(
                                    text = "Processing Receipt Photo...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SlateTextPrimary
                                )
                                Text(
                                    text = "Reading the station, gallons, and total from your photo.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    // Image Preview Thumbnail
                    scanState.currentBitmap?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                                .background(androidx.compose.ui.graphics.Color.Black)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Captured Receipt",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )

                            // Photo Attached Badge
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFF93C5FD),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Photo Attached",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // PROMINENT TOTAL AMOUNT CARD (Requirement: "the details on the receipt will be extracted and total amount shown")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("extracted_total_amount_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = DriveGreenLight
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, DriveGreen)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "TOTAL AMOUNT",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6EE7B7),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val displayTotal = totalAmountInput.toDoubleOrNull() ?: 0.0
                            Text(
                                text = String.format(Locale.US, "$%.2f", displayTotal),
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                color = SlateTextPrimary
                            )
                            if (merchantInput.isNotBlank()) {
                                Text(
                                    text = merchantInput,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFA7F3D0)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (scanState.errorMessage == null && extracted != null &&
                        (extracted.totalAmount > 0.0 || extracted.gallonsOrLiters != null || !extracted.merchant.isNullOrBlank())
                    ) {
                        Text(
                            text = "Read from your photo. Review the details, then save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DriveGreen,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Receipt Details Form (User can review and edit before saving)
                    Text(
                        text = "Receipt Details (Review & Save)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SlateTextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Merchant Field
                    OutlinedTextField(
                        value = merchantInput,
                        onValueChange = { merchantInput = it },
                        label = { Text("Gas Station / Merchant") },
                        leadingIcon = {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MileageBlue)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_merchant_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Total Amount Field
                    OutlinedTextField(
                        value = totalAmountInput,
                        onValueChange = { totalAmountInput = it },
                        label = { Text("Total Amount ($)") },
                        leadingIcon = {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = DriveGreen)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_total_amount_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fuel Type & Gallons (Side by Side)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = gallonsInput,
                            onValueChange = { gallonsInput = it },
                            label = { Text(if (extracted?.unit == "L") "Liters" else "Gallons") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_gallons_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = pricePerUnitInput,
                            onValueChange = { pricePerUnitInput = it },
                            label = { Text(if (extracted?.unit == "L") "Price/L ($)" else "Price/Gal ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_price_per_gal_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fuel Grade & Date (Side by Side)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fuelTypeInput,
                            onValueChange = { fuelTypeInput = it },
                            label = { Text("Fuel Grade") },
                            placeholder = { Text("Regular 87") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_fuel_grade_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = dateInput,
                            onValueChange = { dateInput = it },
                            label = { Text("Date") },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("receipt_date_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = paymentMethodInput,
                        onValueChange = { paymentMethodInput = it },
                        label = { Text("Payment") },
                        leadingIcon = {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = MileageBlue, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_payment_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Retake / Change Photo options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openCamera() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retake", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                pickPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Save Button
                    Button(
                        onClick = {
                            val parsedTotal = totalAmountInput.toDoubleOrNull() ?: 0.0
                            val parsedGallons = gallonsInput.toDoubleOrNull()
                            val parsedPricePerUnit = pricePerUnitInput.toDoubleOrNull()

                            viewModel.saveCurrentReceipt(
                                merchant = merchantInput,
                                totalAmount = parsedTotal,
                                currency = "$",
                                fuelType = fuelTypeInput.takeIf { it.isNotBlank() },
                                gallonsOrLiters = parsedGallons,
                                pricePerUnit = parsedPricePerUnit,
                                receiptDate = dateInput.takeIf { it.isNotBlank() },
                                paymentMethod = paymentMethodInput.takeIf { it.isNotBlank() }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_receipt_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MileageBlue,
                            contentColor = Color.White,
                            disabledContainerColor = MileageBlue.copy(alpha = 0.5f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        ),
                        elevation = null,
                        enabled = !scanState.isScanningOrExtracting && (totalAmountInput.toDoubleOrNull() != null || merchantInput.isNotBlank())
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Gas Receipt", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
