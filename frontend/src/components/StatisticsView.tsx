import {useEffect, useState} from 'react';
import {footballApi, handballApi} from '../services/api';
import './StatisticsView.css';

interface LeagueStatistics {
  tournamentId: number;
  tournamentName: string;
  averageVote: number;
  averageOdds: number;
  totalMatches: number;
}

interface WinningMatchStatisticsByLeague {
  overall: {
    averageVote: number;
    averageOdds: number;
    totalMatches: number;
  };
  byLeague: LeagueStatistics[];
}

function StatisticsView() {
  const [sport, setSport] = useState<'football' | 'handball'>('football');
  const [selectedCountry, setSelectedCountry] = useState<string>('');
  const [topLeaguesOnly, setTopLeaguesOnly] = useState<boolean>(false);
  const [statistics, setStatistics] = useState<WinningMatchStatisticsByLeague | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [, setAvailableCountries] = useState<string[]>([]);

  useEffect(() => {
    loadStatistics();
  }, [sport, selectedCountry, topLeaguesOnly]);

  const loadStatistics = async () => {
    setLoading(true);
    setError(null);
    try {
      const api = sport === 'handball' ? handballApi : footballApi;
      if (api.getWinningMatchStatisticsByLeague) {
        const data = await api.getWinningMatchStatisticsByLeague(
          selectedCountry || undefined,
          sport === 'football' ? topLeaguesOnly : undefined
        );
        setStatistics(data);

        // Extract unique countries from league data
        const countries = new Set<string>();
        data.byLeague.forEach(league => {
          // Try to extract country from tournament name (this is a heuristic)
          // In a real scenario, you might want to fetch available countries from backend
          const parts = league.tournamentName.split(' - ');
          if (parts.length > 1) {
            countries.add(parts[0]);
          }
        });
        setAvailableCountries(Array.from(countries).sort());
      }
    } catch (err) {
      console.error('Failed to load statistics:', err);
      setError('Failed to load statistics');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="statistics-view">
      <div className="statistics-header">
        <h1>Statistics</h1>
        <div className="controls-row">
          <div className="sport-switcher">
            <button
              type="button"
              className={`sport-button ${sport === 'football' ? 'active' : ''}`}
              onClick={() => {
                setSport('football');
                setSelectedCountry('');
              }}
            >
              Football
            </button>
            <button
              type="button"
              className={`sport-button ${sport === 'handball' ? 'active' : ''}`}
              onClick={() => {
                setSport('handball');
                setSelectedCountry('');
              }}
            >
              Handball
            </button>
          </div>

          <div className="country-filter">
            <label htmlFor="country-select">Filter by Country:</label>
            <select
              id="country-select"
              value={selectedCountry}
              onChange={(e) => setSelectedCountry(e.target.value)}
              className="country-select"
            >
              <option value="">All Countries</option>
              <option value="Argentina">Argentina</option>
              <option value="Austria">Austria</option>
              <option value="Belgium">Belgium</option>
              <option value="Brazil">Brazil</option>
              <option value="Croatia">Croatia</option>
              <option value="Czech Republic">Czech Republic</option>
              <option value="Denmark">Denmark</option>
              <option value="England">England</option>
              <option value="France">France</option>
              <option value="Germany">Germany</option>
              <option value="Greece">Greece</option>
              <option value="Italy">Italy</option>
              <option value="Mexico">Mexico</option>
              <option value="Netherlands">Netherlands</option>
              <option value="Norway">Norway</option>
              <option value="Poland">Poland</option>
              <option value="Portugal">Portugal</option>
              <option value="Scotland">Scotland</option>
              <option value="Serbia">Serbia</option>
              <option value="Spain">Spain</option>
              <option value="Sweden">Sweden</option>
              <option value="Switzerland">Switzerland</option>
              <option value="Turkey">Turkey</option>
              <option value="USA">USA</option>
            </select>
          </div>

          {sport === 'football' && (
            <div className="top-leagues-filter">
              <label>
                <input
                  type="checkbox"
                  checked={topLeaguesOnly}
                  onChange={(e) => setTopLeaguesOnly(e.target.checked)}
                />
                Top Leagues Only
              </label>
            </div>
          )}
        </div>
      </div>

      <div className="statistics-content">
        {loading && <div className="loading">Loading statistics...</div>}
        {error && <div className="error">{error}</div>}

        {!loading && !error && statistics && (
          <>
            <div className="overall-stats">
              <h2>
                Overall Statistics
                {selectedCountry && <span className="filter-badge"> - {selectedCountry}</span>}
              </h2>
              <div className="stats-grid">
                <div className="stat-card">
                  <span className="stat-label">Average Vote</span>
                  <span className="stat-value">{statistics.overall.averageVote.toFixed(1)}%</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Average Odds</span>
                  <span className="stat-value">{statistics.overall.averageOdds.toFixed(2)}</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Break-even %</span>
                  <span className="stat-value">{((1 / statistics.overall.averageOdds) * 100).toFixed(1)}%</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Total Matches</span>
                  <span className="stat-value">{statistics.overall.totalMatches}</span>
                </div>
              </div>
            </div>

            <div className="league-stats">
              <h2>Statistics by League</h2>
              <div className="league-list">
                {statistics.byLeague.map((league) => (
                  <div key={league.tournamentId} className="league-card">
                    <h3 className="league-name">{league.tournamentName}</h3>
                    <div className="league-stats-grid">
                      <div className="league-stat">
                        <span className="league-stat-label">Avg Vote:</span>
                        <span className="league-stat-value">{league.averageVote.toFixed(1)}%</span>
                      </div>
                      <div className="league-stat">
                        <span className="league-stat-label">Avg Odds:</span>
                        <span className="league-stat-value">{league.averageOdds.toFixed(2)}</span>
                      </div>
                      <div className="league-stat">
                        <span className="league-stat-label">Break-even %:</span>
                        <span className="league-stat-value">{((1 / league.averageOdds) * 100).toFixed(1)}%</span>
                      </div>
                      <div className="league-stat">
                        <span className="league-stat-label">Matches:</span>
                        <span className="league-stat-value">{league.totalMatches}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default StatisticsView;
