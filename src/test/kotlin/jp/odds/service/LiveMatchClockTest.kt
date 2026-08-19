package jp.odds.service

import jp.odds.entity.SportType
import jp.odds.service.response.model.StatusTime
import jp.odds.service.response.model.TimeInfo
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Sofascore sends a clock reading and the moment it was taken, not a minute, so every case here
 * pins a fixed `now` against a fixed reading rather than trusting the wall clock.
 */
class LiveMatchClockTest {

    private val now = Instant.ofEpochSecond(1_700_000_000)

    private fun read(
        sport: SportType = SportType.Football,
        statusType: String = "inprogress",
        statusDescription: String = "2nd half",
        statusTime: StatusTime? = null,
        time: TimeInfo? = null
    ) = LiveMatchClock.read(sport, statusType, statusDescription, statusTime, time, now)

    @Test
    fun `reads the clock forward from the moment Sofascore stamped it`() {
        // 62:00 on the clock when stamped, 3 minutes ago.
        val clock = read(
            statusTime = StatusTime(initial = 3720, max = 5400, timestamp = now.epochSecond - 180)
        )!!

        assertEquals(65, clock.elapsedMinutes)
        assertEquals(25, clock.minutesRemaining)
        assertEquals("2nd half", clock.period)
    }

    @Test
    fun `added time does not eat into normal time remaining`() {
        // Stamped at 90:00 five minutes ago: the match is deep into added time, not at 95 minutes.
        val clock = read(
            statusTime = StatusTime(initial = 5400, max = 5400, extra = 300, timestamp = now.epochSecond - 300)
        )!!

        assertEquals(90, clock.elapsedMinutes)
        assertEquals(0, clock.minutesRemaining)
        assertEquals(5, clock.addedTimeMinutes)
    }

    @Test
    fun `half time is half a match played whatever the stopped clock says`() {
        val clock = read(
            statusDescription = "Halftime",
            statusTime = StatusTime(initial = 2700, max = 2700, timestamp = now.epochSecond - 600)
        )!!

        assertEquals(45, clock.elapsedMinutes)
        assertEquals(45, clock.minutesRemaining)
    }

    @Test
    fun `extra time has no normal time left to play`() {
        val clock = read(statusDescription = "1st extra half")!!

        assertEquals(90, clock.elapsedMinutes)
        assertEquals(0, clock.minutesRemaining)
    }

    @Test
    fun `handball runs on a sixty minute clock`() {
        val clock = read(
            sport = SportType.Handball,
            statusTime = StatusTime(initial = 1800, max = 3600, timestamp = now.epochSecond - 600)
        )!!

        assertEquals(40, clock.elapsedMinutes)
        assertEquals(20, clock.minutesRemaining)
    }

    @Test
    fun `handball half time is half of sixty minutes`() {
        val clock = read(sport = SportType.Handball, statusDescription = "Halftime")!!

        assertEquals(30, clock.elapsedMinutes)
        assertEquals(30, clock.minutesRemaining)
    }

    @Test
    fun `falls back on the start of the current period, offset by the half`() {
        val clock = read(
            time = TimeInfo(injuryTime1 = 2, currentPeriodStartTimestamp = now.epochSecond - 600)
        )!!

        assertEquals(55, clock.elapsedMinutes)
        assertEquals(35, clock.minutesRemaining)
        assertEquals(2, clock.addedTimeMinutes)
    }

    @Test
    fun `a period start alone cannot run past the end of its half`() {
        // A stale period start would otherwise report a match hours into its second half.
        val clock = read(
            statusDescription = "1st half",
            time = TimeInfo(currentPeriodStartTimestamp = now.epochSecond - 7200)
        )!!

        assertEquals(45, clock.elapsedMinutes)
        assertEquals(45, clock.minutesRemaining)
    }

    @Test
    fun `a match that is not in progress has no clock`() {
        assertNull(read(statusType = "notstarted", statusDescription = "Not started"))
        assertNull(
            read(
                statusType = "finished",
                statusDescription = "Ended",
                statusTime = StatusTime(initial = 5400, max = 5400, timestamp = now.epochSecond)
            )
        )
    }

    @Test
    fun `a live match Sofascore sends no clock for reports none`() {
        assertNull(read(statusTime = null, time = TimeInfo(injuryTime1 = 1)))
    }

    @Test
    fun `describes itself with the added time it knows about`() {
        val clock = read(
            statusTime = StatusTime(initial = 5100, max = 5400, extra = 240, timestamp = now.epochSecond)
        )!!

        assertEquals(
            "85 minutes played, 5 minutes of normal time remaining (2nd half, +4 added time announced)",
            clock.describe()
        )
    }
}
