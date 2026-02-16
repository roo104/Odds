import {useEffect, useState} from 'react';
import {standingsApi} from '../services/api';
import {StandingRow, StandingsResponse} from '../types';
import {getCountryFlag} from '../utils/countryFlags';
import './StandingsView.css';

interface LeagueStandings {
  name: string;
  standings: StandingsResponse;
}

function StandingsView() {
  const [leagueStandings, setLeagueStandings] = useState<LeagueStandings[]>([]);
  const [loading, setLoading] = useState(false);
  const [availableCountries, setAvailableCountries] = useState<string[]>([]);
  const [selectedCountry, setSelectedCountry] = useState<string | null>(null);
  const [loadingCountries, setLoadingCountries] = useState(false);

  useEffect(() => {
    loadAvailableCountries();
  }, []);

  const loadAvailableCountries = async () => {
    setLoadingCountries(true);
    try {
      const countries = await standingsApi.getAvailableCountries();
      setAvailableCountries(countries);
    } catch (error) {
      console.error('Failed to load available countries:', error);
    } finally {
      setLoadingCountries(false);
    }
  };

  const loadStandingsByCountry = async (country: string) => {
    setLoading(true);
    try {
      const data = await standingsApi.getStandingsByCountry(country);
      const standings: LeagueStandings[] = Object.entries(data).map(([name, standings]) => ({
        name,
        standings,
      }));
      setLeagueStandings(standings);
    } catch (error) {
      console.error('Failed to load standings:', error);
    } finally {
      setLoading(false);
    }
  };

  const selectCountry = async (country: string) => {
    // If clicking the same country, deselect it
    if (selectedCountry === country) {
      setSelectedCountry(null);
      setLeagueStandings([]);
      return;
    }

    // Select new country and load its standings
    setSelectedCountry(country);
    await loadStandingsByCountry(country);
  };

  const renderStandingsTable = (standings: StandingsResponse) => {
    if (!standings || !standings.standings || standings.standings.length === 0) {
      return <div className="no-data">No standings data available</div>;
    }

    return standings.standings.map((standingGroup, groupIndex) => (
      <div key={groupIndex} className="standings-group">
        {standingGroup.name && <h4>{standingGroup.name}</h4>}
        <table className="standings-table">
          <thead>
            <tr>
              <th key="pos" className="pos-column">#</th>
              <th key="team" className="team-column">Team</th>
              <th key="played" className="stat-column">P</th>
              <th key="wins" className="stat-column">W</th>
              <th key="draws" className="stat-column">D</th>
              <th key="losses" className="stat-column">L</th>
              <th key="gf" className="stat-column">GF</th>
              <th key="ga" className="stat-column">GA</th>
              <th key="gd" className="stat-column">GD</th>
              <th key="pts" className="stat-column points-column">Pts</th>
            </tr>
          </thead>
          <tbody>
            {standingGroup.rows?.map((row: StandingRow) => (
              <tr key={`${row.team.id}-${row.position}`} className={row.position <= 4 ? 'champions-league' : ''}>
                <td className="pos-column">{row.position}</td>
                <td className="team-column">
                  <div className="team-info">
                    <img
                      src={`https://api.sofascore.com/api/v1/team/${row.team.id}/image`}
                      alt={row.team.name}
                      className="team-logo"
                      onError={(e) => {
                        (e.target as HTMLImageElement).style.display = 'none';
                      }}
                    />
                    <span className="team-name">{row.team.name}</span>
                  </div>
                </td>
                <td className="stat-column">{row.matches}</td>
                <td className="stat-column">{row.wins}</td>
                <td className="stat-column">{row.draws}</td>
                <td className="stat-column">{row.losses}</td>
                <td className="stat-column">{row.scoresFor}</td>
                <td className="stat-column">{row.scoresAgainst}</td>
                <td className="stat-column">{row.scoresFor - row.scoresAgainst}</td>
                <td className="stat-column points-column">{row.points}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    ));
  };

  return (
    <div className="standings-view">
      <div className="standings-header">
        <h2>League Standings</h2>
        <button className="refresh-button" onClick={loadAvailableCountries} disabled={loadingCountries}>
          {loadingCountries ? 'Loading...' : 'Refresh'}
        </button>
      </div>

      {loadingCountries ? (
        <div className="loading">Loading countries...</div>
      ) : availableCountries.length > 0 ? (
        <>
          <div className="country-filter">
            <h3>Select a Country:</h3>
            <div className="country-buttons">
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

          {loading ? (
            <div className="loading">Loading standings...</div>
          ) : !selectedCountry ? (
            <div className="no-data">Please select a country to view standings</div>
          ) : (
            <div className="leagues-container">
              {leagueStandings.map((league) => (
                <details key={league.name} className="league-section" open>
                  <summary>
                    <h3>{league.name}</h3>
                  </summary>
                  <div className="standings-content">
                    {renderStandingsTable(league.standings)}
                  </div>
                </details>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="no-data">No countries available</div>
      )}
    </div>
  );
}

export default StandingsView;
