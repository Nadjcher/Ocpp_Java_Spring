/**
 * QuickTestResult - Affichage des résultats d'un test rapide OCPI
 */

import React, { useState } from 'react';
import { OCPIQuickTest } from './types';

interface QuickTestResultProps {
  result: any | null;
  test: OCPIQuickTest;
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    overflow: 'hidden',
  },
  header: {
    padding: 16,
    borderBottom: '1px solid #e5e7eb',
    background: '#f9fafb',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    fontSize: 14,
    fontWeight: 600,
    color: '#374151',
    margin: 0,
  },
  status: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  statusBadge: {
    padding: '4px 12px',
    borderRadius: 20,
    fontSize: 12,
    fontWeight: 600,
  },
  statusSuccess: {
    background: '#dcfce7',
    color: '#166534',
  },
  statusFailed: {
    background: '#fee2e2',
    color: '#991b1b',
  },
  statusPending: {
    background: '#f3f4f6',
    color: '#6b7280',
  },
  content: {
    padding: 16,
  },
  emptyState: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 40,
    color: '#6b7280',
    textAlign: 'center',
  },
  emptyIcon: {
    fontSize: 32,
    marginBottom: 12,
    opacity: 0.5,
  },
  emptyText: {
    fontSize: 14,
  },
  section: {
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 12,
    fontWeight: 600,
    color: '#6b7280',
    textTransform: 'uppercase' as const,
    letterSpacing: '0.5px',
    marginBottom: 12,
  },
  metricsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(3, 1fr)',
    gap: 12,
  },
  metricCard: {
    padding: 12,
    background: '#f9fafb',
    borderRadius: 6,
    textAlign: 'center',
  },
  metricValue: {
    fontSize: 20,
    fontWeight: 600,
    color: '#111827',
  },
  metricLabel: {
    fontSize: 11,
    color: '#6b7280',
    marginTop: 4,
  },
  errorBox: {
    padding: 16,
    background: '#fef2f2',
    border: '1px solid #fecaca',
    borderRadius: 8,
    color: '#991b1b',
  },
  errorTitle: {
    fontWeight: 600,
    marginBottom: 8,
  },
  errorMessage: {
    fontSize: 13,
    fontFamily: 'ui-monospace, monospace',
  },
  requestInfo: {
    padding: 12,
    background: '#f9fafb',
    borderRadius: 6,
    marginBottom: 12,
  },
  requestLine: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    fontFamily: 'ui-monospace, monospace',
    fontSize: 13,
  },
  methodBadge: {
    padding: '2px 8px',
    borderRadius: 4,
    fontSize: 11,
    fontWeight: 600,
  },
  headersTable: {
    width: '100%',
    fontSize: 12,
    borderCollapse: 'collapse',
  },
  headerRow: {
    borderBottom: '1px solid #e5e7eb',
  },
  headerKey: {
    padding: '8px 12px',
    fontWeight: 500,
    color: '#374151',
    background: '#f9fafb',
    width: '30%',
  },
  headerValue: {
    padding: '8px 12px',
    color: '#6b7280',
    fontFamily: 'ui-monospace, monospace',
    wordBreak: 'break-all',
  },
  jsonViewer: {
    background: '#1e293b',
    color: '#e2e8f0',
    padding: 16,
    borderRadius: 8,
    overflow: 'auto',
    maxHeight: 400,
    fontSize: 12,
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
  },
  tabs: {
    display: 'flex',
    gap: 4,
    marginBottom: 16,
  },
  tab: {
    padding: '8px 16px',
    background: '#f3f4f6',
    border: 'none',
    borderRadius: 6,
    fontSize: 13,
    fontWeight: 500,
    color: '#6b7280',
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  tabActive: {
    background: '#3b82f6',
    color: '#fff',
  },
  validationList: {
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  },
  validationItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    padding: 12,
    background: '#f9fafb',
    borderRadius: 6,
    fontSize: 13,
  },
  validationIcon: {
    width: 20,
    height: 20,
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 12,
  },
  validationPassed: {
    background: '#dcfce7',
    color: '#166534',
  },
  validationFailed: {
    background: '#fee2e2',
    color: '#991b1b',
  },
  batchResults: {
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  },
  batchItem: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 12,
    background: '#f9fafb',
    borderRadius: 6,
    cursor: 'pointer',
    transition: 'background 0.15s ease',
  },
  batchItemHover: {
    background: '#e5e7eb',
  },
  copyBtn: {
    padding: '4px 8px',
    background: '#f3f4f6',
    border: 'none',
    borderRadius: 4,
    fontSize: 11,
    color: '#6b7280',
    cursor: 'pointer',
  },
};

type ResultTab = 'response' | 'headers' | 'validations';

export const QuickTestResult: React.FC<QuickTestResultProps> = ({
  result,
  test,
}) => {
  const [activeTab, setActiveTab] = useState<ResultTab>('response');
  const [expandedBatch, setExpandedBatch] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const getMethodColor = (method: string) => {
    switch (method) {
      case 'GET': return { bg: '#dbeafe', color: '#1e40af' };
      case 'POST': return { bg: '#dcfce7', color: '#166534' };
      case 'PUT': return { bg: '#fef3c7', color: '#92400e' };
      case 'PATCH': return { bg: '#fae8ff', color: '#86198f' };
      case 'DELETE': return { bg: '#fee2e2', color: '#991b1b' };
      default: return { bg: '#f3f4f6', color: '#374151' };
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const formatJson = (data: any) => {
    try {
      if (typeof data === 'string') {
        return JSON.stringify(JSON.parse(data), null, 2);
      }
      return JSON.stringify(data, null, 2);
    } catch {
      return String(data);
    }
  };

  // Handle batch results
  if (result?.batch) {
    return (
      <div style={styles.container}>
        <div style={styles.header}>
          <h3 style={styles.title}>Résultats Batch</h3>
          <div style={styles.status}>
            <span>{result.results.filter((r: any) => r.success).length}/{result.results.length} réussis</span>
          </div>
        </div>
        <div style={styles.content}>
          <div style={styles.batchResults}>
            {result.results.map((r: any, idx: number) => (
              <div
                key={idx}
                style={{
                  ...styles.batchItem,
                  borderLeft: `4px solid ${r.success ? '#10b981' : '#ef4444'}`,
                }}
                onClick={() => setExpandedBatch(expandedBatch === r.testId ? null : r.testId)}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  <span style={{
                    ...styles.validationIcon,
                    ...(r.success ? styles.validationPassed : styles.validationFailed),
                  }}>
                    {r.success ? '[OK]' : '[ERR]'}
                  </span>
                  <span style={{ fontWeight: 500 }}>{r.testId}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                  {r.latencyMs && (
                    <span style={{ fontSize: 12, color: '#6b7280' }}>{r.latencyMs}ms</span>
                  )}
                  <span style={{ fontSize: 12, color: '#9ca3af' }}>
                    {expandedBatch === r.testId ? 'v' : '>'}
                  </span>
                </div>
              </div>
            ))}
          </div>

          {expandedBatch && (
            <div style={{ marginTop: 16 }}>
              <pre style={styles.jsonViewer}>
                {formatJson(result.results.find((r: any) => r.testId === expandedBatch))}
              </pre>
            </div>
          )}
        </div>
      </div>
    );
  }

  // No result yet
  if (!result) {
    return (
      <div style={styles.container}>
        <div style={styles.header}>
          <h3 style={styles.title}>Résultat du Test</h3>
        </div>
        <div style={styles.content}>
          <div style={styles.emptyState}>
            <div style={styles.emptyIcon}>⏳</div>
            <div style={styles.emptyText}>
              Exécutez le test pour voir les résultats
            </div>
          </div>
        </div>
      </div>
    );
  }

  const isSuccess = result.success || result.ok;
  const methodColor = getMethodColor(test.method);

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h3 style={styles.title}>Résultat du Test</h3>
        <div style={styles.status}>
          <span
            style={{
              ...styles.statusBadge,
              ...(isSuccess ? styles.statusSuccess : styles.statusFailed),
            }}
          >
            {isSuccess ? 'SUCCÈS' : 'ÉCHEC'}
          </span>
        </div>
      </div>

      <div style={styles.content}>
        {/* Error display */}
        {result.error && (
          <div style={styles.errorBox}>
            <div style={styles.errorTitle}>Erreur</div>
            <div style={styles.errorMessage}>{result.error}</div>
          </div>
        )}

        {/* Metrics */}
        {!result.error && (
          <div style={styles.section}>
            <div style={styles.sectionTitle}>Métriques</div>
            <div style={styles.metricsGrid}>
              <div style={styles.metricCard}>
                <div style={{
                  ...styles.metricValue,
                  color: result.httpStatus >= 200 && result.httpStatus < 300 ? '#10b981' : '#ef4444',
                }}>
                  {result.httpStatus || result.statusCode || '-'}
                </div>
                <div style={styles.metricLabel}>HTTP Status</div>
              </div>
              <div style={styles.metricCard}>
                <div style={styles.metricValue}>
                  {result.latencyMs || result.latency || '-'}
                </div>
                <div style={styles.metricLabel}>Latence (ms)</div>
              </div>
              <div style={styles.metricCard}>
                <div style={styles.metricValue}>
                  {result.ocpiStatus || '-'}
                </div>
                <div style={styles.metricLabel}>OCPI Status</div>
              </div>
            </div>
          </div>
        )}

        {/* Request Info */}
        <div style={styles.section}>
          <div style={styles.sectionTitle}>Requête</div>
          <div style={styles.requestInfo}>
            <div style={styles.requestLine}>
              <span style={{
                ...styles.methodBadge,
                background: methodColor.bg,
                color: methodColor.color,
              }}>
                {test.method}
              </span>
              <span>{result.requestUrl || test.path}</span>
            </div>
          </div>
        </div>

        {/* Tabs for Response, Headers, Validations */}
        <div style={styles.tabs}>
          {(['response', 'headers', 'validations'] as ResultTab[]).map((tab) => (
            <button
              key={tab}
              style={{
                ...styles.tab,
                ...(activeTab === tab ? styles.tabActive : {}),
              }}
              onClick={() => setActiveTab(tab)}
            >
              {tab === 'response' && 'Réponse'}
              {tab === 'headers' && 'Headers'}
              {tab === 'validations' && 'Validations'}
            </button>
          ))}
        </div>

        {/* Response Tab */}
        {activeTab === 'response' && (
          <div style={styles.section}>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 8 }}>
              <button
                style={styles.copyBtn}
                onClick={() => copyToClipboard(formatJson(result.data || result.rawBody || result))}
              >
                {copied ? 'Copié !' : 'Copier'}
              </button>
            </div>
            <pre style={styles.jsonViewer}>
              {formatJson(result.data || result.rawBody || result)}
            </pre>
          </div>
        )}

        {/* Headers Tab */}
        {activeTab === 'headers' && (
          <div style={styles.section}>
            {result.headers && Object.keys(result.headers).length > 0 ? (
              <table style={styles.headersTable}>
                <tbody>
                  {Object.entries(result.headers).map(([key, value]) => (
                    <tr key={key} style={styles.headerRow}>
                      <td style={styles.headerKey}>{key}</td>
                      <td style={styles.headerValue}>{String(value)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div style={{ textAlign: 'center', padding: 20, color: '#6b7280' }}>
                Aucun header disponible
              </div>
            )}
          </div>
        )}

        {/* Validations Tab */}
        {activeTab === 'validations' && (
          <div style={styles.section}>
            {result.validations && result.validations.length > 0 ? (
              <div style={styles.validationList}>
                {result.validations.map((v: any, idx: number) => (
                  <div key={idx} style={styles.validationItem}>
                    <span style={{
                      ...styles.validationIcon,
                      ...(v.passed ? styles.validationPassed : styles.validationFailed),
                    }}>
                      {v.passed ? '[OK]' : '[ERR]'}
                    </span>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 500 }}>{v.field}</div>
                      <div style={{ fontSize: 12, color: '#6b7280' }}>
                        {v.operator} {v.expected}
                      </div>
                    </div>
                    {!v.passed && v.actual && (
                      <div style={{ fontSize: 12, color: '#ef4444' }}>
                        Reçu: {v.actual}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            ) : test.validations && test.validations.length > 0 ? (
              <div style={styles.validationList}>
                {test.validations.map((v, idx) => (
                  <div key={idx} style={styles.validationItem}>
                    <span style={{
                      ...styles.validationIcon,
                      background: '#f3f4f6',
                      color: '#6b7280',
                    }}>
                      ?
                    </span>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 500 }}>{v.field}</div>
                      <div style={{ fontSize: 12, color: '#6b7280' }}>
                        {v.operator} {v.expected}
                      </div>
                    </div>
                    <span style={{ fontSize: 11, color: '#9ca3af' }}>En attente</span>
                  </div>
                ))}
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: 20, color: '#6b7280' }}>
                Aucune validation configurée
              </div>
            )}
          </div>
        )}

        {/* Timestamp */}
        {result.timestamp && (
          <div style={{ fontSize: 11, color: '#9ca3af', textAlign: 'right' }}>
            Exécuté le {new Date(result.timestamp).toLocaleString()}
          </div>
        )}
      </div>
    </div>
  );
};

export default QuickTestResult;
