import {useState} from 'react';
import FootballMatchesView from './components/FootballMatchesView';
import HandballMatchesView from './components/HandballMatchesView';
import BetsView from './components/BetsView';
import './App.css';

function App() {
  const [sport, setSport] = useState<'football' | 'handball'>('football');
  const [activeView, setActiveView] = useState<'matches' | 'bets'>('matches');

  return (
    <div className="app">
      <div className="main-menu">
        <button
          type="button"
          className={`menu-button ${activeView === 'matches' ? 'active' : ''}`}
          onClick={() => setActiveView('matches')}
        >
          Matches
        </button>
        <button
          type="button"
          className={`menu-button ${activeView === 'bets' ? 'active' : ''}`}
          onClick={() => setActiveView('bets')}
        >
          Bets
        </button>
      </div>

      {activeView === 'matches' && (
        <>
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
          {sport === 'football' ? <FootballMatchesView /> : <HandballMatchesView />}
        </>
      )}

      {activeView === 'bets' && <BetsView />}
    </div>
  );
}

export default App;
