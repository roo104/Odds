import {useEffect, useMemo, useState} from 'react';
import {footballApi, handballApi, ProfitabilityResponse, standingsApi} from '../services/api';
import {getCountryFlag} from '../utils/countryFlags';
import './StatisticsView.css';

function StatisticsView() {
  const [sport, setSport] = useState<'football' | 'handball'>('football');
  const [selectedCountry, setSelectedCountry] = useState<string>('');
  const [topLeaguesOnly, setTopLeaguesOnly] = useState<boolean>(false);
  const [profitability, setProfitability] = useState<ProfitabilityResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [availableCountries, setAvailableCountries] = useState<string[]>([]);
  const [loadingCountries, setLoadingCountries] = useState(false);
  const [voteThreshold, setVoteThreshold] = useState<number>(50);

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

  const calculateThresholdStats = (matches: ProfitabilityResponse['matches'], threshold: number) => {
    if (!matches || matches.length === 0) return null;
    const filtered = matches.filter(m => m.favoriteVote != null && m.favoriteVote >= threshold);
    if (filtered.length === 0) return { matches: 0, favoriteWins: 0, avgOdds: null, roi: null };
    const totalStaked = filtered.length;
    const wins = filtered.filter(m => m.favoriteWon === true);
    const totalReturn = wins.reduce((sum, m) => sum + (m.favoriteOdds ?? 0), 0);
    const roi = ((totalReturn - totalStaked) / totalStaked) * 100;
    const avgOdds = wins.length > 0 ? wins.reduce((sum, m) => sum + (m.favoriteOdds ?? 0), 0) / wins.length : null;
    return { matches: totalStaked, favoriteWins: wins.length, avgOdds, roi };
  };

  const sliderStats = profitability?.matches ? calculateThresholdStats(profitability.matches, voteThreshold) : null;

  const overallStats = useMemo(() => {
    const matches = profitability?.matches;
    if (!matches || matches.length === 0) return null;
    const nonDraws = matches.filter(m => m.homeScore !== m.awayScore);
    const withOdds = nonDraws.filter(m => {
      const winnerOdds = m.homeScore > m.awayScore ? m.oddsHome : m.oddsAway;
      const winnerVote = m.homeScore > m.awayScore ? m.votingHome : m.votingAway;
      return winnerOdds != null && winnerVote != null;
    });
    if (withOdds.length === 0) return { averageVote: 0, averageOdds: 0, totalMatches: matches.length };
    const avgVote = withOdds.reduce((sum, m) => sum + (m.homeScore > m.awayScore ? m.votingHome! : m.votingAway!), 0) / withOdds.length;
    const avgOdds = withOdds.reduce((sum, m) => sum + (m.homeScore > m.awayScore ? m.oddsHome! : m.oddsAway!), 0) / withOdds.length;
    return { averageVote: avgVote, averageOdds: avgOdds, totalMatches: matches.length };
  }, [profitability?.matches]);

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

        {!loading && !error && profitability && overallStats && (
          <>
            <div className="overall-stats">
              <h2>
                Overall Statistics
                {selectedCountry && <span className="filter-badge"> - {selectedCountry}</span>}
              </h2>
              <div className="stats-grid">
                <div className="stat-card">
                  <span className="stat-label">Average Vote</span>
                  <span className="stat-value">{overallStats.averageVote.toFixed(1)}%</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Average Odds</span>
                  <span className="stat-value">{overallStats.averageOdds.toFixed(2)}</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Break-even %</span>
                  <span className="stat-value">{overallStats.averageOdds > 0 ? ((1 / overallStats.averageOdds) * 100).toFixed(1) : '0'}%</span>
                </div>
                <div className="stat-card">
                  <span className="stat-label">Total Matches</span>
                  <span className="stat-value">{overallStats.totalMatches}</span>
                </div>
              </div>
            </div>

            {profitability && (
              <div className="league-stats">
                <h2>Profitable Vote Thresholds</h2>
                <p className="section-description">
                  Minimum crowd-favorite vote % needed to achieve 10% ROI when betting on the favorite.
                </p>
                {profitability.overall && (
                  <div className="profitability-overall">
                    <h3>Overall</h3>
                    <div className="profitability-grid">
                      <div className="profitability-item">
                        <span className="profitability-label">Min Threshold</span>
                        <span className="profitability-value">
                          {profitability.overall.minVoteThreshold != null
                            ? `${profitability.overall.minVoteThreshold}%`
                            : 'N/A'}
                        </span>
                      </div>
                      <div className="profitability-item">
                        <span className="profitability-label">Matches</span>
                        <span className="profitability-value">{profitability.overall.totalMatches}</span>
                      </div>
                      <div className="profitability-item">
                        <span className="profitability-label">Fav Wins</span>
                        <span className="profitability-value">
                          {profitability.overall.favoriteWins}/{profitability.overall.totalMatches}
                          {' '}({profitability.overall.totalMatches > 0 ? ((profitability.overall.favoriteWins / profitability.overall.totalMatches) * 100).toFixed(1) : '0'}%)
                        </span>
                      </div>
                      <div className="profitability-item">
                        <span className="profitability-label">Avg Odds (Fav Wins)</span>
                        <span className="profitability-value">
                          {profitability.overall.averageFavoriteWinOdds != null
                            ? profitability.overall.averageFavoriteWinOdds.toFixed(2)
                            : 'N/A'}
                        </span>
                      </div>
                      <div className="profitability-item">
                        <span className="profitability-label">Above Threshold</span>
                        <span className="profitability-value">{profitability.overall.matchesAboveThreshold}</span>
                      </div>
                      <div className="profitability-item">
                        <span className="profitability-label">ROI</span>
                        <span className={`profitability-value ${profitability.overall.roi != null && profitability.overall.roi >= 10 ? 'profitable' : ''}`}>
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
                      <div className="profitability-grid">
                        <div className="profitability-item">
                          <span className="profitability-label">Min Threshold</span>
                          <span className="profitability-value">
                            {league.minVoteThreshold != null ? `${league.minVoteThreshold}%` : 'N/A'}
                          </span>
                        </div>
                        <div className="profitability-item">
                          <span className="profitability-label">Matches</span>
                          <span className="profitability-value">{league.totalMatches}</span>
                        </div>
                        <div className="profitability-item">
                          <span className="profitability-label">Fav Wins</span>
                          <span className="profitability-value">
                            {league.favoriteWins}/{league.totalMatches}
                            {' '}({league.totalMatches > 0 ? ((league.favoriteWins / league.totalMatches) * 100).toFixed(1) : '0'}%)
                          </span>
                        </div>
                        <div className="profitability-item">
                          <span className="profitability-label">Avg Odds</span>
                          <span className="profitability-value">
                            {league.averageFavoriteWinOdds != null
                              ? league.averageFavoriteWinOdds.toFixed(2)
                              : 'N/A'}
                          </span>
                        </div>
                        <div className="profitability-item">
                          <span className="profitability-label">Above Thr.</span>
                          <span className="profitability-value">{league.matchesAboveThreshold}</span>
                        </div>
                        <div className="profitability-item">
                          <span className="profitability-label">ROI</span>
                          <span className={`profitability-value ${league.roi != null && league.roi >= 10 ? 'profitable' : ''}`}>
                            {league.roi != null ? `${league.roi.toFixed(1)}%` : 'N/A'}
                          </span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {profitability.matches && profitability.matches.length > 0 && (
                  <div className="threshold-slider-section">
                    <h3>Custom Vote Threshold</h3>
                    <div className="slider-container">
                      <button
                        type="button"
                        className="slider-btn"
                        onClick={() => setVoteThreshold(Math.max(30, voteThreshold - 1))}
                      >-</button>
                      <input
                        type="range"
                        min={30}
                        max={99}
                        value={voteThreshold}
                        onChange={(e) => setVoteThreshold(Number(e.target.value))}
                        className="threshold-slider"
                      />
                      <button
                        type="button"
                        className="slider-btn"
                        onClick={() => setVoteThreshold(Math.min(99, voteThreshold + 1))}
                      >+</button>
                      <span className="slider-value">{voteThreshold}%</span>
                    </div>
                    {sliderStats && (
                      <div className="profitability-grid" style={{marginTop: '12px'}}>
                        <div className="profitability-item">
                          <span className="profitability-label">Matches</span>
                          <span className="profitability-value">{sliderStats.matches}</span>
                        </div>
                        <div className="profitability-item">
                          <span className="profitability-label">Fav Wins</span>
                          <span className="profitability-value">
                            {sliderStats.favoriteWins}/{sliderStats.matches}
                            {' '}({sliderStats.matches > 0 ? ((sliderStats.favoriteWins / sliderStats.matches) * 100).toFixed(1) : '0'}%)
                          </span>
                        </div>
                        <div className="profitability-item">
                          <span className="profitability-label">Avg Odds (Fav Wins)</span>
                          <span className="profitability-value">
                            {sliderStats.avgOdds != null ? sliderStats.avgOdds.toFixed(2) : 'N/A'}
                          </span>
                        </div>
                        <div className="profitability-item">
                          <span className="profitability-label">ROI</span>
                          <span className={`profitability-value ${sliderStats.roi != null && sliderStats.roi >= 10 ? 'profitable' : ''}`}>
                            {sliderStats.roi != null ? `${sliderStats.roi.toFixed(1)}%` : 'N/A'}
                          </span>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {profitability.matches && profitability.matches.length > 0 && (
                  <div className="betting-matches-section">
                    <h3>Matches ({profitability.matches.filter(m => m.favoriteVote != null && m.favoriteVote >= voteThreshold).length})</h3>
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
                          {profitability.matches.filter(m => m.favoriteVote != null && m.favoriteVote >= voteThreshold).map((match, index) => {
                            const isDraw = match.homeScore === match.awayScore;
                            const homeIsWinner = match.homeScore > match.awayScore;
                            const awayIsWinner = match.awayScore > match.homeScore;
                            const homeClass = isDraw ? 'draw-result' : homeIsWinner ? 'winner' : 'loser';
                            const awayClass = isDraw ? 'draw-result' : awayIsWinner ? 'winner' : 'loser';
                            const maxVote = Math.max(match.votingHome ?? 0, match.votingDraw ?? 0, match.votingAway ?? 0);
                            const favoriteIs = match.votingHome === maxVote ? 'home' : match.votingDraw === maxVote ? 'draw' : 'away';
                            return (
                              <tr key={index} className={isDraw ? 'draw-row' : ''}>
                                <td className={homeClass}>{match.homeTeamName}</td>
                                <td className={awayClass}>{match.awayTeamName}</td>
                                <td>{match.homeScore} - {match.awayScore}</td>
                                <td className={favoriteIs === 'home' ? 'highest-vote' : ''}>{match.oddsHome?.toFixed(2) ?? '-'}</td>
                                <td className={favoriteIs === 'draw' ? 'highest-vote' : ''}>{match.oddsDraw?.toFixed(2) ?? '-'}</td>
                                <td className={favoriteIs === 'away' ? 'highest-vote' : ''}>{match.oddsAway?.toFixed(2) ?? '-'}</td>
                                <td className={favoriteIs === 'home' ? 'highest-vote' : ''}>{match.votingHome != null ? `${match.votingHome}%` : '-'}</td>
                                <td className={favoriteIs === 'draw' ? 'highest-vote' : ''}>{match.votingDraw != null ? `${match.votingDraw}%` : '-'}</td>
                                <td className={favoriteIs === 'away' ? 'highest-vote' : ''}>{match.votingAway != null ? `${match.votingAway}%` : '-'}</td>
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
