import {useEffect, useState} from 'react';
import {standingsApi} from '../services/api';
import {StandingsResponse} from '../types';
import './StandingsView.css';

interface LeagueStandings {
  name: string;
  standings: StandingsResponse | null;
}

function StandingsView() {
  const [leagueStandings, setLeagueStandings] = useState<LeagueStandings[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadStandings();
  }, []);

  const loadStandings = async () => {
    setLoading(true);
    try {
      const data = await standingsApi.getMajorLeaguesStandings();
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

  const renderStandingsTable = (standings: StandingsResponse | null) => {
    if (!standings || !standings.standings || standings.standings.length === 0) {
      return <div className="no-data">No standings data available</div>;
    }

    return standings.standings.map((standingGroup, groupIndex) => (
      <div key={groupIndex} className="standings-group">
        {standingGroup.name && <h4>{standingGroup.name}</h4>}
        <table className="standings-table">
          <thead>
            <tr>
              <th className="pos-column">#</th>
              <th className="team-column">Team</th>
              <th className="stat-column">P</th>
              <th className="stat-column">W</th>
              <th className="stat-column">D</th>
              <th className="stat-column">L</th>
              <th className="stat-column">GF</th>
              <th className="stat-column">GA</th>
              <th className="stat-column">GD</th>
              <th className="stat-column points-column">Pts</th>
            </tr>
          </thead>
          <tbody>
            {standingGroup.rows?.map((row) => (
              <tr key={row.id} className={row.position <= 4 ? 'champions-league' : ''}>
                <td className="pos-column">{row.position}</td>
                <td className="team-column">
                  <div className="team-info">
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
        <h2>Major European Leagues</h2>
        <button className="refresh-button" onClick={loadStandings} disabled={loading}>
          {loading ? 'Loading...' : 'Refresh'}
        </button>
      </div>

      {loading && leagueStandings.length === 0 ? (
        <div className="loading">Loading standings...</div>
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
    </div>
  );
}

export default StandingsView;
