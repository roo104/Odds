import './FilterControls.css';

interface FilterControlsProps {
  filterNotStarted: boolean;
  setFilterNotStarted: (value: boolean) => void;
  filterMatchCriteria: boolean;
  setFilterMatchCriteria: (value: boolean) => void;
  minOdds: number;
  setMinOdds: (value: number) => void;
  minVotePercent: number;
  setMinVotePercent: (value: number) => void;
}

function FilterControls({
  filterNotStarted,
  setFilterNotStarted,
  filterMatchCriteria,
  setFilterMatchCriteria,
  minOdds,
  setMinOdds,
  minVotePercent,
  setMinVotePercent,
}: FilterControlsProps) {
  return (
    <div className="filter-controls">
      <div className="checkbox-group">
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={filterNotStarted}
            onChange={(e) => setFilterNotStarted(e.target.checked)}
          />
          Show only Not Started matches
        </label>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={filterMatchCriteria}
            onChange={(e) => setFilterMatchCriteria(e.target.checked)}
          />
          Show only matches that match criteria
        </label>
      </div>

      <div className="sliders-group">
        <div className="slider-control">
          <label>
            Min Odds: {minOdds.toFixed(1)}
            <input
              type="range"
              min="1.0"
              max="5.0"
              step="0.1"
              value={minOdds}
              onChange={(e) => setMinOdds(parseFloat(e.target.value))}
            />
          </label>
        </div>

        <div className="slider-control">
          <label>
            Min Vote %: {minVotePercent}
            <input
              type="range"
              min="50"
              max="90"
              step="5"
              value={minVotePercent}
              onChange={(e) => setMinVotePercent(parseInt(e.target.value))}
            />
          </label>
        </div>
      </div>
    </div>
  );
}

export default FilterControls;
