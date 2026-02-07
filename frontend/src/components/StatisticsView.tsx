import {useState} from 'react';
import WinningMatchStatistics from './WinningMatchStatistics';
import {footballApi, handballApi} from '../services/api';
import './StatisticsView.css';

function StatisticsView() {
  const [sport, setSport] = useState<'football' | 'handball'>('handball');

  return (
    <div className="statistics-view">
      <div className="statistics-header">
        <h1>Statistics</h1>
        <div className="sport-switcher">
          <button
            type="button"
            className={`sport-button ${sport === 'football' ? 'active' : ''}`}
            onClick={() => setSport('football')}
          >
            Football
          </button>
          <button
            type="button"
            className={`sport-button ${sport === 'handball' ? 'active' : ''}`}
            onClick={() => setSport('handball')}
          >
            Handball
          </button>
        </div>
      </div>

      <div className="statistics-content">
        {sport === 'handball' && <WinningMatchStatistics api={handballApi} />}
        {sport === 'football' && <WinningMatchStatistics api={footballApi} />}
      </div>
    </div>
  );
}

export default StatisticsView;
