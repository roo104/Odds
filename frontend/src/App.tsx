import {useState} from 'react';
import FootballMatchesView from './components/FootballMatchesView';
import HandballMatchesView from './components/HandballMatchesView';
import BetsView from './components/BetsView';
import StandingsView from './components/StandingsView';
import StatisticsView from './components/StatisticsView';
import SidePanel from './components/SidePanel';
import './App.css';

function App() {
  const [sport, setSport] = useState<'football' | 'handball'>('football');
  const [activeView, setActiveView] = useState<'matches' | 'bets' | 'standings' | 'statistics'>('matches');
  const [sharedDate, setSharedDate] = useState(new Date());

  return (
    <div className="app">
      <SidePanel activeView={activeView} onViewChange={(view) => setActiveView(view as any)} />
      <div className="main-content">
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
            {sport === 'football' ? (
              <FootballMatchesView currentDate={sharedDate} onDateChange={setSharedDate} />
            ) : (
              <HandballMatchesView currentDate={sharedDate} onDateChange={setSharedDate} />
            )}
          </>
        )}

        {activeView === 'standings' && <StandingsView />}

        {activeView === 'bets' && <BetsView />}

        {activeView === 'statistics' && <StatisticsView />}
      </div>
    </div>
  );
}

export default App;
