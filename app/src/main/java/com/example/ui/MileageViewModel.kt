package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GasReceiptEntity
import com.example.data.GasReceiptRepository
import com.example.data.MileageSettings
import com.example.data.SettingsManager
import com.example.data.TripEntity
import com.example.data.TripRepository
import com.example.service.ExtractedReceiptData
import com.example.service.ExtractionResult
import com.example.service.LiveTrackingState
import com.example.service.ReceiptExtractionService
import com.example.service.TrackingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppTab(val label: String) {
    HOME("Home"),
    TRIPS("Trips"),
    RECEIPTS("Gas Receipts"),
    REPORT("Report"),
    SETTINGS("Settings")
}

enum class TripFilter(val label: String) {
    ALL("All"),
    UNCLASSIFIED("To Classify"),
    BUSINESS("Business"),
    PERSONAL("Personal")
}

data class UndoAction(
    val trip: TripEntity,
    val previousType: String?,
    val assignedType: String
)

data class ReceiptScanUiState(
    val isScanningOrExtracting: Boolean = false,
    val currentBitmap: Bitmap? = null,
    val currentSavedImagePath: String? = null,
    val extractedData: ExtractedReceiptData? = null,
    val isDialogVisible: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val errorMessage: String? = null
)

class MileageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TripRepository
    private val gasReceiptRepository: GasReceiptRepository
    private val settingsManager: SettingsManager

    val trips: StateFlow<List<TripEntity>>
    val receipts: StateFlow<List<GasReceiptEntity>>
    val totalGasExpenses: StateFlow<Double?>
    val settings: StateFlow<MileageSettings>
    val trackingState: StateFlow<LiveTrackingState> = TrackingManager.trackingState

    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _activeFilter = MutableStateFlow(TripFilter.ALL)
    val activeFilter: StateFlow<TripFilter> = _activeFilter.asStateFlow()

    private val _undoAction = MutableStateFlow<UndoAction?>(null)
    val undoAction: StateFlow<UndoAction?> = _undoAction.asStateFlow()

    private val _receiptScanState = MutableStateFlow(ReceiptScanUiState())
    val receiptScanState: StateFlow<ReceiptScanUiState> = _receiptScanState.asStateFlow()

    private val _justCompletedTrip = MutableStateFlow<TripEntity?>(null)
    val justCompletedTrip: StateFlow<TripEntity?> = _justCompletedTrip.asStateFlow()

    fun dismissJustCompletedTrip() {
        _justCompletedTrip.value = null
    }

    fun showCompletedTrip(trip: TripEntity) {
        _justCompletedTrip.value = trip
    }

    private var undoDismissJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TripRepository(database.tripDao())
        gasReceiptRepository = GasReceiptRepository(database.gasReceiptDao())
        settingsManager = SettingsManager(application)

        trips = repository.allTrips.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        receipts = gasReceiptRepository.allReceipts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        totalGasExpenses = gasReceiptRepository.totalGasExpenses.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

        settings = settingsManager.settings

        TrackingManager.onTripSavedCallback = { trip ->
            _justCompletedTrip.value = trip
        }
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setFilter(filter: TripFilter) {
        _activeFilter.value = filter
    }

    fun classifyTrip(trip: TripEntity, newType: String) {
        val previousType = trip.type
        val updated = trip.copy(type = newType)
        if (_justCompletedTrip.value?.id == trip.id) {
            _justCompletedTrip.value = null
        }
        viewModelScope.launch {
            repository.updateTrip(updated)
            _undoAction.value = UndoAction(trip = trip, previousType = previousType, assignedType = newType)
            undoDismissJob?.cancel()
            undoDismissJob = launch {
                delay(4500L)
                _undoAction.value = null
            }
        }
    }

    fun undoClassification() {
        val action = _undoAction.value ?: return
        viewModelScope.launch {
            repository.updateTrip(action.trip.copy(type = action.previousType))
            _undoAction.value = null
        }
    }

    fun dismissUndo() {
        _undoAction.value = null
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch {
            repository.deleteTrip(trip)
        }
    }

    fun saveTrip(trip: TripEntity) {
        viewModelScope.launch {
            if (trip.id == 0L) {
                repository.insertTrip(trip)
            } else {
                repository.updateTrip(trip)
            }
        }
    }

    fun updateTrip(trip: TripEntity) = saveTrip(trip)

    fun startManualTracking(context: Context) {
        TrackingManager.startManualTracking(context)
    }

    fun stopManualTracking(context: Context) {
        TrackingManager.stopManualTracking(context)
    }

    fun recordDestinationStopAndStartNewTrip(context: Context) {
        TrackingManager.recordDestinationStopAndStartNewTrip(context)
    }

    private var simulatedTripCount = 0

    fun addSimulatedUnclassifiedTrip(
        distanceKm: Double? = null,
        startAddr: String? = null,
        endAddr: String? = null
    ) {
        val sampleRoutes = listOf(
            Triple(6.4, "450 Market St, Downtown", "820 Silicon Way, Tech Park"),
            Triple(14.8, "1200 Grand Ave, City Center", "55 Airport Blvd, Terminal 2"),
            Triple(4.2, "742 Evergreen Terrace", "Central Medical Plaza, Suite 400"),
            Triple(9.5, "Westside Logistics Hub", "Marina Harbor, Pier 9"),
            Triple(3.6, "Main St Coworking", "The Roasted Bean Cafe")
        )
        val sample = sampleRoutes[simulatedTripCount % sampleRoutes.size]
        simulatedTripCount++

        val dist = distanceKm ?: sample.first
        val start = startAddr ?: sample.second
        val end = endAddr ?: sample.third

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val durationMinutes = (dist * 2.5).toLong().coerceIn(10, 45)
            val entity = TripEntity(
                startDate = now - durationMinutes * 60 * 1000L,
                endDate = now,
                distanceKm = dist,
                type = null, // unclassified so it appears directly on the home screen
                startAddress = start,
                endAddress = end,
                purpose = "Drive (${String.format(java.util.Locale.US, "%.1f", dist)} km)",
                vehicle = settings.value.vehicleName
            )
            repository.insertTrip(entity)
        }
    }

    fun startMovementMonitoring(context: Context) {
        TrackingManager.startMovementMonitoring(context)
    }

    fun stopMovementMonitoring(context: Context) {
        TrackingManager.stopMovementMonitoring(context)
    }

    fun setAutoDetect(enabled: Boolean) {
        setAutoDetect(getApplication(), enabled)
    }

    fun setAutoDetect(context: Context, enabled: Boolean) {
        settingsManager.setAutoDetect(enabled)
        if (enabled) {
            TrackingManager.startMovementMonitoring(context)
        } else {
            TrackingManager.stopMovementMonitoring(context)
        }
    }

    fun changeDwell(delta: Int) {
        val current = settings.value.dwellMinutes
        settingsManager.setDwellMinutes(current + delta)
    }

    fun setUnit(unit: String) {
        settingsManager.setUnit(unit)
    }

    fun setReimbursementRate(rate: Double) {
        settingsManager.setReimbursementRate(rate)
    }

    fun setVehicleName(name: String) {
        settingsManager.setVehicleName(name)
    }

    fun setAnnualGoal(goal: Double) {
        settingsManager.setAnnualGoal(goal)
    }

    fun clearAllTrips() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun seedSampleTrips() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val hour = 3600 * 1000L
            val day = 24 * hour

            val sampleList = listOf(
                TripEntity(
                    startDate = now - 2 * hour,
                    endDate = now - 1 * hour,
                    distanceKm = 14.8,
                    type = null, // To classify
                    startAddress = "742 Evergreen Terr, Springfield",
                    endAddress = "Northwest Business Park, Suite 300",
                    purpose = "Client Quarterly Review",
                    vehicle = "Primary Vehicle"
                ),
                TripEntity(
                    startDate = now - 1 * day,
                    endDate = now - 1 * day + 45 * 60 * 1000L,
                    distanceKm = 28.4,
                    type = "Business",
                    startAddress = "Metro Regional HQ",
                    endAddress = "International Airport Terminal 3",
                    purpose = "Flight to Tech Conference",
                    vehicle = "Primary Vehicle"
                ),
                TripEntity(
                    startDate = now - 2 * day,
                    endDate = now - 2 * day + 30 * 60 * 1000L,
                    distanceKm = 6.2,
                    type = "Personal",
                    startAddress = "Home",
                    endAddress = "Whole Foods Market & Pharmacy",
                    purpose = "Weekly Grocery Restock",
                    vehicle = "Primary Vehicle"
                ),
                TripEntity(
                    startDate = now - 3 * day,
                    endDate = now - 3 * day + 50 * 60 * 1000L,
                    distanceKm = 19.5,
                    type = null, // To classify
                    startAddress = "Springfield Tech Hub",
                    endAddress = "Downtown Medical Plaza",
                    purpose = "Consultation Meeting",
                    vehicle = "Primary Vehicle"
                ),
                TripEntity(
                    startDate = now - 5 * day,
                    endDate = now - 5 * day + 35 * 60 * 1000L,
                    distanceKm = 11.3,
                    type = "Business",
                    startAddress = "Regional Logistics Center",
                    endAddress = "Distribution Hub West",
                    purpose = "Supplier Site Inspection",
                    vehicle = "Primary Vehicle"
                )
            )
            repository.insertTrips(sampleList)
        }
    }

    fun generateCsv(tripsList: List<TripEntity>, isMiles: Boolean, rate: Double): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val unitLabel = if (isMiles) "Miles" else "Kilometers"
        val header = "Date,Start Address,End Address,Distance ($unitLabel),Type,Purpose,Vehicle,Rate/Unit,Estimated Deduction ($)\n"
        val rows = tripsList.joinToString("\n") { t ->
            val dateStr = dateFormat.format(Date(t.startDate))
            val dist = if (isMiles) t.distanceKm * 0.621371 else t.distanceKm
            val estVal = if (t.type == "Business") dist * rate else 0.0
            val escapedStart = "\"${(t.startAddress ?: "").replace("\"", "\"\"")}\""
            val escapedEnd = "\"${(t.endAddress ?: "").replace("\"", "\"\"")}\""
            val escapedPurpose = "\"${(t.purpose ?: "").replace("\"", "\"\"")}\""
            val escapedVehicle = "\"${(t.vehicle ?: "").replace("\"", "\"\"")}\""
            val typeStr = t.type ?: "Unclassified"

            "$dateStr,$escapedStart,$escapedEnd,${String.format(Locale.US, "%.2f", dist)},$typeStr,$escapedPurpose,$escapedVehicle,${String.format(Locale.US, "%.2f", rate)},${String.format(Locale.US, "%.2f", estVal)}"
        }
        return header + rows
    }

    fun shareCsvReport(context: Context) {
        val isMiles = settings.value.unit == "mi"
        val rate = settings.value.reimbursementRate
        val csvData = generateCsv(trips.value, isMiles, rate)

        try {
            val file = File(context.cacheDir, "Mileage_Report_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.csv")
            file.writeText(csvData)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Mileage Tax Report")
                putExtra(Intent.EXTRA_TEXT, "Attached is the CRA/IRS compliant mileage report from MileageTracker.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Mileage Report"))
        } catch (e: Exception) {
            // Fallback to sharing text directly if file provider is not configured
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Mileage Tax Report (CSV)")
                putExtra(Intent.EXTRA_TEXT, csvData)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Mileage Report"))
        }
    }

    fun openReceiptScanner() {
        _receiptScanState.value = ReceiptScanUiState(isDialogVisible = true)
    }

    fun dismissReceiptScanner() {
        _receiptScanState.value = ReceiptScanUiState(isDialogVisible = false)
    }

    fun processCapturedReceipt(
        context: Context,
        bitmap: Bitmap,
        prefilledData: ExtractedReceiptData? = null
    ) {
        viewModelScope.launch {
            _receiptScanState.value = _receiptScanState.value.copy(
                isScanningOrExtracting = true,
                currentBitmap = bitmap,
                errorMessage = null,
                isDialogVisible = true
            )

            // Save bitmap to internal file storage
            val imagePath = withContext(Dispatchers.IO) {
                try {
                    val dir = File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }
                    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    file.absolutePath
                } catch (e: Exception) {
                    null
                }
            }

            // Local receipt extraction and state setup (no cloud/AI dependency)
            val receiptData = prefilledData ?: when (val res = ReceiptExtractionService.extractReceiptDetails(bitmap)) {
                is ExtractionResult.Success -> res.data
                is ExtractionResult.Failure -> res.fallbackData ?: ExtractedReceiptData()
            }

            _receiptScanState.value = _receiptScanState.value.copy(
                isScanningOrExtracting = false,
                currentSavedImagePath = imagePath,
                extractedData = receiptData
            )
        }
    }

    fun processReceiptFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        processCapturedReceipt(context, bitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _receiptScanState.value = _receiptScanState.value.copy(
                            errorMessage = "Could not load selected image.",
                            isDialogVisible = true
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _receiptScanState.value = _receiptScanState.value.copy(
                        errorMessage = "Error reading image: ${e.message}",
                        isDialogVisible = true
                    )
                }
            }
        }
    }

    fun processSampleReceipt(context: Context) {
        viewModelScope.launch {
            val sampleBitmap = ReceiptExtractionService.createSampleGasReceiptBitmap()
            val sampleData = ExtractedReceiptData(
                merchant = "Chevron Service Station",
                totalAmount = 53.13,
                currency = "$",
                fuelType = "Regular 87 (Pump #06)",
                gallonsOrLiters = 13.420,
                unit = "gal",
                pricePerUnit = 3.959,
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                paymentMethod = "Visa Contactless *6021",
                summary = "Chevron fuel refill (Pump 06)",
                rawText = "CHEVRON SERVICE STATION\nPUMP 06 REGULAR\n13.420 GAL @ 3.959/GAL\nTOTAL: $53.13"
            )
            processCapturedReceipt(context, sampleBitmap, prefilledData = sampleData)
        }
    }

    fun updateExtractedData(updatedData: ExtractedReceiptData) {
        _receiptScanState.value = _receiptScanState.value.copy(
            extractedData = updatedData
        )
    }

    fun saveCurrentReceipt(
        merchant: String,
        totalAmount: Double,
        currency: String = "$",
        fuelType: String? = null,
        gallonsOrLiters: Double? = null,
        pricePerUnit: Double? = null,
        receiptDate: String? = null,
        paymentMethod: String? = null,
        tripId: Long? = null
    ) {
        val state = _receiptScanState.value
        val imagePath = state.currentSavedImagePath ?: ""
        val entity = GasReceiptEntity(
            imagePath = imagePath,
            merchant = merchant.ifBlank { "Gas Station" },
            totalAmount = totalAmount,
            currency = currency,
            fuelType = fuelType,
            gallonsOrLiters = gallonsOrLiters,
            pricePerUnit = pricePerUnit,
            receiptDate = receiptDate,
            paymentMethod = paymentMethod,
            rawExtraction = state.extractedData?.rawText,
            tripId = tripId
        )

        viewModelScope.launch {
            gasReceiptRepository.insertReceipt(entity)
            _receiptScanState.value = ReceiptScanUiState(
                isDialogVisible = false,
                isSavedSuccess = true
            )
        }
    }

    fun deleteReceipt(receipt: GasReceiptEntity) {
        viewModelScope.launch {
            if (receipt.imagePath.isNotBlank()) {
                try {
                    File(receipt.imagePath).delete()
                } catch (_: Exception) {}
            }
            gasReceiptRepository.deleteReceipt(receipt)
        }
    }

    fun seedSampleReceipts(context: Context) {
        viewModelScope.launch {
            val dir = File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }
            val now = System.currentTimeMillis()
            val day = 86400000L

            val b1 = ReceiptExtractionService.createSampleGasReceiptBitmap(
                merchant = "SHELL SERVICE PLAZA",
                gallons = 14.250,
                pricePerGal = 3.899,
                total = 55.56,
                fuelType = "REGULAR 87 (PUMP #03)"
            )
            val file1 = File(dir, "sample_receipt_1.jpg")
            FileOutputStream(file1).use { b1.compress(Bitmap.CompressFormat.JPEG, 85, it) }

            val b2 = ReceiptExtractionService.createSampleGasReceiptBitmap(
                merchant = "CHEVRON HIGHWAY EXPRESS",
                gallons = 11.800,
                pricePerGal = 4.199,
                total = 49.55,
                fuelType = "PREMIUM 93 (PUMP #08)"
            )
            val file2 = File(dir, "sample_receipt_2.jpg")
            FileOutputStream(file2).use { b2.compress(Bitmap.CompressFormat.JPEG, 85, it) }

            val list = listOf(
                GasReceiptEntity(
                    timestamp = now - 1 * day,
                    imagePath = file1.absolutePath,
                    merchant = "Shell Service Plaza",
                    totalAmount = 55.56,
                    currency = "$",
                    fuelType = "Regular 87 (Pump 03)",
                    gallonsOrLiters = 14.25,
                    unit = "gal",
                    pricePerUnit = 3.899,
                    receiptDate = "2026-09-05",
                    paymentMethod = "Visa *4921",
                    rawExtraction = "SHELL SERVICE PLAZA\nPUMP 03\nTOTAL: $55.56"
                ),
                GasReceiptEntity(
                    timestamp = now - 4 * day,
                    imagePath = file2.absolutePath,
                    merchant = "Chevron Highway Express",
                    totalAmount = 49.55,
                    currency = "$",
                    fuelType = "Premium 93 (Pump 08)",
                    gallonsOrLiters = 11.80,
                    unit = "gal",
                    pricePerUnit = 4.199,
                    receiptDate = "2026-09-02",
                    paymentMethod = "Amex *1004",
                    rawExtraction = "CHEVRON HIGHWAY EXPRESS\nPUMP 08\nTOTAL: $49.55"
                )
            )
            for (r in list) {
                gasReceiptRepository.insertReceipt(r)
            }
        }
    }
}
