/**
 * PartnerForm - Formulaire de création/édition d'un partenaire OCPI
 */

import React, { useState, useEffect } from 'react';
import { useShallow } from 'zustand/react/shallow';
import { useOCPIStore } from '@/store/ocpiStore';
import { EnvironmentForm } from './EnvironmentForm';
import {
  OCPIPartner,
  OCPIEnvironment,
  OCPIRole,
  OCPIVersion,
  OCPIModule,
  OCPI_ROLES,
  OCPI_MODULES,
  createEmptyPartner,
  createEmptyEnvironment,
} from './types';

interface PartnerFormProps {
  partner: OCPIPartner | null;
  onClose: () => void;
}

type FormStep = 'info' | 'environments' | 'modules';

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    position: 'fixed',
    inset: 0,
    background: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  modal: {
    background: '#fff',
    borderRadius: 12,
    width: '90%',
    maxWidth: 700,
    maxHeight: '90vh',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '16px 24px',
    borderBottom: '1px solid #e5e7eb',
    background: '#f9fafb',
  },
  title: {
    fontSize: 18,
    fontWeight: 600,
    color: '#111827',
    margin: 0,
  },
  closeBtn: {
    padding: 8,
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    fontSize: 20,
    color: '#6b7280',
  },
  steps: {
    display: 'flex',
    padding: '12px 24px',
    borderBottom: '1px solid #e5e7eb',
    background: '#f9fafb',
    gap: 24,
  },
  step: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    fontSize: 13,
    color: '#6b7280',
    cursor: 'pointer',
    padding: '8px 0',
    borderBottom: '2px solid transparent',
    transition: 'all 0.15s ease',
  },
  stepActive: {
    color: '#3b82f6',
    fontWeight: 600,
    borderBottomColor: '#3b82f6',
  },
  stepNumber: {
    width: 24,
    height: 24,
    borderRadius: '50%',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 12,
    fontWeight: 600,
    background: '#e5e7eb',
    color: '#6b7280',
  },
  stepNumberActive: {
    background: '#3b82f6',
    color: '#fff',
  },
  content: {
    flex: 1,
    overflowY: 'auto',
    padding: 24,
  },
  row: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: 16,
    marginBottom: 16,
  },
  row3: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr',
    gap: 16,
    marginBottom: 16,
  },
  rowFull: {
    marginBottom: 16,
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: 6,
  },
  label: {
    fontSize: 13,
    fontWeight: 500,
    color: '#374151',
  },
  required: {
    color: '#ef4444',
  },
  input: {
    padding: '10px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    outline: 'none',
    transition: 'border-color 0.15s ease',
  },
  inputError: {
    borderColor: '#ef4444',
  },
  select: {
    padding: '10px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    outline: 'none',
    background: '#fff',
    cursor: 'pointer',
  },
  textarea: {
    padding: '10px 12px',
    border: '1px solid #d1d5db',
    borderRadius: 6,
    fontSize: 14,
    outline: 'none',
    resize: 'vertical',
    minHeight: 80,
  },
  helpText: {
    fontSize: 12,
    color: '#6b7280',
  },
  errorText: {
    fontSize: 12,
    color: '#ef4444',
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: 600,
    color: '#111827',
    marginBottom: 16,
    paddingBottom: 8,
    borderBottom: '1px solid #e5e7eb',
  },
  modulesGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(3, 1fr)',
    gap: 12,
  },
  moduleItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    border: '1px solid #e5e7eb',
    borderRadius: 8,
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  moduleItemActive: {
    borderColor: '#3b82f6',
    background: '#eff6ff',
  },
  checkbox: {
    width: 18,
    height: 18,
    cursor: 'pointer',
  },
  moduleLabel: {
    fontSize: 13,
    fontWeight: 500,
    color: '#374151',
  },
  moduleDesc: {
    fontSize: 11,
    color: '#6b7280',
    marginTop: 2,
  },
  footer: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '16px 24px',
    borderTop: '1px solid #e5e7eb',
    background: '#f9fafb',
  },
  footerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  },
  btn: {
    padding: '10px 20px',
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
  btnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
  addEnvBtn: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 16,
    border: '2px dashed #d1d5db',
    borderRadius: 8,
    background: 'transparent',
    color: '#6b7280',
    fontSize: 14,
    cursor: 'pointer',
    marginTop: 12,
    transition: 'all 0.15s ease',
  },
};

const STEPS: { id: FormStep; label: string }[] = [
  { id: 'info', label: 'Informations' },
  { id: 'environments', label: 'Environnements' },
  { id: 'modules', label: 'Modules' },
];

export const PartnerForm: React.FC<PartnerFormProps> = ({
  partner,
  onClose,
}) => {
  const { createPartner, updatePartner, loading } = useOCPIStore(
    useShallow((state) => ({
      createPartner: state.createPartner,
      updatePartner: state.updatePartner,
      loading: state.loading,
    }))
  );

  const isEditing = !!partner?.id;
  const [currentStep, setCurrentStep] = useState<FormStep>('info');
  const [formData, setFormData] = useState<Partial<OCPIPartner>>(() =>
    partner || createEmptyPartner()
  );
  const [errors, setErrors] = useState<Record<string, string>>({});

  // Reset form when partner changes
  useEffect(() => {
    if (partner) {
      setFormData(partner);
    } else {
      setFormData(createEmptyPartner());
    }
    setCurrentStep('info');
    setErrors({});
  }, [partner]);

  const updateField = <K extends keyof OCPIPartner>(key: K, value: OCPIPartner[K]) => {
    setFormData((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: '' }));
  };

  const updateEnvironment = (index: number, env: OCPIEnvironment) => {
    const newEnvs = [...(formData.environments || [])];
    newEnvs[index] = env;
    updateField('environments', newEnvs);
  };

  const addEnvironment = () => {
    const envCount = (formData.environments || []).length;
    const newEnv = createEmptyEnvironment(
      `env-${envCount + 1}`,
      `Environment ${envCount + 1}`
    );
    updateField('environments', [...(formData.environments || []), newEnv]);
  };

  const removeEnvironment = (index: number) => {
    const newEnvs = (formData.environments || []).filter((_, i) => i !== index);
    updateField('environments', newEnvs);
    // Ajuster l'environnement actif si nécessaire
    if (formData.activeEnvironmentId === formData.environments?.[index]?.id) {
      updateField('activeEnvironmentId', newEnvs[0]?.id || '');
    }
  };

  const setActiveEnvironment = (index: number) => {
    const envId = formData.environments?.[index]?.id;
    if (envId) {
      updateField('activeEnvironmentId', envId);
      // Mettre à jour isActive sur tous les environnements
      const newEnvs = (formData.environments || []).map((env, i) => ({
        ...env,
        isActive: i === index,
      }));
      updateField('environments', newEnvs);
    }
  };

  const toggleModule = (moduleId: OCPIModule) => {
    const modules = formData.modules || [];
    const newModules = modules.includes(moduleId)
      ? modules.filter(m => m !== moduleId)
      : [...modules, moduleId];
    updateField('modules', newModules);
  };

  const validateStep = (step: FormStep): boolean => {
    const newErrors: Record<string, string> = {};

    switch (step) {
      case 'info':
        if (!formData.name?.trim()) newErrors.name = 'Nom requis';
        if (!formData.countryCode?.trim()) newErrors.countryCode = 'Code pays requis';
        if (!formData.partyId?.trim()) newErrors.partyId = 'Party ID requis';
        if (formData.countryCode && formData.countryCode.length !== 2) {
          newErrors.countryCode = 'Code pays doit être 2 caractères';
        }
        if (formData.partyId && formData.partyId.length !== 3) {
          newErrors.partyId = 'Party ID doit être 3 caractères';
        }
        break;

      case 'environments':
        if (!formData.environments || formData.environments.length === 0) {
          newErrors.environments = 'Au moins un environnement requis';
        } else {
          formData.environments.forEach((env, i) => {
            if (!env.baseUrl?.trim()) {
              newErrors[`env_${i}_baseUrl`] = 'URL requise';
            }
          });
        }
        break;

      case 'modules':
        if (!formData.modules || formData.modules.length === 0) {
          newErrors.modules = 'Au moins un module requis';
        }
        break;
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleNext = () => {
    if (!validateStep(currentStep)) return;

    const stepIndex = STEPS.findIndex(s => s.id === currentStep);
    if (stepIndex < STEPS.length - 1) {
      setCurrentStep(STEPS[stepIndex + 1].id);
    }
  };

  const handlePrev = () => {
    const stepIndex = STEPS.findIndex(s => s.id === currentStep);
    if (stepIndex > 0) {
      setCurrentStep(STEPS[stepIndex - 1].id);
    }
  };

  const handleSubmit = async () => {
    if (!validateStep(currentStep)) return;

    try {
      if (isEditing && partner?.id) {
        await updatePartner(partner.id, formData);
      } else {
        await createPartner(formData);
      }
      onClose();
    } catch (error) {
      console.error('Error saving partner:', error);
      setErrors({ submit: error instanceof Error ? error.message : 'Erreur lors de la sauvegarde' });
    }
  };

  const renderStepContent = () => {
    switch (currentStep) {
      case 'info':
        return (
          <>
            <div style={styles.sectionTitle}>Informations Générales</div>

            <div style={styles.row}>
              <div style={styles.field}>
                <label style={styles.label}>
                  Nom <span style={styles.required}>*</span>
                </label>
                <input
                  type="text"
                  style={{
                    ...styles.input,
                    ...(errors.name ? styles.inputError : {}),
                  }}
                  value={formData.name || ''}
                  onChange={(e) => updateField('name', e.target.value)}
                  placeholder="Shell Recharge"
                />
                {errors.name && <span style={styles.errorText}>{errors.name}</span>}
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Code</label>
                <input
                  type="text"
                  style={styles.input}
                  value={formData.code || ''}
                  onChange={(e) => updateField('code', e.target.value.toUpperCase())}
                  placeholder="SHELL"
                />
                <span style={styles.helpText}>Identifiant court (optionnel)</span>
              </div>
            </div>

            <div style={styles.row3}>
              <div style={styles.field}>
                <label style={styles.label}>
                  Country Code <span style={styles.required}>*</span>
                </label>
                <input
                  type="text"
                  style={{
                    ...styles.input,
                    ...(errors.countryCode ? styles.inputError : {}),
                  }}
                  value={formData.countryCode || ''}
                  onChange={(e) => updateField('countryCode', e.target.value.toUpperCase().slice(0, 2))}
                  placeholder="FR"
                  maxLength={2}
                />
                {errors.countryCode && <span style={styles.errorText}>{errors.countryCode}</span>}
              </div>
              <div style={styles.field}>
                <label style={styles.label}>
                  Party ID <span style={styles.required}>*</span>
                </label>
                <input
                  type="text"
                  style={{
                    ...styles.input,
                    ...(errors.partyId ? styles.inputError : {}),
                  }}
                  value={formData.partyId || ''}
                  onChange={(e) => updateField('partyId', e.target.value.toUpperCase().slice(0, 3))}
                  placeholder="SHR"
                  maxLength={3}
                />
                {errors.partyId && <span style={styles.errorText}>{errors.partyId}</span>}
              </div>
              <div style={styles.field}>
                <label style={styles.label}>
                  Rôle <span style={styles.required}>*</span>
                </label>
                <select
                  style={styles.select}
                  value={formData.role || 'CPO'}
                  onChange={(e) => updateField('role', e.target.value as OCPIRole)}
                >
                  {OCPI_ROLES.map(role => (
                    <option key={role.id} value={role.id}>{role.label}</option>
                  ))}
                </select>
              </div>
            </div>

            <div style={styles.row}>
              <div style={styles.field}>
                <label style={styles.label}>Version OCPI</label>
                <select
                  style={styles.select}
                  value={formData.ocpiVersion || '2.2.1'}
                  onChange={(e) => updateField('ocpiVersion', e.target.value as OCPIVersion)}
                >
                  <option value="2.1.1">2.1.1</option>
                  <option value="2.2">2.2</option>
                  <option value="2.2.1">2.2.1</option>
                </select>
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Logo URL (optionnel)</label>
                <input
                  type="text"
                  style={styles.input}
                  value={formData.logo || ''}
                  onChange={(e) => updateField('logo', e.target.value || undefined)}
                  placeholder="https://..."
                />
              </div>
            </div>

            <div style={styles.rowFull}>
              <div style={styles.field}>
                <label style={styles.label}>Notes</label>
                <textarea
                  style={styles.textarea}
                  value={formData.notes || ''}
                  onChange={(e) => updateField('notes', e.target.value || undefined)}
                  placeholder="Notes internes sur ce partenaire..."
                />
              </div>
            </div>
          </>
        );

      case 'environments':
        return (
          <>
            <div style={styles.sectionTitle}>Environnements</div>
            {errors.environments && (
              <div style={{ ...styles.errorText, marginBottom: 16 }}>{errors.environments}</div>
            )}

            {(formData.environments || []).map((env, index) => (
              <EnvironmentForm
                key={env.id || index}
                environment={env}
                onChange={(newEnv) => updateEnvironment(index, newEnv)}
                onDelete={() => removeEnvironment(index)}
                canDelete={(formData.environments || []).length > 1}
                isActive={env.id === formData.activeEnvironmentId}
                onSetActive={() => setActiveEnvironment(index)}
              />
            ))}

            <button
              style={styles.addEnvBtn}
              onClick={addEnvironment}
              type="button"
            >
              <span>+</span>
              Ajouter un environnement
            </button>
          </>
        );

      case 'modules':
        return (
          <>
            <div style={styles.sectionTitle}>Modules Supportés</div>
            {errors.modules && (
              <div style={{ ...styles.errorText, marginBottom: 16 }}>{errors.modules}</div>
            )}

            <div style={styles.modulesGrid}>
              {OCPI_MODULES.map(mod => {
                const isSelected = (formData.modules || []).includes(mod.id);
                return (
                  <div
                    key={mod.id}
                    style={{
                      ...styles.moduleItem,
                      ...(isSelected ? styles.moduleItemActive : {}),
                    }}
                    onClick={() => toggleModule(mod.id)}
                  >
                    <input
                      type="checkbox"
                      style={styles.checkbox}
                      checked={isSelected}
                      onChange={() => toggleModule(mod.id)}
                    />
                    <div>
                      <div style={styles.moduleLabel}>{mod.label}</div>
                      <div style={styles.moduleDesc}>{mod.description}</div>
                    </div>
                  </div>
                );
              })}
            </div>
          </>
        );
    }
  };

  const currentStepIndex = STEPS.findIndex(s => s.id === currentStep);
  const isLastStep = currentStepIndex === STEPS.length - 1;

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div style={styles.header}>
          <h2 style={styles.title}>
            {isEditing ? `Modifier ${partner?.name}` : 'Nouveau Partenaire'}
          </h2>
          <button style={styles.closeBtn} onClick={onClose}>
            x
          </button>
        </div>

        {/* Steps */}
        <div style={styles.steps}>
          {STEPS.map((step, index) => (
            <div
              key={step.id}
              style={{
                ...styles.step,
                ...(currentStep === step.id ? styles.stepActive : {}),
              }}
              onClick={() => {
                // Permettre navigation directe vers étapes précédentes
                if (index <= currentStepIndex) {
                  setCurrentStep(step.id);
                }
              }}
            >
              <span
                style={{
                  ...styles.stepNumber,
                  ...(currentStep === step.id ? styles.stepNumberActive : {}),
                }}
              >
                {index + 1}
              </span>
              {step.label}
            </div>
          ))}
        </div>

        {/* Content */}
        <div style={styles.content}>
          {renderStepContent()}
          {errors.submit && (
            <div style={{ ...styles.errorText, marginTop: 16 }}>{errors.submit}</div>
          )}
        </div>

        {/* Footer */}
        <div style={styles.footer}>
          <div style={styles.footerLeft}>
            <button
              style={{
                ...styles.btn,
                ...styles.btnSecondary,
                ...(currentStepIndex === 0 ? styles.btnDisabled : {}),
              }}
              onClick={handlePrev}
              disabled={currentStepIndex === 0}
            >
              Précédent
            </button>
          </div>
          <div style={{ display: 'flex', gap: 12 }}>
            <button
              style={{ ...styles.btn, ...styles.btnSecondary }}
              onClick={onClose}
            >
              Annuler
            </button>
            {isLastStep ? (
              <button
                style={{
                  ...styles.btn,
                  ...styles.btnPrimary,
                  ...(loading ? styles.btnDisabled : {}),
                }}
                onClick={handleSubmit}
                disabled={loading}
              >
                {loading ? 'Sauvegarde...' : isEditing ? 'Mettre à jour' : 'Créer'}
              </button>
            ) : (
              <button
                style={{ ...styles.btn, ...styles.btnPrimary }}
                onClick={handleNext}
              >
                Suivant
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default PartnerForm;
