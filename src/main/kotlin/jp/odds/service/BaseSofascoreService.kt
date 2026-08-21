package jp.odds.service

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import jp.odds.dto.*
import jp.odds.entity.SportType
import jp.odds.model.MatchBettingData
import jp.odds.model.MatchDataWithResult
import jp.odds.model.MatchWinningData
import jp.odds.repository.MatchOddsHistoryRepository
import jp.odds.repository.MatchVotesHistoryRepository
import jp.odds.service.response.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.netty.http.client.HttpClient
import java.io.IOException
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

abstract class BaseSofascoreService(
    webClientBuilder: WebClient.Builder,
    private val matchOddsHistoryRepository: MatchOddsHistoryRepository,
    private val matchVotesHistoryRepository: MatchVotesHistoryRepository
) {
    companion object {
        /** Config key holding the extra top leagues for a sport, suffixed with the sport slug. */
        const val EXTRA_TOP_LEAGUES_PREFIX = "sofascore.discovery.extra-top-leagues."

        /**
         * Leagues we already track, plus any of Sofascore's top competitions we do not. Both sides
         * are keyed by unique-tournament id once stored, so the overlap dedupes cleanly and a
         * league is never polled twice in one refresh.
         */
        fun mergeLeagues(stored: List<TrackedLeague>, defaults: List<TrackedLeague>): List<TrackedLeague> {
            val known = stored.filter { it.isUniqueTournament }.map { it.tournamentId }.toSet()
            return stored + defaults.filterNot { it.tournamentId in known }
        }

        /**
         * Reads `id:Name` entries, e.g. `238:Liga Portugal Betclic,37:Eredivisie`. Matching is by
         * id, so the name only ever reaches a log line and a bare id is accepted too. Entries are
         * comma-separated, which means a name may not contain a comma.
         */
        fun parseExtraTopLeagues(raw: String): List<TrackedLeague> = raw
            .split(',')
            .mapNotNull { entry ->
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val id = trimmed.substringBefore(':').trim().toLongOrNull() ?: return@mapNotNull null
                val name = trimmed.substringAfter(':', "").trim()
                TrackedLeague(
                    tournamentId = id,
                    tournamentName = name.ifEmpty { "unique-tournament $id" },
                    isTopLeague = true,
                    isUniqueTournament = true
                )
            }
            .distinctBy { it.tournamentId }

        /**
         * Widens [stored] with the config-pinned [extras], letting a pin win over what we stored.
         *
         * A league Sofascore does not consider top for our country is first seen during an
         * all-leagues refresh, so its rows were written as non-top - and the seed query reads that
         * flag straight back. Without the override a pinned league would stay non-top for as long
         * as those rows live; with it, the next refresh rewrites them as top.
         */
        fun applyExtraTopLeagues(stored: List<TrackedLeague>, extras: List<TrackedLeague>): List<TrackedLeague> {
            val pinned = extras.filter { it.isTopLeague }.map { it.tournamentId }.toSet()
            val promoted = stored.map {
                if (it.isUniqueTournament && it.tournamentId in pinned) it.copy(isTopLeague = true) else it
            }
            return mergeLeagues(stored = promoted, defaults = extras)
        }
    }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected val webClient: WebClient = webClientBuilder
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .responseTimeout(Duration.ofSeconds(10))
                    .doOnConnected { conn ->
                        conn.addHandlerLast(ReadTimeoutHandler(10, TimeUnit.SECONDS))
                    }
                    .compress(true)
            )
        )
        .baseUrl("https://api.sofascore.com/api/v1")
        .defaultHeader(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        )
        .defaultHeader(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        .defaultHeader("Accept-Language", "en-US,en;q=0.9")
        .defaultHeader("Cache-Control", "max-age=0")
        .defaultHeader("Sec-Fetch-Dest", "document")
        .defaultHeader("Sec-Fetch-Mode", "navigate")
        .defaultHeader("Sec-Fetch-Site", "none")
        .defaultHeader("Sec-Fetch-User", "?1")
        .defaultHeader("sec-ch-ua", "\"Brave\";v=\"143\", \"Chromium\";v=\"143\", \"Not A(Brand\";v=\"24\"")
        .defaultHeader("sec-ch-ua-mobile", "?0")
        .defaultHeader("sec-ch-ua-platform", "\"macOS\"")
        .defaultHeader("Upgrade-Insecure-Requests", "1")
        .build()

    @Value("\${sofascore.discovery.max-pages:3}")
    private var maxDiscoveryPages: Int = 3

    /** The day's tournament list is one cheap call per page and drives everything else, so it
     * pages further than an individual tournament's event feed does. */
    @Value("\${sofascore.discovery.tournament-list-max-pages:10}")
    private var maxTournamentListPages: Int = 10

    @Value("\${sofascore.discovery.request-delay-ms:250}")
    private var requestDelayMs: Long = 250

    @Value("\${sofascore.discovery.default-tournaments-country:DK}")
    private var defaultTournamentsCountry: String = "DK"

    /** Read at call time so the extras key can be built from [sportSlug], which @Value cannot see. */
    @Autowired
    private var environment: Environment? = null

    /** The sport this service collects; also supplies the clock a live match is read against. */
    protected abstract val sport: SportType

    /** Sport slug used in Sofascore paths, e.g. `football`. */
    protected val sportSlug: String get() = sport.slug

    /**
     * The clock of a live match, read fresh from Sofascore. Null for anything not in progress, and
     * for a live match whose clock the feed did not carry.
     */
    suspend fun getLiveClock(eventId: Long): LiveMatchClock? = readLiveClock(fetchEventDetails(eventId))

    /** The clock as it stands in an event we already hold, without going back to Sofascore. */
    protected fun readLiveClock(event: SofascoreEvent?): LiveMatchClock? = event?.let {
        LiveMatchClock.read(
            sport = sport,
            statusType = it.status.type,
            statusDescription = it.status.description,
            statusTime = it.statusTime,
            time = it.time
        )
    }

    /**
     * Carries the clock of a freshly fetched event onto the event itself, so a match read straight
     * from Sofascore reaches the UI with the same minute as one read back out of the database.
     */
    protected fun stampLiveClock(event: SofascoreEvent) {
        val clock = readLiveClock(event)
        event.liveElapsedMinutes = clock?.elapsedMinutes
        event.liveMinutesRemaining = clock?.minutesRemaining
    }

    /**
     * Sofascore's own top-competitions list for a country, which is what the withdrawn
     * scheduled-events feed used to flag via `eventFilters.level = top-competitions`.
     *
     * Discovery seeds itself from leagues we have already stored, but a refresh is also the only
     * thing that writes those rows - so an empty table would never recover on its own. Asking
     * Sofascore which leagues matter keeps that self-healing without pinning any ids in config.
     */
    protected suspend fun fetchDefaultTopLeagues(): List<TrackedLeague> = try {
        webClient
            .get()
            .uri("/config/default-unique-tournaments/$defaultTournamentsCountry/$sportSlug")
            .retrieve()
            .awaitBody<DefaultUniqueTournamentsResponse>()
            .uniqueTournaments
            .map {
                TrackedLeague(
                    tournamentId = it.id.toLong(),
                    tournamentName = it.name,
                    isUniqueTournament = true
                )
            }
    } catch (e: Exception) {
        logWebClientError(
            "fetching default tournaments",
            e,
            mapOf("country" to defaultTournamentsCountry, "sport" to sportSlug)
        )
        emptyList()
    }

    /**
     * Leagues pinned as top in `sofascore.discovery.extra-top-leagues.<sport>`.
     *
     * [fetchDefaultTopLeagues] asks Sofascore what a single country's audience is offered, so
     * competitions that matter to us but not to that audience - Liga Portugal for a Danish list -
     * are absent from it and would never be discovered. Pinning ids here is the way in.
     */
    protected fun configuredExtraTopLeagues(): List<TrackedLeague> {
        val key = EXTRA_TOP_LEAGUES_PREFIX + sportSlug
        val env = environment
        if (env == null) {
            // Reading no config looks exactly like configuring none, so say so rather than
            // quietly dropping every pinned league.
            logger.warn("No Environment injected - $key cannot be read and no extra top leagues apply")
            return emptyList()
        }

        val raw = env.getProperty(key).orEmpty()
        val leagues = parseExtraTopLeagues(raw)

        val entryCount = raw.split(',').count { it.isNotBlank() }
        if (leagues.size < entryCount) {
            logger.warn(
                "$key: ignored ${entryCount - leagues.size} of $entryCount entries - " +
                        "each must be `id` or `id:Name`, with ids unique"
            )
        }
        return leagues
    }

    /**
     * The leagues a refresh polls: what we have already stored, widened with the config-pinned
     * extras and then with Sofascore's top competitions for our country.
     *
     * Extras are applied before the fetched defaults so that a pin still decides the top-league
     * flag even when Sofascore's list happens to carry the same competition.
     */
    protected suspend fun discoveryLeagues(stored: List<TrackedLeague>): List<TrackedLeague> {
        val extras = configuredExtraTopLeagues()
        if (extras.isNotEmpty()) {
            logger.debug("${extras.size} extra top leagues pinned in config for $sportSlug")
        }
        return mergeLeagues(
            stored = applyExtraTopLeagues(stored, extras),
            defaults = fetchDefaultTopLeagues()
        )
    }

    /**
     * A league we care about, used to decide which of the day's tournaments to pull events for.
     *
     * [tournamentId] is either Sofascore's season-scoped tournament id (`event.tournament.id`,
     * what we persist) or a unique-tournament id (what the top-competitions list returns). They
     * are separate id spaces, so [isUniqueTournament] says which side to match on.
     */
    data class TrackedLeague(
        val tournamentId: Long,
        val tournamentName: String,
        val isTopLeague: Boolean = true,
        val isUniqueTournament: Boolean = false
    )

    protected fun logWebClientError(operation: String, exception: Exception, context: Map<String, Any> = emptyMap()) {
        val contextStr = if (context.isNotEmpty()) {
            " [${context.entries.joinToString(", ") { "${it.key}=${it.value}" }}]"
        } else ""

        when (exception) {
            is WebClientResponseException -> {
                val statusCode = exception.statusCode.value()
                val responseBody = exception.responseBodyAsString.take(200).let {
                    if (it.length == 200) "$it..." else it
                }
                logger.warn(
                    "HTTP $statusCode error during $operation$contextStr - Response: ${responseBody.ifEmpty { "empty" }}"
                )
            }
            is WebClientRequestException -> {
                val cause = exception.cause
                when (cause) {
                    is TimeoutException -> logger.warn("Timeout during $operation$contextStr - Request took too long")
                    is UnknownHostException -> logger.warn("Unknown host during $operation$contextStr - ${cause.message}")
                    is IOException -> logger.warn("Network error during $operation$contextStr - ${cause.message}")
                    else -> logger.warn("Request failed during $operation$contextStr - ${exception.message}")
                }
            }
            else -> logger.warn("Unexpected error during $operation$contextStr - ${exception.javaClass.simpleName}: ${exception.message}")
        }
    }

    /**
     * The tournaments with fixtures on [date], straight from Sofascore - the closest replacement
     * for the withdrawn scheduled-events feed. Paginated and 1-based; entries carry both the
     * season-scoped tournament id and the unique-tournament id.
     */
    private suspend fun fetchScheduledTournaments(date: LocalDate): List<ScheduledTournament> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val collected = mutableListOf<ScheduledTournament>()

        for (page in 1..maxTournamentListPages) {
            if (requestDelayMs > 0) delay(requestDelayMs)

            val entries = try {
                webClient
                    .get()
                    .uri("/sport/$sportSlug/scheduled-tournaments/$dateStr/page/$page")
                    .retrieve()
                    .awaitBody<ScheduledTournamentsResponse>()
                    .scheduled
            } catch (e: WebClientResponseException.NotFound) {
                // Sofascore 404s past the last page rather than returning an empty list.
                break
            } catch (e: Exception) {
                logWebClientError("fetching scheduled tournaments", e, mapOf("date" to dateStr, "page" to page))
                break
            }

            if (entries.isEmpty()) break
            collected += entries.map { it.tournament }

            if (page == maxTournamentListPages) {
                logger.warn(
                    "Stopped after $maxTournamentListPages pages of scheduled tournaments for $dateStr - " +
                            "raise sofascore.discovery.tournament-list-max-pages if leagues are missing"
                )
            }
        }

        logger.debug("${collected.size} tournaments scheduled on $dateStr")
        return collected
    }

    /**
     * Events for a tournament. Sofascore serves these without a season id, defaulting to the
     * current one, which spares a lookup per tournament.
     */
    private suspend fun fetchTournamentEventsPage(
        tournamentId: Long,
        tournamentName: String,
        direction: String,
        page: Int
    ): List<ScheduledEvent>? = try {
        webClient
            .get()
            .uri("/tournament/$tournamentId/events/$direction/$page")
            .retrieve()
            .awaitBody<TournamentEventsResponse>()
            .events
    } catch (e: WebClientResponseException.NotFound) {
        logger.debug("No $direction page $page for tournament $tournamentId ($tournamentName)")
        null
    } catch (e: Exception) {
        logWebClientError(
            "fetching tournament events",
            e,
            mapOf("tournamentId" to tournamentId, "direction" to direction, "page" to page)
        )
        null
    }

    /**
     * In-progress fixtures for [leagues], which neither paginated feed returns: `next` drops a
     * match the moment it kicks off and `last` only picks it up once it has finished. Without this
     * a refresh during play sees a hole where the day's live matches should be.
     */
    private suspend fun fetchLiveEvents(
        startOfDay: Long,
        endOfDay: Long,
        leagues: List<TrackedLeague>,
        includeAllLeagues: Boolean
    ): List<SofascoreEvent> {
        val byUniqueId = leagues.filter { it.isUniqueTournament }.associateBy { it.tournamentId }
        val bySeasonScopedId = leagues.filterNot { it.isUniqueTournament }.associateBy { it.tournamentId }

        return try {
            webClient
                .get()
                .uri("/sport/$sportSlug/events/live")
                .retrieve()
                .awaitBody<ScheduledEventsResponse>()
                .events
                .filter { it.startTimestamp in startOfDay until endOfDay }
                .mapNotNull { scheduled ->
                    // The live feed spans every competition, so keep only leagues we track - under
                    // whichever id space that league was seeded from.
                    val league = scheduled.tournament.uniqueTournament?.id?.toLong()?.let { byUniqueId[it] }
                        ?: bySeasonScopedId[scheduled.tournament.id]
                    when {
                        league != null ->
                            convertScheduledEventToSofascoreEvent(scheduled).copy(isTopLeague = league.isTopLeague)
                        includeAllLeagues ->
                            convertScheduledEventToSofascoreEvent(scheduled).copy(isTopLeague = false)
                        else -> null
                    }
                }
        } catch (e: Exception) {
            logWebClientError("fetching live events", e, mapOf("sport" to sportSlug))
            emptyList()
        }
    }

    /**
     * Collects every event kicking off on [date] for the leagues in [leagues].
     *
     * Replaces the withdrawn /sport/{sport}/scheduled-events/{date} endpoint. Sofascore still says
     * which tournaments play on a date, so that comes first and only those tournaments are pulled -
     * far fewer calls than polling every tracked league, and it picks up sub-tournaments (a
     * qualification round is its own tournament) that a league-level feed leaves out.
     *
     * Their events then come from separate upcoming/finished feeds: a past date needs only `last`,
     * a future date only `next`, and today needs both plus the live feed, since a match in play has
     * left `next` and not yet reached `last`.
     */
    protected suspend fun fetchEventsForDate(
        date: LocalDate,
        leagues: List<TrackedLeague>,
        includeAllLeagues: Boolean = false
    ): List<SofascoreEvent> {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toEpochSecond()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
        val today = LocalDate.now(zone)

        val directions = when {
            date.isAfter(today) -> listOf("next")
            date.isBefore(today) -> listOf("last")
            else -> listOf("next", "last")
        }

        val byUniqueId = leagues.filter { it.isUniqueTournament }.associateBy { it.tournamentId }
        val bySeasonScopedId = leagues.filterNot { it.isUniqueTournament }.associateBy { it.tournamentId }
        fun trackedLeagueFor(tournament: ScheduledTournament): TrackedLeague? =
            tournament.uniqueTournament?.id?.toLong()?.let { byUniqueId[it] } ?: bySeasonScopedId[tournament.id]

        val scheduled = fetchScheduledTournaments(date)
        val relevant = scheduled.filter { includeAllLeagues || trackedLeagueFor(it) != null }
        logger.info(
            "$date: ${scheduled.size} tournaments scheduled, ${relevant.size} of interest" +
                    if (includeAllLeagues) " (all leagues)" else " (tracked leagues)"
        )

        val collected = LinkedHashMap<Long, SofascoreEvent>()

        if (date == today) {
            fetchLiveEvents(startOfDay, endOfDay, leagues, includeAllLeagues).forEach { collected[it.id] = it }
            logger.debug("Live feed contributed ${collected.size} in-play events for $date")
        }

        for (tournament in relevant) {
            // Unknown tournaments only reach here when every league is wanted; treat them as
            // non-top so they do not pollute the top-league statistics.
            val isTopLeague = trackedLeagueFor(tournament)?.isTopLeague ?: false

            for (direction in directions) {
                var reachedWindowEdge = false

                for (page in 0 until maxDiscoveryPages) {
                    if (requestDelayMs > 0) delay(requestDelayMs)

                    val events = fetchTournamentEventsPage(tournament.id, tournament.name, direction, page)
                    if (events.isNullOrEmpty()) {
                        reachedWindowEdge = true
                        break
                    }

                    events.asSequence()
                        .filter { it.startTimestamp in startOfDay until endOfDay }
                        .forEach { scheduled ->
                            collected[scheduled.id] = convertScheduledEventToSofascoreEvent(scheduled)
                                .copy(isTopLeague = isTopLeague)
                        }

                    // `next` runs forward in time and `last` runs backward, so each stops once the
                    // whole page sits beyond the day we want. Uses min/max rather than first/last so
                    // it does not depend on the ordering inside a page.
                    val passedWindow = when (direction) {
                        "next" -> events.minOf { it.startTimestamp } >= endOfDay
                        else -> events.maxOf { it.startTimestamp } < startOfDay
                    }
                    if (passedWindow) {
                        reachedWindowEdge = true
                        break
                    }
                }

                // Ran out of pages before reaching $date - fixtures for that day may be missing.
                if (!reachedWindowEdge) {
                    logger.warn(
                        "Exhausted $maxDiscoveryPages '$direction' pages for ${tournament.name} " +
                                "(${tournament.id}) without reaching $date - results may be incomplete"
                    )
                }
            }
        }

        logger.info("Discovered ${collected.size} events on $date across ${relevant.size} tournaments")
        return collected.values.toList()
    }

    protected suspend fun fetchOddsForEvent(eventId: Long): Odds? = try {
        val oddsResponse = webClient
            .get()
            .uri("/event/$eventId/odds/1/all")
            .retrieve()
            .awaitBody<OddsResponse>()

        logger.debug("Fetched odds for event $eventId: ${oddsResponse.markets?.size} markets")

        val fullTimeMarket = oddsResponse.markets?.find {
            it.marketName.contains("Full time", ignoreCase = true) ||
                    it.marketName.contains("1X2", ignoreCase = true) ||
                    it.marketName.contains("Match winner", ignoreCase = true)
        }

        fullTimeMarket?.let {
            logger.debug("Found market: ${it.marketName} with ${it.choices?.size} choices")
        } ?: logger.debug("No full time market found for event $eventId")

        fullTimeMarket?.choices?.let { choices ->
            Odds(
                home = choices.find { it.name == "1" }?.fractionalValue,
                draw = choices.find { it.name == "X" }?.fractionalValue,
                away = choices.find { it.name == "2" }?.fractionalValue
            ).also { odds ->
                logger.debug("Extracted odds for event {}: {}", eventId, odds)
            }
        }
    } catch (e: Exception) {
        logWebClientError("fetching odds", e, mapOf("eventId" to eventId))
        null
    }

    protected suspend fun fetchVotingForEvent(eventId: Long): Voting? = try {
        val response = webClient
            .get()
            .uri("/event/$eventId/votes")
            .retrieve()
            .awaitBody<VotingResponse>()

        logger.debug("Fetched votes for event $eventId: vote1=${response.vote?.vote1}, voteX=${response.vote?.voteX}, vote2=${response.vote?.vote2}")

        response.vote?.let { vote ->
            val total = (vote.vote1 ?: 0) + (vote.vote2 ?: 0) + (vote.voteX ?: 0)
            if (total > 0) {
                Voting(
                    home = ((vote.vote1 ?: 0) * 100 / total),
                    draw = ((vote.voteX ?: 0) * 100 / total),
                    away = ((vote.vote2 ?: 0) * 100 / total),
                    total = total
                )
            } else null
        }
    } catch (e: Exception) {
        logWebClientError("fetching voting", e, mapOf("eventId" to eventId))
        null
    }

    protected suspend fun fetchEventDetails(eventId: Long): SofascoreEvent? = try {
        val response = webClient
            .get()
            .uri("/event/$eventId")
            .retrieve()
            .awaitBody<EventDetailsResponse>()

        logger.debug("Fetched event details for $eventId: status=${response.event?.status?.description}")
        response.event
    } catch (e: WebClientResponseException.NotFound) {
        logger.debug("Event $eventId not found (404)")
        null
    } catch (e: Exception) {
        logWebClientError("fetching event details", e, mapOf("eventId" to eventId))
        null
    }

    private suspend fun fetchRawEventStatistics(eventId: Long): EventStatisticsResponse? = try {
        webClient
            .get()
            .uri("/event/$eventId/statistics")
            .retrieve()
            .awaitBody<EventStatisticsResponse>()
            .also { logger.debug("Fetched statistics for event $eventId") }
    } catch (e: WebClientResponseException.NotFound) {
        // Sofascore only serves statistics from kick-off onwards.
        logger.debug("Statistics not found (404) for event $eventId")
        null
    } catch (e: Exception) {
        logWebClientError("fetching event statistics", e, mapOf("eventId" to eventId))
        null
    }

    /**
     * Every statistic Sofascore has for a match, per period, as shown when a match is selected.
     * Empty while a match has not kicked off yet.
     */
    suspend fun getMatchStatistics(eventId: Long): MatchStatisticsResponse {
        val periods = fetchRawEventStatistics(eventId)?.statistics.orEmpty().map { period ->
            MatchStatisticsPeriod(
                period = period.period ?: "ALL",
                groups = period.groups.orEmpty().map { group ->
                    MatchStatisticsGroup(
                        groupName = group.groupName ?: "",
                        items = group.statisticsItems.orEmpty().map { item ->
                            MatchStatisticsItem(
                                name = item.name ?: item.key ?: "",
                                home = item.home ?: "-",
                                away = item.away ?: "-",
                                homeValue = item.homeValue,
                                awayValue = item.awayValue,
                                compareCode = item.compareCode
                            )
                        }
                    )
                }
            )
        }
        return MatchStatisticsResponse(periods = periods)
    }

    protected suspend fun fetchEventStatistics(eventId: Long): EventStatistics? =
        fetchRawEventStatistics(eventId)?.statistics?.firstOrNull()?.let { stats ->
            EventStatistics(
                homeYellowCards = stats.groups?.flatMap { it.statisticsItems ?: emptyList() }
                    ?.find { it.key == "yellowCards" }?.home?.toIntOrNull(),
                homeRedCards = stats.groups?.flatMap { it.statisticsItems ?: emptyList() }
                    ?.find { it.key == "redCards" }?.home?.toIntOrNull(),
                awayYellowCards = stats.groups?.flatMap { it.statisticsItems ?: emptyList() }
                    ?.find { it.key == "yellowCards" }?.away?.toIntOrNull(),
                awayRedCards = stats.groups?.flatMap { it.statisticsItems ?: emptyList() }
                    ?.find { it.key == "redCards" }?.away?.toIntOrNull()
            )
        }

    suspend fun getTeamEvents(teamId: Long): List<SofascoreEvent> = try {
        val response = webClient
            .get()
            .uri("/team/$teamId/events/last/0")
            .retrieve()
            .awaitBody<TeamEventsResponse>()

        logger.info("Fetched ${response.events?.size ?: 0} events for team $teamId")
        response.events?.sortedByDescending { it.startTimestamp } ?: emptyList()
    } catch (e: Exception) {
        logWebClientError("fetching team events", e, mapOf("teamId" to teamId))
        emptyList()
    }

    suspend fun getTournamentStandings(tournamentId: Long, seasonId: Long): StandingsResponse? = try {
        val response = webClient
            .get()
            .uri("/tournament/$tournamentId/season/$seasonId/standings/total")
            .retrieve()
            .awaitBody<StandingsResponse>()

        logger.info("Fetched standings for tournament $tournamentId, season $seasonId: ${response.standings?.size} groups")
        response
    } catch (e: WebClientResponseException.NotFound) {
        logger.debug("Standings not found (404) - tournamentId=$tournamentId, seasonId=$seasonId")
        null
    } catch (e: Exception) {
        logWebClientError("fetching tournament standings", e, mapOf("tournamentId" to tournamentId, "seasonId" to seasonId))
        null
    }

    protected suspend fun saveOddsHistory(eventId: Long, odds: Odds?, timestamp: Instant) {
        odds?.takeIf { it.home != null || it.draw != null || it.away != null }?.let {
            val latestOdds = withContext(Dispatchers.IO) {
                matchOddsHistoryRepository.findFirstByEventIdOrderByRecordedAtDesc(eventId)
            }

            if (latestOdds?.oddsHome != odds.home ||
                latestOdds?.oddsDraw != odds.draw ||
                latestOdds?.oddsAway != odds.away
            ) {
                val oddsHistory = jp.odds.entity.MatchOddsHistory().apply {
                    this.eventId = eventId
                    this.oddsHome = odds.home
                    this.oddsDraw = odds.draw
                    this.oddsAway = odds.away
                    this.recordedAt = timestamp
                }
                withContext(Dispatchers.IO) {
                    matchOddsHistoryRepository.save(oddsHistory)
                }
                logger.debug("Saved odds history for event $eventId (values changed)")
            } else {
                logger.debug("Skipped odds history for event $eventId (no changes)")
            }
        }
    }

    protected suspend fun saveVotesHistory(eventId: Long, voting: Voting?, timestamp: Instant) {
        voting?.takeIf { it.home != null || it.draw != null || it.away != null }?.let {
            val latestVotes = withContext(Dispatchers.IO) {
                matchVotesHistoryRepository.findFirstByEventIdOrderByRecordedAtDesc(eventId)
            }

            if (latestVotes?.votingHome != voting.home ||
                latestVotes?.votingDraw != voting.draw ||
                latestVotes?.votingAway != voting.away ||
                latestVotes?.votingTotal != voting.total
            ) {
                val votesHistory = jp.odds.entity.MatchVotesHistory().apply {
                    this.eventId = eventId
                    this.votingHome = voting.home
                    this.votingDraw = voting.draw
                    this.votingAway = voting.away
                    this.votingTotal = voting.total
                    this.recordedAt = timestamp
                }
                withContext(Dispatchers.IO) {
                    matchVotesHistoryRepository.save(votesHistory)
                }
                logger.debug("Saved votes history for event $eventId (values changed)")
            } else {
                logger.debug("Skipped votes history for event $eventId (no changes)")
            }
        }
    }

    suspend fun getOddsHistory(eventId: Long): List<jp.odds.entity.MatchOddsHistory> = withContext(Dispatchers.IO) {
        matchOddsHistoryRepository.findByEventIdOrderByRecordedAtAsc(eventId)
    }

    suspend fun getVotesHistory(eventId: Long): List<jp.odds.entity.MatchVotesHistory> = withContext(Dispatchers.IO) {
        matchVotesHistoryRepository.findByEventIdOrderByRecordedAtAsc(eventId)
    }

    protected fun convertScheduledEventToSofascoreEvent(scheduledEvent: ScheduledEvent): SofascoreEvent = SofascoreEvent(
        id = scheduledEvent.id,
        startTimestamp = scheduledEvent.startTimestamp,
        homeTeam = Team(
            id = scheduledEvent.homeTeam.id,
            name = scheduledEvent.homeTeam.name,
            country = scheduledEvent.homeTeam.country?.let { Country(it.name) }
        ),
        awayTeam = Team(
            id = scheduledEvent.awayTeam.id,
            name = scheduledEvent.awayTeam.name,
            country = scheduledEvent.awayTeam.country?.let { Country(it.name) }
        ),
        homeScore = scheduledEvent.homeScore?.let {
            Score(current = it.current, display = it.display)
        },
        awayScore = scheduledEvent.awayScore?.let {
            Score(current = it.current, display = it.display)
        },
        status = scheduledEvent.status,
        tournament = Tournament(
            id = scheduledEvent.tournament.id,
            name = scheduledEvent.tournament.name,
            category = Category(
                name = scheduledEvent.tournament.category.name,
                country = scheduledEvent.tournament.category.country?.let { Country(it.name) }
            ),
            uniqueTournamentId = scheduledEvent.tournament.uniqueTournament?.id?.toLong()
        ),
        season = scheduledEvent.season,
        vote = null,
        time = scheduledEvent.time,
        statusTime = scheduledEvent.statusTime,
        eventFilters = scheduledEvent.eventFilters,
        odds = null,
        voting = null,
        homeFormScore = null,
        awayFormScore = null,
        lastUpdated = null,
        isTopLeague = null
    )

    protected fun mapDataToSofascoreEvent(
        eventId: Long,
        startTimestamp: Long,
        homeTeamId: Long,
        homeTeamName: String,
        awayTeamId: Long,
        awayTeamName: String,
        homeScore: Int?,
        awayScore: Int?,
        statusType: String,
        statusDescription: String,
        tournamentId: Long,
        tournamentName: String,
        categoryName: String,
        countryName: String?,
        seasonId: Long?,
        oddsHome: String?,
        oddsDraw: String?,
        oddsAway: String?,
        votingHome: Int?,
        votingDraw: Int?,
        votingAway: Int?,
        votingTotal: Int?,
        lastUpdated: Instant?,
        isTopLeague: Boolean?,
        liveElapsedMinutes: Int? = null,
        liveMinutesRemaining: Int? = null
    ): SofascoreEvent = SofascoreEvent(
        id = eventId,
        startTimestamp = startTimestamp,
        homeTeam = Team(
            id = homeTeamId,
            name = homeTeamName,
            country = null
        ),
        awayTeam = Team(
            id = awayTeamId,
            name = awayTeamName,
            country = null
        ),
        homeScore = homeScore?.let { Score(current = it) },
        awayScore = awayScore?.let { Score(current = it) },
        status = Status(
            type = statusType,
            description = statusDescription
        ),
        tournament = Tournament(
            id = tournamentId,
            name = tournamentName,
            category = Category(
                name = categoryName,
                country = countryName?.let { Country(it) }
            )
        ),
        season = seasonId?.let { Season(id = it, name = "") },
        vote = null,
        odds = if (oddsHome != null || oddsDraw != null || oddsAway != null) {
            Odds(
                home = oddsHome,
                draw = oddsDraw,
                away = oddsAway
            )
        } else null,
        voting = if (votingHome != null || votingDraw != null || votingAway != null) {
            Voting(
                home = votingHome,
                draw = votingDraw,
                away = votingAway,
                total = votingTotal
            )
        } else null,
        lastUpdated = lastUpdated?.epochSecond ?: 0,
        isTopLeague = isTopLeague,
        liveElapsedMinutes = liveElapsedMinutes,
        liveMinutesRemaining = liveMinutesRemaining
    )

    protected fun parseOdds(fractionalOdds: String): Double = try {
        val parts = fractionalOdds.split('/')
        if (parts.size == 2) {
            val numerator = parts[0].toDouble()
            val denominator = parts[1].toDouble()
            (numerator / denominator) + 1.0
        } else {
            0.0
        }
    } catch (_: Exception) {
        0.0
    }

    protected fun <T : MatchDataWithResult> extractWinningMatchData(matches: List<T>): List<MatchWinningData> {
        return matches.mapNotNull { match ->
            val homeScore = match.homeScore ?: return@mapNotNull null
            val awayScore = match.awayScore ?: return@mapNotNull null

            val winningVote: Int?
            val winningOdds: String?

            when {
                homeScore > awayScore -> {
                    winningVote = match.votingHome
                    winningOdds = match.oddsHome
                }
                awayScore > homeScore -> {
                    winningVote = match.votingAway
                    winningOdds = match.oddsAway
                }
                else -> return@mapNotNull null // Skip draws
            }

            if (winningVote != null && winningOdds != null) {
                MatchWinningData(
                    tournamentId = match.tournamentId,
                    tournamentName = match.tournamentName,
                    vote = winningVote,
                    odds = parseOdds(winningOdds)
                )
            } else {
                null
            }
        }
    }

    protected fun calculateWinningStatistics(winningMatchData: List<MatchWinningData>): WinningMatchStatistics {
        if (winningMatchData.isEmpty()) {
            return WinningMatchStatistics(
                averageVote = 0.0,
                averageOdds = 0.0,
                totalMatches = 0
            )
        }

        return WinningMatchStatistics(
            averageVote = winningMatchData.map { it.vote }.average(),
            averageOdds = winningMatchData.map { it.odds }.average(),
            totalMatches = winningMatchData.size
        )
    }

    protected fun <T : MatchDataWithResult> extractBettingData(matches: List<T>): List<MatchBettingData> {
        return matches.mapNotNull { match ->
            val homeScore = match.homeScore ?: return@mapNotNull null
            val awayScore = match.awayScore ?: return@mapNotNull null

            val homeVote = match.votingHome ?: return@mapNotNull null
            val drawVote = match.votingDraw ?: 0
            val awayVote = match.votingAway ?: return@mapNotNull null

            // Favorite is the outcome (home/draw/away) with the most votes
            val maxVote = maxOf(homeVote, drawVote, awayVote)
            val favoriteOutcome = when (maxVote) {
                homeVote -> "home"
                drawVote -> "draw"
                else -> "away"
            }
            val favoriteVote = maxVote
            val favoriteOddsStr = when (favoriteOutcome) {
                "home" -> match.oddsHome
                "draw" -> match.oddsDraw
                else -> match.oddsAway
            }
            val favoriteOdds = favoriteOddsStr?.let { parseOdds(it) } ?: return@mapNotNull null
            if (favoriteOdds <= 0.0) return@mapNotNull null

            val favoriteWon = when (favoriteOutcome) {
                "home" -> homeScore > awayScore
                "draw" -> homeScore == awayScore
                else -> awayScore > homeScore
            }

            MatchBettingData(
                tournamentId = match.tournamentId,
                tournamentName = match.tournamentName,
                homeTeamName = match.homeTeamName,
                awayTeamName = match.awayTeamName,
                homeScore = homeScore,
                awayScore = awayScore,
                oddsHome = match.oddsHome,
                oddsDraw = match.oddsDraw,
                oddsAway = match.oddsAway,
                votingHome = match.votingHome,
                votingDraw = match.votingDraw,
                votingAway = match.votingAway,
                favoriteVote = favoriteVote,
                favoriteOdds = favoriteOdds,
                favoriteWon = favoriteWon
            )
        }
    }

    private fun calculateThresholdROI(matches: List<MatchBettingData>, threshold: Int): Pair<Int, Double> {
        val aboveThreshold = matches.filter { it.favoriteVote >= threshold }
        if (aboveThreshold.isEmpty()) return 0 to 0.0
        val totalStaked = aboveThreshold.size.toDouble()
        val totalReturn = aboveThreshold.sumOf { if (it.favoriteWon) it.favoriteOdds else 0.0 }
        val roi = ((totalReturn - totalStaked) / totalStaked) * 100.0
        return aboveThreshold.size to roi
    }

    private fun findProfitableThreshold(matches: List<MatchBettingData>): LeagueProfitability {
        val tournamentId = matches.firstOrNull()?.tournamentId
        val tournamentName = matches.firstOrNull()?.tournamentName

        val favoriteWins = matches.filter { it.favoriteWon }
        val favoriteWinCount = favoriteWins.size
        val avgFavoriteWinOdds = if (favoriteWins.isNotEmpty()) favoriteWins.map { it.favoriteOdds }.average() else null

        for (threshold in 50..99) {
            val (count, roi) = calculateThresholdROI(matches, threshold)
            if (count > 0 && roi >= 10.0) {
                return LeagueProfitability(
                    tournamentId = tournamentId,
                    tournamentName = tournamentName,
                    minVoteThreshold = threshold,
                    totalMatches = matches.size,
                    matchesAboveThreshold = count,
                    roi = roi,
                    favoriteWins = favoriteWinCount,
                    averageFavoriteWinOdds = avgFavoriteWinOdds
                )
            }
        }

        // No threshold found - show overall ROI across all matches
        val totalStaked = matches.size.toDouble()
        val totalReturn = matches.sumOf { if (it.favoriteWon) it.favoriteOdds else 0.0 }
        val overallRoi = if (totalStaked > 0) ((totalReturn - totalStaked) / totalStaked) * 100.0 else 0.0
        return LeagueProfitability(
            tournamentId = tournamentId,
            tournamentName = tournamentName,
            minVoteThreshold = null,
            totalMatches = matches.size,
            matchesAboveThreshold = matches.size,
            roi = overallRoi,
            favoriteWins = favoriteWinCount,
            averageFavoriteWinOdds = avgFavoriteWinOdds
        )
    }

    protected fun <T : MatchDataWithResult> calculateProfitableThresholds(
        bettingData: List<MatchBettingData>,
        allFinishedMatches: List<T>? = null
    ): ProfitabilityResponse {
        if (bettingData.isEmpty() && allFinishedMatches.isNullOrEmpty()) {
            return ProfitabilityResponse(overall = null, byLeague = emptyList())
        }

        val overall = if (bettingData.isNotEmpty()) findProfitableThreshold(bettingData) else null

        val byLeague = bettingData
            .groupBy { it.tournamentId to it.tournamentName }
            .map { (_, matches) -> findProfitableThreshold(matches) }
            .sortedByDescending { it.totalMatches }

        val matchDetails = allFinishedMatches?.mapNotNull { match ->
            val homeScore = match.homeScore ?: return@mapNotNull null
            val awayScore = match.awayScore ?: return@mapNotNull null

            val homeVote = match.votingHome
            val drawVote = match.votingDraw ?: 0
            val awayVote = match.votingAway

            val favoriteVote: Int?
            val favoriteOdds: Double?
            val favoriteWon: Boolean?

            if (homeVote == null || awayVote == null) {
                favoriteVote = null
                favoriteOdds = null
                favoriteWon = null
            } else {
                val maxVote = maxOf(homeVote, drawVote, awayVote)
                val favoriteOutcome = when (maxVote) {
                    homeVote -> "home"
                    drawVote -> "draw"
                    else -> "away"
                }
                favoriteVote = maxVote
                val oddsStr = when (favoriteOutcome) {
                    "home" -> match.oddsHome
                    "draw" -> match.oddsDraw
                    else -> match.oddsAway
                }
                favoriteOdds = oddsStr?.let { parseOdds(it) }?.takeIf { it > 0.0 }
                favoriteWon = when (favoriteOutcome) {
                    "home" -> homeScore > awayScore
                    "draw" -> homeScore == awayScore
                    else -> awayScore > homeScore
                }
            }

            MatchBettingDetail(
                homeTeamName = match.homeTeamName,
                awayTeamName = match.awayTeamName,
                homeScore = homeScore,
                awayScore = awayScore,
                oddsHome = match.oddsHome?.let { parseOdds(it) }?.takeIf { it > 0.0 },
                oddsDraw = match.oddsDraw?.let { parseOdds(it) }?.takeIf { it > 0.0 },
                oddsAway = match.oddsAway?.let { parseOdds(it) }?.takeIf { it > 0.0 },
                votingHome = match.votingHome,
                votingDraw = match.votingDraw,
                votingAway = match.votingAway,
                favoriteVote = favoriteVote,
                favoriteOdds = favoriteOdds,
                favoriteWon = favoriteWon,
                tournamentName = match.tournamentName
            )
        }

        return ProfitabilityResponse(
            overall = overall?.copy(tournamentId = null, tournamentName = null),
            byLeague = byLeague,
            matches = matchDetails
        )
    }

    protected fun calculateStatisticsByLeague(winningMatchData: List<MatchWinningData>): WinningMatchStatisticsByLeague {
        val overall = calculateWinningStatistics(winningMatchData)

        val byLeague = winningMatchData
            .groupBy { it.tournamentId to it.tournamentName }
            .map { (tournamentInfo, matches) ->
                LeagueStatistics(
                    tournamentId = tournamentInfo.first,
                    tournamentName = tournamentInfo.second,
                    averageVote = matches.map { it.vote }.average(),
                    averageOdds = matches.map { it.odds }.average(),
                    totalMatches = matches.size
                )
            }
            .sortedByDescending { it.totalMatches }

        return WinningMatchStatisticsByLeague(
            overall = overall,
            byLeague = byLeague
        )
    }
}
