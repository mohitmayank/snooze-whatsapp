package com.mohit.snoozewhatsapp

import com.mohit.snoozewhatsapp.ui.formatTimeRemaining
import org.junit.Assert.*
import org.junit.Test

class UiUtilsTest {

    @Test
    fun formatTimeRemaining_pastTime_returnsActive() {
        val past = System.currentTimeMillis() - 1000
        assertEquals("Active", formatTimeRemaining(past))
    }

    @Test
    fun formatTimeRemaining_30Minutes_returns30m() {
        val future = System.currentTimeMillis() + 30 * 60_000
        assertEquals("30m", formatTimeRemaining(future))
    }

    @Test
    fun formatTimeRemaining_60Minutes_returns1h() {
        val future = System.currentTimeMillis() + 60 * 60_000
        assertEquals("1h", formatTimeRemaining(future))
    }

    @Test
    fun formatTimeRemaining_90Minutes_returns1h30m() {
        val future = System.currentTimeMillis() + 90 * 60_000
        assertEquals("1h 30m", formatTimeRemaining(future))
    }
}
