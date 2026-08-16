-- Sofascore withdrew /sport/{sport}/scheduled-events/{date}, so fixtures are now discovered per
-- tournament. tournament_id is season-scoped, while the tournament feeds and Sofascore's own
-- top-competitions list are keyed by the stable unique-tournament id - store it so league
-- discovery can seed itself from previously collected matches.
ALTER TABLE daily_football_match_data
    ADD COLUMN unique_tournament_id BIGINT;

ALTER TABLE daily_handball_match_data
    ADD COLUMN unique_tournament_id BIGINT;
