package com.mohit.snoozewhatsapp

import com.mohit.snoozewhatsapp.scheduler.ScheduleManager
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ScheduleManagerTest {

    @Test
    fun nextOccurrenceOf_futureTimeToday_returnsTodayMs() {
        val cal = Calendar.getInstance()
        val futureHour = (cal.get(Calendar.HOUR_OF_DAY) + 1).coerceAtMost(23)
        val result = ScheduleManager.nextOccurrenceOf(futureHour, 0)
        assertTrue("Should be in the future", result > System.currentTimeMillis())
        val resultCal = Calendar.getInstance().apply { timeInMillis = result }
        assertEquals(futureHour, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun nextOccurrenceOf_pastTimeToday_returnsTomorrow() {
        // 00:01 is almost certainly in the past for any wall-clock time after midnight
        val result = ScheduleManager.nextOccurrenceOf(0, 1)
        assertTrue("Should be in the future", result > System.currentTimeMillis())
        val resultCal = Calendar.getInstance().apply { timeInMillis = result }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        assertEquals(tomorrow.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun nextOccurrenceOf_minuteZeroHourZero_returnsFutureTime() {
        val result = ScheduleManager.nextOccurrenceOf(0, 0)
        assertTrue("Midnight should be scheduled for tomorrow", result > System.currentTimeMillis())
    }
}
