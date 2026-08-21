package jp.odds.service.claude

import jp.odds.dto.*
import jp.odds.entity.MatchPrediction
import jp.odds.entity.SportType
import jp.odds.repository.DailyFootballMatchDataRepository
import jp.odds.repository.DailyHandballMatchDataRepository
import jp.odds.repository.MatchPredictionRepository
import jp.odds.service.FootballService
import jp.odds.service.HandballService
import jp.odds.service.news.MatchTeamNews
import jp.odds.service.news.TeamNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds a prediction for one match: the stored odds and public vote, football team news off the
 * news desks, plus - once the match is under way - the clock and Sofascore's live statistics, all
 * handed to Claude as a compact fact sheet.
 *
 * The fact sheet is returned alongside the prediction, because a prediction is only worth as much
 * as the numbers behind it and those should be visible rather than implied.
 */
@Service
class MatchPredictionService(
    private val claudeService: ClaudeService,
    private val footballRepository: DailyFootballMatchDataRepository,
    private val handballRepository: DailyHandballMatchDataRepository,
    private val footballService: FootballService,
    private val handballService: HandballService,
    private val matchPredictionRepository: MatchPredictionRepository,
    private val teamNewsService: TeamNewsService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private companion object {
        /** The closing line the prompt asks for, e.g. `PROBABILITIES: home=45 draw=25 away=30`. */
        val PROBABILITY_LINE = Regex("""^\s*PROBABILITIES\s*:.*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

        fun outcomeShare(line: String, outcome: String): Double? =
            Regex("""$outcome\s*[=:]\s*(\d+(?:[.,]\d+)?)""", RegexOption.IGNORE_CASE)
                .find(line)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    }

    suspend fun predict(request: MatchPredictionRequest): MatchPredictionResponse {
        val sport = SportType.entries.firstOrNull { it.name.equals(request.sport, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown sport '${request.sport}'")

        val facts = loadFacts(request.eventId, sport)
            ?: throw IllegalArgumentException("No stored ${sport.name.lowercase()} match with event id ${request.eventId}")

        // Sofascore 404s statistics before kick-off, so only a live or finished match has any.
        val statistics = if (facts.isLive || facts.isFinished) {
            runCatching {
                when (sport) {
                    SportType.Football -> footballService.getMatchStatistics(facts.eventId)
                    SportType.Handball -> handballService.getMatchStatistics(facts.eventId)
                }
            }.onFailure { log.warn("Statistics lookup failed for event {}", facts.eventId, it) }
                .getOrNull()
        } else null

        val clockLine = if (facts.isLive) liveClockLine(facts, sport) else null

        // Team news is a football-only source list for now, and a match already under way is
        // better read off the clock and the statistics than off this morning's headlines.
        val teamNews = if (sport == SportType.Football && !facts.isLive && !facts.isFinished) {
            runCatching { teamNewsService.forMatch(facts.homeTeam, facts.awayTeam) }
                .onFailure { log.warn("Team news lookup failed for event {}", facts.eventId, it) }
                .getOrNull()
        } else null

        val statLines = statistics?.let { formatStatistics(it) }.orEmpty()
        val context = buildContext(facts, clockLine, statLines, teamNews)
        val answer = claudeService.ask(
            ClaudeAskRequest(prompt = buildPrompt(facts, context, teamNews), provider = request.provider)
        )

        // The percentages live in a closing line meant for us, not for the reader: pull them out
        // for storage and drop the line before the prose is shown.
        val marketOdds = facts.decimalOdds()
        val probabilities = parseProbabilities(answer.text)
        val prose = PROBABILITY_LINE.replace(answer.text, "").trim()
        val predictedAt = Instant.now()

        val stored = MatchPrediction(
            eventId = facts.eventId,
            sport = sport,
            startTimestamp = facts.startTimestamp,
            homeTeamName = facts.homeTeam,
            awayTeamName = facts.awayTeam,
            statusDescription = facts.statusDescription,
            wasLive = facts.isLive,
            hadStatistics = statLines.isNotEmpty(),
            teamNewsHeadlines = teamNews?.headlineCount ?: 0,
            probabilityHome = probabilities?.home,
            probabilityDraw = probabilities?.draw,
            probabilityAway = probabilities?.away,
            predictedOutcome = topOutcome(probabilities),
            oddsHome = marketOdds["Home"],
            oddsDraw = marketOdds["Draw"],
            oddsAway = marketOdds["Away"],
            homeScore = facts.homeScore,
            awayScore = facts.awayScore,
            prediction = prose,
            provider = answer.provider,
            model = answer.model,
            durationMs = answer.durationMs,
            costUsd = answer.costUsd,
            createdAt = predictedAt
        )
        // A prediction that cannot be filed is still a prediction worth showing, so a failure here
        // must not swallow the answer the user is waiting on.
        runCatching { withContext(Dispatchers.IO) { matchPredictionRepository.save(stored) } }
            .onFailure { log.warn("Could not store prediction for event {}", facts.eventId, it) }

        return MatchPredictionResponse(
            eventId = facts.eventId,
            homeTeam = facts.homeTeam,
            awayTeam = facts.awayTeam,
            statusDescription = facts.statusDescription,
            isLive = facts.isLive,
            hasStatistics = statLines.isNotEmpty(),
            teamNewsHeadlines = teamNews?.headlineCount ?: 0,
            prediction = prose,
            contextUsed = context,
            probabilities = probabilities,
            predictedOutcome = stored.predictedOutcome,
            predictedAt = predictedAt.epochSecond,
            provider = answer.provider,
            model = answer.model,
            durationMs = answer.durationMs,
            costUsd = answer.costUsd
        )
    }

    /** The latest prediction per match for a day, for the matches table to show on hover. */
    suspend fun storedPredictions(sportName: String, date: LocalDate): List<StoredMatchPrediction> {
        val sport = SportType.entries.firstOrNull { it.name.equals(sportName, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown sport '$sportName'")

        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

        val predictions = withContext(Dispatchers.IO) {
            matchPredictionRepository
                .findBySportAndStartTimestampBetweenOrderByCreatedAtAsc(sport, startOfDay, endOfDay)
        }
        // Oldest first, so the last row per event is the newest reading of that match.
        return predictions.associateBy { it.eventId }.values.map { it.toDto() }
    }

    private fun MatchPrediction.toDto() = StoredMatchPrediction(
        eventId = eventId,
        sport = sport.name,
        predictedAt = createdAt.epochSecond,
        statusDescription = statusDescription,
        wasLive = wasLive,
        hadStatistics = hadStatistics,
        teamNewsHeadlines = teamNewsHeadlines,
        probabilities = if (probabilityHome == null && probabilityDraw == null && probabilityAway == null) null
        else PredictionProbabilities(probabilityHome, probabilityDraw, probabilityAway),
        predictedOutcome = predictedOutcome,
        marketOdds = if (oddsHome == null && oddsDraw == null && oddsAway == null) null
        else PredictionOdds(oddsHome, oddsDraw, oddsAway),
        homeScore = homeScore,
        awayScore = awayScore,
        prediction = prediction,
        provider = provider,
        model = model
    )

    /** Reads the closing `PROBABILITIES:` line; null when Claude answered without one. */
    private fun parseProbabilities(text: String): PredictionProbabilities? {
        val line = PROBABILITY_LINE.findAll(text).lastOrNull()?.value ?: return null
        val home = outcomeShare(line, "home")
        val draw = outcomeShare(line, "draw")
        val away = outcomeShare(line, "away")
        if (home == null && draw == null && away == null) {
            log.warn("Prediction ended with an unreadable probability line: {}", line.trim())
            return null
        }
        return PredictionProbabilities(home, draw, away)
    }

    private fun topOutcome(probabilities: PredictionProbabilities?): String? {
        val ranked = listOfNotNull(
            probabilities?.home?.let { "HOME" to it },
            probabilities?.draw?.let { "DRAW" to it },
            probabilities?.away?.let { "AWAY" to it }
        )
        return ranked.maxByOrNull { it.second }?.first
    }

    /**
     * How much of the match is left, read fresh: a recorded clock ages by a minute every minute,
     * and the difference between 20 and 80 minutes gone is most of what a live prediction turns
     * on. The reading stored at the last refresh is the fallback when Sofascore does not answer.
     */
    private suspend fun liveClockLine(facts: MatchFacts, sport: SportType): String? {
        val fresh = runCatching {
            when (sport) {
                SportType.Football -> footballService.getLiveClock(facts.eventId)
                SportType.Handball -> handballService.getLiveClock(facts.eventId)
            }
        }.onFailure { log.warn("Live clock lookup failed for event {}", facts.eventId, it) }
            .getOrNull()

        if (fresh != null) return fresh.describe()

        val remaining = facts.liveMinutesRemaining ?: return null
        return buildString {
            facts.liveElapsedMinutes?.let { append("$it minutes played, ") }
            append("$remaining minutes of normal time remaining")
            append(" - as recorded ${formatRecordedAt(facts.lastUpdated)}, the clock could not be re-read just now")
        }
    }

    private suspend fun loadFacts(eventId: Long, sport: SportType): MatchFacts? = withContext(Dispatchers.IO) {
        when (sport) {
            SportType.Football -> footballRepository.findByEventId(eventId)?.let { m ->
                MatchFacts(
                    eventId = m.eventId,
                    sport = sport,
                    homeTeam = m.homeTeamName,
                    awayTeam = m.awayTeamName,
                    tournament = m.tournamentName,
                    country = m.countryName ?: m.categoryName,
                    startTimestamp = m.startTimestamp,
                    statusType = m.statusType,
                    statusDescription = m.statusDescription,
                    homeScore = m.homeScore,
                    awayScore = m.awayScore,
                    oddsHome = m.oddsHome,
                    oddsDraw = m.oddsDraw,
                    oddsAway = m.oddsAway,
                    votingHome = m.votingHome,
                    votingDraw = m.votingDraw,
                    votingAway = m.votingAway,
                    votingTotal = m.votingTotal,
                    liveElapsedMinutes = m.liveElapsedMinutes,
                    liveMinutesRemaining = m.liveMinutesRemaining,
                    lastUpdated = m.lastUpdated,
                    cards = listOfNotNull(
                        cardLine("Home", m.homeYellowCards, m.homeRedCards),
                        cardLine("Away", m.awayYellowCards, m.awayRedCards)
                    )
                )
            }

            SportType.Handball -> handballRepository.findByEventId(eventId)?.let { m ->
                MatchFacts(
                    eventId = m.eventId,
                    sport = sport,
                    homeTeam = m.homeTeamName,
                    awayTeam = m.awayTeamName,
                    tournament = m.tournamentName,
                    country = m.countryName ?: m.categoryName,
                    startTimestamp = m.startTimestamp,
                    statusType = m.statusType,
                    statusDescription = m.statusDescription,
                    homeScore = m.homeScore,
                    awayScore = m.awayScore,
                    oddsHome = m.oddsHome,
                    oddsDraw = m.oddsDraw,
                    oddsAway = m.oddsAway,
                    votingHome = m.votingHome,
                    votingDraw = m.votingDraw,
                    votingAway = m.votingAway,
                    votingTotal = m.votingTotal,
                    liveElapsedMinutes = m.liveElapsedMinutes,
                    liveMinutesRemaining = m.liveMinutesRemaining,
                    lastUpdated = m.lastUpdated,
                    cards = emptyList()
                )
            }
        }
    }

    private fun cardLine(side: String, yellow: Int?, red: Int?): String? =
        if (yellow == null && red == null) null
        else "$side ${yellow ?: 0} yellow, ${red ?: 0} red"

    /** The fact sheet, in the order a human would read it. */
    private fun buildContext(
        facts: MatchFacts,
        clockLine: String?,
        statLines: List<String>,
        teamNews: MatchTeamNews?
    ): String = buildString {
        appendLine("Sport: ${facts.sport.name}")
        appendLine("Match: ${facts.homeTeam} (home) vs ${facts.awayTeam} (away)")
        appendLine("Competition: ${facts.tournament}${facts.country?.let { " ($it)" } ?: ""}")
        appendLine("Kick-off: ${formatKickoff(facts.startTimestamp)}")
        appendLine("Status: ${facts.statusDescription} [${facts.statusType}]")
        if (facts.homeScore != null || facts.awayScore != null) {
            appendLine("Current score: ${facts.homeScore ?: 0} - ${facts.awayScore ?: 0}")
        }
        if (facts.isLive) {
            appendLine("Live clock: ${clockLine ?: "not published for this match."}")
        }
        facts.cards.forEach { appendLine("Cards: $it") }

        val odds = facts.decimalOdds()
        if (odds.isEmpty()) {
            appendLine("Bookmaker odds: not available")
        } else {
            appendLine("Bookmaker odds (decimal): " + odds.entries.joinToString(" | ") {
                "${it.key} ${"%.2f".format(it.value)}"
            })
            appendLine("Implied probability after removing the margin: " + normalise(odds).entries.joinToString(" | ") {
                "${it.key} ${"%.1f".format(it.value * 100)}%"
            })
        }

        if (facts.votingTotal != null && facts.votingTotal > 0) {
            appendLine(
                "Public vote (${facts.votingTotal} votes): Home ${facts.votingHome ?: 0}% | " +
                    "Draw ${facts.votingDraw ?: 0}% | Away ${facts.votingAway ?: 0}%"
            )
        }

        if (statLines.isEmpty()) {
            appendLine(
                if (facts.isLive) "Live statistics: none published for this match yet."
                else "Live statistics: not applicable, the match has not started."
            )
        } else {
            appendLine("Live match statistics:")
            statLines.forEach { appendLine("  $it") }
        }

        appendTeamNews(teamNews, facts)
    }.trim()

    /**
     * The team news block, always the same shape: a line per side whether or not anything was
     * written about it, so the same match read twice reads the same way.
     */
    private fun StringBuilder.appendTeamNews(teamNews: MatchTeamNews?, facts: MatchFacts) {
        if (teamNews == null) {
            appendLine(
                when {
                    facts.sport != SportType.Football ->
                        "Team news: no news sources are read for ${facts.sport.name.lowercase()}."
                    facts.isLive || facts.isFinished ->
                        "Team news: not gathered, the match is past kick-off."
                    else -> "Team news: the news feeds could not be read just now."
                }
            )
            return
        }
        appendLine(
            "Team news headlines (${teamNews.sourceLabels}; last ${teamNews.maxAgeDays} days, " +
                "matched by team name):"
        )
        teamNews.teams.forEach { team ->
            if (team.headlines.isEmpty()) {
                appendLine("  ${team.team}: nothing found")
            } else {
                appendLine("  ${team.team}:")
                team.headlines.forEach { appendLine("    - $it") }
            }
        }
    }

    private fun buildPrompt(facts: MatchFacts, context: String, teamNews: MatchTeamNews?): String = buildString {
        appendLine("Predict the outcome of this match from the facts below.")
        appendLine()
        appendLine(context)
        appendLine()
        if (facts.isLive) {
            appendLine(
                "The match is in progress: predict the final result rather than the current one, and " +
                    "weigh the score against the time left - the same lead is worth far more with " +
                    "five minutes to play than with an hour."
            )
            appendLine()
        }
        // The skill document, verbatim: how to read the headlines is part of the recipe, not
        // something to re-word per match.
        if (teamNews != null && teamNews.guidance.isNotBlank()) {
            appendLine(teamNews.guidance)
            appendLine()
        }
        appendLine("Answer in this shape, in plain prose, no markdown headings:")
        appendLine("1. Most likely outcome (home win / draw / away win) with your own rough percentage for each.")
        if (facts.isLive) {
            appendLine(
                "2. What the live statistics and the time remaining say about how the match is " +
                    "actually going, beyond the scoreline."
            )
        } else {
            appendLine(
                if (teamNews != null && teamNews.headlineCount > 0)
                    "2. What the odds and the public vote imply, where they disagree, and whether " +
                        "any headline above is relevant enough to move the call."
                else "2. What the odds and the public vote imply, and where they disagree."
            )
        }
        appendLine("3. Whether any of the three prices looks like value against your own percentages, and why.")
        appendLine("4. The main thing that would change your view.")
        appendLine()
        appendLine(
            "Be concrete and brief - under 200 words. You have only what is above" +
                (if (teamNews != null && teamNews.headlineCount > 0) " - headlines aside, no" else ": no team news, no") +
                " form guide and no head-to-head. Say so where it limits the call rather than " +
                "inventing detail."
        )
        appendLine()
        // The percentages are stored and shown next to the market's, so they have to come back in a
        // shape that can be read without guessing at prose.
        appendLine(
            "End with one final line, nothing after it, exactly in this form - whole numbers " +
                "summing to 100, the same percentages you gave in point 1:"
        )
        appendLine("PROBABILITIES: home=45 draw=25 away=30")
    }

    /** Sofascore's `ALL` period is the whole-match view; the per-half breakdowns add noise here. */
    private fun formatStatistics(statistics: MatchStatisticsResponse): List<String> {
        val period = statistics.periods.firstOrNull { it.period.equals("ALL", ignoreCase = true) }
            ?: statistics.periods.firstOrNull()
            ?: return emptyList()
        // Sofascore repeats some items across groups ("Total shots" lands in two); identical lines
        // add nothing to the prompt but noise.
        return period.groups.flatMap { group ->
            group.items.map { item -> "${item.name}: ${item.home} - ${item.away}" }
        }.distinct()
    }

    private fun formatRecordedAt(lastUpdated: Instant?): String = lastUpdated
        ?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern("HH:mm z"))
        ?.let { "at $it" }
        ?: "at the last refresh"

    private fun formatKickoff(startTimestamp: Long): String =
        Instant.ofEpochSecond(startTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))

    /** Strips the bookmaker margin so the three implied probabilities sum to 100%. */
    private fun normalise(odds: Map<String, Double>): Map<String, Double> {
        val implied = odds.mapValues { 1.0 / it.value }
        val total = implied.values.sum()
        return if (total <= 0.0) implied else implied.mapValues { it.value / total }
    }

    private data class MatchFacts(
        val eventId: Long,
        val sport: SportType,
        val homeTeam: String,
        val awayTeam: String,
        val tournament: String,
        val country: String?,
        val startTimestamp: Long,
        val statusType: String,
        val statusDescription: String,
        val homeScore: Int?,
        val awayScore: Int?,
        val oddsHome: String?,
        val oddsDraw: String?,
        val oddsAway: String?,
        val votingHome: Int?,
        val votingDraw: Int?,
        val votingAway: Int?,
        val votingTotal: Int?,
        val liveElapsedMinutes: Int?,
        val liveMinutesRemaining: Int?,
        val lastUpdated: Instant?,
        val cards: List<String>
    ) {
        val isLive: Boolean get() = statusType.equals("inprogress", ignoreCase = true)
        val isFinished: Boolean get() = statusType.equals("finished", ignoreCase = true)

        /** Odds are stored fractional ("5/4"); the UI shows decimal, so the prompt should too. */
        fun decimalOdds(): Map<String, Double> = buildMap {
            toDecimal(oddsHome)?.let { put("Home", it) }
            toDecimal(oddsDraw)?.let { put("Draw", it) }
            toDecimal(oddsAway)?.let { put("Away", it) }
        }

        private fun toDecimal(fractional: String?): Double? {
            val parts = fractional?.split('/') ?: return null
            if (parts.size != 2) return null
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
            return numerator / denominator + 1.0
        }
    }
}
