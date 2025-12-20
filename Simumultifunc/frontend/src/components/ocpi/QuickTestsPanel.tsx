/**
 * QuickTestsPanel - Panel principal pour les tests rapides OCPI
 * Arborescence par module avec configuration et exécution
 */

import React, { useState, useEffect } from 'react';
import { useShallow } from 'zustand/react/shallow';
import { useOCPIStore } from '@/store/ocpiStore';
import { QuickTestConfig } from './QuickTestConfig';
import { QuickTestResult } from './QuickTestResult';
import {
  OCPIQuickTest,
  OCPIModule,
  OCPI_MODULES,
  createEmptyQuickTest,
} from './types';

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'grid',
    gridTemplateColumns: '280px 1fr',
    gap: 16,
    height: '100%',
  },
  sidebar: {
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  sidebarHeader: {
    padding: 16,
    borderBottom: '1px solid #e5e7eb',
  },
  sidebarTitle: {
    fontSize: 16,
    fontWeight: 600,
    color: '#111827',
    margin: 0,
    marginBottom: 12,
  },
  partnerSelect: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    background: '#fff',
  },
  moduleList: {
    flex: 1,
    overflowY: 'auto',
    padding: 8,
  },
  moduleGroup: {
    marginBottom: 8,
  },
  moduleHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '10px 12px',
    background: '#f9fafb',
    borderRadius: 6,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
    border: '1px solid transparent',
  },
  moduleHeaderActive: {
    background: '#dbeafe',
    borderColor: '#3b82f6',
  },
  moduleIcon: {
    width: 8,
    height: 8,
    borderRadius: '50%',
  },
  moduleName: {
    flex: 1,
    fontSize: 14,
    fontWeight: 500,
    color: '#374151',
    textTransform: 'capitalize' as const,
  },
  moduleCount: {
    fontSize: 11,
    color: '#6b7280',
    background: '#e5e7eb',
    padding: '2px 6px',
    borderRadius: 10,
  },
  testList: {
    paddingLeft: 20,
    marginTop: 4,
  },
  testItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '8px 12px',
    borderRadius: 4,
    cursor: 'pointer',
    fontSize: 13,
    color: '#4b5563',
    transition: 'background 0.15s ease',
  },
  testItemHover: {
    background: '#f3f4f6',
  },
  testItemActive: {
    background: '#dbeafe',
    color: '#1d4ed8',
    fontWeight: 500,
  },
  main: {
    display: 'flex',
    flexDirection: 'column',
    gap: 16,
  },
  mainHeader: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
  },
  mainTitle: {
    fontSize: 18,
    fontWeight: 600,
    color: '#111827',
    margin: 0,
  },
  mainSubtitle: {
    fontSize: 13,
    color: '#6b7280',
    marginTop: 4,
  },
  actions: {
    display: 'flex',
    gap: 8,
  },
  btn: {
    padding: '8px 16px',
    borderRadius: 6,
    fontSize: 14,
    fontWeight: 500,
    cursor: 'pointer',
    border: 'none',
    transition: 'all 0.15s ease',
  },
  btnPrimary: {
    background: '#3b82f6',
    color: '#fff',
  },
  btnSecondary: {
    background: '#f3f4f6',
    color: '#374151',
  },
  btnSuccess: {
    background: '#10b981',
    color: '#fff',
  },
  btnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
  content: {
    flex: 1,
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 16,
  },
  emptyState: {
    gridColumn: '1 / -1',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 60,
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
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
    color: '#6b7280',
  },
  batchPanel: {
    gridColumn: '1 / -1',
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    padding: 16,
  },
  batchHeader: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  batchTitle: {
    fontSize: 14,
    fontWeight: 600,
    color: '#374151',
  },
  batchGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
    gap: 12,
  },
  batchItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: 12,
    border: '1px solid #e5e7eb',
    borderRadius: 6,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  batchItemSelected: {
    borderColor: '#3b82f6',
    background: '#eff6ff',
  },
  checkbox: {
    width: 16,
    height: 16,
    accentColor: '#3b82f6',
  },
};

// Module colors for visual distinction
const MODULE_COLORS: Record<OCPIModule, string> = {
  credentials: '#8b5cf6',
  locations: '#3b82f6',
  sessions: '#10b981',
  cdrs: '#f59e0b',
  tariffs: '#ec4899',
  tokens: '#6366f1',
  commands: '#ef4444',
  chargingprofiles: '#14b8a6',
  hubclientinfo: '#64748b',
};

// Default quick tests per module
const DEFAULT_TESTS: Record<OCPIModule, { name: string; method: string; path: string }[]> = {
  credentials: [
    { name: 'Get Credentials', method: 'GET', path: '/credentials' },
  ],
  locations: [
    { name: 'List Locations', method: 'GET', path: '/locations' },
    { name: 'Get Location', method: 'GET', path: '/locations/{location_id}' },
    { name: 'Get EVSE', method: 'GET', path: '/locations/{location_id}/{evse_uid}' },
  ],
  sessions: [
    { name: 'List Sessions', method: 'GET', path: '/sessions' },
    { name: 'Get Session', method: 'GET', path: '/sessions/{country_code}/{party_id}/{session_id}' },
  ],
  cdrs: [
    { name: 'List CDRs', method: 'GET', path: '/cdrs' },
    { name: 'Get CDR', method: 'GET', path: '/cdrs/{cdr_id}' },
  ],
  tariffs: [
    { name: 'List Tariffs', method: 'GET', path: '/tariffs' },
  ],
  tokens: [
    { name: 'List Tokens', method: 'GET', path: '/tokens' },
    { name: 'Authorize Token', method: 'POST', path: '/tokens/{token_uid}/authorize' },
  ],
  commands: [
    { name: 'Start Session', method: 'POST', path: '/commands/START_SESSION' },
    { name: 'Stop Session', method: 'POST', path: '/commands/STOP_SESSION' },
    { name: 'Reserve Now', method: 'POST', path: '/commands/RESERVE_NOW' },
    { name: 'Unlock Connector', method: 'POST', path: '/commands/UNLOCK_CONNECTOR' },
  ],
  chargingprofiles: [
    { name: 'Get Active Profile', method: 'GET', path: '/chargingprofiles/{session_id}' },
    { name: 'Set Profile', method: 'PUT', path: '/chargingprofiles/{session_id}' },
    { name: 'Delete Profile', method: 'DELETE', path: '/chargingprofiles/{session_id}' },
  ],
  hubclientinfo: [
    { name: 'Get Client Info', method: 'GET', path: '/hubclientinfo' },
  ],
};

export const QuickTestsPanel: React.FC = () => {
  const {
    partners,
    quickTests,
    selectedPartnerId,
    selectedQuickTestId,
    selectPartner,
    selectQuickTest,
    executeQuickTest,
    loadingQuickTests,
    fetchPartners,
  } = useOCPIStore(
    useShallow((state) => ({
      partners: state.partners,
      quickTests: state.quickTests,
      selectedPartnerId: state.selectedPartnerId,
      selectedQuickTestId: state.selectedQuickTestId,
      selectPartner: state.selectPartner,
      selectQuickTest: state.selectQuickTest,
      executeQuickTest: state.executeQuickTest,
      loadingQuickTests: state.loadingQuickTests,
      fetchPartners: state.fetchPartners,
    }))
  );

  const [expandedModules, setExpandedModules] = useState<Set<OCPIModule>>(new Set(['locations', 'sessions']));
  const [selectedTest, setSelectedTest] = useState<OCPIQuickTest | null>(null);
  const [testResult, setTestResult] = useState<any>(null);
  const [executing, setExecuting] = useState(false);
  const [batchMode, setBatchMode] = useState(false);
  const [selectedBatchTests, setSelectedBatchTests] = useState<Set<string>>(new Set());
  const [hoveredTest, setHoveredTest] = useState<string | null>(null);

  useEffect(() => {
    fetchPartners();
  }, [fetchPartners]);

  const selectedPartner = partners.find(p => p.id === selectedPartnerId);

  const toggleModule = (module: OCPIModule) => {
    const newExpanded = new Set(expandedModules);
    if (newExpanded.has(module)) {
      newExpanded.delete(module);
    } else {
      newExpanded.add(module);
    }
    setExpandedModules(newExpanded);
  };

  const handleSelectTest = (module: OCPIModule, testIndex: number) => {
    const tests = DEFAULT_TESTS[module];
    if (tests && tests[testIndex]) {
      const test = tests[testIndex];
      const quickTest: OCPIQuickTest = {
        id: `${module}-${testIndex}`,
        name: test.name,
        module,
        method: test.method as any,
        path: test.path,
        params: [],
        validations: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      setSelectedTest(quickTest);
      setTestResult(null);
    }
  };

  const handleExecuteTest = async () => {
    if (!selectedTest || !selectedPartnerId) return;

    setExecuting(true);
    try {
      const result = await executeQuickTest(selectedTest.id, selectedPartnerId);
      setTestResult(result);
    } catch (error: any) {
      setTestResult({
        success: false,
        error: error.message,
        timestamp: new Date().toISOString(),
      });
    } finally {
      setExecuting(false);
    }
  };

  const handleBatchToggle = (testId: string) => {
    const newSelected = new Set(selectedBatchTests);
    if (newSelected.has(testId)) {
      newSelected.delete(testId);
    } else {
      newSelected.add(testId);
    }
    setSelectedBatchTests(newSelected);
  };

  const handleExecuteBatch = async () => {
    if (selectedBatchTests.size === 0 || !selectedPartnerId) return;

    setExecuting(true);
    const results: any[] = [];

    for (const testId of selectedBatchTests) {
      try {
        const result = await executeQuickTest(testId, selectedPartnerId);
        results.push({ ...result, testId });
      } catch (error: any) {
        results.push({ success: false, error: error.message, testId });
      }
    }

    setTestResult({ batch: true, results });
    setExecuting(false);
  };

  // Group modules by category
  const moduleCategories = {
    'Core': ['credentials', 'locations'] as OCPIModule[],
    'Transactions': ['sessions', 'cdrs', 'tariffs'] as OCPIModule[],
    'Operations': ['tokens', 'commands', 'chargingprofiles'] as OCPIModule[],
    'Hub': ['hubclientinfo'] as OCPIModule[],
  };

  return (
    <div style={styles.container}>
      {/* Sidebar - Module Tree */}
      <div style={styles.sidebar}>
        <div style={styles.sidebarHeader}>
          <h3 style={styles.sidebarTitle}>Quick Tests</h3>
          <select
            style={styles.partnerSelect}
            value={selectedPartnerId || ''}
            onChange={(e) => selectPartner(e.target.value || null)}
          >
            <option value="">-- Sélectionner Partenaire --</option>
            {partners.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.role})
              </option>
            ))}
          </select>
        </div>

        <div style={styles.moduleList}>
          {Object.entries(moduleCategories).map(([category, modules]) => (
            <div key={category} style={{ marginBottom: 16 }}>
              <div style={{
                fontSize: 11,
                fontWeight: 600,
                color: '#9ca3af',
                textTransform: 'uppercase',
                letterSpacing: '0.5px',
                padding: '4px 12px',
                marginBottom: 4,
              }}>
                {category}
              </div>
              {modules.map((module) => {
                const tests = DEFAULT_TESTS[module];
                const isExpanded = expandedModules.has(module);
                const testCount = tests?.length || 0;

                return (
                  <div key={module} style={styles.moduleGroup}>
                    <div
                      style={{
                        ...styles.moduleHeader,
                        ...(isExpanded ? styles.moduleHeaderActive : {}),
                      }}
                      onClick={() => toggleModule(module)}
                    >
                      <span
                        style={{
                          ...styles.moduleIcon,
                          background: MODULE_COLORS[module],
                        }}
                      />
                      <span style={styles.moduleName}>{module}</span>
                      <span style={styles.moduleCount}>{testCount}</span>
                      <span style={{ fontSize: 10, color: '#9ca3af' }}>
                        {isExpanded ? '▼' : '▶'}
                      </span>
                    </div>

                    {isExpanded && tests && (
                      <div style={styles.testList}>
                        {tests.map((test, idx) => {
                          const testId = `${module}-${idx}`;
                          const isActive = selectedTest?.id === testId;
                          const isHovered = hoveredTest === testId;

                          return (
                            <div
                              key={idx}
                              style={{
                                ...styles.testItem,
                                ...(isHovered && !isActive ? styles.testItemHover : {}),
                                ...(isActive ? styles.testItemActive : {}),
                              }}
                              onClick={() => handleSelectTest(module, idx)}
                              onMouseEnter={() => setHoveredTest(testId)}
                              onMouseLeave={() => setHoveredTest(null)}
                            >
                              <span style={{
                                fontSize: 10,
                                fontWeight: 600,
                                padding: '2px 4px',
                                borderRadius: 3,
                                background: test.method === 'GET' ? '#dbeafe' :
                                           test.method === 'POST' ? '#dcfce7' :
                                           test.method === 'PUT' ? '#fef3c7' :
                                           test.method === 'DELETE' ? '#fee2e2' : '#f3f4f6',
                                color: test.method === 'GET' ? '#1e40af' :
                                       test.method === 'POST' ? '#166534' :
                                       test.method === 'PUT' ? '#92400e' :
                                       test.method === 'DELETE' ? '#991b1b' : '#374151',
                              }}>
                                {test.method}
                              </span>
                              <span>{test.name}</span>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      </div>

      {/* Main Content */}
      <div style={styles.main}>
        {/* Header */}
        <div style={styles.mainHeader}>
          <div>
            <h2 style={styles.mainTitle}>
              {selectedTest ? selectedTest.name : 'Tests Rapides OCPI'}
            </h2>
            <div style={styles.mainSubtitle}>
              {selectedTest
                ? `${selectedTest.method} ${selectedTest.path}`
                : 'Sélectionnez un test dans le menu à gauche'}
            </div>
          </div>
          <div style={styles.actions}>
            <button
              style={{
                ...styles.btn,
                ...styles.btnSecondary,
                ...(batchMode ? { background: '#dbeafe', color: '#1d4ed8' } : {}),
              }}
              onClick={() => setBatchMode(!batchMode)}
            >
              {batchMode ? 'Mode Simple' : 'Mode Batch'}
            </button>
            {selectedTest && !batchMode && (
              <button
                style={{
                  ...styles.btn,
                  ...styles.btnPrimary,
                  ...(!selectedPartnerId || executing ? styles.btnDisabled : {}),
                }}
                onClick={handleExecuteTest}
                disabled={!selectedPartnerId || executing}
              >
                {executing ? 'Exécution...' : 'Exécuter'}
              </button>
            )}
          </div>
        </div>

        {/* Content */}
        <div style={styles.content}>
          {batchMode ? (
            /* Batch Mode */
            <div style={styles.batchPanel}>
              <div style={styles.batchHeader}>
                <span style={styles.batchTitle}>
                  Sélectionnez les tests à exécuter ({selectedBatchTests.size} sélectionnés)
                </span>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button
                    style={{ ...styles.btn, ...styles.btnSecondary, padding: '6px 12px', fontSize: 12 }}
                    onClick={() => {
                      const allTests = new Set<string>();
                      Object.entries(DEFAULT_TESTS).forEach(([module, tests]) => {
                        tests.forEach((_, idx) => allTests.add(`${module}-${idx}`));
                      });
                      setSelectedBatchTests(allTests);
                    }}
                  >
                    Tout sélectionner
                  </button>
                  <button
                    style={{ ...styles.btn, ...styles.btnSecondary, padding: '6px 12px', fontSize: 12 }}
                    onClick={() => setSelectedBatchTests(new Set())}
                  >
                    Désélectionner
                  </button>
                  <button
                    style={{
                      ...styles.btn,
                      ...styles.btnSuccess,
                      padding: '6px 12px',
                      fontSize: 12,
                      ...(selectedBatchTests.size === 0 || !selectedPartnerId || executing ? styles.btnDisabled : {}),
                    }}
                    onClick={handleExecuteBatch}
                    disabled={selectedBatchTests.size === 0 || !selectedPartnerId || executing}
                  >
                    {executing ? 'Exécution...' : `Exécuter ${selectedBatchTests.size} tests`}
                  </button>
                </div>
              </div>
              <div style={styles.batchGrid}>
                {Object.entries(DEFAULT_TESTS).map(([module, tests]) =>
                  tests.map((test, idx) => {
                    const testId = `${module}-${idx}`;
                    const isSelected = selectedBatchTests.has(testId);

                    return (
                      <div
                        key={testId}
                        style={{
                          ...styles.batchItem,
                          ...(isSelected ? styles.batchItemSelected : {}),
                        }}
                        onClick={() => handleBatchToggle(testId)}
                      >
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => {}}
                          style={styles.checkbox}
                        />
                        <span
                          style={{
                            width: 6,
                            height: 6,
                            borderRadius: '50%',
                            background: MODULE_COLORS[module as OCPIModule],
                          }}
                        />
                        <span style={{ fontSize: 13, flex: 1 }}>{test.name}</span>
                        <span style={{
                          fontSize: 10,
                          fontWeight: 600,
                          color: '#6b7280',
                        }}>
                          {test.method}
                        </span>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          ) : selectedTest ? (
            /* Single Test Mode */
            <>
              <QuickTestConfig
                test={selectedTest}
                partner={selectedPartner}
                onChange={(updated) => setSelectedTest(updated)}
              />
              <QuickTestResult
                result={testResult}
                test={selectedTest}
              />
            </>
          ) : (
            /* Empty State */
            <div style={styles.emptyState}>
              <div style={styles.emptyIcon}>🧪</div>
              <div style={styles.emptyTitle}>Aucun test sélectionné</div>
              <div style={styles.emptyText}>
                Sélectionnez un test dans le menu à gauche pour commencer
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default QuickTestsPanel;
