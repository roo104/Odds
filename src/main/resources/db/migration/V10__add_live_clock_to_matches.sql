-- A live match's clock, recorded with the rest of a refresh. The score alone says little without
-- it - 1-0 at 20 minutes and 1-0 at 88 are different matches - and it is what the Claude match
-- prediction is told about how much is still to play. Null whenever the match was not live at the
-- moment it was last read; last_updated says when that was.
ALTER TABLE daily_football_match_data
    ADD COLUMN live_elapsed_minutes INT,
    ADD COLUMN live_minutes_remaining INT;

ALTER TABLE daily_handball_match_data
    ADD COLUMN live_elapsed_minutes INT,
    ADD COLUMN live_minutes_remaining INT;
