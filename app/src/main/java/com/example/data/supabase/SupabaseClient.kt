package com.example.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseClient(
    val config: SupabaseConfig,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(): String = config.getUrl().removeSuffix("/")
    private fun anonKey(): String = config.getAnonKey()

    private fun buildHeaders(includeAuth: Boolean = true): Map<String, String> {
        val key = anonKey()
        val headers = mutableMapOf(
            "apikey" to key,
            "Content-Type" to "application/json"
        )
        if (includeAuth) {
            val session = config.getSession()
            val token = session?.accessToken?.takeIf { it.isNotBlank() } ?: key
            if (token.isNotBlank()) {
                headers["Authorization"] = "Bearer $token"
            }
        }
        return headers
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Supabase anon key is not set. Please provide your Supabase anon/publishable key.")
            )
        }

        try {
            // First check the auth settings endpoint which is open to anon API keys
            val authUrl = "${baseUrl()}/auth/v1/settings"
            val authRequest = Request.Builder().url(authUrl).get()
            buildHeaders(includeAuth = false).forEach { (k, v) -> authRequest.header(k, v) }

            httpClient.newCall(authRequest.build()).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext Result.success("Connected to Supabase project successfully (${response.code})")
                }
                if (response.code == 401) {
                    return@withContext Result.failure(IOException("Unauthorized (401): Check your Supabase anon API key."))
                }
            }

            // Fallback: check table endpoint
            testTableEndpoint()
        } catch (e: Exception) {
            Result.failure(IOException("Failed to connect to ${baseUrl()}: ${e.message}", e))
        }
    }

    private fun testTableEndpoint(): Result<String> {
        val url = "${baseUrl()}/rest/v1/trips?select=id&limit=1"
        val requestBuilder = Request.Builder().url(url).get()
        buildHeaders(includeAuth = true).forEach { (k, v) -> requestBuilder.header(k, v) }

        return try {
            httpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    Result.success("Connected to Supabase trips table")
                } else if (resp.code == 404 || resp.code == 400) {
                    val body = resp.body?.string() ?: ""
                    if (body.contains("relation") || body.contains("does not exist") || body.contains("PGRST")) {
                        Result.success("Connected to Supabase project (trips table needs to be created with schema.sql)")
                    } else {
                        Result.failure(IOException("Supabase error ${resp.code}: $body"))
                    }
                } else {
                    Result.failure(IOException("Supabase error ${resp.code}: ${resp.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, pass: String, name: String?): Result<SupabaseSession> =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured()) {
                return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
            }

            try {
                val url = "${baseUrl()}/auth/v1/signup"
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

                buildHeaders(includeAuth = false).forEach { (k, v) -> requestBuilder.header(k, v) }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = extractErrorMessage(body) ?: "Sign up failed (${response.code})"
                        return@withContext Result.failure(IOException(errMsg))
                    }

                    val jsonResp = JSONObject(body)
                    val accessToken = jsonResp.optString("access_token", "")
                    val refreshToken = jsonResp.optString("refresh_token", "")
                    val userObj = jsonResp.optJSONObject("user") ?: jsonResp

                    val userId = userObj.optString("id")
                    val userEmail = userObj.optString("email", email)
                    val userName = userObj.optJSONObject("user_metadata")?.optString("full_name") ?: name

                    val session = SupabaseSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        user = SupabaseUser(id = userId, email = userEmail, name = userName),
                        expiresAt = if (accessToken.isNotBlank()) {
                            System.currentTimeMillis() + (jsonResp.optLong("expires_in", 3600L) * 1000L)
                        } else {
                            0L
                        }
                    )
                    if (accessToken.isNotBlank()) {
                        config.saveSession(session)
                    }
                    Result.success(session)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun signIn(email: String, pass: String): Result<SupabaseSession> =
        withContext(Dispatchers.IO) {
            if (!config.isConfigured()) {
                return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
            }

            try {
                val url = "${baseUrl()}/auth/v1/token?grant_type=password"
                val json = JSONObject().apply {
                    put("email", email.trim())
                    put("password", pass)
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody(jsonMediaType))

                buildHeaders(includeAuth = false).forEach { (k, v) -> requestBuilder.header(k, v) }

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val errMsg = extractErrorMessage(body) ?: "Sign in failed (${response.code})"
                        return@withContext Result.failure(IOException(errMsg))
                    }

                    val jsonResp = JSONObject(body)
                    val accessToken = jsonResp.optString("access_token")
                    val refreshToken = jsonResp.optString("refresh_token")
                    val userObj = jsonResp.optJSONObject("user")

                    val userId = userObj?.optString("id") ?: ""
                    val userEmail = userObj?.optString("email") ?: email
                    val userName = userObj?.optJSONObject("user_metadata")?.optString("full_name")

                    val session = SupabaseSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        user = SupabaseUser(id = userId, email = userEmail, name = userName),
                        expiresAt = System.currentTimeMillis() + (jsonResp.optLong("expires_in", 3600L) * 1000L)
                    )
                    config.saveSession(session)
                    Result.success(session)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun resetPassword(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        }

        try {
            val url = "${baseUrl()}/auth/v1/recover"
            val json = JSONObject().apply {
                put("email", email.trim())
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody(jsonMediaType))

            buildHeaders(includeAuth = false).forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = extractErrorMessage(body) ?: "Password reset request failed (${response.code})"
                    return@withContext Result.failure(IOException(errMsg))
                }
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
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
                buildHeaders(includeAuth = true).forEach { (k, v) -> requestBuilder.header(k, v) }
                httpClient.newCall(requestBuilder.build()).execute().close()
            } catch (_: Exception) {}
        }
        config.clearSession()
        Result.success(true)
    }

    suspend fun fetchTrips(): Result<List<SupabaseTripDto>> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/trips?select=*&order=start_date.desc"
            val requestBuilder = Request.Builder().url(url).get()
            buildHeaders(includeAuth = true).forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = extractErrorMessage(body) ?: "Fetch trips failed (${response.code})"
                    return@withContext Result.failure(IOException(errMsg))
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
            Result.failure(e)
        }
    }

    suspend fun upsertTrip(tripDto: SupabaseTripDto): Result<SupabaseTripDto> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/trips?on_conflict=client_id"
            val bodyJson = tripDto.toJsonObject().toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody(jsonMediaType))
                .header("Prefer", "resolution=merge-duplicates,return=representation")

            buildHeaders(includeAuth = true).forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = extractErrorMessage(body) ?: "Upsert trip failed (${response.code})"
                    return@withContext Result.failure(IOException(errMsg))
                }

                val array = JSONArray(body)
                if (array.length() > 0) {
                    Result.success(SupabaseTripDto.fromJsonObject(array.getJSONObject(0)))
                } else {
                    Result.success(tripDto)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTrip(clientId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/trips?client_id=eq.$clientId"
            val requestBuilder = Request.Builder().url(url).delete()
            buildHeaders(includeAuth = true).forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("Delete trip failed (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchReceipts(): Result<List<SupabaseReceiptDto>> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/gas_receipts?select=*&order=timestamp.desc"
            val requestBuilder = Request.Builder().url(url).get()
            buildHeaders(includeAuth = true).forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = extractErrorMessage(body) ?: "Fetch receipts failed (${response.code})"
                    return@withContext Result.failure(IOException(errMsg))
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
            Result.failure(e)
        }
    }

    suspend fun upsertReceipt(receiptDto: SupabaseReceiptDto): Result<SupabaseReceiptDto> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/gas_receipts?on_conflict=client_id"
            val bodyJson = receiptDto.toJsonObject().toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(bodyJson.toRequestBody(jsonMediaType))
                .header("Prefer", "resolution=merge-duplicates,return=representation")

            buildHeaders(includeAuth = true).forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errMsg = extractErrorMessage(body) ?: "Upsert receipt failed (${response.code})"
                    return@withContext Result.failure(IOException(errMsg))
                }

                val array = JSONArray(body)
                if (array.length() > 0) {
                    Result.success(SupabaseReceiptDto.fromJsonObject(array.getJSONObject(0)))
                } else {
                    Result.success(receiptDto)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteReceipt(clientId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!config.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured"))
        }

        try {
            val url = "${baseUrl()}/rest/v1/gas_receipts?client_id=eq.$clientId"
            val requestBuilder = Request.Builder().url(url).delete()
            buildHeaders(includeAuth = true).forEach { (k, v) -> requestBuilder.header(k, v) }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("Delete receipt failed (${response.code})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractErrorMessage(jsonBody: String): String? {
        return try {
            val obj = JSONObject(jsonBody)
            val msg = obj.optString("msg")
                .ifBlank { obj.optString("message") }
                .ifBlank { obj.optString("error_description") }
            val errorCode = obj.optString("error_code").ifBlank { obj.optString("error") }

            when (errorCode) {
                "invalid_credentials" -> "Invalid email or password. Please verify your credentials and try again."
                "user_already_exists" -> "An account with this email already exists. Please sign in instead."
                "over_email_send_rate_limit" -> "For security reasons, please wait a minute before requesting another email."
                "email_not_confirmed" -> "Please confirm your email address before signing in. Check your inbox for the confirmation link."
                else -> {
                    if (msg.isNotBlank()) msg else errorCode.takeIf { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
