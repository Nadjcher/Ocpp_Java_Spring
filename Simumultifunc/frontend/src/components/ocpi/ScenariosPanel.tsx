/**
 * ScenariosPanel - Panel principal pour les scénarios de tests OCPI
 * Liste avec tags, filtres et exécution de scénarios
 */

import React, { useState, useEffect } from 'react';
import { useShallow } from 'zustand/react/shallow';
import { useOCPIStore } from '@/store/ocpiStore';
import { ScenarioEditor } from './ScenarioEditor';
import {
  OCPITestScenario,
  ScenarioCategory,
  TestStatus,
  SCENARIO_CATEGORIES,
} from './types';

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'grid',
    gridTemplateColumns: '320px 1fr',
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
  searchInput: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    marginBottom: 12,
  },
  filterRow: {
    display: 'flex',
    gap: 8,
    flexWrap: 'wrap',
  },
  filterChip: {
    padding: '4px 10px',
    borderRadius: 16,
    fontSize: 12,
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
    border: '1px solid transparent',
  },
  filterChipActive: {
    background: '#dbeafe',
    color: '#1d4ed8',
    borderColor: '#3b82f6',
  },
  filterChipInactive: {
    background: '#f3f4f6',
    color: '#6b7280',
  },
  scenarioList: {
    flex: 1,
    overflowY: 'auto',
    padding: 8,
  },
  scenarioCard: {
    padding: 12,
    border: '1px solid #e5e7eb',
    borderRadius: 6,
    marginBottom: 8,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
    background: '#fff',
  },
  scenarioCardSelected: {
    borderColor: '#3b82f6',
    background: '#eff6ff',
  },
  scenarioCardHover: {
    borderColor: '#d1d5db',
    background: '#f9fafb',
  },
  scenarioName: {
    fontSize: 14,
    fontWeight: 500,
    color: '#111827',
    marginBottom: 4,
  },
  scenarioMeta: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    fontSize: 12,
    color: '#6b7280',
  },
  categoryBadge: {
    padding: '2px 6px',
    borderRadius: 4,
    fontSize: 10,
    fontWeight: 600,
    textTransform: 'uppercase' as const,
  },
  tagsList: {
    display: 'flex',
    gap: 4,
    marginTop: 8,
    flexWrap: 'wrap',
  },
  tag: {
    padding: '2px 6px',
    background: '#f3f4f6',
    borderRadius: 4,
    fontSize: 10,
    color: '#6b7280',
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
  btnDanger: {
    background: '#fee2e2',
    color: '#dc2626',
  },
  btnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
  content: {
    flex: 1,
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    overflow: 'hidden',
  },
  emptyState: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 60,
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
    marginBottom: 16,
  },
  executionPanel: {
    padding: 16,
    borderBottom: '1px solid #e5e7eb',
    background: '#f9fafb',
  },
  executionRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  },
  selectPartner: {
    flex: 1,
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
  },
  progressBar: {
    height: 4,
    background: '#e5e7eb',
    borderRadius: 2,
    overflow: 'hidden',
    marginTop: 12,
  },
  progressFill: {
    height: '100%',
    background: '#3b82f6',
    transition: 'width 0.3s ease',
  },
  stepsPreview: {
    padding: 16,
    maxHeight: 400,
    overflowY: 'auto',
  },
  stepItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    padding: 12,
    borderBottom: '1px solid #f3f4f6',
  },
  stepNumber: {
    width: 24,
    height: 24,
    borderRadius: '50%',
    background: '#e5e7eb',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 12,
    fontWeight: 600,
    color: '#6b7280',
  },
  stepInfo: {
    flex: 1,
  },
  stepName: {
    fontSize: 14,
    fontWeight: 500,
    color: '#111827',
  },
  stepDetails: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  stepStatus: {
    padding: '4px 8px',
    borderRadius: 4,
    fontSize: 11,
    fontWeight: 600,
  },
  statusPassed: {
    background: '#dcfce7',
    color: '#166534',
  },
  statusFailed: {
    background: '#fee2e2',
    color: '#991b1b',
  },
  statusRunning: {
    background: '#dbeafe',
    color: '#1d4ed8',
  },
  statusPending: {
    background: '#f3f4f6',
    color: '#6b7280',
  },
  addBtn: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 12,
    border: '2px dashed #d1d5db',
    borderRadius: 6,
    background: 'transparent',
    color: '#6b7280',
    fontSize: 14,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
    width: '100%',
  },
};

// Category colors
const CATEGORY_COLORS: Record<ScenarioCategory, { bg: string; color: string }> = {
  smoke: { bg: '#dcfce7', color: '#166534' },
  integration: { bg: '#e0e7ff', color: '#3730a3' },
  regression: { bg: '#fef3c7', color: '#92400e' },
  e2e: { bg: '#dbeafe', color: '#1d4ed8' },
  performance: { bg: '#fae8ff', color: '#86198f' },
  security: { bg: '#fee2e2', color: '#991b1b' },
  custom: { bg: '#f3f4f6', color: '#374151' },
};

export const ScenariosPanel: React.FC = () => {
  const {
    partners,
    scenarios,
    selectedPartnerId,
    selectedScenarioId,
    loadingScenarios,
    selectPartner,
    selectScenario,
    fetchScenarios,
    fetchPartners,
    executeScenario,
    createScenario,
    deleteScenario,
  } = useOCPIStore(
    useShallow((state) => ({
      partners: state.partners,
      scenarios: state.scenarios,
      selectedPartnerId: state.selectedPartnerId,
      selectedScenarioId: state.selectedScenarioId,
      loadingScenarios: state.loadingScenarios,
      selectPartner: state.selectPartner,
      selectScenario: state.selectScenario,
      fetchScenarios: state.fetchScenarios,
      fetchPartners: state.fetchPartners,
      executeScenario: state.executeScenario,
      createScenario: state.createScenario,
      deleteScenario: state.deleteScenario,
    }))
  );

  const [searchTerm, setSearchTerm] = useState('');
  const [filterCategory, setFilterCategory] = useState<ScenarioCategory | null>(null);
  const [filterTags, setFilterTags] = useState<string[]>([]);
  const [hoveredScenario, setHoveredScenario] = useState<string | null>(null);
  const [executing, setExecuting] = useState(false);
  const [executionProgress, setExecutionProgress] = useState(0);
  const [editMode, setEditMode] = useState(false);

  useEffect(() => {
    fetchScenarios();
    fetchPartners();
  }, [fetchScenarios, fetchPartners]);

  const selectedScenario = scenarios.find(s => s.id === selectedScenarioId);
  const selectedPartner = partners.find(p => p.id === selectedPartnerId);

  // Get all unique tags
  const allTags = [...new Set(scenarios.flatMap(s => s.tags))];

  // Filter scenarios
  const filteredScenarios = scenarios.filter(s => {
    if (searchTerm && !s.name.toLowerCase().includes(searchTerm.toLowerCase())) {
      return false;
    }
    if (filterCategory && s.category !== filterCategory) {
      return false;
    }
    if (filterTags.length > 0 && !filterTags.some(t => s.tags.includes(t))) {
      return false;
    }
    return true;
  });

  const handleExecute = async () => {
    if (!selectedScenario || !selectedPartnerId) return;

    setExecuting(true);
    setExecutionProgress(0);

    try {
      // Simulate progress
      const progressInterval = setInterval(() => {
        setExecutionProgress(prev => Math.min(prev + 10, 90));
      }, 500);

      await executeScenario(selectedScenario.id, selectedPartnerId);

      clearInterval(progressInterval);
      setExecutionProgress(100);

      setTimeout(() => {
        setExecutionProgress(0);
        setExecuting(false);
      }, 1000);
    } catch (error) {
      setExecuting(false);
      setExecutionProgress(0);
    }
  };

  const handleDelete = async () => {
    if (!selectedScenario) return;
    if (confirm(`Supprimer le scénario "${selectedScenario.name}" ?`)) {
      await deleteScenario(selectedScenario.id);
      selectScenario(null);
    }
  };

  const handleCreateNew = async () => {
    const newScenario = await createScenario({
      name: 'Nouveau Scénario',
      description: 'Description du scénario',
      tags: [],
      category: 'custom',
      priority: 'medium',
      enabled: true,
      steps: [],
      variables: {},
    });
    selectScenario(newScenario.id);
    setEditMode(true);
  };

  const toggleTag = (tag: string) => {
    if (filterTags.includes(tag)) {
      setFilterTags(filterTags.filter(t => t !== tag));
    } else {
      setFilterTags([...filterTags, tag]);
    }
  };

  const getStatusStyle = (status: TestStatus) => {
    switch (status) {
      case 'passed': return styles.statusPassed;
      case 'failed': return styles.statusFailed;
      case 'running': return styles.statusRunning;
      default: return styles.statusPending;
    }
  };

  return (
    <div style={styles.container}>
      {/* Sidebar - Scenario List */}
      <div style={styles.sidebar}>
        <div style={styles.sidebarHeader}>
          <h3 style={styles.sidebarTitle}>Scénarios de Tests</h3>
          <input
            type="text"
            placeholder="Rechercher..."
            style={styles.searchInput}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          <div style={styles.filterRow}>
            {SCENARIO_CATEGORIES.map((cat) => (
              <span
                key={cat.id}
                style={{
                  ...styles.filterChip,
                  ...(filterCategory === cat.id ? styles.filterChipActive : styles.filterChipInactive),
                }}
                onClick={() => setFilterCategory(filterCategory === cat.id ? null : cat.id)}
              >
                {cat.label}
              </span>
            ))}
          </div>
        </div>

        <div style={styles.scenarioList}>
          {loadingScenarios ? (
            <div style={{ textAlign: 'center', padding: 20, color: '#6b7280' }}>
              Chargement...
            </div>
          ) : filteredScenarios.length === 0 ? (
            <div style={{ textAlign: 'center', padding: 20, color: '#6b7280' }}>
              Aucun scénario trouvé
            </div>
          ) : (
            filteredScenarios.map((scenario) => {
              const isSelected = selectedScenarioId === scenario.id;
              const isHovered = hoveredScenario === scenario.id;
              const catColor = CATEGORY_COLORS[scenario.category];

              return (
                <div
                  key={scenario.id}
                  style={{
                    ...styles.scenarioCard,
                    ...(isSelected ? styles.scenarioCardSelected : {}),
                    ...(isHovered && !isSelected ? styles.scenarioCardHover : {}),
                  }}
                  onClick={() => selectScenario(scenario.id)}
                  onMouseEnter={() => setHoveredScenario(scenario.id)}
                  onMouseLeave={() => setHoveredScenario(null)}
                >
                  <div style={styles.scenarioName}>{scenario.name}</div>
                  <div style={styles.scenarioMeta}>
                    <span
                      style={{
                        ...styles.categoryBadge,
                        background: catColor.bg,
                        color: catColor.color,
                      }}
                    >
                      {scenario.category}
                    </span>
                    <span>{scenario.steps?.length || 0} étapes</span>
                  </div>
                  {scenario.tags.length > 0 && (
                    <div style={styles.tagsList}>
                      {scenario.tags.slice(0, 3).map((tag) => (
                        <span key={tag} style={styles.tag}>{tag}</span>
                      ))}
                      {scenario.tags.length > 3 && (
                        <span style={styles.tag}>+{scenario.tags.length - 3}</span>
                      )}
                    </div>
                  )}
                </div>
              );
            })
          )}

          <button style={styles.addBtn} onClick={handleCreateNew}>
            <span>+</span>
            Nouveau Scénario
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div style={styles.main}>
        {/* Header */}
        <div style={styles.mainHeader}>
          <div>
            <h2 style={styles.mainTitle}>
              {selectedScenario ? selectedScenario.name : 'Scénarios OCPI'}
            </h2>
            <div style={styles.mainSubtitle}>
              {selectedScenario
                ? selectedScenario.description
                : 'Sélectionnez un scénario ou créez-en un nouveau'}
            </div>
          </div>
          <div style={styles.actions}>
            {selectedScenario && (
              <>
                <button
                  style={{ ...styles.btn, ...styles.btnSecondary }}
                  onClick={() => setEditMode(!editMode)}
                >
                  {editMode ? 'Aperçu' : 'Éditer'}
                </button>
                <button
                  style={{ ...styles.btn, ...styles.btnDanger }}
                  onClick={handleDelete}
                >
                  Supprimer
                </button>
              </>
            )}
          </div>
        </div>

        {/* Content */}
        <div style={styles.content}>
          {!selectedScenario ? (
            <div style={styles.emptyState}>
              <div style={styles.emptyIcon}>[FILE]</div>
              <div style={styles.emptyTitle}>Aucun scénario sélectionné</div>
              <div style={styles.emptyText}>
                Sélectionnez un scénario dans la liste ou créez-en un nouveau
              </div>
              <button
                style={{ ...styles.btn, ...styles.btnPrimary }}
                onClick={handleCreateNew}
              >
                + Créer un scénario
              </button>
            </div>
          ) : editMode ? (
            <ScenarioEditor
              scenario={selectedScenario}
              onClose={() => setEditMode(false)}
            />
          ) : (
            <>
              {/* Execution Panel */}
              <div style={styles.executionPanel}>
                <div style={styles.executionRow}>
                  <select
                    style={styles.selectPartner}
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
                  <button
                    style={{
                      ...styles.btn,
                      ...styles.btnSuccess,
                      ...(!selectedPartnerId || executing ? styles.btnDisabled : {}),
                    }}
                    onClick={handleExecute}
                    disabled={!selectedPartnerId || executing}
                  >
                    {executing ? 'Exécution...' : 'Exécuter'}
                  </button>
                </div>
                {executing && (
                  <div style={styles.progressBar}>
                    <div
                      style={{
                        ...styles.progressFill,
                        width: `${executionProgress}%`,
                      }}
                    />
                  </div>
                )}
              </div>

              {/* Steps Preview */}
              <div style={styles.stepsPreview}>
                <div style={{
                  fontSize: 14,
                  fontWeight: 600,
                  color: '#374151',
                  marginBottom: 12,
                }}>
                  Étapes ({selectedScenario.steps?.length || 0})
                </div>
                {selectedScenario.steps && selectedScenario.steps.length > 0 ? (
                  selectedScenario.steps.map((step, idx) => (
                    <div key={step.id} style={styles.stepItem}>
                      <div style={styles.stepNumber}>{idx + 1}</div>
                      <div style={styles.stepInfo}>
                        <div style={styles.stepName}>{step.name}</div>
                        <div style={styles.stepDetails}>
                          {step.method} {step.endpoint}
                        </div>
                      </div>
                      <span style={{
                        ...styles.stepStatus,
                        ...getStatusStyle('pending'),
                      }}>
                        En attente
                      </span>
                    </div>
                  ))
                ) : (
                  <div style={{ textAlign: 'center', padding: 20, color: '#6b7280' }}>
                    Aucune étape définie
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default ScenariosPanel;
