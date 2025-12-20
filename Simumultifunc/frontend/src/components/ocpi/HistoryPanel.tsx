/**
 * HistoryPanel - Panel d'historique des tests OCPI
 * Affiche les résultats de tests avec filtres et détails
 */

import React, { useState, useEffect } from 'react';
import { useShallow } from 'zustand/react/shallow';
import { useOCPIStore } from '@/store/ocpiStore';
import { ResultDetail } from './ResultDetail';
import {
  OCPITestResult,
  TestStatus,
} from './types';

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'grid',
    gridTemplateColumns: '1fr 400px',
    gap: 16,
    height: '100%',
  },
  containerNoDetail: {
    gridTemplateColumns: '1fr',
  },
  main: {
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  header: {
    padding: 16,
    borderBottom: '1px solid #e5e7eb',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    fontSize: 16,
    fontWeight: 600,
    color: '#111827',
    margin: 0,
  },
  actions: {
    display: 'flex',
    gap: 8,
  },
  btn: {
    padding: '6px 12px',
    borderRadius: 6,
    fontSize: 13,
    fontWeight: 500,
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
  filtersBar: {
    padding: 12,
    borderBottom: '1px solid #e5e7eb',
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    flexWrap: 'wrap',
    background: '#f9fafb',
  },
  filterGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
  },
  filterLabel: {
    fontSize: 12,
    color: '#6b7280',
    fontWeight: 500,
  },
  filterSelect: {
    padding: '4px 8px',
    border: '1px solid #d1d5db',
    borderRadius: 4,
    fontSize: 12,
    background: '#fff',
  },
  filterInput: {
    padding: '4px 8px',
    border: '1px solid #d1d5db',
    borderRadius: 4,
    fontSize: 12,
    width: 120,
  },
  clearFilters: {
    fontSize: 12,
    color: '#6b7280',
    cursor: 'pointer',
    textDecoration: 'underline',
  },
  stats: {
    display: 'flex',
    gap: 16,
    padding: '8px 16px',
    borderBottom: '1px solid #e5e7eb',
    fontSize: 12,
  },
  statItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
  },
  statValue: {
    fontWeight: 600,
  },
  list: {
    flex: 1,
    overflowY: 'auto',
  },
  resultRow: {
    display: 'flex',
    alignItems: 'center',
    padding: '12px 16px',
    borderBottom: '1px solid #f3f4f6',
    cursor: 'pointer',
    transition: 'background 0.15s ease',
  },
  resultRowSelected: {
    background: '#eff6ff',
  },
  resultRowHover: {
    background: '#f9fafb',
  },
  statusDot: {
    width: 10,
    height: 10,
    borderRadius: '50%',
    marginRight: 12,
    flexShrink: 0,
  },
  resultInfo: {
    flex: 1,
    minWidth: 0,
  },
  resultName: {
    fontSize: 14,
    fontWeight: 500,
    color: '#111827',
    marginBottom: 2,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  resultMeta: {
    fontSize: 12,
    color: '#6b7280',
    display: 'flex',
    gap: 12,
  },
  resultStats: {
    display: 'flex',
    alignItems: 'center',
    gap: 16,
    fontSize: 12,
    flexShrink: 0,
  },
  badge: {
    padding: '2px 8px',
    borderRadius: 12,
    fontSize: 11,
    fontWeight: 500,
  },
  badgePassed: {
    background: '#dcfce7',
    color: '#166534',
  },
  badgeFailed: {
    background: '#fee2e2',
    color: '#991b1b',
  },
  badgeRunning: {
    background: '#dbeafe',
    color: '#1d4ed8',
  },
  badgePending: {
    background: '#f3f4f6',
    color: '#6b7280',
  },
  emptyState: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 60,
    color: '#6b7280',
    textAlign: 'center',
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 16,
    opacity: 0.3,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: 500,
    color: '#374151',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
  },
  pagination: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '12px 16px',
    borderTop: '1px solid #e5e7eb',
    fontSize: 13,
  },
  pageInfo: {
    color: '#6b7280',
  },
  pageButtons: {
    display: 'flex',
    gap: 4,
  },
  pageBtn: {
    padding: '4px 8px',
    background: '#f3f4f6',
    border: 'none',
    borderRadius: 4,
    fontSize: 12,
    cursor: 'pointer',
  },
  pageBtnActive: {
    background: '#3b82f6',
    color: '#fff',
  },
  pageBtnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
};

const STATUS_COLORS: Record<TestStatus, string> = {
  passed: '#10b981',
  failed: '#ef4444',
  error: '#f59e0b',
  skipped: '#9ca3af',
  running: '#3b82f6',
  pending: '#d1d5db',
};

export const HistoryPanel: React.FC = () => {
  const {
    results,
    partners,
    filters,
    loadingResults,
    fetchResults,
    setFilters,
    getFilteredResults,
  } = useOCPIStore(
    useShallow((state) => ({
      results: state.results,
      partners: state.partners,
      filters: state.filters,
      loadingResults: state.loadingResults,
      fetchResults: state.fetchResults,
      setFilters: state.setFilters,
      getFilteredResults: state.getFilteredResults,
    }))
  );

  const [selectedResultId, setSelectedResultId] = useState<string | null>(null);
  const [hoveredResultId, setHoveredResultId] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 20;

  useEffect(() => {
    fetchResults();
  }, [fetchResults]);

  const filteredResults = getFilteredResults();
  const selectedResult = results.find(r => r.id === selectedResultId);

  // Pagination
  const totalPages = Math.ceil(filteredResults.length / pageSize);
  const startIndex = (currentPage - 1) * pageSize;
  const paginatedResults = filteredResults.slice(startIndex, startIndex + pageSize);

  // Stats
  const passedCount = filteredResults.filter(r => r.status === 'passed').length;
  const failedCount = filteredResults.filter(r => r.status === 'failed').length;
  const avgDuration = filteredResults.length > 0
    ? Math.round(filteredResults.reduce((sum, r) => sum + r.durationMs, 0) / filteredResults.length)
    : 0;

  const handleFilterChange = (key: string, value: any) => {
    setFilters({
      history: {
        ...filters.history,
        [key]: value || undefined,
      },
    });
    setCurrentPage(1);
  };

  const clearFilters = () => {
    setFilters({
      history: {
        partnerId: undefined,
        status: undefined,
        type: undefined,
        dateFrom: undefined,
        dateTo: undefined,
      },
    });
    setCurrentPage(1);
  };

  const hasFilters = filters.history.partnerId || filters.history.status ||
    filters.history.type || filters.history.dateFrom || filters.history.dateTo;

  const getStatusBadgeStyle = (status: TestStatus) => {
    switch (status) {
      case 'passed': return styles.badgePassed;
      case 'failed': return styles.badgeFailed;
      case 'running': return styles.badgeRunning;
      default: return styles.badgePending;
    }
  };

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <div style={{
      ...styles.container,
      ...(selectedResult ? {} : styles.containerNoDetail),
    }}>
      {/* Main List */}
      <div style={styles.main}>
        {/* Header */}
        <div style={styles.header}>
          <h3 style={styles.title}>Historique des Tests</h3>
          <div style={styles.actions}>
            <button
              style={{ ...styles.btn, ...styles.btnSecondary }}
              onClick={() => fetchResults()}
            >
              Rafraîchir
            </button>
            <button
              style={{ ...styles.btn, ...styles.btnDanger }}
              onClick={() => {
                if (confirm('Nettoyer les anciens résultats ?')) {
                  // TODO: Implement cleanup
                }
              }}
            >
              Nettoyer
            </button>
          </div>
        </div>

        {/* Filters */}
        <div style={styles.filtersBar}>
          <div style={styles.filterGroup}>
            <span style={styles.filterLabel}>Partenaire:</span>
            <select
              style={styles.filterSelect}
              value={filters.history.partnerId || ''}
              onChange={(e) => handleFilterChange('partnerId', e.target.value)}
            >
              <option value="">Tous</option>
              {partners.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>

          <div style={styles.filterGroup}>
            <span style={styles.filterLabel}>Status:</span>
            <select
              style={styles.filterSelect}
              value={filters.history.status || ''}
              onChange={(e) => handleFilterChange('status', e.target.value)}
            >
              <option value="">Tous</option>
              <option value="passed">Passed</option>
              <option value="failed">Failed</option>
              <option value="error">Error</option>
              <option value="running">Running</option>
            </select>
          </div>

          <div style={styles.filterGroup}>
            <span style={styles.filterLabel}>Type:</span>
            <select
              style={styles.filterSelect}
              value={filters.history.type || ''}
              onChange={(e) => handleFilterChange('type', e.target.value)}
            >
              <option value="">Tous</option>
              <option value="quick">Quick Test</option>
              <option value="scenario">Scénario</option>
            </select>
          </div>

          <div style={styles.filterGroup}>
            <span style={styles.filterLabel}>Depuis:</span>
            <input
              type="date"
              style={styles.filterInput}
              value={filters.history.dateFrom || ''}
              onChange={(e) => handleFilterChange('dateFrom', e.target.value)}
            />
          </div>

          <div style={styles.filterGroup}>
            <span style={styles.filterLabel}>Jusqu'au:</span>
            <input
              type="date"
              style={styles.filterInput}
              value={filters.history.dateTo || ''}
              onChange={(e) => handleFilterChange('dateTo', e.target.value)}
            />
          </div>

          {hasFilters && (
            <span style={styles.clearFilters} onClick={clearFilters}>
              Effacer filtres
            </span>
          )}
        </div>

        {/* Stats */}
        <div style={styles.stats}>
          <div style={styles.statItem}>
            <span>Total:</span>
            <span style={styles.statValue}>{filteredResults.length}</span>
          </div>
          <div style={styles.statItem}>
            <span style={{ color: '#10b981' }}>Passed:</span>
            <span style={{ ...styles.statValue, color: '#10b981' }}>{passedCount}</span>
          </div>
          <div style={styles.statItem}>
            <span style={{ color: '#ef4444' }}>Failed:</span>
            <span style={{ ...styles.statValue, color: '#ef4444' }}>{failedCount}</span>
          </div>
          <div style={styles.statItem}>
            <span>Durée moy:</span>
            <span style={styles.statValue}>{avgDuration}ms</span>
          </div>
        </div>

        {/* List */}
        <div style={styles.list}>
          {loadingResults ? (
            <div style={{ textAlign: 'center', padding: 40, color: '#6b7280' }}>
              Chargement...
            </div>
          ) : paginatedResults.length === 0 ? (
            <div style={styles.emptyState}>
              <div style={styles.emptyIcon}>📊</div>
              <div style={styles.emptyTitle}>
                {hasFilters ? 'Aucun résultat trouvé' : 'Aucun historique'}
              </div>
              <div style={styles.emptyText}>
                {hasFilters
                  ? 'Modifiez vos filtres pour voir plus de résultats'
                  : 'Exécutez des tests pour voir les résultats ici'}
              </div>
            </div>
          ) : (
            paginatedResults.map((result) => {
              const isSelected = selectedResultId === result.id;
              const isHovered = hoveredResultId === result.id;

              return (
                <div
                  key={result.id}
                  style={{
                    ...styles.resultRow,
                    ...(isSelected ? styles.resultRowSelected : {}),
                    ...(isHovered && !isSelected ? styles.resultRowHover : {}),
                  }}
                  onClick={() => setSelectedResultId(isSelected ? null : result.id)}
                  onMouseEnter={() => setHoveredResultId(result.id)}
                  onMouseLeave={() => setHoveredResultId(null)}
                >
                  <div
                    style={{
                      ...styles.statusDot,
                      background: STATUS_COLORS[result.status],
                    }}
                  />
                  <div style={styles.resultInfo}>
                    <div style={styles.resultName}>{result.testName}</div>
                    <div style={styles.resultMeta}>
                      <span>{result.partnerName}</span>
                      <span>{result.environmentName}</span>
                      <span>{formatDate(result.startedAt)}</span>
                    </div>
                  </div>
                  <div style={styles.resultStats}>
                    <span style={{
                      ...styles.badge,
                      ...getStatusBadgeStyle(result.status),
                    }}>
                      {result.status.toUpperCase()}
                    </span>
                    <span style={{ color: '#6b7280' }}>{result.durationMs}ms</span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div style={styles.pagination}>
            <span style={styles.pageInfo}>
              {startIndex + 1} - {Math.min(startIndex + pageSize, filteredResults.length)} sur {filteredResults.length}
            </span>
            <div style={styles.pageButtons}>
              <button
                style={{
                  ...styles.pageBtn,
                  ...(currentPage === 1 ? styles.pageBtnDisabled : {}),
                }}
                onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
                disabled={currentPage === 1}
              >
                Préc.
              </button>
              {[...Array(Math.min(5, totalPages))].map((_, i) => {
                const page = i + 1;
                return (
                  <button
                    key={page}
                    style={{
                      ...styles.pageBtn,
                      ...(currentPage === page ? styles.pageBtnActive : {}),
                    }}
                    onClick={() => setCurrentPage(page)}
                  >
                    {page}
                  </button>
                );
              })}
              <button
                style={{
                  ...styles.pageBtn,
                  ...(currentPage === totalPages ? styles.pageBtnDisabled : {}),
                }}
                onClick={() => setCurrentPage(Math.min(totalPages, currentPage + 1))}
                disabled={currentPage === totalPages}
              >
                Suiv.
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Detail Panel */}
      {selectedResult && (
        <ResultDetail
          result={selectedResult}
          onClose={() => setSelectedResultId(null)}
        />
      )}
    </div>
  );
};

export default HistoryPanel;
