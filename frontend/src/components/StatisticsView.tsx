import {useEffect, useState} from 'react';
import {footballApi, handballApi, ProfitabilityResponse} from '../services/api';
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
  const [profitability, setProfitability] = useState<ProfitabilityResponse | null>(null);
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

      if (api.getProfitableThresholds) {
        const profitData = await api.getProfitableThresholds(
          selectedCountry || undefined,
          sport === 'football' ? topLeaguesOnly : undefined
        );
        setProfitability(profitData);
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

            {profitability && (
              <div className="league-stats">
                <h2>Profitable Vote Thresholds</h2>
                <p className="section-description">
                  Minimum crowd-favorite vote % needed to achieve 10% ROI when betting on the favorite.
                </p>
                {profitability.overall && (
                  <div className="overall-stats" style={{marginBottom: '1rem'}}>
                    <h3>Overall</h3>
                    <div className="league-stats-grid">
                      <div className="league-stat">
                        <span className="league-stat-label">Min Threshold:</span>
                        <span className="league-stat-value">
                          {profitability.overall.minVoteThreshold != null
                            ? `${profitability.overall.minVoteThreshold}%`
                            : 'N/A'}
                        </span>
                      </div>
                      <div className="league-stat">
                        <span className="league-stat-label">Matches:</span>
                        <span className="league-stat-value">{profitability.overall.totalMatches}</span>
                      </div>
                      <div className="league-stat">
                        <span className="league-stat-label">Above Threshold:</span>
                        <span className="league-stat-value">{profitability.overall.matchesAboveThreshold}</span>
                      </div>
                      <div className="league-stat">
                        <span className="league-stat-label">ROI:</span>
                        <span className={`league-stat-value ${profitability.overall.roi != null && profitability.overall.roi >= 10 ? 'profitable' : ''}`}>
                          {profitability.overall.roi != null ? `${profitability.overall.roi.toFixed(1)}%` : 'N/A'}
                        </span>
                      </div>
                    </div>
                  </div>
                )}
                <div className="league-list">
                  {profitability.byLeague.map((league) => (
                    <div key={league.tournamentId} className="league-card">
                      <h3 className="league-name">{league.tournamentName}</h3>
                      <div className="league-stats-grid">
                        <div className="league-stat">
                          <span className="league-stat-label">Min Threshold:</span>
                          <span className="league-stat-value">
                            {league.minVoteThreshold != null ? `${league.minVoteThreshold}%` : 'N/A'}
                          </span>
                        </div>
                        <div className="league-stat">
                          <span className="league-stat-label">Matches:</span>
                          <span className="league-stat-value">{league.totalMatches}</span>
                        </div>
                        <div className="league-stat">
                          <span className="league-stat-label">Above Threshold:</span>
                          <span className="league-stat-value">{league.matchesAboveThreshold}</span>
                        </div>
                        <div className="league-stat">
                          <span className="league-stat-label">ROI:</span>
                          <span className={`league-stat-value ${league.roi != null && league.roi >= 10 ? 'profitable' : ''}`}>
                            {league.roi != null ? `${league.roi.toFixed(1)}%` : 'N/A'}
                          </span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {profitability.matches && profitability.matches.length > 0 && (
                  <div className="betting-matches-section">
                    <h3>Matches Used in Calculation</h3>
                    <div className="betting-matches-table-container">
                      <table className="betting-matches-table">
                        <thead>
                          <tr>
                            <th>Home</th>
                            <th>Away</th>
                            <th>Score</th>
                            <th>H Odds</th>
                            <th>D Odds</th>
                            <th>A Odds</th>
                            <th>H Vote</th>
                            <th>D Vote</th>
                            <th>A Vote</th>
                            <th>Fav Won</th>
                            <th>League</th>
                          </tr>
                        </thead>
                        <tbody>
                          {profitability.matches.map((match, index) => {
                            const isDraw = match.homeScore === match.awayScore;
                            const homeIsWinner = match.homeScore > match.awayScore;
                            const awayIsWinner = match.awayScore > match.homeScore;
                            const homeClass = isDraw ? 'draw-result' : homeIsWinner ? 'winner' : 'loser';
                            const awayClass = isDraw ? 'draw-result' : awayIsWinner ? 'winner' : 'loser';
                            return (
                              <tr key={index} className={isDraw ? 'draw-row' : ''}>
                                <td className={homeClass}>{match.homeTeamName}</td>
                                <td className={awayClass}>{match.awayTeamName}</td>
                                <td>{match.homeScore} - {match.awayScore}</td>
                                <td>{match.oddsHome?.toFixed(2) ?? '-'}</td>
                                <td>{match.oddsDraw?.toFixed(2) ?? '-'}</td>
                                <td>{match.oddsAway?.toFixed(2) ?? '-'}</td>
                                <td>{match.votingHome != null ? `${match.votingHome}%` : '-'}</td>
                                <td>{match.votingDraw != null ? `${match.votingDraw}%` : '-'}</td>
                                <td>{match.votingAway != null ? `${match.votingAway}%` : '-'}</td>
                                <td className={match.favoriteWon === null ? '' : match.favoriteWon ? 'profitable' : 'loss'}>
                                  {match.favoriteWon === null ? '-' : match.favoriteWon ? 'Yes' : 'No'}
                                </td>
                                <td>{match.tournamentName}</td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

export default StatisticsView;
