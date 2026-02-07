import {useEffect, useState} from 'react';
import './WinningMatchStatistics.css';

interface WinningMatchStatisticsProps {
  api: {
    getWinningMatchStatistics?: () => Promise<{
      averageVote: number;
      averageOdds: number;
      totalMatches: number;
    }>;
  };
}

function WinningMatchStatistics({ api }: WinningMatchStatisticsProps) {
  const [statistics, setStatistics] = useState<{
    averageVote: number;
    averageOdds: number;
    totalMatches: number;
  } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadStatistics();
  }, []);

  const loadStatistics = async () => {
    if (!api.getWinningMatchStatistics) return;

    setLoading(true);
    setError(null);
    try {
      const data = await api.getWinningMatchStatistics();
      setStatistics(data);
    } catch (err) {
      console.error('Failed to load winning match statistics:', err);
      setError('Failed to load statistics');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="statistics-card">Loading statistics...</div>;
  }

  if (error) {
    return <div className="statistics-card error">{error}</div>;
  }

  if (!statistics || statistics.totalMatches === 0) {
    return <div className="statistics-card">No data available</div>;
  }

  return (
    <div className="statistics-card">
      <h3>Winning Match Statistics</h3>
      <div className="statistics-content">
        <div className="stat-item">
          <span className="stat-label">Avg Vote:</span>
          <span className="stat-value">{statistics.averageVote.toFixed(1)}%</span>
        </div>
        <div className="stat-item">
          <span className="stat-label">Avg Odds:</span>
          <span className="stat-value">{statistics.averageOdds.toFixed(2)}</span>
        </div>
        <div className="stat-item">
          <span className="stat-label">Total Matches:</span>
          <span className="stat-value">{statistics.totalMatches}</span>
        </div>
      </div>
    </div>
  );
}

export default WinningMatchStatistics;
