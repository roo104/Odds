import './SidePanel.css';

interface SidePanelProps {
  activeView: string;
  onViewChange: (view: string) => void;
}

function SidePanel({ activeView, onViewChange }: SidePanelProps) {
  return (
    <div className="side-panel">
      <div className="side-panel-header">
        <h2>Navigation</h2>
      </div>
      <nav className="side-panel-nav">
        <button
          type="button"
          className={`nav-item ${activeView === 'matches' ? 'active' : ''}`}
          onClick={() => onViewChange('matches')}
        >
          <span className="nav-icon">⚽</span>
          <span className="nav-label">Matches</span>
        </button>
        <button
          type="button"
          className={`nav-item ${activeView === 'standings' ? 'active' : ''}`}
          onClick={() => onViewChange('standings')}
        >
          <span className="nav-icon">🏆</span>
          <span className="nav-label">Standings</span>
        </button>
        <button
          type="button"
          className={`nav-item ${activeView === 'bets' ? 'active' : ''}`}
          onClick={() => onViewChange('bets')}
        >
          <span className="nav-icon">🎲</span>
          <span className="nav-label">Bets</span>
        </button>
        <button
          type="button"
          className={`nav-item ${activeView === 'statistics' ? 'active' : ''}`}
          onClick={() => onViewChange('statistics')}
        >
          <span className="nav-icon">📊</span>
          <span className="nav-label">Statistics</span>
        </button>
        <button
          type="button"
          className={`nav-item ${activeView === 'config' ? 'active' : ''}`}
          onClick={() => onViewChange('config')}
        >
          <span className="nav-icon">⚙️</span>
          <span className="nav-label">Config</span>
        </button>
      </nav>
    </div>
  );
}

export default SidePanel;
