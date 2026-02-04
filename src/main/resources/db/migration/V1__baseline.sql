-- Baseline migration for existing schema
-- Generated from existing JPA entities

CREATE TABLE IF NOT EXISTS daily_match_data (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    match_date DATE NOT NULL,
    event_id BIGINT NOT NULL,
    start_timestamp BIGINT NOT NULL,
    home_team_id BIGINT NOT NULL,
    home_team_name VARCHAR(255) NOT NULL,
    away_team_id BIGINT NOT NULL,
    away_team_name VARCHAR(255) NOT NULL,
    tournament_id BIGINT NOT NULL,
    tournament_name VARCHAR(255) NOT NULL,
    category_name VARCHAR(255) NOT NULL,
    country_name VARCHAR(255),
    odds_home VARCHAR(255),
    odds_draw VARCHAR(255),
    odds_away VARCHAR(255),
    voting_home INT,
    voting_draw INT,
    voting_away INT,
    voting_total INT,
    status_type VARCHAR(255) NOT NULL,
    status_description VARCHAR(255) NOT NULL,
    home_score INT,
    away_score INT,
    last_updated TIMESTAMP(6),
    is_top_league BOOLEAN NOT NULL,
    CONSTRAINT uk_event_id UNIQUE (event_id)
);

CREATE TABLE IF NOT EXISTS match_odds_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    odds_home VARCHAR(255),
    odds_draw VARCHAR(255),
    odds_away VARCHAR(255),
    recorded_at TIMESTAMP(6) NOT NULL,
    INDEX idx_odds_event_id (event_id),
    INDEX idx_odds_recorded_at (recorded_at)
);

CREATE TABLE IF NOT EXISTS match_votes_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    voting_home INT,
    voting_draw INT,
    voting_away INT,
    voting_total INT,
    recorded_at TIMESTAMP(6) NOT NULL,
    INDEX idx_votes_event_id (event_id),
    INDEX idx_votes_recorded_at (recorded_at)
);
