package org.mycarcompanion.app.ui.mechanics

import kotlinx.datetime.Instant
import org.mycarcompanion.app.data.models.MechanicJob
import kotlin.test.Test
import kotlin.test.assertEquals

class JobTimerTest {

    private val now = Instant.parse("2026-10-23T18:00:00Z")

    @Test
    fun runningTimerAddsToBankedTime() {
        val job = MechanicJob(timerStartedAt = "2026-10-23T17:30:00Z", timerLoggedSeconds = 600)
        assertEquals(600L + 1800, timerSeconds(job, now))
    }

    @Test
    fun pausedOrBrokenTimerUsesBankedTimeOnly() {
        assertEquals(42L, timerSeconds(MechanicJob(timerLoggedSeconds = 42), now))
        assertEquals(42L, timerSeconds(MechanicJob(timerStartedAt = "not a date", timerLoggedSeconds = 42), now))
        // a start time ahead of this device's clock never counts down
        assertEquals(0L, timerSeconds(MechanicJob(timerStartedAt = "2026-10-23T19:00:00Z"), now))
    }

    @Test
    fun billedHoursRoundUpToTheTenth() {
        assertEquals(0.0, billedHours(0))
        assertEquals(0.1, billedHours(1))
        assertEquals(0.1, billedHours(360))
        assertEquals(0.2, billedHours(361))
        assertEquals(1.5, billedHours(5400))
    }
}
