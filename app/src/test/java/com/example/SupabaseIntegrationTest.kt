package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.GasReceiptEntity
import com.example.data.TripEntity
import com.example.data.supabase.SupabaseClient
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseReceiptDto
import com.example.data.supabase.SupabaseSession
import com.example.data.supabase.SupabaseSyncManager
import com.example.data.supabase.SupabaseTripDto
import com.example.data.supabase.SupabaseUser
import com.example.data.supabase.SyncStatus
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseIntegrationTest {

    private lateinit var app: Application
    private lateinit var config: SupabaseConfig

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        config = SupabaseConfig(app)
        config.clearSession()
    }

    @Test
    fun testDefaultSupabaseUrlMatchesProject() {
        val url = config.getUrl()
        assertEquals("https://gszhecjcksyxlptlmwok.supabase.co", url)
    }

    @Test
    fun testSupabaseConfigAnonKeyAndSession() {
        config.setAnonKey("test_anon_key_123")
        assertEquals("test_anon_key_123", config.getAnonKey())
        assertTrue(config.isConfigured())

        val session = SupabaseSession(
            accessToken = "mock_access_token",
            refreshToken = "mock_refresh_token",
            user = SupabaseUser(
                id = "usr_123",
                email = "driver@example.com",
                name = "Test Driver"
            )
        )
        config.saveSession(session)

        val retrieved = config.getSession()
        assertNotNull(retrieved)
        assertEquals("usr_123", retrieved?.user?.id)
        assertEquals("driver@example.com", retrieved?.user?.email)
        assertEquals("mock_access_token", retrieved?.accessToken)

        config.clearSession()
        assertNull(config.getSession())
    }

    @Test
    fun testTripDtoSerializationAndMapping() {
        val trip = TripEntity(
            id = 42L,
            startDate = 1700000000000L,
            endDate = 1700003600000L,
            distanceKm = 18.5,
            type = "Business",
            startAddress = "123 Main St",
            endAddress = "456 Market St",
            purpose = "Client Visit",
            vehicle = "Model 3",
            pathJson = "[{\"lat\":37.77,\"lng\":-122.41}]"
        )

        val dto = SupabaseTripDto.fromTripEntity(trip, userId = "usr_42")
        assertEquals(42L, dto.clientId)
        assertEquals("usr_42", dto.userId)
        assertEquals(18.5, dto.distanceKm, 0.001)

        val json = dto.toJsonObject()
        assertEquals(42L, json.getLong("client_id"))
        assertEquals("usr_42", json.getString("user_id"))
        assertEquals("Business", json.getString("type"))

        val parsedDto = SupabaseTripDto.fromJsonObject(json)
        assertEquals(dto.clientId, parsedDto.clientId)
        assertEquals(dto.distanceKm, parsedDto.distanceKm, 0.001)
        assertEquals(dto.type, parsedDto.type)
        assertEquals(dto.purpose, parsedDto.purpose)

        val convertedEntity = parsedDto.toTripEntity()
        assertEquals(trip.id, convertedEntity.id)
        assertEquals(trip.startDate, convertedEntity.startDate)
        assertEquals(trip.distanceKm, convertedEntity.distanceKm, 0.001)
        assertEquals(trip.type, convertedEntity.type)
        assertEquals(trip.startAddress, convertedEntity.startAddress)
        assertEquals(trip.endAddress, convertedEntity.endAddress)
    }

    @Test
    fun testReceiptDtoSerializationAndMapping() {
        val receipt = GasReceiptEntity(
            id = 101L,
            timestamp = 1700005000000L,
            imagePath = "/data/receipts/rec_101.jpg",
            merchant = "Shell Service Station",
            totalAmount = 62.40,
            currency = "$",
            fuelType = "Premium 91",
            gallonsOrLiters = 15.6,
            unit = "gal",
            pricePerUnit = 4.00,
            receiptDate = "2026-10-10",
            paymentMethod = "Mastercard *1234",
            rawExtraction = "SHELL\nTOTAL: $62.40",
            tripId = 42L
        )

        val dto = SupabaseReceiptDto.fromGasReceiptEntity(receipt, userId = "usr_42")
        assertEquals(101L, dto.clientId)
        assertEquals("Shell Service Station", dto.merchant)
        assertEquals(62.40, dto.totalAmount, 0.001)

        val json = dto.toJsonObject()
        assertEquals(101L, json.getLong("client_id"))
        assertEquals("Shell Service Station", json.getString("merchant"))
        assertEquals(62.40, json.getDouble("total_amount"), 0.001)

        val parsedDto = SupabaseReceiptDto.fromJsonObject(json)
        assertEquals(dto.clientId, parsedDto.clientId)
        assertEquals(dto.totalAmount, parsedDto.totalAmount, 0.001)
        assertEquals(dto.merchant, parsedDto.merchant)
        assertEquals(dto.gallonsOrLiters, parsedDto.gallonsOrLiters)

        val convertedEntity = parsedDto.toGasReceiptEntity()
        assertEquals(receipt.id, convertedEntity.id)
        assertEquals(receipt.merchant, convertedEntity.merchant)
        assertEquals(receipt.totalAmount, convertedEntity.totalAmount, 0.001)
        assertEquals(receipt.tripId, convertedEntity.tripId)
    }

    @Test
    fun testSyncManagerReportsNotConfiguredWhenKeyMissing() = runTest {
        config.setAnonKey("")
        val client = SupabaseClient(config)
        val db = com.example.data.AppDatabase.getDatabase(app)
        val tripRepo = com.example.data.TripRepository(db.tripDao())
        val receiptRepo = com.example.data.GasReceiptRepository(db.gasReceiptDao())
        val syncManager = SupabaseSyncManager(tripRepo, receiptRepo, client)

        val state = syncManager.syncAll()
        assertEquals(SyncStatus.NOT_CONFIGURED, state.status)
    }

    @Test
    fun testExtractErrorMessageInvalidCredentials() {
        val client = SupabaseClient(config)
        val json = """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}"""
        val error = client.extractErrorMessage(json)
        assertEquals("Invalid email or password. Please verify your credentials and try again.", error)
    }

    @Test
    fun testExtractErrorMessageUserAlreadyExists() {
        val client = SupabaseClient(config)
        val json = """{"code":422,"error_code":"user_already_exists","msg":"User already registered"}"""
        val error = client.extractErrorMessage(json)
        assertEquals("An account with this email already exists. Please sign in instead.", error)
    }

    @Test
    fun testExtractErrorMessageRateLimit() {
        val client = SupabaseClient(config)
        val json = """{"code":429,"error_code":"over_email_send_rate_limit","msg":"Rate limit exceeded"}"""
        val error = client.extractErrorMessage(json)
        assertEquals("For security reasons, please wait a minute before requesting another email.", error)
    }

    @Test
    fun testResetPasswordFailsWhenNotConfigured() = runTest {
        config.setAnonKey("")
        val client = SupabaseClient(config)
        val res = client.resetPassword("driver@example.com")
        assertTrue(res.isFailure)
    }

    @Test
    fun testViewModelSignInValidationAndState() {
        config.setAnonKey("")
        val vm = com.example.ui.MileageViewModel(app)

        var reportedError: String? = null
        vm.signIn("invalid-email", "pass", onError = { reportedError = it })
        assertEquals("Please enter a valid email address.", reportedError)
        assertEquals("Please enter a valid email address.", vm.authUiState.value.errorMessage)

        vm.signIn("valid@example.com", "123", onError = { reportedError = it })
        assertEquals("Password must be at least 6 characters.", reportedError)
        assertEquals("Password must be at least 6 characters.", vm.authUiState.value.errorMessage)
    }

    @Test
    fun testViewModelSignUpValidationAndState() {
        config.setAnonKey("")
        val vm = com.example.ui.MileageViewModel(app)

        var reportedError: String? = null
        vm.signUp("", "valid@example.com", "password123", onError = { reportedError = it })
        assertEquals("Please enter your name.", reportedError)

        vm.signUp("John Doe", "bad-email", "password123", onError = { reportedError = it })
        assertEquals("Please enter a valid email address.", reportedError)

        vm.signUp("John Doe", "valid@example.com", "123", onError = { reportedError = it })
        assertEquals("Password must be at least 6 characters.", reportedError)
    }
}
