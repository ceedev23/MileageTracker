package com.example.data.supabase

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "SupabaseClient"

class SupabaseClient(
    val config: SupabaseConfig,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val refreshMutex = Mutex()

    private fun baseUrl(): String = config.getUrl().removeSuffix("/")
    private fun anonKey(): String = config.getAnonKey()

    private fun headers(bearer: String? = null): Map<String, String> {
        val key = anonKey()
        val headers = mutableMapOf(
            "apikey" to key,
            "Content-Type" to "application/json"
        )
        if (!bearer.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $bearer"
        }
        return headers
    }

    private suspend fun authorizedHeaders(): Map<String, String> {
        val existing = config.getSession()
        val session = ensureFreshSession()
        val bearer = when {
            session != null -> session.accessToken
            existing != null && config.getSession() == null ->
                throw IOException("Session expired. Sign in again.")
            existing != null ->
                throw IOException("Couldn't refresh your session. Check your connection.")
            else -> anonKey()
        }
        return headers(bearer.ifBlank { anonKey() })
    }

    suspend fun ensureFreshSession(): SupabaseSession? = withContext(Dispatchers.IO) {
        val existing = config.getSession() ?: return@withContext null
        if (existing.refreshToken.isNotBlank() && !existing.isAccessExpired()) return@withContext existing
        if (existing.refreshToken.isBlank()) {
            config.clearSession()
            return@withContext null
        }
        refreshMutex.withLock {
            val current = config.getSession() ?: return@withLock null
            if (!current.isAccessExpired()) return@withLock current
            val refreshed = refreshSession(current)
            if (refreshed.isSuccess) {
                refreshed.getOrNull()
            } else if (refreshed.exceptionOrNull() is SessionRejectedException) {
                config.clearSession()
                null
            } else if (current.expiresAt > System.currentTimeMillis()) {
                current
            } else {
                null
            }
        }
    }

    private fun refreshSession(session: SupabaseSession): Result<SupabaseSession> {
        return try {
            val url = "${baseUrl()}/auth/v1/token?grant_type=refresh_token"
            val json = JSONObject().put("refresh_token", session.refreshToken)
            val requestBuilder = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody(jsonMediaType))
            headers().forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    if (response.code == 400 || response.code == 401 || response.code == 403) {
                        return Result.failure(SessionRejectedException())
                    }
                    return Result.failure(fromBody(
                        "Couldn't refresh your session. Check your connection.",
                        "session refresh",
                        response.code,
                        body
                    ))
                }
                val parsed = sessionFromAuthResponse(
                    JSONObject(body),
                    session.user,
                    session.refreshToken
                )
                config.saveSession(parsed)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't refresh your session. Check your connection.", "session refresh failed", e))
        }
    }

    private fun sessionFromAuthResponse(
        json: JSONObject,
        fallback: SupabaseUser,
        previousRefresh: String = ""
    ): SupabaseSession {
        val userObj = json.optJSONObject("user") ?: json
        val metadataName = userObj.optJSONObject("user_metadata")?.optString("full_name").orEmpty()
        val accessToken = json.optString("access_token")
        return SupabaseSession(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token").ifBlank { previousRefresh },
            user = SupabaseUser(
                id = userObj.optString("id").ifBlank { fallback.id },
                email = userObj.optString("email").ifBlank { fallback.email },
                name = metadataName.ifBlank { fallback.name }
            ),
            expiresAt = if (accessToken.isBlank()) {
                0L
            } else {
                System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
            }
        )
    }

    private fun loginLimiter(): LoginRateLimiter = LoginRateLimiter(
        failures = config.loginFailureCount(),
        lockedUntil = config.loginLockedUntil()
    )

    private fun persistLoginLimiter(limiter: LoginRateLimiter) {
        config.saveLoginAttempts(limiter.failures, limiter.lockedUntil)
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(hidden("Couldn't connect. Check your connection.", "anon key missing"))
        }

        try {
            // First check the auth settings endpoint which is open to anon API keys
            val authUrl = "${baseUrl()}/auth/v1/settings"
            val authRequest = Request.Builder().url(authUrl).get()
            headers().forEach { (k, v) -> authRequest.header(k, v) }

            httpClient.newCall(authRequest.build()).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext Result.success("Connected to Supabase project successfully (${response.code})")
                }
                if (response.code == 401) {
                    return@withContext Result.failure(hidden("Couldn't connect. Check your connection.", "auth settings HTTP 401"))
                }
            }

            // Fallback: check table endpoint
            testTableEndpoint()
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't connect. Check your connection.", "connect ${baseUrl()}", e))
        }
    }

    private suspend fun testTableEndpoint(): Result<String> {
        val url = "${baseUrl()}/rest/v1/trips?select=id&limit=1"
        val requestBuilder = Request.Builder().url(url).get()
        authorizedHeaders().forEach { (k, v) -> requestBuilder.header(k, v) }

        return try {
            httpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Connected to Supabase trips table")
                } else if (resp.code == 404 || resp.code == 400) {
                    val body = resp.body?.string() ?: ""
                    if (body.contains("relation") || body.contains("does not exist") || body.contains("PGRST")) {
                        Result.success("Connected to Supabase project (trips table needs to be created with schema.sql)")
                    } else {
                        Result.failure(hidden(
                            "Couldn't connect. Check your connection.",
                            "trips probe HTTP ${resp.code} ${body.take(180)}"
                        ))
                    }
                } else {
                    Result.failure(hidden(
                        "Couldn't connect. Check your connection.",
                        "trips probe HTTP ${resp.code}"
                    ))
                }
            }
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't connect. Check your connection.", "trips probe failed", e))
        }
    }

    suspend fun signUp(email: String, pass: String, name: String?): Result<SupabaseSession> =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured()) {
                return@withContext Result.failure(hidden("Sign up failed. Please try again.", "not configured"))
            }

            try {
                val url = "${baseUrl()}/auth/v1/signup"
                // GoTrue bcrypt-hashes password. A client hash would break accounts and reset emails.
                val json = JSONObject().apply {
                    put("email", email.trim())
                    put("password", pass)
                    if (!name.isNullOrBlank()) {
                        put("data", JSONObject().put("full_name", name.trim()))
                    }
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody(jsonMediaType))

                headers().forEach { (k, v) -> requestBuilder.header(k, v) }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(fromBody(
                            "Sign up failed. Please try again.",
                            "sign up",
                            response.code,
                            body
                        ))
                    }

                    val session = sessionFromAuthResponse(
                        JSONObject(body),
                        SupabaseUser(id = "", email = email, name = name)
                    )
                    if (session.accessToken.isNotBlank()) {
                        config.saveSession(session)
                    }
                    Result.success(session)
                }
            } catch (e: Exception) {
                Result.failure(hidden("Sign up failed. Please try again.", "sign up failed", e))
            }
        }

    suspend fun signIn(email: String, pass: String): Result<SupabaseSession> =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured()) {
                return@withContext Result.failure(hidden("Sign in failed. Please try again.", "not configured"))
            }

            val limiter = loginLimiter()
            limiter.blockMessage()?.let { return@withContext Result.failure(IOException(it)) }

            try {
                val url = "${baseUrl()}/auth/v1/token?grant_type=password"
                // GoTrue bcrypt-hashes password. A client hash would break accounts and reset emails.
                val json = JSONObject().apply {
                    put("email", email.trim())
                    put("password", pass)
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody(jsonMediaType))

                headers().forEach { (k, v) -> requestBuilder.header(k, v) }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        if (response.code == 400 || response.code == 401 || response.code == 429) {
                            limiter.recordFailure()
                            persistLoginLimiter(limiter)
                        }
                        return@withContext Result.failure(fromBody(
                            "Sign in failed. Please try again.",
                            "sign in",
                            response.code,
                            body
                        ))
                    }

                    val session = sessionFromAuthResponse(
                        JSONObject(body),
                        SupabaseUser(id = "", email = email, name = null)
                    )
                    config.saveSession(session)
                    limiter.recordSuccess()
                    persistLoginLimiter(limiter)
                    Result.success(session)
                }
            } catch (e: Exception) {
                Result.failure(hidden("Sign in failed. Please try again.", "sign in failed", e))
            }
        }

    suspend fun resetPassword(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(hidden("Failed to send reset link.", "not configured"))
        }

        try {
            val url = "${baseUrl()}/auth/v1/recover"
            val json = JSONObject().apply {
                put("email", email.trim())
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody(jsonMediaType))

            headers().forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(fromBody(
                        "Failed to send reset link.",
                        "password reset",
                        response.code,
                        body
                    ))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(hidden("Failed to send reset link.", "password reset failed", e))
        }
    }

    suspend fun signOut(): Result<Boolean> = withContext(Dispatchers.IO) {
        val session = config.getSession()
        if (session != null && session.accessToken.isNotBlank() && config.isConfigured()) {
            try {
                val url = "${baseUrl()}/auth/v1/logout"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post("{}".toRequestBody(jsonMediaType))
                headers(session.accessToken).forEach { (k, v) -> requestBuilder.header(k, v) }
                httpClient.newCall(requestBuilder.build()).execute().close()
            } catch (_: Exception) {}
        }
        config.clearSession()
        Result.success(true)
    }

    suspend fun fetchTrips(): Result<List<SupabaseTripDto>> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(hidden("Couldn't sync. Try again.", "not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/trips?select=*&order=start_date.desc"
            val requestBuilder = Request.Builder().url(url).get()
            authorizedHeaders().forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(fromBody("Couldn't sync. Try again.", "fetch trips", response.code, body))
                }

                val list = mutableListOf<SupabaseTripDto>()
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(SupabaseTripDto.fromJsonObject(obj))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't sync. Try again.", "fetch trips failed", e))
        }
    }

    suspend fun upsertTrip(tripDto: SupabaseTripDto): Result<SupabaseTripDto> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(hidden("Couldn't sync. Try again.", "not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/trips?on_conflict=client_id"
            val bodyJson = tripDto.toJsonObject().toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody(jsonMediaType))
                .header("Prefer", "resolution=merge-duplicates,return=representation")

            authorizedHeaders().forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(fromBody("Couldn't sync. Try again.", "upsert trip", response.code, body))
                }

                val array = JSONArray(body)
                if (array.length() > 0) {
                    Result.success(SupabaseTripDto.fromJsonObject(array.getJSONObject(0)))
                } else {
                    Result.success(tripDto)
                }
            }
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't sync. Try again.", "upsert trip failed", e))
        }
    }

    suspend fun deleteTrip(clientId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(hidden("Couldn't sync. Try again.", "not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/trips?client_id=eq.$clientId"
            val requestBuilder = Request.Builder().url(url).delete()
            authorizedHeaders().forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(hidden("Couldn't sync. Try again.", "delete trip HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't sync. Try again.", "delete trip failed", e))
        }
    }

    suspend fun fetchReceipts(): Result<List<SupabaseReceiptDto>> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(hidden("Couldn't sync. Try again.", "not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/gas_receipts?select=*&order=timestamp.desc"
            val requestBuilder = Request.Builder().url(url).get()
            authorizedHeaders().forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(fromBody("Couldn't sync. Try again.", "fetch receipts", response.code, body))
                }

                val list = mutableListOf<SupabaseReceiptDto>()
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(SupabaseReceiptDto.fromJsonObject(obj))
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't sync. Try again.", "fetch receipts failed", e))
        }
    }

    suspend fun upsertReceipt(receiptDto: SupabaseReceiptDto): Result<SupabaseReceiptDto> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(hidden("Couldn't sync. Try again.", "not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/gas_receipts?on_conflict=client_id"
            val bodyJson = receiptDto.toJsonObject().toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody(jsonMediaType))
                .header("Prefer", "resolution=merge-duplicates,return=representation")

            authorizedHeaders().forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(fromBody("Couldn't sync. Try again.", "upsert receipt", response.code, body))
                }

                val array = JSONArray(body)
                if (array.length() > 0) {
                    Result.success(SupabaseReceiptDto.fromJsonObject(array.getJSONObject(0)))
                } else {
                    Result.success(receiptDto)
                }
            }
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't sync. Try again.", "upsert receipt failed", e))
        }
    }

    suspend fun deleteReceipt(clientId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(hidden("Couldn't sync. Try again.", "not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/gas_receipts?client_id=eq.$clientId"
            val requestBuilder = Request.Builder().url(url).delete()
            authorizedHeaders().forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(hidden("Couldn't sync. Try again.", "delete receipt HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(hidden("Couldn't sync. Try again.", "delete receipt failed", e))
        }
    }

    /** Receipt storage always requires a real session; never fall back to the anon key. */
    suspend fun receiptObject(method: String, key: String, bytes: ByteArray? = null): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val session = ensureFreshSession() ?: throw IOException("Sign in to back up receipt photos.")
                require(ReceiptPhotoBackup.validKey(key, session.user.id)) { "Invalid receipt object" }
                val path = if (method == "GET") "object/authenticated" else "object"
                val suffix = if (method == "DELETE") "receipt-photos" else "receipt-photos/$key"
                val builder = Request.Builder().url("${baseUrl()}/storage/v1/$path/$suffix")
                headers(session.accessToken).forEach { (k, v) -> builder.header(k, v) }
                when (method) {
                    "POST" -> {
                        requireNotNull(bytes)
                        ReceiptPhotoBackup.validateImage(bytes)
                        builder.header("Content-Type", "image/jpeg").header("x-upsert", "true")
                            .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                    }
                    "GET" -> builder.get()
                    "DELETE" -> builder.delete(JSONObject().put("prefixes", JSONArray().put(key)).toString().toRequestBody(jsonMediaType))
                    else -> throw IOException("Unsupported receipt operation")
                }
                httpClient.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful && !(method == "DELETE" && response.code == 404)) throw IOException("Receipt photo operation failed. Try syncing again.")
                    if (method != "GET") return@withContext Result.success(ByteArray(0))
                    val body = response.body ?: throw IOException("Receipt photo was empty.")
                    require(body.contentLength() <= ReceiptPhotoBackup.MAX_BYTES) { "Receipt photo is too large" }
                    val output = java.io.ByteArrayOutputStream()
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= ReceiptPhotoBackup.MAX_BYTES) { "Receipt photo is too large" }
                            output.write(buffer, 0, count)
                        }
                    }
                    Result.success(output.toByteArray().also { ReceiptPhotoBackup.validateImage(it) })
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                // Do not log document bytes, object paths, credentials or provider response bodies.
                Result.failure(IOException("Receipt photo backup or restore failed. Your local photo is kept. Try syncing again."))
            }
        }

    // Known auth codes only. Anything else is logged by the caller and replaced with a fixed sentence.
    fun extractErrorMessage(jsonBody: String): String? {
        return try {
            val obj = JSONObject(jsonBody)
            val errorCode = obj.optString("error_code").ifBlank { obj.optString("error") }

            when (errorCode) {
                "invalid_credentials" -> "Invalid email or password. Please verify your credentials and try again."
                "user_already_exists" -> "An account with this email already exists. Please sign in instead."
                "over_email_send_rate_limit" -> "For security reasons, please wait a minute before requesting another email."
                "email_not_confirmed" -> "Please confirm your email address before signing in. Check your inbox for the confirmation link."
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    // logcat only; screens read the exception message
    private fun hidden(userMessage: String, detail: String, error: Throwable? = null): IOException {
        if (error == null) Log.e(TAG, detail) else Log.e(TAG, detail, error)
        return IOException(userMessage)
    }

    private fun fromBody(userMessage: String, action: String, code: Int, body: String): IOException {
        extractErrorMessage(body)?.let { return IOException(it) }
        return hidden(userMessage, "$action HTTP $code ${body.take(180)}")
    }
}

private class SessionRejectedException : IOException("Session expired. Sign in again.")
