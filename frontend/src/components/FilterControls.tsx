import './FilterControls.css';

interface FilterControlsProps {
  filterNotStarted: boolean;
  setFilterNotStarted: (value: boolean) => void;
  filterLive: boolean;
  setFilterLive: (value: boolean) => void;
  filterMatchCriteria: boolean;
  setFilterMatchCriteria: (value: boolean) => void;
  minOdds: number;
  setMinOdds: (value: number) => void;
  minVotePercent: number;
  setMinVotePercent: (value: number) => void;
  filterTopLeaguesOnly?: boolean;
  setFilterTopLeaguesOnly?: (value: boolean) => void;
}

function FilterControls({
  filterNotStarted,
  setFilterNotStarted,
  filterLive,
  setFilterLive,
  filterMatchCriteria,
  setFilterMatchCriteria,
  minOdds,
  setMinOdds,
  minVotePercent,
  setMinVotePercent,
  filterTopLeaguesOnly,
  setFilterTopLeaguesOnly,
}: FilterControlsProps) {
  return (
    <div className="filter-controls">
      <div className="checkbox-group">
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={filterNotStarted}
            onChange={(e) => {
              setFilterNotStarted(e.target.checked);
              // Nothing is both not started and live, so the two never apply together
              if (e.target.checked) setFilterLive(false);
            }}
          />
          Show only not started matches
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={filterLive}
            onChange={(e) => {
              setFilterLive(e.target.checked);
              if (e.target.checked) setFilterNotStarted(false);
            }}
          />
          Show only live matches
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={filterMatchCriteria}
            onChange={(e) => setFilterMatchCriteria(e.target.checked)}
          />
          Show only matches that match criteria
        </label>
        {filterTopLeaguesOnly !== undefined && setFilterTopLeaguesOnly && (
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={filterTopLeaguesOnly}
              onChange={(e) => setFilterTopLeaguesOnly(e.target.checked)}
            />
            Show only top leagues
          </label>
        )}
      </div>

      <div className="sliders-group">
        <div className="slider-control">
          <label>Min Odds: {minOdds.toFixed(1)}</label>
          <div className="slider-with-buttons">
            <button
              className="slider-button"
              onClick={() => setMinOdds(Math.max(1.0, minOdds - 0.1))}
            >
              -
            </button>
            <input
              type="range"
              min="1.0"
              max="5.0"
              step="0.1"
              value={minOdds}
              onChange={(e) => setMinOdds(parseFloat(e.target.value))}
            />
            <button
              className="slider-button"
              onClick={() => setMinOdds(Math.min(5.0, minOdds + 0.1))}
            >
              +
            </button>
          </div>
        </div>

        <div className="slider-control">
          <label>Min Vote %: {minVotePercent}</label>
          <div className="slider-with-buttons">
            <button
              className="slider-button"
              onClick={() => setMinVotePercent(Math.max(50, minVotePercent - 5))}
            >
              -
            </button>
            <input
              type="range"
              min="50"
              max="90"
              step="5"
              value={minVotePercent}
              onChange={(e) => setMinVotePercent(parseInt(e.target.value))}
            />
            <button
              className="slider-button"
              onClick={() => setMinVotePercent(Math.min(90, minVotePercent + 5))}
            >
              +
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default FilterControls;
