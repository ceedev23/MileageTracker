package com.example

import com.example.data.supabase.LoginRateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRateLimiterTest {
    @Test
    fun fiveFailuresLockForOneMinuteThenClear() {
        var now = 1_000L
        val limiter = LoginRateLimiter(clock = { now })

        repeat(4) { limiter.recordFailure() }
        assertNull(limiter.blockMessage())

        limiter.recordFailure()
        val message = limiter.blockMessage()
        assertTrue(message?.startsWith("Too many sign-in attempts") == true)
        assertEquals(0, limiter.failures)
        assertEquals(61_000L, limiter.lockedUntil)

        now = 61_000L
        assertNull(limiter.blockMessage())
        limiter.recordSuccess()
        assertEquals(0, limiter.failures)
        assertEquals(0L, limiter.lockedUntil)
    }
}
