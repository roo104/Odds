package jp.odds.view

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.details.Details
import com.vaadin.flow.component.dialog.Dialog
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.html.H3
import com.vaadin.flow.component.html.H4
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.NumberField
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route
import jp.odds.dto.SofascoreEvent
import jp.odds.dto.SofascoreEventsResponse
import jp.odds.service.SofascoreService
import kotlinx.coroutines.runBlocking
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Route("")
class FootballMatchesView(private val sofascoreService: SofascoreService) : VerticalLayout() {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private var allMatches: List<SofascoreEvent> = emptyList()
    private val contentLayout = VerticalLayout()
    private var minOdds: Double = 3.0
    private var minVotePercent: Int = 70
    private val objectMapper = ObjectMapper()

    init {
        setSizeFull()
        element.themeList.add("dark")
        addStyleSheet()

        val filterCheckbox = Checkbox("Show only Not Started matches")
        filterCheckbox.addValueChangeListener { event ->
            refreshMatchDisplay(event.value)
        }

        // Odds slider
        val oddsSlider = NumberField("Min Odds for Highlight")
        oddsSlider.value = 3.0
        oddsSlider.min = 1.0
        oddsSlider.max = 5.0
        oddsSlider.step = 0.1
        oddsSlider.setWidth("200px")
        oddsSlider.isStepButtonsVisible = true
        oddsSlider.addValueChangeListener { event ->
            minOdds = event.value ?: 3.0
            refreshMatchDisplay(filterCheckbox.value)
        }

        // Vote percentage slider
        val voteSlider = NumberField("Min Vote % for Highlight")
        voteSlider.value = 70.0
        voteSlider.min = 50.0
        voteSlider.max = 90.0
        voteSlider.step = 5.0
        voteSlider.setWidth("200px")
        voteSlider.addValueChangeListener { event ->
            minVotePercent = event.value.toInt()
            refreshMatchDisplay(filterCheckbox.value)
        }

        val filterLayout = HorizontalLayout(filterCheckbox, oddsSlider, voteSlider)
        filterLayout.setWidthFull()

        // Import JSON button
        val importButton = Button("Import JSON") {
            showImportDialog()
        }

        val topLayout = HorizontalLayout(filterLayout, importButton)
        topLayout.setWidthFull()
        topLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER)

        add(topLayout)
        add(contentLayout)
        contentLayout.setSizeFull()

        loadMatches()
    }

    private fun addStyleSheet() {
        element.executeJs(
            """
            document.documentElement.setAttribute('theme', 'dark');
            const style = document.createElement('style');
            style.textContent = `
                vaadin-grid::part(highlight-green-row) {
                    background-color: rgba(0, 255, 0, 0.2) !important;
                }
                vaadin-grid::part(win-row) {
                    background-color: rgba(0, 255, 0, 0.3) !important;
                }
                vaadin-grid::part(loss-row) {
                    background-color: rgba(255, 0, 0, 0.3) !important;
                }
            `;
            document.head.appendChild(style);
            """
        )
    }

    private fun createGrid(matches: List<SofascoreEvent>): Grid<SofascoreEvent> {
        val grid = Grid(SofascoreEvent::class.java, false)

        grid.addColumn { event ->
            dateTimeFormatter.format(Instant.ofEpochSecond(event.startTimestamp))
        }.setHeader("Date & Time").setAutoWidth(true)

        grid.addColumn { event -> event.homeTeam.name }.setHeader("Home Team").setAutoWidth(true)
        grid.addColumn { event -> event.awayTeam.name }.setHeader("Away Team").setAutoWidth(true)

        grid.addColumn { event ->
            val homeScore = event.homeScore?.current ?: "-"
            val awayScore = event.awayScore?.current ?: "-"
            "$homeScore - $awayScore"
        }.setHeader("Score").setAutoWidth(true)

        grid.addColumn { event -> formatOdds(event.odds?.home) }.setHeader("Home Odds").setAutoWidth(true)
        grid.addColumn { event -> formatOdds(event.odds?.draw) }.setHeader("Draw Odds").setAutoWidth(true)
        grid.addColumn { event -> formatOdds(event.odds?.away) }.setHeader("Away Odds").setAutoWidth(true)

        grid.addColumn { event -> event.voting?.home?.let { "$it%" } ?: "-" }.setHeader("Home Vote %")
            .setAutoWidth(true)
        grid.addColumn { event -> event.voting?.draw?.let { "$it%" } ?: "-" }.setHeader("Draw Vote %")
            .setAutoWidth(true)
        grid.addColumn { event -> event.voting?.away?.let { "$it%" } ?: "-" }.setHeader("Away Vote %")
            .setAutoWidth(true)

        grid.addColumn { event -> event.status.description }.setHeader("Status").setAutoWidth(true)
        grid.addColumn { event -> event.tournament.name }.setHeader("Tournament").setAutoWidth(true)

        grid.setItems(matches)

        // Highlight rows where votes > 70% and odds > 3.0
        grid.setPartNameGenerator { event ->
            if (shouldHighlight(event)) "highlight-green-row" else null
        }

        // Add click listener to show previous matches
        grid.addItemClickListener { event ->
            showPreviousMatchesDialog(event.item)
        }

        grid.setAllRowsVisible(true)
        grid.setWidthFull()
        return grid
    }

    private fun showPreviousMatchesDialog(event: SofascoreEvent) {
        val dialog = Dialog()
        dialog.setHeaderTitle("Previous Matches: ${event.homeTeam.name} vs ${event.awayTeam.name}")
        dialog.setWidth("800px")

        val content = VerticalLayout()

        runBlocking {
            val homeTeamEvents = sofascoreService.getTeamEvents(event.homeTeam.id).take(5)
            val awayTeamEvents = sofascoreService.getTeamEvents(event.awayTeam.id).take(5)

            // Calculate points
            val homePoints = calculatePoints(homeTeamEvents, event.homeTeam.id)
            val awayPoints = calculatePoints(awayTeamEvents, event.awayTeam.id)

            // Home team section
            content.add(H4("${event.homeTeam.name} - Recent Matches (Points: $homePoints)"))
            val homeGrid = createPreviousMatchesGrid(homeTeamEvents, event.homeTeam.id)
            content.add(homeGrid)

            // Away team section
            content.add(H4("${event.awayTeam.name} - Recent Matches (Points: $awayPoints)"))
            val awayGrid = createPreviousMatchesGrid(awayTeamEvents, event.awayTeam.id)
            content.add(awayGrid)
        }

        dialog.add(content)
        dialog.open()
    }

    private fun calculatePoints(matches: List<SofascoreEvent>, teamId: Long): Int {
        return matches.sumOf { event ->
            val homeScore = event.homeScore?.current
            val awayScore = event.awayScore?.current

            if (homeScore != null && awayScore != null) {
                val isHomeTeam = event.homeTeam.id == teamId
                when {
                    homeScore == awayScore -> 1 // Draw
                    (isHomeTeam && homeScore > awayScore) || (!isHomeTeam && awayScore > homeScore) -> 2 // Win
                    else -> 0 // Loss
                }
            } else {
                0
            }
        }
    }

    private fun createPreviousMatchesGrid(matches: List<SofascoreEvent>, teamId: Long): Grid<SofascoreEvent> {
        val grid = Grid(SofascoreEvent::class.java, false)

        grid.addColumn { event ->
            dateTimeFormatter.format(Instant.ofEpochSecond(event.startTimestamp))
        }.setHeader("Date").setAutoWidth(true)

        grid.addColumn { event -> event.homeTeam.name }.setHeader("Home").setAutoWidth(true)
        grid.addColumn { event -> event.awayTeam.name }.setHeader("Away").setAutoWidth(true)

        grid.addColumn { event ->
            val homeScore = event.homeScore?.current ?: "-"
            val awayScore = event.awayScore?.current ?: "-"
            "$homeScore - $awayScore"
        }.setHeader("Score").setAutoWidth(true)

        grid.addColumn { event -> event.tournament.name }.setHeader("Tournament").setAutoWidth(true)

        grid.setItems(matches)

        // Highlight wins in green and losses in red
        grid.setPartNameGenerator { event ->
            val homeScore = event.homeScore?.current
            val awayScore = event.awayScore?.current

            if (homeScore != null && awayScore != null) {
                val isHomeTeam = event.homeTeam.id == teamId
                val won = if (isHomeTeam) homeScore > awayScore else awayScore > homeScore

                if (won) "win-row" else "loss-row"
            } else {
                null
            }
        }

        grid.setAllRowsVisible(true)
        grid.setWidthFull()

        return grid
    }

    private fun calculateConfidenceScore(event: SofascoreEvent, outcome: String): String {
        val voting = event.voting
        val odds = event.odds
        val homeForm = event.homeFormScore ?: 0
        val awayForm = event.awayFormScore ?: 0

        if (voting == null || odds == null) return "-"

        // Get values based on outcome
        val votePercent = when (outcome) {
            "home" -> voting.home ?: 0
            "draw" -> voting.draw ?: 0
            "away" -> voting.away ?: 0
            else -> 0
        }

        val oddsValue = when (outcome) {
            "home" -> parseOdds(odds.home)
            "draw" -> parseOdds(odds.draw)
            "away" -> parseOdds(odds.away)
            else -> 0.0
        }

        val formScore = when (outcome) {
            "home" -> homeForm
            "away" -> awayForm
            "draw" -> (homeForm + awayForm) / 2 // Average for draws
            else -> 0
        }

        // Calculate confidence score (0-100)
        // Higher odds = higher potential value (normalize odds from 1-10 to 0-100)
        val oddsComponent = ((oddsValue - 1.0) / 9.0 * 100).coerceIn(0.0, 100.0)

        // Higher vote % = more confidence (already 0-100)
        val voteComponent = votePercent.toDouble()

        // Form score (0-10 possible, normalize to 0-100)
        val formComponent = (formScore / 10.0 * 100).coerceIn(0.0, 100.0)

        // Weighted average: 40% odds, 40% votes, 20% form
        val score = (oddsComponent * 0.4 + voteComponent * 0.4 + formComponent * 0.2)

        return String.format("%.0f", score)
    }

    private fun shouldHighlight(event: SofascoreEvent): Boolean {
        val voting = event.voting ?: return false
        val odds = event.odds ?: return false

        // Check home win
        if ((voting.home ?: 0) > minVotePercent && parseOdds(odds.home) > minOdds) return true

        // Check draw
        if ((voting.draw ?: 0) > minVotePercent && parseOdds(odds.draw) > minOdds) return true

        // Check away win
        if ((voting.away ?: 0) > minVotePercent && parseOdds(odds.away) > minOdds) return true

        return false
    }

    private fun parseOdds(fractionalOdds: String?): Double {
        if (fractionalOdds == null) return 0.0
        return try {
            val parts = fractionalOdds.split("/")
            if (parts.size == 2) {
                val numerator = parts[0].toDouble()
                val denominator = parts[1].toDouble()
                (numerator / denominator) + 1.0
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun formatOdds(fractionalOdds: String?): String {
        if (fractionalOdds == null) return "-"

        return try {
            val parts = fractionalOdds.split("/")
            if (parts.size == 2) {
                val numerator = parts[0].toDouble()
                val denominator = parts[1].toDouble()
                val decimal = (numerator / denominator) + 1.0
                String.format("%.2f", decimal)
            } else {
                fractionalOdds
            }
        } catch (e: Exception) {
            fractionalOdds
        }
    }

    private fun loadMatches() {
        runBlocking {
            allMatches = sofascoreService.getTodayFootballMatches()
            refreshMatchDisplay(false)
        }
    }

    private fun refreshMatchDisplay(filterNotStarted: Boolean) {
        contentLayout.removeAll()

        val matchesToDisplay = if (filterNotStarted) {
            allMatches.filter { it.status.description == "Not started" }
        } else {
            allMatches
        }

        val groupedByCategory = matchesToDisplay.groupBy { it.tournament.category.name }

        groupedByCategory.forEach { (category, categoryMatches) ->
            val grid = createGrid(categoryMatches)
            val details = Details(H3(category), grid)
            details.isOpened = true
            details.setWidthFull()
            contentLayout.add(details)
        }
    }

    private fun showImportDialog() {
        val dialog = Dialog()
        dialog.setHeaderTitle("Import Sofascore API JSON")
        dialog.setWidth("800px")
        dialog.setHeight("600px")

        val textArea = TextArea("Paste JSON Response")
        textArea.setWidthFull()
        textArea.setHeight("400px")
        textArea.placeholder =
            "Paste the JSON response from https://api.sofascore.com/api/v1/sport/football/scheduled-events/YYYY-MM-DD"

        val importBtn = Button("Import") {
            try {
                val jsonText = textArea.value
                if (jsonText.isNullOrBlank()) {
                    Notification.show("Please paste JSON content", 3000, Notification.Position.MIDDLE)
                    return@Button
                }

                val response = objectMapper.readValue(jsonText, SofascoreEventsResponse::class.java)

                allMatches = response.events
                refreshMatchDisplay(false)

                Notification.show(
                    "Successfully imported ${response.events.size} matches",
                    3000,
                    Notification.Position.MIDDLE
                )
                dialog.close()
            } catch (e: Exception) {
                Notification.show("Error parsing JSON: ${e.message}", 5000, Notification.Position.MIDDLE)
            }
        }

        val cancelBtn = Button("Cancel") {
            dialog.close()
        }

        val buttonLayout = HorizontalLayout(importBtn, cancelBtn)
        val content = VerticalLayout(textArea, buttonLayout)
        content.setSizeFull()

        dialog.add(content)
        dialog.open()
    }
}
