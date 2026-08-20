import {useCallback, useEffect, useRef, useState} from 'react';
import {SofascoreEvent} from '../types';
import {claudeApi, ClaudeProviderType, ClaudeStatus, MatchPrediction} from '../services/api';
import './MatchPredictionDialog.css';

interface MatchPredictionDialogProps {
  event: SofascoreEvent;
  sport: 'football' | 'handball';
  /** Fired once a prediction is stored, so the matches table can pick it up for its hover card. */
  onPredicted?: (prediction: MatchPrediction) => void;
  onClose: () => void;
}

const OUTCOME_LABELS: Record<string, string> = {
  HOME: 'Home win',
  DRAW: 'Draw',
  AWAY: 'Away win',
};

const PROVIDER_LABELS: Record<ClaudeProviderType, string> = {
  CLI: 'Local CLI',
  API: 'API key',
};

function MatchPredictionDialog({event, sport, onPredicted, onClose}: MatchPredictionDialogProps) {
  const [prediction, setPrediction] = useState<MatchPrediction | null>(null);
  const [status, setStatus] = useState<ClaudeStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [showFacts, setShowFacts] = useState(false);
  // Held in a ref because the parent passes a fresh closure on every render, and a changing
  // callback in the dependency list below would re-run the prediction on each of them.
  const onPredictedRef = useRef(onPredicted);
  onPredictedRef.current = onPredicted;

  const runPrediction = useCallback(async () => {
    setLoading(true);
    setError(null);
    setPrediction(null);
    try {
      const result = await claudeApi.predictMatch(event.id, sport);
      setPrediction(result);
      onPredictedRef.current?.(result);
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

              {prediction.probabilities && (
                <div className="prediction-probabilities">
                  {([
                    {key: 'HOME', label: event.homeTeam.name, percent: prediction.probabilities.home},
                    {key: 'DRAW', label: 'Draw', percent: prediction.probabilities.draw},
                    {key: 'AWAY', label: event.awayTeam.name, percent: prediction.probabilities.away},
                  ]).map(({key, label, percent}) => (
                    <div
                      key={key}
                      className={`prediction-probability ${prediction.predictedOutcome === key ? 'top' : ''}`}
                    >
                      <span className="prediction-probability-label">{label}</span>
                      <span className="prediction-probability-value">
                        {percent != null ? `${percent.toFixed(0)}%` : '-'}
                      </span>
                      <span className="prediction-probability-odds">
                        {percent != null && percent > 0 ? (100 / percent).toFixed(2) : '-'}
                      </span>
                      <span className="prediction-probability-bar" style={{width: `${percent ?? 0}%`}} />
                    </div>
                  ))}
                  <div className="prediction-probability-legend">
                    Claude&rsquo;s percentages and the decimal price they imply
                    {prediction.predictedOutcome && ` · call: ${OUTCOME_LABELS[prediction.predictedOutcome]}`}
                  </div>
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
