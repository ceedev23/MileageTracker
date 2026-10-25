package com.example.data.supabase

/**
 * Counts failed password sign-ins. Five failures lock the next attempt for one minute.
 * shortcut: stored on this install only, a reinstall clears it; the Supabase project rate limit is the real control.
 */
class LoginRateLimiter(
    private val maxAttempts: Int = 5,
    private val lockoutMs: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
    failures: Int = 0,
    lockedUntil: Long = 0L
) {
    var failures: Int = failures
        private set
    var lockedUntil: Long = lockedUntil
        private set

    fun blockMessage(): String? {
        val remaining = lockedUntil - clock()
        if (remaining <= 0L) return null
        val seconds = (remaining + 999) / 1000
        return "Too many sign-in attempts. Try again in $seconds seconds."
    }

    fun recordFailure() {
        if (lockedUntil > clock()) return
        failures += 1
        if (failures >= maxAttempts) {
            lockedUntil = clock() + lockoutMs
            failures = 0
        }
    }

    fun recordSuccess() {
        failures = 0
        lockedUntil = 0L
    }
}
