package jp.odds.service

import jp.odds.entity.SportType
import jp.odds.service.response.model.StatusTime
import jp.odds.service.response.model.TimeInfo
import java.time.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * Where a live match stands on the clock: how much has been played and how much of normal time is
 * still to come. A score means very different things at 20 minutes and at 88, so this is the piece
 * a prediction cannot do without.
 *
 * Sofascore does not send a minute; it sends a clock reading and the moment it was taken
 * ([StatusTime.initial] at [StatusTime.timestamp]), which is read forward to now.
 */
data class LiveMatchClock(
    /** Minutes played, counted from kick-off and capped at the end of the current period. */
    val elapsedMinutes: Int,
    /** Minutes of normal time still to play; 0 once the match is into added or extra time. */
    val minutesRemaining: Int,
    /** Sofascore's own wording for the phase, e.g. "2nd half" or "Halftime". */
    val period: String,
    /** Added time announced for the current period, when Sofascore has published it. */
    val addedTimeMinutes: Int?
) {

    /** One line for a fact sheet, in the order a person following the match would say it. */
    fun describe(): String = buildString {
        append("$elapsedMinutes minutes played, $minutesRemaining minutes of normal time remaining")
        append(" ($period")
        addedTimeMinutes?.takeIf { it > 0 }?.let { append(", +$it added time announced") }
        append(")")
    }

    companion object {

        private const val SECONDS_PER_MINUTE = 60

        /**
         * Reads the clock of a live match, or null when there is no clock to read - the match has
         * not kicked off, has finished, or Sofascore sent neither a reading nor a period start.
         *
         * [statusDescription] carries phases the running clock cannot express on its own: it is
         * stopped at half time, and past normal time in extra time or a shoot-out.
         */
        fun read(
            sport: SportType,
            statusType: String,
            statusDescription: String,
            statusTime: StatusTime?,
            time: TimeInfo?,
            now: Instant = Instant.now()
        ): LiveMatchClock? {
            if (!statusType.equals("inprogress", ignoreCase = true)) return null

            val period = statusDescription.ifBlank { "in progress" }
            val addedTime = addedTimeMinutes(statusTime, time)

            val elapsed = when {
                isBreak(statusDescription) -> sport.periodMinutes
                isBeyondNormalTime(statusDescription) -> sport.regulationMinutes
                else -> elapsedMinutes(statusTime, time, sport, statusDescription, now) ?: return null
            }

            return LiveMatchClock(
                elapsedMinutes = min(elapsed, sport.regulationMinutes),
                minutesRemaining = max(0, sport.regulationMinutes - elapsed),
                period = period,
                addedTimeMinutes = addedTime
            )
        }

        /**
         * The running clock, read forward from the moment Sofascore last stamped it. Falls back on
         * the start of the current period, which the per-tournament feed sends when `statusTime`
         * is missing - that needs the period to place it, since it restarts each half.
         */
        private fun elapsedMinutes(
            statusTime: StatusTime?,
            time: TimeInfo?,
            sport: SportType,
            statusDescription: String,
            now: Instant
        ): Int? {
            val initial = statusTime?.initial
            val stampedAt = statusTime?.timestamp
            if (initial != null && stampedAt != null) {
                val seconds = initial + max(0L, now.epochSecond - stampedAt)
                // Play past `max` is added time, which the clock shows as "45+2" rather than
                // running on - so nothing beyond the period's end counts towards normal time.
                // `max` counts from kick-off like `initial` does; anything smaller is not the
                // ceiling we think it is, so it is left out rather than winding the match back.
                val capped = statusTime.max
                    ?.takeIf { it >= initial }
                    ?.let { min(seconds, it.toLong()) }
                    ?: seconds
                return (capped / SECONDS_PER_MINUTE).toInt()
            }

            val periodStart = time?.currentPeriodStartTimestamp ?: return null
            val offset = if (isSecondPeriod(statusDescription)) sport.periodMinutes else 0
            val played = max(0L, now.epochSecond - periodStart) / SECONDS_PER_MINUTE
            return min(offset + played.toInt(), offset + sport.periodMinutes)
        }

        /** `extra` is seconds on the live clock; `injuryTime1/2` are whole minutes per half. */
        private fun addedTimeMinutes(statusTime: StatusTime?, time: TimeInfo?): Int? =
            statusTime?.extra?.takeIf { it > 0 }?.let { it / SECONDS_PER_MINUTE }
                ?: time?.injuryTime2
                ?: time?.injuryTime1

        private fun isBreak(description: String): Boolean =
            description.contains("halftime", ignoreCase = true) ||
                description.contains("half time", ignoreCase = true) ||
                description.contains("break", ignoreCase = true)

        private fun isBeyondNormalTime(description: String): Boolean =
            description.contains("extra", ignoreCase = true) ||
                description.contains("overtime", ignoreCase = true) ||
                description.contains("penalt", ignoreCase = true)

        private fun isSecondPeriod(description: String): Boolean =
            description.contains("2nd", ignoreCase = true) ||
                description.contains("second", ignoreCase = true)
    }
}
