package com.example.data.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.InputCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SupabaseConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_config_prefs", Context.MODE_PRIVATE)

    private val defaultUrl = try {
        BuildConfig.SUPABASE_URL.ifBlank { "https://gszhecjcksyxlptlmwok.supabase.co" }
    } catch (_: Throwable) {
        "https://gszhecjcksyxlptlmwok.supabase.co"
    }

    private val defaultAnonKey = try {
        BuildConfig.SUPABASE_ANON_KEY.takeIf { it != "YOUR_SUPABASE_ANON_KEY" } ?: ""
    } catch (_: Throwable) {
        ""
    }

    private val _urlFlow = MutableStateFlow(getUrl())
    val urlFlow: StateFlow<String> = _urlFlow.asStateFlow()

    private val _anonKeyFlow = MutableStateFlow(getAnonKey())
    val anonKeyFlow: StateFlow<String> = _anonKeyFlow.asStateFlow()

    private val _sessionFlow = MutableStateFlow(getSession())
    val sessionFlow: StateFlow<SupabaseSession?> = _sessionFlow.asStateFlow()

    fun getUrl(): String {
        val stored = prefs.getString(KEY_URL, null)
        return stored ?: defaultUrl
    }

    fun setUrl(url: String) {
        val clean = InputCheck.projectUrl(url) ?: return
        prefs.edit().putString(KEY_URL, clean).apply()
        _urlFlow.value = clean
    }

    fun getAnonKey(): String {
        val stored = prefs.getString(KEY_ANON_KEY, null)
        return stored ?: defaultAnonKey
    }

    fun setAnonKey(key: String) {
        val clean = InputCheck.anonKey(key) ?: return
        prefs.edit().putString(KEY_ANON_KEY, clean).apply()
        _anonKeyFlow.value = clean
    }

    fun clearAnonKey() {
        prefs.edit().remove(KEY_ANON_KEY).apply()
        _anonKeyFlow.value = defaultAnonKey
    }

    fun isConfigured(): Boolean {
        val key = getAnonKey()
        return key.isNotBlank() && key != "YOUR_SUPABASE_ANON_KEY"
    }

    fun getSession(): SupabaseSession? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val userEmail = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val userName = prefs.getString(KEY_USER_NAME, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        return SupabaseSession(
            accessToken = token,
            refreshToken = refreshToken,
            user = SupabaseUser(id = userId, email = userEmail, name = userName),
            expiresAt = expiresAt
        )
    }

    fun saveSession(session: SupabaseSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_USER_EMAIL, session.user.email)
            .putString(KEY_USER_NAME, session.user.name)
            .putLong(KEY_EXPIRES_AT, session.expiresAt)
            .apply()
        _sessionFlow.value = session
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_EXPIRES_AT)
            .apply()
        _sessionFlow.value = null
    }

    fun loginFailureCount(): Int = prefs.getInt(KEY_LOGIN_FAILURES, 0)

    fun loginLockedUntil(): Long = prefs.getLong(KEY_LOGIN_LOCKED_UNTIL, 0L)

    fun saveLoginAttempts(failures: Int, lockedUntil: Long) {
        prefs.edit()
            .putInt(KEY_LOGIN_FAILURES, failures)
            .putLong(KEY_LOGIN_LOCKED_UNTIL, lockedUntil)
            .commit()
    }

    companion object {
        private const val KEY_URL = "supabase_url"
        private const val KEY_ANON_KEY = "supabase_anon_key"
        private const val KEY_ACCESS_TOKEN = "supabase_access_token"
        private const val KEY_REFRESH_TOKEN = "supabase_refresh_token"
        private const val KEY_USER_ID = "supabase_user_id"
        private const val KEY_USER_EMAIL = "supabase_user_email"
        private const val KEY_USER_NAME = "supabase_user_name"
        private const val KEY_EXPIRES_AT = "supabase_expires_at"
        private const val KEY_LOGIN_FAILURES = "login_failures"
        private const val KEY_LOGIN_LOCKED_UNTIL = "login_locked_until"
    }
}
