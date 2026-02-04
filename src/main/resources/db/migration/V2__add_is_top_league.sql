-- Add is_top_league column to daily_match_data table
ALTER TABLE daily_match_data ADD COLUMN is_top_league BOOLEAN NOT NULL DEFAULT TRUE;
