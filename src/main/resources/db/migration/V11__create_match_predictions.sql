-- Every Claude prediction that has been run, one row per run. The latest row for a match is what
-- the matches table shows on hover, so the numbers Claude gave survive closing the dialog; the
-- older rows keep the trail of how the call moved as the odds, the vote and the clock moved.
--
-- Odds are the decimal prices the prediction was made against, kept alongside Claude's own
-- probabilities so a later reading can be compared with the market it disagreed with.
CREATE TABLE IF NOT EXISTS match_predictions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    sport VARCHAR(20) NOT NULL,
    start_timestamp BIGINT NOT NULL,
    home_team_name VARCHAR(255) NOT NULL,
    away_team_name VARCHAR(255) NOT NULL,
    status_description VARCHAR(255) NOT NULL,
    was_live BOOLEAN NOT NULL,
    had_statistics BOOLEAN NOT NULL,
    -- Claude's own percentages, 0-100. Null when the answer came back without the closing
    -- machine-readable line - the prose is still worth keeping.
    probability_home DOUBLE,
    probability_draw DOUBLE,
    probability_away DOUBLE,
    -- HOME, DRAW or AWAY: whichever probability is highest.
    predicted_outcome VARCHAR(10),
    odds_home DOUBLE,
    odds_draw DOUBLE,
    odds_away DOUBLE,
    home_score INT,
    away_score INT,
    prediction TEXT NOT NULL,
    provider VARCHAR(10) NOT NULL,
    model VARCHAR(100),
    duration_ms BIGINT NOT NULL,
    cost_usd DOUBLE,
    created_at TIMESTAMP(6) NOT NULL,
    INDEX idx_prediction_event (event_id, created_at),
    INDEX idx_prediction_sport_start (sport, start_timestamp)
);
