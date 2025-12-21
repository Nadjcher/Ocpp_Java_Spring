import React, { useState } from 'react';
import {
  GlobalConfig,
  GlobalEnvironment,
  GlobalEnvId,
  ConnectionStatus,
} from './types';

interface ConnectionPanelProps {
  config: GlobalConfig;
  onEnvironmentChange: (envId: GlobalEnvId) => void;
  onConfigUpdate: (config: GlobalConfig) => void;
  onOpenConfig: () => void;
}

const styles: Record<string, React.CSSProperties> = {
  panel: {
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    padding: 16,
    marginBottom: 20,
  },
  envSelector: {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    marginBottom: 16,
  },
  label: {
    color: '#6b7280',
    fontSize: 13,
    fontWeight: 500,
  },
  envButtons: {
    display: 'flex',
    gap: 8,
  },
  envBtn: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    padding: '8px 16px',
    borderRadius: 20,
    background: 'transparent',
    color: '#374151',
    cursor: 'pointer',
    transition: 'all 0.2s',
    fontSize: 13,
    fontWeight: 500,
  },
  envIndicator: {
    width: 8,
    height: 8,
    borderRadius: '50%',
  },
  connectionCards: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 12,
    marginBottom: 16,
  },
  card: {
    background: '#f9fafb',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    padding: 16,
  },
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    marginBottom: 12,
    fontWeight: 600,
    fontSize: 14,
    color: '#111827',
  },
  cardIcon: {
    fontSize: 18,
  },
  cardVersion: {
    marginLeft: 'auto',
    fontSize: 11,
    padding: '2px 8px',
    background: '#e5e7eb',
    borderRadius: 4,
    color: '#6b7280',
  },
  field: {
    marginBottom: 10,
  },
  fieldLabel: {
    fontSize: 11,
    color: '#6b7280',
    marginBottom: 4,
  },
  urlDisplay: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  urlCode: {
    flex: 1,
    padding: '8px 12px',
    background: '#fff',
    border: '1px solid #d1d5db',
    borderRadius: 4,
    fontFamily: 'monospace',
    fontSize: 12,
    color: '#374151',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  btnIcon: {
    padding: 6,
    background: 'transparent',
    border: 'none',
    cursor: 'pointer',
    fontSize: 14,
    opacity: 0.7,
    color: '#6b7280',
  },
  tokenDisplay: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  badge: {
    fontSize: 10,
    padding: '2px 6px',
    borderRadius: 4,
    background: '#2563eb',
    color: 'white',
    fontWeight: 500,
  },
  status: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    marginTop: 12,
    paddingTop: 12,
    borderTop: '1px solid #e5e7eb',
    fontSize: 13,
  },
  statusIcon: {
    fontSize: 10,
  },
  statusText: {
    fontWeight: 500,
    color: '#111827',
  },
  lastTested: {
    fontSize: 11,
    color: '#6b7280',
  },
  actions: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: 8,
  },
  btn: {
    padding: '8px 16px',
    borderRadius: 6,
    border: 'none',
    cursor: 'pointer',
    fontSize: 13,
    fontWeight: 500,
    display: 'flex',
    alignItems: 'center',
    gap: 6,
  },
  btnPrimary: {
    background: '#2563eb',
    color: 'white',
  },
  btnSecondary: {
    background: '#f3f4f6',
    color: '#374151',
  },
};

function getStatusIcon(status: ConnectionStatus): string {
  switch (status) {
    case 'connected': return '🟢';
    case 'error': return '🔴';
    case 'testing': return '🟡';
    default: return '⚪';
  }
}

function getStatusText(status: ConnectionStatus, connections?: number): string {
  switch (status) {
    case 'connected':
      return connections && connections > 0
        ? `Connecte (${connections} borne${connections > 1 ? 's' : ''})`
        : 'Connecte';
    case 'error': return 'Erreur connexion';
    case 'testing': return 'Test en cours...';
    default: return 'Non teste';
  }
}

function formatRelativeTime(isoDate?: string): string {
  if (!isoDate) return '';
  const date = new Date(isoDate);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return "a l'instant";
  if (diffMin < 60) return `il y a ${diffMin} min`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `il y a ${diffHours}h`;
  return `il y a ${Math.floor(diffHours / 24)}j`;
}

export const ConnectionPanel: React.FC<ConnectionPanelProps> = ({
  config,
  onEnvironmentChange,
  onConfigUpdate,
  onOpenConfig,
}) => {
  const [showToken, setShowToken] = useState(false);
  const [testing, setTesting] = useState(false);

  const activeEnv = config.environments.find(e => e.id === config.activeEnvironmentId);

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const testConnections = async () => {
    if (!activeEnv) return;
    setTesting(true);

    try {
      // Update status to testing
      const updatedEnvs = config.environments.map(e =>
        e.id === activeEnv.id
          ? { ...e, ocpiStatus: 'testing' as ConnectionStatus, wsStatus: 'testing' as ConnectionStatus }
          : e
      );
      onConfigUpdate({ ...config, environments: updatedEnvs });

      // Test OCPI connection
      const ocpiResult = await fetch('/api/ocpi/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          baseUrl: activeEnv.ocpiBaseUrl,
          tokenType: activeEnv.ocpiTokenType,
          token: activeEnv.ocpiToken,
          cognitoConfig: activeEnv.ocpiTokenType === 'cognito' ? {
            clientId: activeEnv.cognitoClientId,
            clientSecret: activeEnv.cognitoClientSecret,
            tokenUrl: activeEnv.cognitoTokenUrl,
          } : undefined,
        }),
      }).then(r => r.json()).catch(() => ({ status: 'error' }));

      // Test WebSocket connection
      const wsResult = await fetch('/api/ocpi/test-websocket', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          wsUrl: activeEnv.wsUrl,
          protocol: activeEnv.wsProtocol,
        }),
      }).then(r => r.json()).catch(() => ({ status: 'error', activeConnections: 0 }));

      // Update with results
      const finalEnvs = config.environments.map(e =>
        e.id === activeEnv.id
          ? {
              ...e,
              ocpiStatus: (ocpiResult.status === 'connected' ? 'connected' : 'error') as ConnectionStatus,
              wsStatus: (wsResult.status === 'connected' ? 'connected' : 'error') as ConnectionStatus,
              activeWsConnections: wsResult.activeConnections || 0,
              lastTestedAt: new Date().toISOString(),
              lastError: ocpiResult.error || wsResult.error,
            }
          : e
      );
      onConfigUpdate({ ...config, environments: finalEnvs });
    } finally {
      setTesting(false);
    }
  };

  return (
    <div style={styles.panel}>
      {/* Environment Selector */}
      <div style={styles.envSelector}>
        <span style={styles.label}>Environnement Actif :</span>
        <div style={styles.envButtons}>
          {config.environments.map(env => {
            const isActive = env.id === config.activeEnvironmentId;
            return (
              <button
                key={env.id}
                style={{
                  ...styles.envBtn,
                  border: `2px solid ${env.color}`,
                  background: isActive ? env.color : 'transparent',
                  color: isActive ? 'white' : '#374151',
                }}
                onClick={() => onEnvironmentChange(env.id)}
              >
                <span
                  style={{
                    ...styles.envIndicator,
                    background: isActive ? 'white' : env.color,
                  }}
                />
                {env.name}
              </button>
            );
          })}
        </div>
      </div>

      {activeEnv && (
        <>
          {/* Connection Cards */}
          <div style={styles.connectionCards}>
            {/* OCPI Connection */}
            <div style={styles.card}>
              <div style={styles.cardHeader}>
                <span style={styles.cardIcon}>[WEB]</span>
                <span>Connexion OCPI</span>
                <span style={styles.cardVersion}>{activeEnv.ocpiVersion}</span>
              </div>

              <div style={styles.field}>
                <div style={styles.fieldLabel}>URL :</div>
                <div style={styles.urlDisplay}>
                  <code style={styles.urlCode}>{activeEnv.ocpiBaseUrl || 'Non configure'}</code>
                  <button
                    style={styles.btnIcon}
                    onClick={() => copyToClipboard(activeEnv.ocpiBaseUrl)}
                    title="Copier"
                  >
                    [COPY]
                  </button>
                </div>
              </div>

              <div style={styles.field}>
                <div style={styles.fieldLabel}>Token :</div>
                <div style={styles.tokenDisplay}>
                  <code style={{ ...styles.urlCode, flex: 1 }}>
                    {showToken
                      ? (activeEnv.ocpiToken || '[Cognito - Token dynamique]')
                      : '••••••••••••••••'}
                  </code>
                  <button
                    style={styles.btnIcon}
                    onClick={() => setShowToken(!showToken)}
                    title={showToken ? 'Masquer' : 'Afficher'}
                  >
                    {showToken ? '[HIDE]' : '[SHOW]'}
                  </button>
                  {activeEnv.ocpiTokenType === 'cognito' && (
                    <span style={styles.badge}>Cognito</span>
                  )}
                </div>
              </div>

              <div style={styles.status}>
                <span style={styles.statusIcon}>{getStatusIcon(activeEnv.ocpiStatus)}</span>
                <span style={styles.statusText}>{getStatusText(activeEnv.ocpiStatus)}</span>
                {activeEnv.lastTestedAt && (
                  <span style={styles.lastTested}>
                    ({formatRelativeTime(activeEnv.lastTestedAt)})
                  </span>
                )}
              </div>
            </div>

            {/* WebSocket Connection */}
            <div style={styles.card}>
              <div style={styles.cardHeader}>
                <span style={styles.cardIcon}>[PLUG]</span>
                <span>Connexion WebSocket OCPP</span>
                <span style={styles.cardVersion}>{activeEnv.wsProtocol}</span>
              </div>

              <div style={styles.field}>
                <div style={styles.fieldLabel}>URL CSMS :</div>
                <div style={styles.urlDisplay}>
                  <code style={styles.urlCode}>{activeEnv.wsUrl || 'Non configure'}</code>
                  <button
                    style={styles.btnIcon}
                    onClick={() => copyToClipboard(activeEnv.wsUrl)}
                    title="Copier"
                  >
                    [COPY]
                  </button>
                </div>
              </div>

              <div style={styles.status}>
                <span style={styles.statusIcon}>{getStatusIcon(activeEnv.wsStatus)}</span>
                <span style={styles.statusText}>
                  {getStatusText(activeEnv.wsStatus, activeEnv.activeWsConnections)}
                </span>
              </div>
            </div>
          </div>

          {/* Actions */}
          <div style={styles.actions}>
            <button
              style={{ ...styles.btn, ...styles.btnPrimary }}
              onClick={testConnections}
              disabled={testing}
            >
              {testing ? '[TIMER] Test...' : '[SYNC] Tester Connexions'}
            </button>
            <button
              style={{ ...styles.btn, ...styles.btnSecondary }}
              onClick={onOpenConfig}
            >
              [CONFIG] Configurer
            </button>
          </div>
        </>
      )}
    </div>
  );
};

export default ConnectionPanel;
