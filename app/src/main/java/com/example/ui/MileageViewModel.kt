package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.room.withTransaction
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.InputCheck
import com.example.data.GasReceiptEntity
import com.example.data.GasReceiptRepository
import com.example.data.MileageSettings
import com.example.data.SettingsManager
import com.example.data.TripEntity
import com.example.data.TripRepository
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseSyncManager
import com.example.data.supabase.SupabaseSyncState
import com.example.data.supabase.SyncStatus
import com.example.service.AccountantReportExporter
import com.example.service.AccountantReportBundle
import com.example.service.ReportSnapshot
import com.example.service.ReportPeriod
import com.example.service.ActiveTripDraft
import com.example.service.ObdBluetoothReader
import com.example.service.Odometer
import com.example.service.ExtractedReceiptData
import com.example.service.ExtractionResult
import com.example.service.LiveTrackingState
import com.example.service.ReceiptExtractionService
import com.example.service.TrackingManager
import com.example.service.TripNotifier
import com.example.service.tripUpdates
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

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

data class ReceiptCaptureTarget(
    val file: File,
    val uri: Uri
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

data class UserProfile(
    val name: String,
    val email: String,
    val isGuest: Boolean = false
)

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val infoMessage: String? = null
)

data class ReportExportUiState(
    val isPreparing: Boolean = false,
    val bundle: AccountantReportBundle? = null,
    val error: String? = null
)

class MileageViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val odometerReadings = database.odometerReadingDao().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vehicleExpenses = database.vehicleExpenseDao().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val repository: TripRepository
    private val gasReceiptRepository: GasReceiptRepository
    private val settingsManager: SettingsManager

    val supabaseConfig: SupabaseConfig = SupabaseConfig(application)
    val supabaseClient: SupabaseClient = SupabaseClient(supabaseConfig)
    val receiptPhotoBackup = com.example.data.supabase.ReceiptPhotoBackup(application, supabaseClient)
    val supabaseSyncManager: SupabaseSyncManager
    val supabaseSyncState: StateFlow<SupabaseSyncState>

    val trips: StateFlow<List<TripEntity>>
    val receipts: StateFlow<List<GasReceiptEntity>>
    val totalGasExpenses: StateFlow<Double?>
    val settings: StateFlow<MileageSettings>
    val trackingState: StateFlow<LiveTrackingState> = TrackingManager.trackingState

    private val _reportExportState = MutableStateFlow(ReportExportUiState())
    val reportExportState: StateFlow<ReportExportUiState> = _reportExportState.asStateFlow()

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

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _isAuthPromptVisible = MutableStateFlow<Boolean>(true)
    val isAuthPromptVisible: StateFlow<Boolean> = _isAuthPromptVisible.asStateFlow()

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    fun clearAuthMessages() {
        _authUiState.value = AuthUiState()
    }

    fun signIn(
        email: String,
        password: String? = null,
        name: String? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val cleanEmail = InputCheck.email(email)
        if (cleanEmail == null) {
            val err = "Please enter a valid email address."
            _authUiState.value = AuthUiState(errorMessage = err)
            onError?.invoke(err)
            return
        }

        val passwordError = InputCheck.passwordProblem(password)
        if (passwordError != null) {
            _authUiState.value = AuthUiState(errorMessage = passwordError)
            onError?.invoke(passwordError)
            return
        }
        val pass = password.orEmpty()

        _authUiState.value = AuthUiState(isLoading = true)

        viewModelScope.launch {
            if (supabaseConfig.isConfigured()) {
                val res = supabaseClient.signIn(cleanEmail, pass)
                if (res.isSuccess) {
                    val session = res.getOrNull()
                    val cleanName = session?.user?.name?.takeIf { it.isNotBlank() }
                        ?: name?.takeIf { it.isNotBlank() }
                        ?: cleanEmail.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

                    _userProfile.value = UserProfile(
                        name = cleanName,
                        email = session?.user?.email ?: cleanEmail,
                        isGuest = false
                    )
                    _isAuthPromptVisible.value = false
                    _authUiState.value = AuthUiState(isLoading = false)
                    onSuccess?.invoke()
                    supabaseSyncManager.syncAll()
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Sign in failed. Please check your credentials."
                    _authUiState.value = AuthUiState(isLoading = false, errorMessage = err)
                    onError?.invoke(err)
                }
            } else {
                val cleanName = name?.takeIf { it.isNotBlank() }
                    ?: cleanEmail.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                _userProfile.value = UserProfile(
                    name = cleanName,
                    email = cleanEmail,
                    isGuest = false
                )
                _isAuthPromptVisible.value = false
                _authUiState.value = AuthUiState(isLoading = false)
                onSuccess?.invoke()
            }
        }
    }

    fun signUp(
        name: String,
        email: String,
        password: String? = null,
        onSuccess: ((Boolean) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val cleanName = InputCheck.exactName(name)
        if (cleanName == null) {
            val err = if (name.isBlank()) {
                "Please enter your name."
            } else {
                "Name can use letters, spaces, hyphens, and apostrophes."
            }
            _authUiState.value = AuthUiState(errorMessage = err)
            onError?.invoke(err)
            return
        }

        val cleanEmail = InputCheck.email(email)
        if (cleanEmail == null) {
            val err = "Please enter a valid email address."
            _authUiState.value = AuthUiState(errorMessage = err)
            onError?.invoke(err)
            return
        }

        val passwordError = InputCheck.passwordProblem(password)
        if (passwordError != null) {
            _authUiState.value = AuthUiState(errorMessage = passwordError)
            onError?.invoke(passwordError)
            return
        }
        val pass = password.orEmpty()

        _authUiState.value = AuthUiState(isLoading = true)

        viewModelScope.launch {
            if (supabaseConfig.isConfigured()) {
                val res = supabaseClient.signUp(cleanEmail, pass, cleanName)
                if (res.isSuccess) {
                    val session = res.getOrNull()
                    if (session != null && session.accessToken.isNotBlank()) {
                        _userProfile.value = UserProfile(
                            name = session.user.name ?: cleanName,
                            email = session.user.email,
                            isGuest = false
                        )
                        _isAuthPromptVisible.value = false
                        _authUiState.value = AuthUiState(isLoading = false)
                        onSuccess?.invoke(true)
                        supabaseSyncManager.syncAll()
                    } else {
                        val msg = "Account created! A confirmation email has been sent to $cleanEmail. Please verify your email before signing in."
                        _authUiState.value = AuthUiState(
                            isLoading = false,
                            successMessage = msg
                        )
                        onSuccess?.invoke(false)
                    }
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Sign up failed. Please try again."
                    _authUiState.value = AuthUiState(isLoading = false, errorMessage = err)
                    onError?.invoke(err)
                }
            } else {
                _userProfile.value = UserProfile(
                    name = cleanName,
                    email = cleanEmail,
                    isGuest = false
                )
                _isAuthPromptVisible.value = false
                _authUiState.value = AuthUiState(isLoading = false)
                onSuccess?.invoke(true)
            }
        }
    }

    fun resetPassword(
        email: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val cleanEmail = InputCheck.email(email)
        if (cleanEmail == null) {
            onError?.invoke("Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            if (supabaseConfig.isConfigured()) {
                val res = supabaseClient.resetPassword(cleanEmail)
                if (res.isSuccess) {
                    onSuccess?.invoke()
                } else {
                    val err = res.exceptionOrNull()?.message ?: "Failed to send reset link."
                    onError?.invoke(err)
                }
            } else {
                onSuccess?.invoke()
            }
        }
    }

    fun continueAsGuest() {
        _userProfile.value = UserProfile(
            name = "Guest Driver",
            email = "guest@mileage.app",
            isGuest = true
        )
        _isAuthPromptVisible.value = false
        _authUiState.value = AuthUiState()
    }

    fun signOut() {
        viewModelScope.launch {
            supabaseClient.signOut()
        }
        _userProfile.value = null
        _isAuthPromptVisible.value = true
        _authUiState.value = AuthUiState()
    }

    fun setReceiptPhotoBackup(enabled: Boolean) {
        receiptPhotoBackup.setEnabled(enabled)
        supabaseSyncManager.triggerSync()
    }

    fun syncWithSupabase() {
        supabaseSyncManager.triggerSync()
    }

    fun saveSupabaseAnonKey(key: String) {
        val clean = InputCheck.anonKey(key) ?: return
        supabaseConfig.setAnonKey(clean)
        if (supabaseConfig.isConfigured()) {
            supabaseSyncManager.triggerSync()
        }
    }

    fun testSupabaseConnection(callback: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = supabaseClient.testConnection()
            callback(result)
        }
    }

    fun showAuthScreen() {
        _isAuthPromptVisible.value = true
    }

    fun dismissAuthScreen() {
        if (_userProfile.value == null) {
            continueAsGuest()
        } else {
            _isAuthPromptVisible.value = false
        }
    }

    fun setOnboardingSeen(seen: Boolean) {
        settingsManager.setOnboardingSeen(seen)
    }

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
        viewModelScope.launch {
            database.withTransaction {
                val old = settingsManager.settings.value
                val dao = database.odometerReadingDao()
                val prefs = application.getSharedPreferences("odometer_history_migration", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("done", false)) {
                    if (old.odometerKm != null && old.odometerAt > 0 && dao.latest(old.vehicleName) == null) {
                        dao.insert(com.example.data.OdometerReadingEntity(vehicle = old.vehicleName, recordedAt = old.odometerAt,
                            readingKm = old.odometerKm, source = old.odometerSource))
                    }
                    prefs.edit().putBoolean("done", true).commit()
                }
            }
        }

        supabaseSyncManager = SupabaseSyncManager(
            tripRepository = repository,
            receiptRepository = gasReceiptRepository,
            supabaseClient = supabaseClient,
            scope = viewModelScope,
            photos = receiptPhotoBackup
        )
        supabaseSyncState = supabaseSyncManager.syncState

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
        TrackingManager.setDwellMinutes(settingsManager.settings.value.dwellMinutes)

        viewModelScope.launch {
            supabaseConfig.sessionFlow.collect { session ->
                if (session == null && _userProfile.value?.isGuest == false) {
                    _userProfile.value = null
                    _isAuthPromptVisible.value = true
                    _authUiState.value = AuthUiState(errorMessage = "Your session expired. Sign in again.")
                }
            }
        }

        viewModelScope.launch {
            val initial = repository.getAllTripsList()
            var seen: Set<Long>? = initial.map { it.id }.toSet()
            var skipEmptyReplay = seen.orEmpty().isNotEmpty()
            TripNotifier.post(getApplication(), tripUpdates(null, initial))
            trips.collect { list ->
                if (skipEmptyReplay && list.isEmpty()) return@collect
                skipEmptyReplay = false
                val update = tripUpdates(seen, list)
                seen = list.map { it.id }.toSet()
                TripNotifier.post(getApplication(), update)
            }
        }

        viewModelScope.launch {
            val existing = supabaseConfig.getSession()
            val fresh = supabaseClient.ensureFreshSession()
            when {
                fresh != null -> showSignedIn(fresh)
                existing != null && supabaseConfig.getSession() != null -> showSignedIn(existing)
                existing != null -> {
                    _isAuthPromptVisible.value = true
                    _authUiState.value = AuthUiState(errorMessage = "Your session expired. Sign in again.")
                }
            }
            if (supabaseConfig.isConfigured()) {
                supabaseSyncManager.syncAll()
            }
        }
    }

    private fun showSignedIn(session: SupabaseSession) {
        _userProfile.value = UserProfile(
            name = session.user.name?.takeIf { it.isNotBlank() }
                ?: session.user.email.substringBefore("@"),
            email = session.user.email,
            isGuest = false
        )
        _isAuthPromptVisible.value = false
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setFilter(filter: TripFilter) {
        _activeFilter.value = filter
    }

    fun classifyTrip(trip: TripEntity, newType: String) {
        if (newType != "Business" && newType != "Personal") return
        val previousType = trip.type
        val updated = trip.copy(type = newType)
        if (_justCompletedTrip.value?.id == trip.id) {
            _justCompletedTrip.value = null
        }
        viewModelScope.launch {
            repository.updateTrip(updated)
            supabaseSyncManager.onTripSaved(updated)
            _undoAction.value = UndoAction(trip = trip, previousType = previousType, assignedType = newType)
            undoDismissJob?.cancel()
            undoDismissJob = launch {
                delay(4500L)
                _undoAction.value = null
            }
        }
    }

    fun classifyTrips(ids: Set<Long>, type: String, onResult: (Result<Int>) -> Unit) {
        if (ids.isEmpty() || type !in listOf("Business", "Personal")) return
        viewModelScope.launch {
            val saved = runCatching { repository.classifyTrips(ids.toList(), type) }
            saved.onSuccess { updated ->
                _undoAction.value = null
                onResult(Result.success(updated.size))
                updated.forEach { supabaseSyncManager.onTripSaved(it) }
            }.onFailure { onResult(Result.failure(it)) }
        }
    }

    fun undoClassification() {
        val action = _undoAction.value ?: return
        viewModelScope.launch {
            val reverted = action.trip.copy(type = action.previousType)
            repository.updateTrip(reverted)
            supabaseSyncManager.onTripSaved(reverted)
            _undoAction.value = null
        }
    }

    fun dismissUndo() {
        _undoAction.value = null
    }

    fun deleteTrip(trip: TripEntity) {
        viewModelScope.launch {
            repository.deleteTrip(trip)
            supabaseSyncManager.onTripDeleted(trip)
        }
    }

    fun saveTrip(trip: TripEntity) {
        val clean = InputCheck.storedTrip(trip, strict = false) ?: return
        viewModelScope.launch {
            val savedId = if (clean.id == 0L) {
                repository.insertTrip(clean)
            } else {
                repository.updateTrip(clean)
                clean.id
            }
            val tripToSync = if (clean.id == 0L) clean.copy(id = savedId) else clean
            supabaseSyncManager.onTripSaved(tripToSync)
        }
    }

    fun updateTrip(trip: TripEntity) = saveTrip(trip)

    fun startManualTracking(context: Context) {
        TrackingManager.startManualTracking(context)
    }

    fun stopManualTracking(context: Context) {
        TrackingManager.stopManualTracking(context)
    }

    fun updateActiveTripDraft(
        category: String?,
        vehicle: String,
        note: String,
        startPlaceName: String?
    ) {
        TrackingManager.updateActiveDraft(
            ActiveTripDraft(
                category = if (category == "Business" || category == "Personal") category else null,
                vehicle = InputCheck.finishLabel(vehicle, 40) ?: "",
                note = InputCheck.finishLabel(note, 160) ?: "",
                startPlaceName = startPlaceName?.let { InputCheck.finishLabel(it, 80) }
            )
        )
    }

    fun startMovementMonitoring(context: Context) {
        TrackingManager.startMovementMonitoring(context)
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

    fun setTaxCountry(country: String) {
        settingsManager.setTaxCountry(country)
    }

    fun setUnit(unit: String) {
        settingsManager.setUnit(InputCheck.distanceUnit(unit) ?: return)
    }

    fun setReimbursementRate(rate: Double) {
        settingsManager.setReimbursementRate(InputCheck.rate(rate) ?: return)
    }

    fun setVehicleName(name: String) {
        settingsManager.setVehicleName(InputCheck.exactLabel(name, 40) ?: return)
    }

    fun setAnnualGoal(goal: Double) {
        settingsManager.setAnnualGoal(InputCheck.annualGoal(goal) ?: return)
    }

    fun readCarOdometer(address: String, onResult: (Result<Double>) -> Unit) {
        val mac = InputCheck.bluetoothAddress(address)
        if (mac == null) {
            onResult(Result.failure(IOException("That adapter is no longer paired.")))
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ObdBluetoothReader.readKm(getApplication(), mac) }
            }
            onResult(result)
        }
    }

    fun importOdometerPhoto(uri: Uri, onResult: (Result<Pair<String, String>>) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { runCatching { com.example.service.OdometerPhotoStore.importPhoto(getApplication(), uri) } })
        }
    }

    fun discardOdometerPhoto(path: String) {
        viewModelScope.launch(Dispatchers.IO) { com.example.service.OdometerPhotoStore.delete(getApplication(), path) }
    }

    fun deleteOdometerReading(id: Long, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching {
                val reading = database.odometerReadingDao().find(id) ?: return@runCatching
                database.odometerReadingDao().delete(id)
                reading.photoPath?.let { withContext(Dispatchers.IO) { com.example.service.OdometerPhotoStore.delete(getApplication(), it) } }
                Unit
            })
        }
    }

    fun saveOdometerReading(km: Double, source: String, logTrip: Boolean,
        photoPath: String? = null, photoReviewed: Boolean = false,
        onResult: (Result<Unit>) -> Unit = {}) {
        val cleanKm = InputCheck.odometerKm(km)
        val cleanSource = InputCheck.odometerSource(source)
        if (cleanKm == null || cleanSource == null || cleanKm <= 0.0) {
            onResult(Result.failure(IllegalArgumentException("Enter a valid odometer reading"))); return
        }
        val vehicle = settings.value.vehicleName
        viewModelScope.launch {
            var tripToSync: TripEntity? = null
            val result = runCatching {
                if (photoPath != null) require(photoReviewed && com.example.service.OdometerPhotoStore.file(getApplication(), photoPath) != null) {
                    "Review the reading and units against the photo before saving"
                }
                database.withTransaction {
                    val dao = database.odometerReadingDao()
                    val previous = dao.latest(vehicle)
                    require(previous == null || cleanKm >= previous.readingKm) { "Reading is below the last reading for this vehicle. Check the vehicle and units." }
                    val now = System.currentTimeMillis()
                    val delta = Odometer.tripKm(previous?.readingKm, cleanKm)
                    if (logTrip && previous != null && delta != null) {
                        val overlapping = repository.getAllTripsList().any {
                            (it.vehicle == vehicle || it.vehicle.isNullOrBlank()) && it.endDate > previous.recordedAt && it.startDate < now
                        }
                        require(!overlapping) { "Trips already exist in this interval. Save the reading without logging another trip to avoid double counting." }
                        val trip = TripEntity(startDate = previous.recordedAt, endDate = now, distanceKm = delta,
                            type = null, purpose = "Odometer", vehicle = vehicle)
                        tripToSync = trip.copy(id = repository.insertTrip(trip))
                    }
                    dao.insert(com.example.data.OdometerReadingEntity(vehicle = vehicle, recordedAt = now,
                        readingKm = cleanKm, source = cleanSource, photoPath = photoPath, photoReviewed = photoReviewed))
                }
                settingsManager.setOdometer(cleanKm, cleanSource)
            }
            onResult(result)
            if (result.isSuccess) tripToSync?.let { supabaseSyncManager.onTripSaved(it) }
        }
    }

    fun saveVehicleExpenses(expense: com.example.data.VehicleExpenseEntity, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching {
                com.example.service.CanadianVehicleExpenses.calculate(expense, repository.getAllTripsList())
                database.vehicleExpenseDao().save(expense)
            })
        }
    }

    fun deleteVehicleExpenses(vehicle: String, year: Int, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { database.vehicleExpenseDao().delete(vehicle, year) }) }
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

    fun generateCsv(tripsList: List<TripEntity>, isMiles: Boolean, rate: Double): String =
        AccountantReportExporter.tripCsv(ReportSnapshot.create(tripsList, emptyList(), ReportPeriod.ALL, isMiles, rate))

    fun prepareAccountantReport(snapshot: ReportSnapshot) {
        if (_reportExportState.value.isPreparing) return
        _reportExportState.value = ReportExportUiState(isPreparing = true)
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val bundle = withContext(Dispatchers.IO) {
                    AccountantReportExporter.create(snapshot, app.filesDir, app.cacheDir)
                }
                _reportExportState.value = ReportExportUiState(bundle = bundle)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                _reportExportState.value = ReportExportUiState(error = "Could not create the export package. Nothing was shared. Try again.")
            }
        }
    }

    fun dismissReportExport() { _reportExportState.value = ReportExportUiState() }

    fun shareAccountantReport(context: Context) {
        val bundle = _reportExportState.value.bundle ?: return
        try {
            check(bundle.file.isFile)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", bundle.file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Mileage and Fuel Expense Records")
                putExtra(Intent.EXTRA_TEXT, "Trip and fuel-expense records with available receipt images. Review missing_photos.csv for unavailable photos.")
                clipData = ClipData.newRawUri("Accountant report", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Accountant Package"))
            dismissReportExport()
        } catch (_: Exception) {
            _reportExportState.value = ReportExportUiState(error = "Could not open sharing. Your receipt records are unchanged. Try exporting again.")
        }
    }

    fun openReceiptScanner() {
        _receiptScanState.value = ReceiptScanUiState(isDialogVisible = true)
    }

    fun dismissReceiptScanner() {
        _receiptScanState.value = ReceiptScanUiState(isDialogVisible = false)
    }

    fun createReceiptCaptureTarget(context: Context): ReceiptCaptureTarget {
        val dir = File(context.cacheDir, "receipt_captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        file.createNewFile()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return ReceiptCaptureTarget(file, uri)
    }

    fun reportReceiptCaptureError(message: String) {
        _receiptScanState.value = _receiptScanState.value.copy(
            isDialogVisible = true,
            isScanningOrExtracting = false,
            errorMessage = message
        )
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

            val extraction = if (prefilledData != null) {
                ExtractionResult.Success(prefilledData)
            } else {
                ReceiptExtractionService.extractReceiptDetails(bitmap)
            }
            val receiptData = when (extraction) {
                is ExtractionResult.Success -> extraction.data
                is ExtractionResult.Failure -> extraction.fallbackData ?: ExtractedReceiptData()
            }
            val errorMessage = (extraction as? ExtractionResult.Failure)?.message

            _receiptScanState.value = _receiptScanState.value.copy(
                isScanningOrExtracting = false,
                currentSavedImagePath = imagePath,
                extractedData = receiptData,
                errorMessage = errorMessage
            )
        }
    }

    fun processReceiptFromUri(context: Context, uri: Uri) {
        if (uri.scheme != "content") {
            reportReceiptCaptureError("Could not load that photo. Try taking it again.")
            return
        }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    decodeReceiptBitmap(context, uri)
                } catch (_: Exception) {
                    null
                } finally {
                    deleteCaptureFile(uri)
                }
            }
            if (bitmap != null) {
                processCapturedReceipt(context, bitmap)
            } else {
                _receiptScanState.value = _receiptScanState.value.copy(
                    errorMessage = "Could not load that photo. Try taking it again.",
                    isDialogVisible = true,
                    isScanningOrExtracting = false
                )
            }
        }
    }

    fun processSampleReceipt(context: Context) {
        viewModelScope.launch {
            val sampleBitmap = ReceiptExtractionService.createSampleGasReceiptBitmap()
            val sampleData = ExtractedReceiptData(
                merchant = "Petro-Canada",
                totalAmount = 72.27,
                currency = "$",
                fuelType = "Regular (Pump #06)",
                gallonsOrLiters = 45.200,
                unit = "L",
                pricePerUnit = 1.599,
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                paymentMethod = "Visa Contactless *6021",
                summary = "Petro-Canada fuel refill (Pump 06)",
                rawText = "PETRO-CANADA\nPUMP 06 REGULAR\n45.200 L @ 1.599/L\nTOTAL: $72.27"
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
        val entity = InputCheck.storedReceipt(
            GasReceiptEntity(
                imagePath = state.currentSavedImagePath ?: "",
                merchant = merchant,
                totalAmount = totalAmount,
                currency = currency,
                fuelType = fuelType,
                gallonsOrLiters = gallonsOrLiters,
                unit = state.extractedData?.unit ?: "L",
                pricePerUnit = pricePerUnit,
                receiptDate = receiptDate,
                paymentMethod = paymentMethod,
                rawExtraction = state.extractedData?.rawText,
                tripId = tripId
            ),
            strict = true
        )
        if (entity == null) {
            reportReceiptCaptureError("Check the receipt fields and try again.")
            return
        }

        viewModelScope.launch {
            val insertedId = gasReceiptRepository.insertReceipt(entity)
            val entityToSync = entity.copy(id = insertedId)
            supabaseSyncManager.onReceiptSaved(entityToSync)
            _receiptScanState.value = ReceiptScanUiState(
                isDialogVisible = false,
                isSavedSuccess = true
            )
        }
    }

    fun deleteReceipt(receipt: GasReceiptEntity) {
        viewModelScope.launch {
            supabaseSyncManager.deleteReceiptLocally(receipt)
            val image = InputCheck.receiptFile(getApplication<Application>().filesDir, receipt.imagePath)
            if (image != null) {
                try {
                    image.delete()
                } catch (_: Exception) {}
            }
            supabaseSyncManager.onReceiptDeleted(receipt)
        }
    }

    fun seedSampleReceipts(context: Context) {
        viewModelScope.launch {
            val dir = File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }
            val now = System.currentTimeMillis()
            val day = 86400000L

            val b1 = ReceiptExtractionService.createSampleGasReceiptBitmap(
                merchant = "ESSO",
                litres = 52.340,
                pricePerLitre = 1.549,
                total = 81.07,
                fuelType = "REGULAR (PUMP #03)"
            )
            val file1 = File(dir, "sample_receipt_1.jpg")
            FileOutputStream(file1).use { b1.compress(Bitmap.CompressFormat.JPEG, 85, it) }

            val b2 = ReceiptExtractionService.createSampleGasReceiptBitmap(
                merchant = "ULTRAMAR",
                litres = 48.150,
                pricePerLitre = 1.629,
                total = 78.44,
                fuelType = "PREMIUM (PUMP #08)"
            )
            val file2 = File(dir, "sample_receipt_2.jpg")
            FileOutputStream(file2).use { b2.compress(Bitmap.CompressFormat.JPEG, 85, it) }

            val list = listOf(
                GasReceiptEntity(
                    timestamp = now - 1 * day,
                    imagePath = file1.absolutePath,
                    merchant = "Esso",
                    totalAmount = 81.07,
                    currency = "$",
                    fuelType = "Regular (Pump 03)",
                    gallonsOrLiters = 52.340,
                    unit = "L",
                    pricePerUnit = 1.549,
                    receiptDate = "2026-09-05",
                    paymentMethod = "Visa *4921",
                    rawExtraction = "ESSO\nPUMP 03\n52.340 L @ 1.549/L\nTOTAL: $81.07"
                ),
                GasReceiptEntity(
                    timestamp = now - 4 * day,
                    imagePath = file2.absolutePath,
                    merchant = "Ultramar",
                    totalAmount = 78.44,
                    currency = "$",
                    fuelType = "Premium (Pump 08)",
                    gallonsOrLiters = 48.150,
                    unit = "L",
                    pricePerUnit = 1.629,
                    receiptDate = "2026-09-02",
                    paymentMethod = "Amex *1004",
                    rawExtraction = "ULTRAMAR\nPUMP 08\n48.150 L @ 1.629/L\nTOTAL: $78.44"
                )
            )
            for (r in list) {
                gasReceiptRepository.insertReceipt(r)
            }
        }
    }

    private fun decodeReceiptBitmap(context: Context, uri: Uri): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readCapped(it) } ?: return null
        if (!InputCheck.acceptReceiptImage(bytes)) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

        val rotation = try {
            ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees
        } catch (_: Exception) {
            0
        }
        if (rotation == 0) return decoded

        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }

    private fun readCapped(stream: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > InputCheck.MAX_RECEIPT_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun deleteCaptureFile(uri: Uri) {
        val name = uri.lastPathSegment ?: return
        val file = InputCheck.captureFile(getApplication<Application>().cacheDir, name) ?: return
        if (file.exists()) file.delete()
    }
}
