import {useCallback, useEffect, useState} from 'react';
import {SofascoreEvent} from '../types';
import {claudeApi, ClaudeProviderType, ClaudeStatus, MatchPrediction} from '../services/api';
import './MatchPredictionDialog.css';

interface MatchPredictionDialogProps {
  event: SofascoreEvent;
  sport: 'football' | 'handball';
  onClose: () => void;
}

const PROVIDER_LABELS: Record<ClaudeProviderType, string> = {
  CLI: 'Local CLI',
  API: 'API key',
};

function MatchPredictionDialog({event, sport, onClose}: MatchPredictionDialogProps) {
  const [prediction, setPrediction] = useState<MatchPrediction | null>(null);
  const [status, setStatus] = useState<ClaudeStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [showFacts, setShowFacts] = useState(false);

  const runPrediction = useCallback(async () => {
    setLoading(true);
    setError(null);
    setPrediction(null);
    try {
      setPrediction(await claudeApi.predictMatch(event.id, sport));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [event.id, sport]);

  useEffect(() => {
    claudeApi.getStatus().then(setStatus).catch(() => setStatus(null));
    runPrediction();
  }, [runPrediction]);

  const handleSwitchProvider = async (provider: ClaudeProviderType) => {
    try {
      setStatus(await claudeApi.setProvider(provider));
      await runPrediction();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const isLive = event.status.type === 'inprogress';

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog-content prediction-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <div>
            <h2>{event.homeTeam.name} vs {event.awayTeam.name}</h2>
            <div className="prediction-subheader">
              {event.tournament.name} · {event.status.description}
              {isLive && <span className="prediction-live-badge">LIVE</span>}
            </div>
          </div>
          <button className="close-button" onClick={onClose}>×</button>
        </div>

        <div className="prediction-body">
          {loading && (
            <div className="prediction-loading">
              Reading the odds{isLive ? ' and live statistics' : ''}…
              <span className="prediction-loading-hint">The local CLI takes a few seconds to start.</span>
            </div>
          )}

          {error && <div className="prediction-error">{error}</div>}

          {prediction && (
            <>
              {!prediction.hasStatistics && prediction.isLive && (
                <div className="prediction-note">
                  No live statistics published for this match yet — the call is based on odds and votes alone.
                </div>
              )}
              {!prediction.isLive && (
                <div className="prediction-note">
                  Match has not kicked off, so there are no live statistics — this is an odds-and-votes read.
                </div>
              )}

              <div className="prediction-text">{prediction.prediction}</div>

              <button
                type="button"
                className="prediction-facts-toggle"
                onClick={() => setShowFacts((shown) => !shown)}
              >
                {showFacts ? '▾' : '▸'} Numbers Claude was given
              </button>
              {showFacts && <pre className="prediction-facts">{prediction.contextUsed}</pre>}
            </>
          )}
        </div>

        <div className="prediction-footer">
          <div className="prediction-provider">
            <span>Answered by:</span>
            {(['CLI', 'API'] as ClaudeProviderType[]).map((type) => {
              const info = status?.providers.find((p) => p.type === type);
              return (
                <button
                  key={type}
                  type="button"
                  className={`prediction-provider-button ${status?.active === type ? 'active' : ''}`}
                  disabled={loading || !status || !info?.available}
                  title={info?.unavailableReason ?? undefined}
                  onClick={() => handleSwitchProvider(type)}
                >
                  {PROVIDER_LABELS[type]}
                </button>
              );
            })}
          </div>
          <div className="prediction-meta">
            {prediction && (
              <>
                {prediction.model && `${prediction.model} · `}
                {(prediction.durationMs / 1000).toFixed(1)}s
                {prediction.costUsd != null && ` · $${prediction.costUsd.toFixed(3)}`}
              </>
            )}
          </div>
          <button type="button" className="prediction-rerun" onClick={runPrediction} disabled={loading}>
            {loading ? 'Working…' : 'Ask again'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default MatchPredictionDialog;
