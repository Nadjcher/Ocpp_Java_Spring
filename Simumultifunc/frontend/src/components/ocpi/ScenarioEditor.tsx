/**
 * ScenarioEditor - Éditeur visuel de scénarios OCPI
 * Permet de créer/modifier des étapes de test avec drag & drop
 */

import React, { useState } from 'react';
import { useOCPIStore } from '@/store/ocpiStore';
import {
  OCPITestScenario,
  OCPITestStep,
  ScenarioCategory,
  OCPIModule,
  SCENARIO_CATEGORIES,
  OCPI_MODULES,
} from './types';

interface ScenarioEditorProps {
  scenario: OCPITestScenario;
  onClose: () => void;
}

const styles: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    height: '100%',
  },
  header: {
    padding: 16,
    borderBottom: '1px solid #e5e7eb',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    background: '#f9fafb',
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
  content: {
    flex: 1,
    display: 'grid',
    gridTemplateColumns: '350px 1fr',
    gap: 16,
    padding: 16,
    overflowY: 'auto',
  },
  formSection: {
    background: '#fff',
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    padding: 16,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: 600,
    color: '#374151',
    marginBottom: 16,
    paddingBottom: 8,
    borderBottom: '1px solid #e5e7eb',
  },
  field: {
    marginBottom: 16,
  },
  label: {
    display: 'block',
    fontSize: 13,
    fontWeight: 500,
    color: '#374151',
    marginBottom: 4,
  },
  input: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
  },
  textarea: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    minHeight: 80,
    resize: 'vertical' as const,
  },
  select: {
    width: '100%',
    padding: '8px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    background: '#fff',
  },
  tagInput: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: 4,
    padding: 8,
    border: '1px solid #d1d5db',
    borderRadius: 6,
    minHeight: 40,
  },
  tag: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
    padding: '2px 8px',
    background: '#dbeafe',
    color: '#1d4ed8',
    borderRadius: 4,
    fontSize: 12,
  },
  tagRemove: {
    cursor: 'pointer',
    fontWeight: 'bold',
  },
  tagInputField: {
    border: 'none',
    outline: 'none',
    fontSize: 13,
    flex: 1,
    minWidth: 100,
  },
  stepsSection: {
    display: 'flex',
    flexDirection: 'column',
  },
  stepsList: {
    flex: 1,
    overflowY: 'auto',
  },
  stepCard: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: 12,
    padding: 12,
    border: '1px solid #e5e7eb',
    borderRadius: 6,
    marginBottom: 8,
    background: '#fff',
    cursor: 'grab',
  },
  stepCardDragging: {
    opacity: 0.5,
    boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
  },
  stepCardSelected: {
    borderColor: '#3b82f6',
    background: '#eff6ff',
  },
  stepHandle: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    padding: '4px 0',
    cursor: 'grab',
    color: '#9ca3af',
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
    flexShrink: 0,
  },
  stepContent: {
    flex: 1,
    minWidth: 0,
  },
  stepName: {
    fontSize: 14,
    fontWeight: 500,
    color: '#111827',
    marginBottom: 4,
  },
  stepMeta: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    fontSize: 12,
    color: '#6b7280',
  },
  methodBadge: {
    padding: '2px 6px',
    borderRadius: 4,
    fontSize: 10,
    fontWeight: 600,
  },
  stepActions: {
    display: 'flex',
    gap: 4,
  },
  iconBtn: {
    padding: 4,
    background: 'transparent',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
    color: '#6b7280',
    fontSize: 14,
    transition: 'all 0.15s ease',
  },
  addStepBtn: {
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
    marginTop: 8,
  },
  stepEditor: {
    background: '#f9fafb',
    borderRadius: 8,
    padding: 16,
    marginTop: 16,
  },
  stepEditorTitle: {
    fontSize: 14,
    fontWeight: 600,
    color: '#374151',
    marginBottom: 16,
  },
  row: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 12,
  },
};

const METHOD_COLORS: Record<string, { bg: string; color: string }> = {
  GET: { bg: '#dbeafe', color: '#1e40af' },
  POST: { bg: '#dcfce7', color: '#166534' },
  PUT: { bg: '#fef3c7', color: '#92400e' },
  PATCH: { bg: '#fae8ff', color: '#86198f' },
  DELETE: { bg: '#fee2e2', color: '#991b1b' },
};

export const ScenarioEditor: React.FC<ScenarioEditorProps> = ({
  scenario,
  onClose,
}) => {
  const { updateScenario } = useOCPIStore();

  const [localScenario, setLocalScenario] = useState<OCPITestScenario>({ ...scenario });
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null);
  const [tagInput, setTagInput] = useState('');
  const [draggedStep, setDraggedStep] = useState<string | null>(null);

  const selectedStep = localScenario.steps?.find(s => s.id === selectedStepId);

  const updateLocal = (updates: Partial<OCPITestScenario>) => {
    setLocalScenario({ ...localScenario, ...updates });
  };

  const handleSave = async () => {
    await updateScenario(scenario.id, localScenario);
    onClose();
  };

  const addTag = () => {
    if (tagInput.trim() && !localScenario.tags.includes(tagInput.trim())) {
      updateLocal({ tags: [...localScenario.tags, tagInput.trim()] });
      setTagInput('');
    }
  };

  const removeTag = (tag: string) => {
    updateLocal({ tags: localScenario.tags.filter(t => t !== tag) });
  };

  const addStep = () => {
    const newStep: OCPITestStep = {
      id: `step-${Date.now()}`,
      name: 'Nouvelle étape',
      order: (localScenario.steps?.length || 0) + 1,
      method: 'GET',
      module: 'locations',
      endpoint: '/locations',
      headers: {},
      expectedStatus: 200,
      validations: [],
      saveResponseAs: {},
      continueOnFailure: false,
    };
    updateLocal({ steps: [...(localScenario.steps || []), newStep] });
    setSelectedStepId(newStep.id);
  };

  const updateStep = (stepId: string, updates: Partial<OCPITestStep>) => {
    updateLocal({
      steps: localScenario.steps?.map(s =>
        s.id === stepId ? { ...s, ...updates } : s
      ),
    });
  };

  const deleteStep = (stepId: string) => {
    updateLocal({
      steps: localScenario.steps?.filter(s => s.id !== stepId),
    });
    if (selectedStepId === stepId) {
      setSelectedStepId(null);
    }
  };

  const moveStep = (fromIndex: number, toIndex: number) => {
    if (!localScenario.steps) return;
    const steps = [...localScenario.steps];
    const [moved] = steps.splice(fromIndex, 1);
    steps.splice(toIndex, 0, moved);
    // Update order
    const reordered = steps.map((s, idx) => ({ ...s, order: idx + 1 }));
    updateLocal({ steps: reordered });
  };

  const handleDragStart = (stepId: string) => {
    setDraggedStep(stepId);
  };

  const handleDragOver = (e: React.DragEvent, targetIndex: number) => {
    e.preventDefault();
    if (!draggedStep || !localScenario.steps) return;
    const fromIndex = localScenario.steps.findIndex(s => s.id === draggedStep);
    if (fromIndex !== targetIndex) {
      moveStep(fromIndex, targetIndex);
    }
  };

  const handleDragEnd = () => {
    setDraggedStep(null);
  };

  return (
    <div style={styles.container}>
      {/* Header */}
      <div style={styles.header}>
        <h3 style={styles.title}>Édition du scénario</h3>
        <div style={styles.actions}>
          <button style={{ ...styles.btn, ...styles.btnSecondary }} onClick={onClose}>
            Annuler
          </button>
          <button style={{ ...styles.btn, ...styles.btnPrimary }} onClick={handleSave}>
            Enregistrer
          </button>
        </div>
      </div>

      {/* Content */}
      <div style={styles.content}>
        {/* Left - Scenario Info */}
        <div style={styles.formSection}>
          <div style={styles.sectionTitle}>Informations</div>

          <div style={styles.field}>
            <label style={styles.label}>Nom</label>
            <input
              type="text"
              style={styles.input}
              value={localScenario.name}
              onChange={(e) => updateLocal({ name: e.target.value })}
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Description</label>
            <textarea
              style={styles.textarea}
              value={localScenario.description}
              onChange={(e) => updateLocal({ description: e.target.value })}
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Catégorie</label>
            <select
              style={styles.select}
              value={localScenario.category}
              onChange={(e) => updateLocal({ category: e.target.value as ScenarioCategory })}
            >
              {SCENARIO_CATEGORIES.map((cat) => (
                <option key={cat.id} value={cat.id}>{cat.label}</option>
              ))}
            </select>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Priorité</label>
            <select
              style={styles.select}
              value={localScenario.priority}
              onChange={(e) => updateLocal({ priority: e.target.value as any })}
            >
              <option value="high">Haute</option>
              <option value="medium">Moyenne</option>
              <option value="low">Basse</option>
            </select>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Tags</label>
            <div style={styles.tagInput}>
              {localScenario.tags.map((tag) => (
                <span key={tag} style={styles.tag}>
                  {tag}
                  <span style={styles.tagRemove} onClick={() => removeTag(tag)}>×</span>
                </span>
              ))}
              <input
                type="text"
                style={styles.tagInputField}
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && addTag()}
                placeholder="Ajouter un tag..."
              />
            </div>
          </div>

          <div style={styles.field}>
            <label style={{ ...styles.label, display: 'flex', alignItems: 'center', gap: 8 }}>
              <input
                type="checkbox"
                checked={localScenario.enabled}
                onChange={(e) => updateLocal({ enabled: e.target.checked })}
              />
              Scénario actif
            </label>
          </div>
        </div>

        {/* Right - Steps */}
        <div style={styles.stepsSection}>
          <div style={styles.sectionTitle}>Étapes ({localScenario.steps?.length || 0})</div>

          <div style={styles.stepsList}>
            {localScenario.steps?.map((step, idx) => {
              const methodColor = METHOD_COLORS[step.method] || METHOD_COLORS.GET;
              const isSelected = selectedStepId === step.id;
              const isDragging = draggedStep === step.id;

              return (
                <div
                  key={step.id}
                  draggable
                  onDragStart={() => handleDragStart(step.id)}
                  onDragOver={(e) => handleDragOver(e, idx)}
                  onDragEnd={handleDragEnd}
                  style={{
                    ...styles.stepCard,
                    ...(isSelected ? styles.stepCardSelected : {}),
                    ...(isDragging ? styles.stepCardDragging : {}),
                  }}
                  onClick={() => setSelectedStepId(step.id)}
                >
                  <div style={styles.stepHandle}>
                    <span>⋮⋮</span>
                  </div>
                  <div style={styles.stepNumber}>{idx + 1}</div>
                  <div style={styles.stepContent}>
                    <div style={styles.stepName}>{step.name}</div>
                    <div style={styles.stepMeta}>
                      <span style={{
                        ...styles.methodBadge,
                        background: methodColor.bg,
                        color: methodColor.color,
                      }}>
                        {step.method}
                      </span>
                      <span>{step.endpoint}</span>
                    </div>
                  </div>
                  <div style={styles.stepActions}>
                    <button
                      style={styles.iconBtn}
                      onClick={(e) => {
                        e.stopPropagation();
                        deleteStep(step.id);
                      }}
                      title="Supprimer"
                    >
                      🗑
                    </button>
                  </div>
                </div>
              );
            })}

            <button style={styles.addStepBtn} onClick={addStep}>
              <span>+</span>
              Ajouter une étape
            </button>
          </div>

          {/* Step Editor */}
          {selectedStep && (
            <div style={styles.stepEditor}>
              <div style={styles.stepEditorTitle}>Édition de l'étape</div>

              <div style={styles.field}>
                <label style={styles.label}>Nom de l'étape</label>
                <input
                  type="text"
                  style={styles.input}
                  value={selectedStep.name}
                  onChange={(e) => updateStep(selectedStep.id, { name: e.target.value })}
                />
              </div>

              <div style={styles.row}>
                <div style={styles.field}>
                  <label style={styles.label}>Méthode</label>
                  <select
                    style={styles.select}
                    value={selectedStep.method}
                    onChange={(e) => updateStep(selectedStep.id, { method: e.target.value as any })}
                  >
                    <option value="GET">GET</option>
                    <option value="POST">POST</option>
                    <option value="PUT">PUT</option>
                    <option value="PATCH">PATCH</option>
                    <option value="DELETE">DELETE</option>
                  </select>
                </div>

                <div style={styles.field}>
                  <label style={styles.label}>Module</label>
                  <select
                    style={styles.select}
                    value={selectedStep.module}
                    onChange={(e) => updateStep(selectedStep.id, { module: e.target.value as OCPIModule })}
                  >
                    {OCPI_MODULES.map((mod) => (
                      <option key={mod.id} value={mod.id}>{mod.label}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div style={styles.field}>
                <label style={styles.label}>Endpoint</label>
                <input
                  type="text"
                  style={styles.input}
                  value={selectedStep.endpoint}
                  onChange={(e) => updateStep(selectedStep.id, { endpoint: e.target.value })}
                  placeholder="/locations/{location_id}"
                />
              </div>

              <div style={styles.row}>
                <div style={styles.field}>
                  <label style={styles.label}>Status attendu</label>
                  <input
                    type="number"
                    style={styles.input}
                    value={selectedStep.expectedStatus}
                    onChange={(e) => updateStep(selectedStep.id, { expectedStatus: parseInt(e.target.value) })}
                  />
                </div>

                <div style={styles.field}>
                  <label style={{ ...styles.label, display: 'flex', alignItems: 'center', gap: 8 }}>
                    <input
                      type="checkbox"
                      checked={selectedStep.continueOnFailure}
                      onChange={(e) => updateStep(selectedStep.id, { continueOnFailure: e.target.checked })}
                    />
                    Continuer en cas d'échec
                  </label>
                </div>
              </div>

              {selectedStep.method !== 'GET' && (
                <div style={styles.field}>
                  <label style={styles.label}>Body (JSON)</label>
                  <textarea
                    style={{ ...styles.textarea, fontFamily: 'monospace' }}
                    value={selectedStep.body || ''}
                    onChange={(e) => updateStep(selectedStep.id, { body: e.target.value })}
                    placeholder='{"key": "value"}'
                  />
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ScenarioEditor;
