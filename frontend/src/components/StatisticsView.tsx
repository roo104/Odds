import {useEffect, useState} from 'react';
import {footballApi, handballApi, ProfitabilityResponse, standingsApi} from '../services/api';
import {getCountryFlag} from '../utils/countryFlags';
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
  const [availableCountries, setAvailableCountries] = useState<string[]>([]);
  const [loadingCountries, setLoadingCountries] = useState(false);

  useEffect(() => {
    loadAvailableCountries();
  }, []);

  useEffect(() => {
    if (selectedCountry) {
      loadStatistics();
    }
  }, [sport, selectedCountry, topLeaguesOnly]);

  const loadAvailableCountries = async () => {
    setLoadingCountries(true);
    try {
      const countries = await standingsApi.getAvailableCountries();
      setAvailableCountries(countries);
    } catch (err) {
      console.error('Failed to load available countries:', err);
    } finally {
      setLoadingCountries(false);
    }
  };

  const selectCountry = (country: string) => {
    if (selectedCountry === country) {
      setSelectedCountry('');
      setStatistics(null);
      setProfitability(null);
      return;
    }
    setSelectedCountry(country);
  };

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
                setStatistics(null);
                setProfitability(null);
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
                setStatistics(null);
                setProfitability(null);
              }}
            >
              Handball
            </button>
          </div>

          {selectedCountry && sport === 'football' && (
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

      {loadingCountries ? (
        <div className="loading">Loading countries...</div>
      ) : availableCountries.length > 0 ? (
        <div className="statistics-country-filter">
          <h3>Select a Country:</h3>
          <div className="statistics-country-buttons">
            {availableCountries.map((country) => (
              <button
                key={country}
                className={selectedCountry === country ? 'active' : ''}
                onClick={() => selectCountry(country)}
                disabled={loading}
              >
                <span className="flag">{getCountryFlag(country)}</span>
                {country}
              </button>
            ))}
          </div>
        </div>
      ) : (
        <div className="no-data">No countries available</div>
      )}

      <div className="statistics-content">
        {loading && <div className="loading">Loading statistics...</div>}
        {error && <div className="error">{error}</div>}

        {!loading && !error && !selectedCountry && (
          <div className="no-data">Please select a country to view statistics</div>
        )}

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
                        <span className="league-stat-label">Favorite Wins:</span>
                        <span className="league-stat-value">
                          {profitability.overall.favoriteWins} / {profitability.overall.totalMatches}
                          {' '}({profitability.overall.totalMatches > 0 ? ((profitability.overall.favoriteWins / profitability.overall.totalMatches) * 100).toFixed(1) : '0'}%)
                        </span>
                      </div>
                      <div className="league-stat">
                        <span className="league-stat-label">Avg Odds (Fav Wins):</span>
                        <span className="league-stat-value">
                          {profitability.overall.averageFavoriteWinOdds != null
                            ? profitability.overall.averageFavoriteWinOdds.toFixed(2)
                            : 'N/A'}
                        </span>
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
                          <span className="league-stat-label">Favorite Wins:</span>
                          <span className="league-stat-value">
                            {league.favoriteWins} / {league.totalMatches}
                            {' '}({league.totalMatches > 0 ? ((league.favoriteWins / league.totalMatches) * 100).toFixed(1) : '0'}%)
                          </span>
                        </div>
                        <div className="league-stat">
                          <span className="league-stat-label">Avg Odds (Fav Wins):</span>
                          <span className="league-stat-value">
                            {league.averageFavoriteWinOdds != null
                              ? league.averageFavoriteWinOdds.toFixed(2)
                              : 'N/A'}
                          </span>
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
