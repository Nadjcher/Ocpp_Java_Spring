// ═══════════════════════════════════════════════════════════════════════════
// OCPI Result Detail Component
// ═══════════════════════════════════════════════════════════════════════════

import React, { useState } from 'react';
import {
  OCPITestResult,
  OCPIStepResult,
  ValidationResult,
  RequestDetails,
  ResponseDetails,
  getTestStatusColor,
  getTestStatusLabel,
  formatDuration,
} from './types';

// ─────────────────────────────────────────────────────────────────────────────
// Styles
// ─────────────────────────────────────────────────────────────────────────────

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column' as const,
    height: '100%',
    backgroundColor: '#fff',
    borderRadius: '8px',
    overflow: 'hidden',
  },
  header: {
    padding: '16px 20px',
    borderBottom: '1px solid #e5e7eb',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    backgroundColor: '#f9fafb',
  },
  headerLeft: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '8px',
  },
  title: {
    fontSize: '18px',
    fontWeight: 600,
    color: '#111827',
    margin: 0,
  },
  subtitle: {
    fontSize: '13px',
    color: '#6b7280',
  },
  closeButton: {
    padding: '8px',
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    color: '#6b7280',
    borderRadius: '4px',
    transition: 'all 0.2s',
  },
  statusBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '6px',
    padding: '4px 10px',
    borderRadius: '12px',
    fontSize: '12px',
    fontWeight: 500,
  },
  metricsBar: {
    display: 'flex',
    gap: '24px',
    padding: '12px 20px',
    borderBottom: '1px solid #e5e7eb',
    backgroundColor: '#f9fafb',
  },
  metric: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: '2px',
  },
  metricLabel: {
    fontSize: '11px',
    color: '#6b7280',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
  },
  metricValue: {
    fontSize: '14px',
    fontWeight: 500,
    color: '#111827',
  },
  tabs: {
    display: 'flex',
    borderBottom: '1px solid #e5e7eb',
    backgroundColor: '#fff',
    padding: '0 16px',
  },
  tab: {
    padding: '12px 16px',
    fontSize: '13px',
    fontWeight: 500,
    color: '#6b7280',
    background: 'none',
    border: 'none',
    borderBottom: '2px solid transparent',
    cursor: 'pointer',
    transition: 'all 0.2s',
    marginBottom: '-1px',
  },
  tabActive: {
    color: '#3b82f6',
    borderBottomColor: '#3b82f6',
  },
  content: {
    flex: 1,
    overflow: 'auto',
    padding: '16px 20px',
  },
  section: {
    marginBottom: '24px',
  },
  sectionTitle: {
    fontSize: '13px',
    fontWeight: 600,
    color: '#374151',
    marginBottom: '12px',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
  },
  codeBlock: {
    backgroundColor: '#1e293b',
    borderRadius: '8px',
    padding: '16px',
    overflow: 'auto',
    maxHeight: '400px',
  },
  code: {
    fontFamily: 'Monaco, Consolas, monospace',
    fontSize: '12px',
    color: '#e2e8f0',
    lineHeight: '1.6',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-word' as const,
  },
  headersTable: {
    width: '100%',
    borderCollapse: 'collapse' as const,
    fontSize: '13px',
  },
  headerRow: {
    borderBottom: '1px solid #e5e7eb',
  },
  headerCell: {
    padding: '10px 12px',
    textAlign: 'left' as const,
    fontWeight: 500,
    color: '#374151',
  },
  headerValue: {
    padding: '10px 12px',
    textAlign: 'left' as const,
    color: '#6b7280',
    fontFamily: 'Monaco, Consolas, monospace',
    fontSize: '12px',
    wordBreak: 'break-all' as const,
  },
  validationItem: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: '12px',
    padding: '12px',
    backgroundColor: '#f9fafb',
    borderRadius: '6px',
    marginBottom: '8px',
  },
  validationIcon: {
    width: '20px',
    height: '20px',
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '12px',
    flexShrink: 0,
  },
  validationContent: {
    flex: 1,
  },
  validationField: {
    fontFamily: 'Monaco, Consolas, monospace',
    fontSize: '12px',
    color: '#3b82f6',
    marginBottom: '4px',
  },
  validationMessage: {
    fontSize: '13px',
    color: '#374151',
  },
  validationActual: {
    fontSize: '12px',
    color: '#6b7280',
    marginTop: '4px',
  },
  stepCard: {
    border: '1px solid #e5e7eb',
    borderRadius: '8px',
    marginBottom: '12px',
    overflow: 'hidden',
  },
  stepHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '12px 16px',
    backgroundColor: '#f9fafb',
    cursor: 'pointer',
  },
  stepTitle: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
  },
  stepNumber: {
    width: '24px',
    height: '24px',
    borderRadius: '50%',
    backgroundColor: '#e5e7eb',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '12px',
    fontWeight: 600,
    color: '#374151',
  },
  stepName: {
    fontSize: '14px',
    fontWeight: 500,
    color: '#111827',
  },
  stepMeta: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    fontSize: '12px',
    color: '#6b7280',
  },
  stepContent: {
    padding: '16px',
    borderTop: '1px solid #e5e7eb',
  },
  copyButton: {
    padding: '4px 8px',
    fontSize: '11px',
    color: '#9ca3af',
    background: 'transparent',
    border: '1px solid #374151',
    borderRadius: '4px',
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  errorBox: {
    backgroundColor: '#fef2f2',
    border: '1px solid #fecaca',
    borderRadius: '8px',
    padding: '16px',
    color: '#dc2626',
    fontSize: '13px',
  },
  emptyState: {
    textAlign: 'center' as const,
    padding: '40px 20px',
    color: '#6b7280',
  },
  contextItem: {
    display: 'flex',
    justifyContent: 'space-between',
    padding: '8px 12px',
    backgroundColor: '#f9fafb',
    borderRadius: '4px',
    marginBottom: '8px',
  },
  contextKey: {
    fontWeight: 500,
    color: '#374151',
    fontSize: '13px',
  },
  contextValue: {
    fontFamily: 'Monaco, Consolas, monospace',
    fontSize: '12px',
    color: '#6b7280',
  },
};

// ─────────────────────────────────────────────────────────────────────────────
// Props
// ─────────────────────────────────────────────────────────────────────────────

interface ResultDetailProps {
  result: OCPITestResult;
  onClose: () => void;
}

type TabType = 'overview' | 'request' | 'response' | 'validations' | 'steps' | 'context';

// ─────────────────────────────────────────────────────────────────────────────
// Sub-Components
// ─────────────────────────────────────────────────────────────────────────────

const JsonViewer: React.FC<{ data: any; title?: string }> = ({ data, title }) => {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(JSON.stringify(data, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div style={styles.section}>
      {title && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <div style={styles.sectionTitle}>{title}</div>
          <button
            style={styles.copyButton}
            onClick={handleCopy}
            onMouseOver={(e) => {
              e.currentTarget.style.color = '#e2e8f0';
              e.currentTarget.style.borderColor = '#6b7280';
            }}
            onMouseOut={(e) => {
              e.currentTarget.style.color = '#9ca3af';
              e.currentTarget.style.borderColor = '#374151';
            }}
          >
            {copied ? '[OK] Copié' : 'Copier'}
          </button>
        </div>
      )}
      <div style={styles.codeBlock}>
        <pre style={styles.code}>
          {typeof data === 'string' ? data : JSON.stringify(data, null, 2)}
        </pre>
      </div>
    </div>
  );
};

const HeadersTable: React.FC<{ headers: Record<string, string>; title?: string }> = ({ headers, title }) => {
  const entries = Object.entries(headers || {});

  if (entries.length === 0) {
    return (
      <div style={styles.section}>
        {title && <div style={styles.sectionTitle}>{title}</div>}
        <div style={{ ...styles.emptyState, padding: '20px' }}>Aucun header</div>
      </div>
    );
  }

  return (
    <div style={styles.section}>
      {title && <div style={styles.sectionTitle}>{title}</div>}
      <table style={styles.headersTable}>
        <tbody>
          {entries.map(([key, value]) => (
            <tr key={key} style={styles.headerRow}>
              <td style={styles.headerCell}>{key}</td>
              <td style={styles.headerValue}>{value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

const ValidationsList: React.FC<{ validations: ValidationResult[] }> = ({ validations }) => {
  if (!validations || validations.length === 0) {
    return (
      <div style={styles.emptyState}>
        <p>Aucune validation configurée</p>
      </div>
    );
  }

  return (
    <div>
      {validations.map((vr, index) => (
        <div key={index} style={styles.validationItem}>
          <div
            style={{
              ...styles.validationIcon,
              backgroundColor: vr.passed ? '#dcfce7' : '#fef2f2',
              color: vr.passed ? '#16a34a' : '#dc2626',
            }}
          >
            {vr.passed ? '[OK]' : '[ERR]'}
          </div>
          <div style={styles.validationContent}>
            <div style={styles.validationField}>{vr.validation.field}</div>
            <div style={styles.validationMessage}>
              {vr.validation.operator} {JSON.stringify(vr.validation.expected)}
            </div>
            {!vr.passed && vr.actualValue !== undefined && (
              <div style={styles.validationActual}>
                Valeur actuelle: {JSON.stringify(vr.actualValue)}
              </div>
            )}
            {vr.message && (
              <div style={{ ...styles.validationActual, color: vr.passed ? '#16a34a' : '#dc2626' }}>
                {vr.message}
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
};

const StepResultCard: React.FC<{ step: OCPIStepResult; index: number }> = ({ step, index }) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div style={styles.stepCard}>
      <div style={styles.stepHeader} onClick={() => setExpanded(!expanded)}>
        <div style={styles.stepTitle}>
          <div
            style={{
              ...styles.stepNumber,
              backgroundColor: step.status === 'passed' ? '#dcfce7' : step.status === 'failed' ? '#fef2f2' : '#e5e7eb',
              color: step.status === 'passed' ? '#16a34a' : step.status === 'failed' ? '#dc2626' : '#374151',
            }}
          >
            {index + 1}
          </div>
          <span style={styles.stepName}>{step.stepName}</span>
        </div>
        <div style={styles.stepMeta}>
          <span
            style={{
              ...styles.statusBadge,
              backgroundColor: `${getTestStatusColor(step.status)}20`,
              color: getTestStatusColor(step.status),
            }}
          >
            {getTestStatusLabel(step.status)}
          </span>
          <span>{formatDuration(step.durationMs)}</span>
          <span style={{ fontSize: '16px', color: '#9ca3af' }}>{expanded ? '▼' : '▶'}</span>
        </div>
      </div>

      {expanded && (
        <div style={styles.stepContent}>
          {step.errorMessage && (
            <div style={{ ...styles.errorBox, marginBottom: '16px' }}>
              {step.errorMessage}
            </div>
          )}

          {step.request && (
            <div style={styles.section}>
              <div style={styles.sectionTitle}>Requête</div>
              <div style={{ fontSize: '13px', marginBottom: '8px' }}>
                <span style={{ fontWeight: 600, color: '#3b82f6' }}>{step.request.method}</span>{' '}
                <span style={{ color: '#6b7280' }}>{step.request.url}</span>
              </div>
              {step.request.body && <JsonViewer data={step.request.body} title="Body" />}
            </div>
          )}

          {step.response && (
            <div style={styles.section}>
              <div style={styles.sectionTitle}>Réponse</div>
              <div style={{ fontSize: '13px', marginBottom: '8px' }}>
                <span
                  style={{
                    fontWeight: 600,
                    color: step.response.status >= 200 && step.response.status < 300 ? '#16a34a' : '#dc2626',
                  }}
                >
                  {step.response.status}
                </span>{' '}
                <span style={{ color: '#6b7280' }}>{step.response.statusText}</span>
              </div>
              {step.response.body && <JsonViewer data={step.response.body} />}
            </div>
          )}

          {step.validationResults && step.validationResults.length > 0 && (
            <div style={styles.section}>
              <div style={styles.sectionTitle}>Validations</div>
              <ValidationsList validations={step.validationResults} />
            </div>
          )}

          {step.extractedVariables && Object.keys(step.extractedVariables).length > 0 && (
            <div style={styles.section}>
              <div style={styles.sectionTitle}>Variables extraites</div>
              {Object.entries(step.extractedVariables).map(([key, value]) => (
                <div key={key} style={styles.contextItem}>
                  <span style={styles.contextKey}>{key}</span>
                  <span style={styles.contextValue}>{JSON.stringify(value)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
// Main Component
// ─────────────────────────────────────────────────────────────────────────────

export const ResultDetail: React.FC<ResultDetailProps> = ({ result, onClose }) => {
  const [activeTab, setActiveTab] = useState<TabType>('overview');

  const isScenario = result.type === 'scenario';
  const tabs: { id: TabType; label: string; show: boolean }[] = [
    { id: 'overview', label: 'Aperçu', show: true },
    { id: 'request', label: 'Requête', show: !!result.request },
    { id: 'response', label: 'Réponse', show: !!result.response },
    { id: 'validations', label: 'Validations', show: !!result.validationResults?.length },
    { id: 'steps', label: `Étapes (${result.stepResults?.length || 0})`, show: isScenario },
    { id: 'context', label: 'Contexte', show: !!result.context && Object.keys(result.context).length > 0 },
  ];

  const visibleTabs = tabs.filter((t) => t.show);

  const renderOverview = () => (
    <div>
      {/* Status */}
      <div style={styles.section}>
        <div style={styles.sectionTitle}>Statut</div>
        <span
          style={{
            ...styles.statusBadge,
            backgroundColor: `${getTestStatusColor(result.status)}20`,
            color: getTestStatusColor(result.status),
            fontSize: '14px',
            padding: '8px 16px',
          }}
        >
          ● {getTestStatusLabel(result.status)}
        </span>
      </div>

      {/* Error */}
      {result.errorMessage && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Erreur</div>
          <div style={styles.errorBox}>{result.errorMessage}</div>
        </div>
      )}

      {/* Info */}
      <div style={styles.section}>
        <div style={styles.sectionTitle}>Informations</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div style={styles.contextItem}>
            <span style={styles.contextKey}>Type</span>
            <span style={styles.contextValue}>{result.type === 'quick' ? 'Test rapide' : 'Scénario'}</span>
          </div>
          <div style={styles.contextItem}>
            <span style={styles.contextKey}>Partenaire</span>
            <span style={styles.contextValue}>{result.partnerName}</span>
          </div>
          <div style={styles.contextItem}>
            <span style={styles.contextKey}>Environnement</span>
            <span style={styles.contextValue}>{result.environmentName}</span>
          </div>
          <div style={styles.contextItem}>
            <span style={styles.contextKey}>Durée</span>
            <span style={styles.contextValue}>{formatDuration(result.durationMs)}</span>
          </div>
        </div>
      </div>

      {/* Quick summary for scenarios */}
      {isScenario && result.stepResults && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Résumé des étapes</div>
          <div style={{ display: 'flex', gap: '16px' }}>
            <div style={{ ...styles.metric }}>
              <span style={styles.metricLabel}>Total</span>
              <span style={styles.metricValue}>{result.stepResults.length}</span>
            </div>
            <div style={{ ...styles.metric }}>
              <span style={styles.metricLabel}>Réussies</span>
              <span style={{ ...styles.metricValue, color: '#16a34a' }}>
                {result.stepResults.filter((s) => s.status === 'passed').length}
              </span>
            </div>
            <div style={{ ...styles.metric }}>
              <span style={styles.metricLabel}>Échouées</span>
              <span style={{ ...styles.metricValue, color: '#dc2626' }}>
                {result.stepResults.filter((s) => s.status === 'failed').length}
              </span>
            </div>
            <div style={{ ...styles.metric }}>
              <span style={styles.metricLabel}>Ignorées</span>
              <span style={{ ...styles.metricValue, color: '#6b7280' }}>
                {result.stepResults.filter((s) => s.status === 'skipped').length}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Validations summary */}
      {result.validationResults && result.validationResults.length > 0 && (
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Validations</div>
          <div style={{ display: 'flex', gap: '16px', marginBottom: '16px' }}>
            <span style={{ color: '#16a34a', fontWeight: 500 }}>
              [OK] {result.validationResults.filter((v) => v.passed).length} réussies
            </span>
            <span style={{ color: '#dc2626', fontWeight: 500 }}>
              [ERR] {result.validationResults.filter((v) => !v.passed).length} échouées
            </span>
          </div>
          <ValidationsList validations={result.validationResults.slice(0, 3)} />
          {result.validationResults.length > 3 && (
            <button
              style={{
                ...styles.tab,
                marginTop: '8px',
                color: '#3b82f6',
              }}
              onClick={() => setActiveTab('validations')}
            >
              Voir toutes les validations ({result.validationResults.length})
            </button>
          )}
        </div>
      )}
    </div>
  );

  const renderRequest = () => {
    if (!result.request) {
      return <div style={styles.emptyState}>Aucune requête disponible</div>;
    }

    return (
      <div>
        <div style={styles.section}>
          <div style={styles.sectionTitle}>URL</div>
          <div
            style={{
              backgroundColor: '#f9fafb',
              padding: '12px 16px',
              borderRadius: '6px',
              fontFamily: 'Monaco, Consolas, monospace',
              fontSize: '13px',
              wordBreak: 'break-all',
            }}
          >
            <span style={{ fontWeight: 600, color: '#3b82f6' }}>{result.request.method}</span>{' '}
            <span style={{ color: '#374151' }}>{result.request.url}</span>
          </div>
        </div>

        <HeadersTable headers={result.request.headers} title="Headers" />

        {result.request.body && <JsonViewer data={result.request.body} title="Body" />}
      </div>
    );
  };

  const renderResponse = () => {
    if (!result.response) {
      return <div style={styles.emptyState}>Aucune réponse disponible</div>;
    }

    const statusColor =
      result.response.status >= 200 && result.response.status < 300
        ? '#16a34a'
        : result.response.status >= 400
        ? '#dc2626'
        : '#f59e0b';

    return (
      <div>
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Statut HTTP</div>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              backgroundColor: `${statusColor}15`,
              padding: '8px 16px',
              borderRadius: '6px',
            }}
          >
            <span style={{ fontWeight: 600, fontSize: '18px', color: statusColor }}>{result.response.status}</span>
            <span style={{ color: '#6b7280' }}>{result.response.statusText}</span>
            <span style={{ color: '#9ca3af', fontSize: '12px' }}>({formatDuration(result.response.durationMs)})</span>
          </div>
        </div>

        <HeadersTable headers={result.response.headers} title="Headers" />

        {result.response.body && <JsonViewer data={result.response.body} title="Body" />}
      </div>
    );
  };

  const renderValidations = () => {
    if (!result.validationResults || result.validationResults.length === 0) {
      return <div style={styles.emptyState}>Aucune validation</div>;
    }

    return <ValidationsList validations={result.validationResults} />;
  };

  const renderSteps = () => {
    if (!result.stepResults || result.stepResults.length === 0) {
      return <div style={styles.emptyState}>Aucune étape</div>;
    }

    return (
      <div>
        {result.stepResults.map((step, index) => (
          <StepResultCard key={step.stepId} step={step} index={index} />
        ))}
      </div>
    );
  };

  const renderContext = () => {
    if (!result.context || Object.keys(result.context).length === 0) {
      return <div style={styles.emptyState}>Aucune variable de contexte</div>;
    }

    return (
      <div>
        <div style={styles.sectionTitle}>Variables extraites</div>
        {Object.entries(result.context).map(([key, value]) => (
          <div key={key} style={styles.contextItem}>
            <span style={styles.contextKey}>{key}</span>
            <span style={styles.contextValue}>
              {typeof value === 'object' ? JSON.stringify(value) : String(value)}
            </span>
          </div>
        ))}
      </div>
    );
  };

  const renderContent = () => {
    switch (activeTab) {
      case 'overview':
        return renderOverview();
      case 'request':
        return renderRequest();
      case 'response':
        return renderResponse();
      case 'validations':
        return renderValidations();
      case 'steps':
        return renderSteps();
      case 'context':
        return renderContext();
      default:
        return null;
    }
  };

  return (
    <div style={styles.container}>
      {/* Header */}
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <h2 style={styles.title}>{result.testName}</h2>
          <div style={styles.subtitle}>
            {new Date(result.startedAt).toLocaleString('fr-FR')} • {result.partnerName} • {result.environmentName}
          </div>
        </div>
        <button
          style={styles.closeButton}
          onClick={onClose}
          onMouseOver={(e) => {
            e.currentTarget.style.backgroundColor = '#f3f4f6';
          }}
          onMouseOut={(e) => {
            e.currentTarget.style.backgroundColor = 'transparent';
          }}
        >
          [X]
        </button>
      </div>

      {/* Metrics Bar */}
      <div style={styles.metricsBar}>
        <div style={styles.metric}>
          <span style={styles.metricLabel}>Statut</span>
          <span
            style={{
              ...styles.statusBadge,
              backgroundColor: `${getTestStatusColor(result.status)}20`,
              color: getTestStatusColor(result.status),
            }}
          >
            {getTestStatusLabel(result.status)}
          </span>
        </div>
        <div style={styles.metric}>
          <span style={styles.metricLabel}>Durée</span>
          <span style={styles.metricValue}>{formatDuration(result.durationMs)}</span>
        </div>
        {result.response && (
          <div style={styles.metric}>
            <span style={styles.metricLabel}>HTTP</span>
            <span
              style={{
                ...styles.metricValue,
                color:
                  result.response.status >= 200 && result.response.status < 300
                    ? '#16a34a'
                    : result.response.status >= 400
                    ? '#dc2626'
                    : '#f59e0b',
              }}
            >
              {result.response.status}
            </span>
          </div>
        )}
        {isScenario && result.stepResults && (
          <div style={styles.metric}>
            <span style={styles.metricLabel}>Étapes</span>
            <span style={styles.metricValue}>
              {result.stepResults.filter((s) => s.status === 'passed').length}/{result.stepResults.length}
            </span>
          </div>
        )}
      </div>

      {/* Tabs */}
      <div style={styles.tabs}>
        {visibleTabs.map((tab) => (
          <button
            key={tab.id}
            style={{
              ...styles.tab,
              ...(activeTab === tab.id ? styles.tabActive : {}),
            }}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content */}
      <div style={styles.content}>{renderContent()}</div>
    </div>
  );
};

export default ResultDetail;
