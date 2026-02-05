CREATE TABLE IF NOT EXISTS match_bets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    sport VARCHAR(20) NOT NULL,
    selection VARCHAR(10) NOT NULL,
    home_team_name VARCHAR(255) NOT NULL,
    away_team_name VARCHAR(255) NOT NULL,
    start_timestamp BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    INDEX idx_bets_event_id (event_id),
    INDEX idx_bets_created_at (created_at)
);
