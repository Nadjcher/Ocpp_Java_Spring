import React, { useState } from 'react';
import { GlobalEnvironment, OCPIVersion, AuthType, OcppProtocol } from './types';

interface EnvironmentConfigModalProps {
  environment: GlobalEnvironment;
  onSave: (env: GlobalEnvironment) => void;
  onClose: () => void;
}

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    background: 'rgba(0,0,0,0.7)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  modal: {
    background: '#1e293b',
    borderRadius: 12,
    width: '100%',
    maxWidth: 600,
    maxHeight: '90vh',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    padding: '16px 20px',
    borderBottom: '1px solid #334155',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    fontSize: 18,
    fontWeight: 600,
    color: '#f1f5f9',
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  closeBtn: {
    background: 'transparent',
    border: 'none',
    fontSize: 20,
    color: '#94a3b8',
    cursor: 'pointer',
    padding: 4,
  },
  body: {
    padding: 20,
    overflowY: 'auto',
    flex: 1,
  },
  fieldset: {
    border: '1px solid #334155',
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
  },
  legend: {
    fontSize: 14,
    fontWeight: 600,
    color: '#e2e8f0',
    padding: '0 8px',
  },
  formGroup: {
    marginBottom: 14,
  },
  label: {
    display: 'block',
    fontSize: 12,
    color: '#94a3b8',
    marginBottom: 6,
    fontWeight: 500,
  },
  input: {
    width: '100%',
    padding: '10px 12px',
    background: '#0f172a',
    border: '1px solid #334155',
    borderRadius: 6,
    color: '#e2e8f0',
    fontSize: 13,
  },
  select: {
    width: '100%',
    padding: '10px 12px',
    background: '#0f172a',
    border: '1px solid #334155',
    borderRadius: 6,
    color: '#e2e8f0',
    fontSize: 13,
  },
  formRow: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 12,
  },
  hint: {
    fontSize: 11,
    color: '#64748b',
    marginTop: 4,
  },
  footer: {
    padding: '16px 20px',
    borderTop: '1px solid #334155',
    display: 'flex',
    justifyContent: 'flex-end',
    gap: 8,
  },
  btn: {
    padding: '10px 20px',
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
    background: '#3b82f6',
    color: 'white',
  },
  btnSecondary: {
    background: '#334155',
    color: '#e2e8f0',
  },
  btnWarning: {
    background: '#f59e0b',
    color: 'white',
  },
};

export const EnvironmentConfigModal: React.FC<EnvironmentConfigModalProps> = ({
  environment,
  onSave,
  onClose,
}) => {
  const [formData, setFormData] = useState<GlobalEnvironment>({ ...environment });
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<'success' | 'error' | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(formData);
  };

  const testConnection = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const response = await fetch('/api/ocpi/test-connection', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          baseUrl: formData.ocpiBaseUrl,
          tokenType: formData.ocpiTokenType,
          token: formData.ocpiToken,
          cognitoConfig: formData.ocpiTokenType === 'cognito' ? {
            clientId: formData.cognitoClientId,
            clientSecret: formData.cognitoClientSecret,
            tokenUrl: formData.cognitoTokenUrl,
          } : undefined,
        }),
      });
      const result = await response.json();
      setTestResult(result.status === 'connected' ? 'success' : 'error');
    } catch {
      setTestResult('error');
    } finally {
      setTesting(false);
    }
  };

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.modal} onClick={e => e.stopPropagation()}>
        <div style={styles.header}>
          <h3 style={styles.title}>
            Configuration Environnement :
            <span style={{ color: formData.color }}> {formData.displayName}</span>
          </h3>
          <button style={styles.closeBtn} onClick={onClose}>×</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div style={styles.body}>
            {/* Section OCPI */}
            <fieldset style={styles.fieldset}>
              <legend style={styles.legend}>[WEB] Connexion OCPI</legend>

              <div style={styles.formGroup}>
                <label style={styles.label}>URL de base OCPI *</label>
                <input
                  type="url"
                  style={styles.input}
                  value={formData.ocpiBaseUrl}
                  onChange={e => setFormData({ ...formData, ocpiBaseUrl: e.target.value })}
                  placeholder="https://api.example.com/ocpi"
                  required
                />
              </div>

              <div style={styles.formRow}>
                <div style={styles.formGroup}>
                  <label style={styles.label}>Version OCPI</label>
                  <select
                    style={styles.select}
                    value={formData.ocpiVersion}
                    onChange={e => setFormData({ ...formData, ocpiVersion: e.target.value as OCPIVersion })}
                  >
                    <option value="2.1.1">OCPI 2.1.1</option>
                    <option value="2.2">OCPI 2.2</option>
                    <option value="2.2.1">OCPI 2.2.1</option>
                  </select>
                </div>

                <div style={styles.formGroup}>
                  <label style={styles.label}>Type d'authentification</label>
                  <select
                    style={styles.select}
                    value={formData.ocpiTokenType}
                    onChange={e => setFormData({ ...formData, ocpiTokenType: e.target.value as AuthType })}
                  >
                    <option value="token">Token Statique</option>
                    <option value="cognito">AWS Cognito</option>
                    <option value="oauth2">OAuth2</option>
                  </select>
                </div>
              </div>

              {formData.ocpiTokenType === 'token' ? (
                <div style={styles.formGroup}>
                  <label style={styles.label}>Token OCPI</label>
                  <input
                    type="password"
                    style={styles.input}
                    value={formData.ocpiToken}
                    onChange={e => setFormData({ ...formData, ocpiToken: e.target.value })}
                    placeholder="Token-xxx-yyy-zzz"
                  />
                </div>
              ) : (
                <>
                  <div style={styles.formGroup}>
                    <label style={styles.label}>Token URL *</label>
                    <input
                      type="url"
                      style={styles.input}
                      value={formData.cognitoTokenUrl || ''}
                      onChange={e => setFormData({ ...formData, cognitoTokenUrl: e.target.value })}
                      placeholder="https://cognito-idp.eu-west-1.amazonaws.com/xxx/oauth2/token"
                    />
                  </div>
                  <div style={styles.formRow}>
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Client ID *</label>
                      <input
                        type="text"
                        style={styles.input}
                        value={formData.cognitoClientId || ''}
                        onChange={e => setFormData({ ...formData, cognitoClientId: e.target.value })}
                      />
                    </div>
                    <div style={styles.formGroup}>
                      <label style={styles.label}>Client Secret *</label>
                      <input
                        type="password"
                        style={styles.input}
                        value={formData.cognitoClientSecret || ''}
                        onChange={e => setFormData({ ...formData, cognitoClientSecret: e.target.value })}
                      />
                    </div>
                  </div>
                </>
              )}
            </fieldset>

            {/* Section WebSocket */}
            <fieldset style={styles.fieldset}>
              <legend style={styles.legend}>[PLUG] Connexion WebSocket OCPP</legend>

              <div style={styles.formGroup}>
                <label style={styles.label}>URL WebSocket CSMS *</label>
                <input
                  type="url"
                  style={styles.input}
                  value={formData.wsUrl}
                  onChange={e => setFormData({ ...formData, wsUrl: e.target.value })}
                  placeholder="wss://ocpp.example.com/ocpp"
                  required
                />
                <div style={styles.hint}>URL pour connecter les bornes simulees au CSMS</div>
              </div>

              <div style={styles.formGroup}>
                <label style={styles.label}>Protocole OCPP</label>
                <select
                  style={styles.select}
                  value={formData.wsProtocol}
                  onChange={e => setFormData({ ...formData, wsProtocol: e.target.value as OcppProtocol })}
                >
                  <option value="ocpp1.6">OCPP 1.6</option>
                  <option value="ocpp2.0.1">OCPP 2.0.1</option>
                </select>
              </div>
            </fieldset>

            {/* Test Result */}
            {testResult && (
              <div style={{
                padding: 12,
                borderRadius: 6,
                marginBottom: 16,
                background: testResult === 'success' ? '#052e16' : '#450a0a',
                color: testResult === 'success' ? '#4ade80' : '#fca5a5',
              }}>
                {testResult === 'success' ? '[OK] Connexion reussie' : '[ERR] Echec de connexion'}
              </div>
            )}
          </div>

          <div style={styles.footer}>
            <button
              type="button"
              style={{ ...styles.btn, ...styles.btnSecondary }}
              onClick={onClose}
            >
              Annuler
            </button>
            <button
              type="button"
              style={{ ...styles.btn, ...styles.btnWarning }}
              onClick={testConnection}
              disabled={testing}
            >
              {testing ? '[TIMER] Test...' : '[SYNC] Tester'}
            </button>
            <button
              type="submit"
              style={{ ...styles.btn, ...styles.btnPrimary }}
            >
              [SAVE] Sauvegarder
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EnvironmentConfigModal;
