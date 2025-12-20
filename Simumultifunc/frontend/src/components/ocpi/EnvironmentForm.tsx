/**
 * EnvironmentForm - Formulaire d'environnement OCPI
 */

import React from 'react';
import { OCPIEnvironment, AuthType, createEmptyEnvironment } from './types';

interface EnvironmentFormProps {
  environment: OCPIEnvironment;
  onChange: (env: OCPIEnvironment) => void;
  onDelete?: () => void;
  canDelete?: boolean;
  isActive?: boolean;
  onSetActive?: () => void;
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    padding: 16,
    marginBottom: 12,
    background: '#fafafa',
  },
  containerActive: {
    borderColor: '#3b82f6',
    background: '#eff6ff',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  headerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  },
  envName: {
    fontSize: 14,
    fontWeight: 600,
    color: '#111827',
  },
  activeBadge: {
    fontSize: 10,
    padding: '2px 8px',
    borderRadius: 10,
    background: '#3b82f6',
    color: '#fff',
    fontWeight: 500,
  },
  headerRight: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  btn: {
    padding: '4px 10px',
    borderRadius: 4,
    fontSize: 12,
    cursor: 'pointer',
    border: 'none',
    transition: 'all 0.15s ease',
  },
  btnSecondary: {
    background: '#f3f4f6',
    color: '#374151',
  },
  btnDanger: {
    background: '#fee2e2',
    color: '#dc2626',
  },
  row: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 12,
    marginBottom: 12,
  },
  rowFull: {
    marginBottom: 12,
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
  },
  label: {
    fontSize: 12,
    fontWeight: 500,
    color: '#374151',
  },
  input: {
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    outline: 'none',
    transition: 'border-color 0.15s ease',
  },
  select: {
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    outline: 'none',
    background: '#fff',
    cursor: 'pointer',
  },
  authSection: {
    marginTop: 16,
    paddingTop: 16,
    borderTop: '1px dashed #e5e7eb',
  },
  authTitle: {
    fontSize: 13,
    fontWeight: 600,
    color: '#374151',
    marginBottom: 12,
  },
  tokenGroup: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr',
    gap: 12,
    marginBottom: 12,
  },
  helpText: {
    fontSize: 11,
    color: '#6b7280',
    marginTop: 4,
  },
};

export const EnvironmentForm: React.FC<EnvironmentFormProps> = ({
  environment,
  onChange,
  onDelete,
  canDelete = true,
  isActive = false,
  onSetActive,
}) => {
  const update = (updates: Partial<OCPIEnvironment>) => {
    onChange({ ...environment, ...updates });
  };

  const updateAuth = (authType: AuthType) => {
    update({
      authType,
      // Reset les autres champs d'auth
      ...(authType !== 'token' ? { token: undefined, tokenA: undefined, tokenB: undefined, tokenC: undefined } : {}),
      ...(authType !== 'oauth2' ? { oauth2: undefined } : {}),
      ...(authType !== 'cognito' ? { cognito: undefined } : {}),
    });
  };

  return (
    <div style={{
      ...styles.container,
      ...(isActive ? styles.containerActive : {}),
    }}>
      {/* Header */}
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <span style={styles.envName}>{environment.name || 'Nouvel environnement'}</span>
          {isActive && <span style={styles.activeBadge}>Actif</span>}
        </div>
        <div style={styles.headerRight}>
          {!isActive && onSetActive && (
            <button
              style={{ ...styles.btn, ...styles.btnSecondary }}
              onClick={onSetActive}
            >
              Activer
            </button>
          )}
          {canDelete && onDelete && (
            <button
              style={{ ...styles.btn, ...styles.btnDanger }}
              onClick={onDelete}
            >
              Supprimer
            </button>
          )}
        </div>
      </div>

      {/* Infos de base */}
      <div style={styles.row}>
        <div style={styles.field}>
          <label style={styles.label}>Nom</label>
          <input
            type="text"
            style={styles.input}
            value={environment.name}
            onChange={(e) => update({ name: e.target.value })}
            placeholder="Test, Staging, Production..."
          />
        </div>
        <div style={styles.field}>
          <label style={styles.label}>ID</label>
          <input
            type="text"
            style={styles.input}
            value={environment.id}
            onChange={(e) => update({ id: e.target.value.toLowerCase().replace(/\s/g, '-') })}
            placeholder="test, staging, prod..."
          />
        </div>
      </div>

      <div style={styles.rowFull}>
        <div style={styles.field}>
          <label style={styles.label}>Base URL</label>
          <input
            type="text"
            style={styles.input}
            value={environment.baseUrl}
            onChange={(e) => update({ baseUrl: e.target.value })}
            placeholder="https://api.partner.com/ocpi"
          />
          <span style={styles.helpText}>URL de base pour les requêtes OCPI</span>
        </div>
      </div>

      <div style={styles.rowFull}>
        <div style={styles.field}>
          <label style={styles.label}>Versions URL (optionnel)</label>
          <input
            type="text"
            style={styles.input}
            value={environment.versionsUrl || ''}
            onChange={(e) => update({ versionsUrl: e.target.value || undefined })}
            placeholder="https://api.partner.com/ocpi/versions"
          />
          <span style={styles.helpText}>Si différent de baseUrl + /versions</span>
        </div>
      </div>

      {/* Section Authentification */}
      <div style={styles.authSection}>
        <div style={styles.authTitle}>Authentification</div>

        <div style={styles.rowFull}>
          <div style={styles.field}>
            <label style={styles.label}>Type</label>
            <select
              style={styles.select}
              value={environment.authType}
              onChange={(e) => updateAuth(e.target.value as AuthType)}
            >
              <option value="token">Token OCPI (Token A/B/C)</option>
              <option value="oauth2">OAuth2 Client Credentials</option>
              <option value="cognito">AWS Cognito</option>
            </select>
          </div>
        </div>

        {/* Token Auth */}
        {environment.authType === 'token' && (
          <>
            <div style={styles.tokenGroup}>
              <div style={styles.field}>
                <label style={styles.label}>Token A (reçu)</label>
                <input
                  type="password"
                  style={styles.input}
                  value={environment.tokenA || ''}
                  onChange={(e) => update({ tokenA: e.target.value || undefined })}
                  placeholder="Token reçu du partenaire"
                />
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Token B (envoyé)</label>
                <input
                  type="password"
                  style={styles.input}
                  value={environment.tokenB || ''}
                  onChange={(e) => update({ tokenB: e.target.value || undefined })}
                  placeholder="Token envoyé au partenaire"
                />
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Token C (test)</label>
                <input
                  type="password"
                  style={styles.input}
                  value={environment.tokenC || ''}
                  onChange={(e) => update({ tokenC: e.target.value || undefined })}
                  placeholder="Token pour tests"
                />
              </div>
            </div>
            <span style={styles.helpText}>
              Token A: reçu lors du credentials handshake. Token B: à envoyer au partenaire. Token C: pour tests.
            </span>
          </>
        )}

        {/* OAuth2 Auth */}
        {environment.authType === 'oauth2' && (
          <>
            <div style={styles.rowFull}>
              <div style={styles.field}>
                <label style={styles.label}>Token URL</label>
                <input
                  type="text"
                  style={styles.input}
                  value={environment.oauth2?.tokenUrl || ''}
                  onChange={(e) => update({
                    oauth2: { ...environment.oauth2!, tokenUrl: e.target.value }
                  })}
                  placeholder="https://auth.partner.com/oauth2/token"
                />
              </div>
            </div>
            <div style={styles.row}>
              <div style={styles.field}>
                <label style={styles.label}>Client ID</label>
                <input
                  type="text"
                  style={styles.input}
                  value={environment.oauth2?.clientId || ''}
                  onChange={(e) => update({
                    oauth2: { ...environment.oauth2!, clientId: e.target.value }
                  })}
                  placeholder="client_id"
                />
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Client Secret</label>
                <input
                  type="password"
                  style={styles.input}
                  value={environment.oauth2?.clientSecret || ''}
                  onChange={(e) => update({
                    oauth2: { ...environment.oauth2!, clientSecret: e.target.value }
                  })}
                  placeholder="client_secret"
                />
              </div>
            </div>
            <div style={styles.row}>
              <div style={styles.field}>
                <label style={styles.label}>Scope (optionnel)</label>
                <input
                  type="text"
                  style={styles.input}
                  value={environment.oauth2?.scope || ''}
                  onChange={(e) => update({
                    oauth2: { ...environment.oauth2!, scope: e.target.value || undefined }
                  })}
                  placeholder="ocpi.read ocpi.write"
                />
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Grant Type</label>
                <select
                  style={styles.select}
                  value={environment.oauth2?.grantType || 'client_credentials'}
                  onChange={(e) => update({
                    oauth2: { ...environment.oauth2!, grantType: e.target.value as any }
                  })}
                >
                  <option value="client_credentials">Client Credentials</option>
                  <option value="password">Password</option>
                </select>
              </div>
            </div>
          </>
        )}

        {/* Cognito Auth */}
        {environment.authType === 'cognito' && (
          <>
            <div style={styles.rowFull}>
              <div style={styles.field}>
                <label style={styles.label}>Token URL (Cognito)</label>
                <input
                  type="text"
                  style={styles.input}
                  value={environment.cognito?.tokenUrl || ''}
                  onChange={(e) => update({
                    cognito: { ...environment.cognito!, tokenUrl: e.target.value }
                  })}
                  placeholder="https://your-pool.auth.eu-west-1.amazoncognito.com/oauth2/token"
                />
              </div>
            </div>
            <div style={styles.row}>
              <div style={styles.field}>
                <label style={styles.label}>Client ID</label>
                <input
                  type="text"
                  style={styles.input}
                  value={environment.cognito?.clientId || ''}
                  onChange={(e) => update({
                    cognito: { ...environment.cognito!, clientId: e.target.value }
                  })}
                  placeholder="cognito_client_id"
                />
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Client Secret</label>
                <input
                  type="password"
                  style={styles.input}
                  value={environment.cognito?.clientSecret || ''}
                  onChange={(e) => update({
                    cognito: { ...environment.cognito!, clientSecret: e.target.value }
                  })}
                  placeholder="cognito_client_secret"
                />
              </div>
            </div>
            <div style={styles.row}>
              <div style={styles.field}>
                <label style={styles.label}>Region (optionnel)</label>
                <input
                  type="text"
                  style={styles.input}
                  value={environment.cognito?.region || ''}
                  onChange={(e) => update({
                    cognito: { ...environment.cognito!, region: e.target.value || undefined }
                  })}
                  placeholder="eu-west-1"
                />
              </div>
              <div style={styles.field}>
                <label style={styles.label}>User Pool ID (optionnel)</label>
                <input
                  type="text"
                  style={styles.input}
                  value={environment.cognito?.userPoolId || ''}
                  onChange={(e) => update({
                    cognito: { ...environment.cognito!, userPoolId: e.target.value || undefined }
                  })}
                  placeholder="eu-west-1_xxxxx"
                />
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default EnvironmentForm;
