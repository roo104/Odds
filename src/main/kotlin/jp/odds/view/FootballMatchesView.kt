package jp.odds.view

import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.checkbox.Checkbox
import com.vaadin.flow.component.datepicker.DatePicker
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
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Route("")
class FootballMatchesView(private val sofascoreService: SofascoreService) : VerticalLayout() {

    private val logger = LoggerFactory.getLogger(FootballMatchesView::class.java)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private var allMatches: List<SofascoreEvent> = emptyList()
    private val contentLayout = VerticalLayout()
    private var minOdds: Double = 3.0
    private var minVotePercent: Int = 70
    private val objectMapper = ObjectMapper()
    private var currentDate: LocalDate = LocalDate.now()
    private lateinit var filterCheckbox: Checkbox
    private lateinit var matchCriteriaCheckbox: Checkbox

    init {
        setSizeFull()
        element.themeList.add("dark")
        addStyleSheet()

        // Date navigation
        val prevDayButton = Button("Previous Day") {
            currentDate = currentDate.minusDays(1)
            loadMatches(currentDate)
        }

        val datePicker = DatePicker("Date")
        datePicker.value = currentDate
        datePicker.addValueChangeListener { event ->
            currentDate = event.value
            loadMatches(currentDate)
        }

        val nextDayButton = Button("Next Day") {
            currentDate = currentDate.plusDays(1)
            loadMatches(currentDate)
        }

        val todayButton = Button("Today") {
            currentDate = LocalDate.now()
            datePicker.value = currentDate
            loadMatches(currentDate)
        }

        val dateNavLayout = HorizontalLayout(prevDayButton, datePicker, nextDayButton, todayButton)
        dateNavLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER)
        dateNavLayout.style.set("gap", "10px")
        dateNavLayout.style.set("padding", "10px")

        filterCheckbox = Checkbox("Show only Not Started matches")
        filterCheckbox.addValueChangeListener { event ->
            refreshMatchDisplay(event.value, matchCriteriaCheckbox.value)
        }

        matchCriteriaCheckbox = Checkbox("Show only matches that match criteria")
        matchCriteriaCheckbox.addValueChangeListener { event ->
            refreshMatchDisplay(filterCheckbox.value, event.value)
        }

        val checkboxLayout = VerticalLayout(filterCheckbox, matchCriteriaCheckbox)
        checkboxLayout.style.set("gap", "5px")
        checkboxLayout.style.set("padding", "0")

        // Odds slider
        val oddsSlider = NumberField("Min Odds")
        oddsSlider.value = 2.5
        oddsSlider.min = 1.0
        oddsSlider.max = 5.0
        oddsSlider.step = 0.1
        oddsSlider.setWidth("150px")
        oddsSlider.isStepButtonsVisible = true
        oddsSlider.addValueChangeListener { event ->
            minOdds = event.value ?: 3.0
            refreshMatchDisplay(filterCheckbox.value, matchCriteriaCheckbox.value)
        }

        // Vote percentage slider
        val voteSlider = NumberField("Min Vote %")
        voteSlider.value = 70.0
        voteSlider.min = 50.0
        voteSlider.max = 90.0
        voteSlider.step = 5.0
        voteSlider.setWidth("150px")
        voteSlider.isStepButtonsVisible = true
        voteSlider.addValueChangeListener { event ->
            minVotePercent = event.value.toInt()
            refreshMatchDisplay(filterCheckbox.value, matchCriteriaCheckbox.value)
        }

        val slidersLayout = HorizontalLayout(oddsSlider, voteSlider)
        slidersLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END)
        slidersLayout.style.set("gap", "15px")

        // Import JSON button
        val importButton = Button("Import JSON") {
            showImportDialog()
        }
        importButton.style.set("margin-left", "auto")

        val filterLayout = HorizontalLayout(checkboxLayout, slidersLayout, importButton)
        filterLayout.setWidthFull()
        filterLayout.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER)
        filterLayout.style.set("gap", "20px")
        filterLayout.style.set("padding", "10px")

        val topLayout = VerticalLayout(dateNavLayout, filterLayout)
        topLayout.setWidthFull()
        topLayout.style.set("gap", "5px")
        topLayout.style.set("padding", "10px")

        add(topLayout)
        add(contentLayout)
        contentLayout.setSizeFull()

        loadMatches(currentDate)
    }

    private fun addStyleSheet() {
        element.executeJs(
            """
            document.documentElement.setAttribute('theme', 'dark');
            document.body.setAttribute('theme', 'dark');
            const style = document.createElement('style');
            style.textContent = `
                html, body {
                    background-color: #1e1e1e !important;
                    color: #e0e0e0 !important;
                }
                * {
                    color: #e0e0e0 !important;
                }
                label, span, div {
                    color: #e0e0e0 !important;
                }
                vaadin-vertical-layout, vaadin-horizontal-layout {
                    background-color: #1e1e1e !important;
                }
                vaadin-grid {
                    background-color: #2d2d2d !important;
                }
                vaadin-grid::part(header-cell) {
                    background-color: #3a3a3a !important;
                    color: #e0e0e0 !important;
                }
                vaadin-grid::part(cell) {
                    background-color: #2d2d2d !important;
                    color: #e0e0e0 !important;
                }
                vaadin-grid::part(row) {
                    background-color: #2d2d2d !important;
                }
                vaadin-grid-cell-content {
                    color: #e0e0e0 !important;
                }
                vaadin-details {
                    background-color: #1e1e1e !important;
                }
                vaadin-details-summary {
                    background-color: #2d2d2d !important;
                }
                vaadin-details-summary h3 {
                    color: #e0e0e0 !important;
                }
                vaadin-details::part(summary) {
                    background-color: #2d2d2d !important;
                }
                vaadin-details::part(content) {
                    background-color: #1e1e1e !important;
                }
                h3, h4 {
                    color: #e0e0e0 !important;
                }
                vaadin-button {
                    background-color: #3a3a3a !important;
                    color: #e0e0e0 !important;
                    border: 1px solid #555 !important;
                }
                vaadin-button::part(label) {
                    color: #e0e0e0 !important;
                }
                vaadin-button:hover {
                    background-color: #4a4a4a !important;
                }
                vaadin-date-picker,
                vaadin-number-field,
                vaadin-text-area {
                    background-color: #2d2d2d !important;
                }
                vaadin-date-picker::part(input-field),
                vaadin-number-field::part(input-field),
                vaadin-text-area::part(input-field) {
                    background-color: #2d2d2d !important;
                    color: #e0e0e0 !important;
                }
                vaadin-date-picker::part(label),
                vaadin-number-field::part(label),
                vaadin-text-area::part(label) {
                    color: #b0b0b0 !important;
                }
                vaadin-date-picker input,
                vaadin-number-field input,
                vaadin-text-area textarea {
                    background-color: #2d2d2d !important;
                    color: #e0e0e0 !important;
                }
                vaadin-input-container {
                    background-color: #2d2d2d !important;
                }
                vaadin-date-picker-overlay {
                    background-color: #2d2d2d !important;
                }
                vaadin-date-picker-overlay::part(overlay) {
                    background-color: #2d2d2d !important;
                }
                vaadin-date-picker-overlay-content {
                    background-color: #2d2d2d !important;
                }
                vaadin-month-calendar {
                    background-color: #2d2d2d !important;
                    color: #e0e0e0 !important;
                }
                vaadin-month-calendar::part(month-header) {
                    background-color: #3a3a3a !important;
                    color: #e0e0e0 !important;
                }
                vaadin-month-calendar::part(date) {
                    color: #e0e0e0 !important;
                }
                vaadin-month-calendar::part(today) {
                    color: #4fc3f7 !important;
                }
                vaadin-month-calendar::part(weekday) {
                    color: #b0b0b0 !important;
                }
                vaadin-date-picker-year-scroller {
                    background-color: #2d2d2d !important;
                }
                vaadin-date-picker-year {
                    background-color: #2d2d2d !important;
                    color: #e0e0e0 !important;
                }
                vaadin-date-picker-year::part(year-number) {
                    color: #e0e0e0 !important;
                }
                vaadin-date-picker-year div {
                    color: #e0e0e0 !important;
                }
                vaadin-infinite-scroller div {
                    color: #e0e0e0 !important;
                }
                vaadin-checkbox::part(label) {
                    color: #e0e0e0 !important;
                }
                vaadin-dialog-overlay {
                    background-color: rgba(30, 30, 30, 0.95) !important;
                }
                vaadin-dialog-overlay::part(overlay) {
                    background-color: #2d2d2d !important;
                }
                vaadin-dialog-overlay::part(content) {
                    background-color: #2d2d2d !important;
                }
                vaadin-dialog::part(header) {
                    background-color: #3a3a3a !important;
                    color: #e0e0e0 !important;
                }
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

    private fun loadMatches(date: LocalDate = LocalDate.now()) {
        runBlocking {
            allMatches = sofascoreService.getFootballMatchesByDate(date)
            refreshMatchDisplay(filterCheckbox.value, matchCriteriaCheckbox.value)
            logger.info("Loaded and displayed ${allMatches.size} matches for date: $date (filterNotStarted=${filterCheckbox.value}, filterMatchCriteria=${matchCriteriaCheckbox.value})")
        }
    }

    private fun refreshMatchDisplay(filterNotStarted: Boolean, filterMatchCriteria: Boolean) {
        contentLayout.removeAll()

        var matchesToDisplay = allMatches

        if (filterNotStarted) {
            matchesToDisplay = matchesToDisplay.filter { it.status.description == "Not started" }
        }

        if (filterMatchCriteria) {
            matchesToDisplay = matchesToDisplay.filter { shouldHighlight(it) }
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
                refreshMatchDisplay(false, false)

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
