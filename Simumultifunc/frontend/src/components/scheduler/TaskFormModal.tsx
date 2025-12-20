/**
 * Task Form Modal Component
 *
 * Modal multi-étapes pour créer/modifier une tâche planifiée.
 * Étapes: 1. Info générale, 2. Planification, 3. Configuration, 4. Options avancées
 */

import React, { useState, useEffect } from 'react';
import {
  X,
  ChevronLeft,
  ChevronRight,
  Check,
  Info,
  Calendar,
  Settings,
  Sliders,
  Zap,
  TestTube,
  Activity,
  Code,
  AlertCircle,
} from 'lucide-react';
import { useSchedulerStore } from '../../store/schedulerStore';
import { useShallow } from 'zustand/react/shallow';
import {
  ScheduledTask,
  TaskType,
  TaskPriority,
  TaskStatus,
  ScheduleType,
  RecurrencePattern,
  NotificationEvent,
  NotificationType,
  SessionConfig,
  TnrConfig,
  PerformanceConfig,
  CustomConfig,
  TaskSchedule,
  ExecutionOptions,
  DEFAULT_EXECUTION_OPTIONS,
  CRON_PRESETS,
} from '../../types/scheduler.types';
import { CronBuilder } from './CronBuilder';
import { cronParser } from '../../services/cronParser';

// =============================================================================
// Types
// =============================================================================

interface TaskFormModalProps {
  isOpen: boolean;
  task?: Partial<ScheduledTask>;
  onClose: () => void;
}

type Step = 'info' | 'schedule' | 'config' | 'advanced';

interface FormData {
  name: string;
  description: string;
  type: TaskType;
  priority: TaskPriority;
  enabled: boolean;
  schedule: TaskSchedule;
  executionOptions: ExecutionOptions;
  sessionConfig: SessionConfig;
  tnrConfig: TnrConfig;
  performanceConfig: PerformanceConfig;
  customConfig: CustomConfig;
  notifications: {
    enabled: boolean;
    events: NotificationEvent[];
  };
}

// =============================================================================
// Initial Data
// =============================================================================

const initialFormData: FormData = {
  name: '',
  description: '',
  type: TaskType.SESSION,
  priority: TaskPriority.NORMAL,
  enabled: true,
  schedule: {
    type: ScheduleType.ONCE,
    scheduledAt: new Date(),
  },
  executionOptions: { ...DEFAULT_EXECUTION_OPTIONS },
  sessionConfig: {
    chargePointId: '',
    connectorId: 1,
    idTag: 'TEST001',
    energyKwh: 20,
    durationMinutes: 60,
    enableSmartCharging: false,
    meterValuesInterval: 60,
  },
  tnrConfig: {
    scenarioIds: [],
    generateReports: true,
    reportFormats: ['json', 'html'],
    parallel: false,
    failFast: false,
  },
  performanceConfig: {
    testType: 'load',
    concurrentConnections: 10,
    durationSeconds: 300,
    rampUpSeconds: 60,
  },
  customConfig: {},
  notifications: {
    enabled: false,
    events: [NotificationEvent.ON_FAILURE],
  },
};

const STEPS: { id: Step; label: string; icon: React.FC<{ className?: string }> }[] = [
  { id: 'info', label: 'Informations', icon: Info },
  { id: 'schedule', label: 'Planification', icon: Calendar },
  { id: 'config', label: 'Configuration', icon: Settings },
  { id: 'advanced', label: 'Avancé', icon: Sliders },
];

// =============================================================================
// Component
// =============================================================================

export const TaskFormModal: React.FC<TaskFormModalProps> = ({
  isOpen,
  task,
  onClose,
}) => {
  const { createTask, updateTask, loading, error } = useSchedulerStore(
    useShallow((state) => ({
      createTask: state.createTask,
      updateTask: state.updateTask,
      loading: state.loading,
      error: state.error,
    }))
  );

  const [currentStep, setCurrentStep] = useState<Step>('info');
  const [formData, setFormData] = useState<FormData>(initialFormData);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const isEditing = !!task?.id;

  // Initialize form with task data
  useEffect(() => {
    if (task) {
      setFormData({
        name: task.name || '',
        description: task.description || '',
        type: task.type || TaskType.SESSION,
        priority: task.priority || TaskPriority.NORMAL,
        enabled: task.enabled ?? true,
        schedule: task.schedule || initialFormData.schedule,
        executionOptions: { ...DEFAULT_EXECUTION_OPTIONS, ...task.executionOptions },
        sessionConfig: task.type === TaskType.SESSION
          ? (task.config as SessionConfig)
          : initialFormData.sessionConfig,
        tnrConfig: task.type === TaskType.TNR
          ? (task.config as TnrConfig)
          : initialFormData.tnrConfig,
        performanceConfig: task.type === TaskType.PERFORMANCE
          ? (task.config as PerformanceConfig)
          : initialFormData.performanceConfig,
        customConfig: task.type === TaskType.CUSTOM
          ? (task.config as CustomConfig)
          : initialFormData.customConfig,
        notifications: initialFormData.notifications,
      });
    } else {
      setFormData(initialFormData);
    }
    setCurrentStep('info');
    setErrors({});
  }, [task, isOpen]);

  const updateFormData = <K extends keyof FormData>(key: K, value: FormData[K]) => {
    setFormData((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: '' }));
  };

  const validateStep = (step: Step): boolean => {
    const newErrors: Record<string, string> = {};

    switch (step) {
      case 'info':
        if (!formData.name.trim()) {
          newErrors.name = 'Le nom est requis';
        }
        break;
      case 'schedule':
        if (formData.schedule.type === ScheduleType.CRON) {
          const validation = cronParser.validate(formData.schedule.cronExpression || '');
          if (!validation.valid) {
            newErrors.cron = validation.error || 'Expression cron invalide';
          }
        }
        if (formData.schedule.type === ScheduleType.ONCE && !formData.schedule.scheduledAt) {
          newErrors.scheduledAt = 'La date est requise';
        }
        break;
      case 'config':
        if (formData.type === TaskType.SESSION) {
          if (!formData.sessionConfig.chargePointId) {
            newErrors.chargePointId = 'Le Charge Point ID est requis';
          }
        }
        if (formData.type === TaskType.TNR) {
          if (!formData.tnrConfig.scenarioIds?.length && !formData.tnrConfig.scenarioId) {
            newErrors.scenarioIds = 'Sélectionnez au moins un scénario';
          }
        }
        break;
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleNext = () => {
    if (!validateStep(currentStep)) return;

    const stepIndex = STEPS.findIndex((s) => s.id === currentStep);
    if (stepIndex < STEPS.length - 1) {
      setCurrentStep(STEPS[stepIndex + 1].id);
    }
  };

  const handleBack = () => {
    const stepIndex = STEPS.findIndex((s) => s.id === currentStep);
    if (stepIndex > 0) {
      setCurrentStep(STEPS[stepIndex - 1].id);
    }
  };

  const handleSubmit = async () => {
    if (!validateStep(currentStep)) return;

    const config = {
      [TaskType.SESSION]: formData.sessionConfig,
      [TaskType.TNR]: formData.tnrConfig,
      [TaskType.PERFORMANCE]: formData.performanceConfig,
      [TaskType.CUSTOM]: formData.customConfig,
    }[formData.type];

    const taskData = {
      name: formData.name,
      description: formData.description,
      type: formData.type,
      priority: formData.priority,
      enabled: formData.enabled,
      status: TaskStatus.SCHEDULED,
      schedule: formData.schedule,
      executionOptions: formData.executionOptions,
      config,
    };

    try {
      if (isEditing && task?.id) {
        await updateTask(task.id, taskData);
      } else {
        await createTask(taskData as any);
      }
      onClose();
    } catch (err) {
      // Error is handled by store
    }
  };

  if (!isOpen) return null;

  const stepIndex = STEPS.findIndex((s) => s.id === currentStep);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
            {isEditing ? 'Modifier la tâche' : 'Nouvelle tâche'}
          </h2>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Stepper */}
        <div className="flex items-center px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          {STEPS.map((step, index) => (
            <React.Fragment key={step.id}>
              <button
                onClick={() => index < stepIndex && setCurrentStep(step.id)}
                className={`flex items-center space-x-2 ${
                  index < stepIndex ? 'cursor-pointer' : 'cursor-default'
                }`}
                disabled={index > stepIndex}
              >
                <div
                  className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium transition-colors
                    ${currentStep === step.id
                      ? 'bg-blue-600 text-white'
                      : index < stepIndex
                        ? 'bg-green-500 text-white'
                        : 'bg-gray-200 dark:bg-gray-700 text-gray-500'}`}
                >
                  {index < stepIndex ? (
                    <Check className="w-4 h-4" />
                  ) : (
                    index + 1
                  )}
                </div>
                <span
                  className={`text-sm hidden sm:block ${
                    currentStep === step.id
                      ? 'text-blue-600 dark:text-blue-400 font-medium'
                      : 'text-gray-500 dark:text-gray-400'
                  }`}
                >
                  {step.label}
                </span>
              </button>
              {index < STEPS.length - 1 && (
                <div
                  className={`flex-1 h-0.5 mx-4 ${
                    index < stepIndex ? 'bg-green-500' : 'bg-gray-200 dark:bg-gray-700'
                  }`}
                />
              )}
            </React.Fragment>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          {error && (
            <div className="mb-4 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg flex items-center text-red-700 dark:text-red-300">
              <AlertCircle className="w-5 h-5 mr-2" />
              {error}
            </div>
          )}

          {currentStep === 'info' && (
            <StepInfo
              formData={formData}
              errors={errors}
              onChange={updateFormData}
            />
          )}

          {currentStep === 'schedule' && (
            <StepSchedule
              formData={formData}
              errors={errors}
              onChange={updateFormData}
            />
          )}

          {currentStep === 'config' && (
            <StepConfig
              formData={formData}
              errors={errors}
              onChange={updateFormData}
            />
          )}

          {currentStep === 'advanced' && (
            <StepAdvanced
              formData={formData}
              errors={errors}
              onChange={updateFormData}
            />
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-gray-200 dark:border-gray-700">
          <button
            onClick={handleBack}
            disabled={stepIndex === 0}
            className="flex items-center space-x-2 px-4 py-2 text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <ChevronLeft className="w-4 h-4" />
            <span>Précédent</span>
          </button>

          {stepIndex < STEPS.length - 1 ? (
            <button
              onClick={handleNext}
              className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
            >
              <span>Suivant</span>
              <ChevronRight className="w-4 h-4" />
            </button>
          ) : (
            <button
              onClick={handleSubmit}
              disabled={loading}
              className="flex items-center space-x-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
            >
              {loading ? (
                <>
                  <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  <span>Enregistrement...</span>
                </>
              ) : (
                <>
                  <Check className="w-4 h-4" />
                  <span>{isEditing ? 'Mettre à jour' : 'Créer'}</span>
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Step Components
// =============================================================================

interface StepProps {
  formData: FormData;
  errors: Record<string, string>;
  onChange: <K extends keyof FormData>(key: K, value: FormData[K]) => void;
}

const StepInfo: React.FC<StepProps> = ({ formData, errors, onChange }) => {
  const typeOptions = [
    { value: TaskType.SESSION, label: 'Session OCPP', icon: Zap, color: '#3b82f6' },
    { value: TaskType.TNR, label: 'Test TNR', icon: TestTube, color: '#10b981' },
    { value: TaskType.PERFORMANCE, label: 'Test Performance', icon: Activity, color: '#f59e0b' },
    { value: TaskType.CUSTOM, label: 'Personnalisé', icon: Code, color: '#8b5cf6' },
  ];

  return (
    <div className="space-y-6">
      {/* Name */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Nom de la tâche *
        </label>
        <input
          type="text"
          value={formData.name}
          onChange={(e) => onChange('name', e.target.value)}
          placeholder="Ex: Session quotidienne CP001"
          className={`w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 dark:bg-gray-700
            ${errors.name ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`}
        />
        {errors.name && (
          <p className="mt-1 text-sm text-red-500">{errors.name}</p>
        )}
      </div>

      {/* Description */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Description
        </label>
        <textarea
          value={formData.description}
          onChange={(e) => onChange('description', e.target.value)}
          placeholder="Description optionnelle de la tâche..."
          rows={3}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 dark:bg-gray-700"
        />
      </div>

      {/* Type */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Type de tâche *
        </label>
        <div className="grid grid-cols-2 gap-3">
          {typeOptions.map((option) => (
            <button
              key={option.value}
              onClick={() => onChange('type', option.value)}
              className={`flex items-center space-x-3 p-4 border-2 rounded-lg transition-all
                ${formData.type === option.value
                  ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:border-gray-300'}`}
            >
              <div
                className="p-2 rounded-lg"
                style={{ backgroundColor: `${option.color}20` }}
              >
                <option.icon className="w-5 h-5" style={{ color: option.color }} />
              </div>
              <span className="font-medium text-gray-900 dark:text-white">{option.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Priority */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Priorité
        </label>
        <select
          value={formData.priority}
          onChange={(e) => onChange('priority', e.target.value as TaskPriority)}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 dark:bg-gray-700"
        >
          <option value={TaskPriority.LOW}>Basse</option>
          <option value={TaskPriority.NORMAL}>Normale</option>
          <option value={TaskPriority.HIGH}>Haute</option>
          <option value={TaskPriority.CRITICAL}>Critique</option>
        </select>
      </div>

      {/* Enabled */}
      <div className="flex items-center space-x-3">
        <input
          type="checkbox"
          id="enabled"
          checked={formData.enabled}
          onChange={(e) => onChange('enabled', e.target.checked)}
          className="w-4 h-4 text-blue-600 rounded border-gray-300"
        />
        <label htmlFor="enabled" className="text-sm text-gray-700 dark:text-gray-300">
          Activer la tâche dès sa création
        </label>
      </div>
    </div>
  );
};

const StepSchedule: React.FC<StepProps> = ({ formData, errors, onChange }) => {
  const [showCronBuilder, setShowCronBuilder] = useState(false);

  const updateSchedule = (updates: Partial<TaskSchedule>) => {
    onChange('schedule', { ...formData.schedule, ...updates });
  };

  return (
    <div className="space-y-6">
      {/* Schedule Type */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Type de planification
        </label>
        <div className="grid grid-cols-2 gap-3">
          {[
            { value: ScheduleType.ONCE, label: 'Une seule fois' },
            { value: ScheduleType.RECURRING, label: 'Récurrent' },
            { value: ScheduleType.CRON, label: 'Expression Cron' },
          ].map((option) => (
            <button
              key={option.value}
              onClick={() => updateSchedule({ type: option.value })}
              className={`p-3 border-2 rounded-lg text-sm font-medium transition-all
                ${formData.schedule.type === option.value
                  ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700'
                  : 'border-gray-200 dark:border-gray-700 hover:border-gray-300'}`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {/* Once - Date picker */}
      {formData.schedule.type === ScheduleType.ONCE && (
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Date et heure d'exécution
          </label>
          <input
            type="datetime-local"
            value={formData.schedule.scheduledAt
              ? new Date(formData.schedule.scheduledAt).toISOString().slice(0, 16)
              : ''}
            onChange={(e) => updateSchedule({ scheduledAt: new Date(e.target.value) })}
            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-blue-500 dark:bg-gray-700"
          />
          {errors.scheduledAt && (
            <p className="mt-1 text-sm text-red-500">{errors.scheduledAt}</p>
          )}
        </div>
      )}

      {/* Recurring */}
      {formData.schedule.type === ScheduleType.RECURRING && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Fréquence
              </label>
              <select
                value={formData.schedule.recurrence?.pattern || RecurrencePattern.DAILY}
                onChange={(e) => updateSchedule({
                  recurrence: {
                    ...formData.schedule.recurrence,
                    pattern: e.target.value as RecurrencePattern,
                    interval: formData.schedule.recurrence?.interval || 1,
                  }
                })}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
              >
                <option value={RecurrencePattern.MINUTELY}>Toutes les minutes</option>
                <option value={RecurrencePattern.HOURLY}>Toutes les heures</option>
                <option value={RecurrencePattern.DAILY}>Tous les jours</option>
                <option value={RecurrencePattern.WEEKLY}>Toutes les semaines</option>
                <option value={RecurrencePattern.MONTHLY}>Tous les mois</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Intervalle
              </label>
              <input
                type="number"
                min="1"
                value={formData.schedule.recurrence?.interval || 1}
                onChange={(e) => updateSchedule({
                  recurrence: {
                    ...formData.schedule.recurrence,
                    pattern: formData.schedule.recurrence?.pattern || RecurrencePattern.DAILY,
                    interval: parseInt(e.target.value) || 1,
                  }
                })}
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
              />
            </div>
          </div>
        </div>
      )}

      {/* Cron */}
      {formData.schedule.type === ScheduleType.CRON && (
        <div className="space-y-4">
          {/* Presets */}
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              Préréglages
            </label>
            <div className="flex flex-wrap gap-2">
              {CRON_PRESETS.slice(0, 6).map((preset) => (
                <button
                  key={preset.expression}
                  onClick={() => updateSchedule({
                    cronExpression: preset.expression,
                    cronDescription: preset.description,
                  })}
                  className={`px-3 py-1.5 text-sm border rounded-lg transition-colors
                    ${formData.schedule.cronExpression === preset.expression
                      ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700'
                      : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'}`}
                >
                  {preset.label}
                </button>
              ))}
            </div>
          </div>

          {/* Cron expression input */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
                Expression Cron
              </label>
              <button
                onClick={() => setShowCronBuilder(!showCronBuilder)}
                className="text-sm text-blue-600 hover:text-blue-700"
              >
                {showCronBuilder ? 'Masquer le builder' : 'Utiliser le builder'}
              </button>
            </div>
            <input
              type="text"
              value={formData.schedule.cronExpression || ''}
              onChange={(e) => {
                const expression = e.target.value;
                updateSchedule({
                  cronExpression: expression,
                  cronDescription: cronParser.describe(expression),
                });
              }}
              placeholder="* * * * *"
              className={`w-full px-3 py-2 font-mono border rounded-lg focus:ring-2 focus:ring-blue-500 dark:bg-gray-700
                ${errors.cron ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`}
            />
            {errors.cron && (
              <p className="mt-1 text-sm text-red-500">{errors.cron}</p>
            )}
            {formData.schedule.cronExpression && !errors.cron && (
              <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                {cronParser.describe(formData.schedule.cronExpression)}
              </p>
            )}
          </div>

          {/* Cron Builder */}
          {showCronBuilder && (
            <CronBuilder
              value={formData.schedule.cronExpression || '* * * * *'}
              onChange={(expression) => updateSchedule({
                cronExpression: expression,
                cronDescription: cronParser.describe(expression),
              })}
            />
          )}
        </div>
      )}
    </div>
  );
};

const StepConfig: React.FC<StepProps> = ({ formData, errors, onChange }) => {
  switch (formData.type) {
    case TaskType.SESSION:
      return (
        <SessionConfigForm
          config={formData.sessionConfig}
          errors={errors}
          onChange={(config) => onChange('sessionConfig', config)}
        />
      );
    case TaskType.TNR:
      return (
        <TnrConfigForm
          config={formData.tnrConfig}
          errors={errors}
          onChange={(config) => onChange('tnrConfig', config)}
        />
      );
    case TaskType.PERFORMANCE:
      return (
        <PerformanceConfigForm
          config={formData.performanceConfig}
          errors={errors}
          onChange={(config) => onChange('performanceConfig', config)}
        />
      );
    case TaskType.CUSTOM:
      return (
        <CustomConfigForm
          config={formData.customConfig}
          errors={errors}
          onChange={(config) => onChange('customConfig', config)}
        />
      );
  }
};

const StepAdvanced: React.FC<StepProps> = ({ formData, errors, onChange }) => {
  const updateOptions = (updates: Partial<ExecutionOptions>) => {
    onChange('executionOptions', { ...formData.executionOptions, ...updates });
  };

  return (
    <div className="space-y-6">
      {/* Timeout */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Timeout (secondes)
        </label>
        <input
          type="number"
          min="1"
          value={(formData.executionOptions.timeout || 300000) / 1000}
          onChange={(e) => updateOptions({ timeout: parseInt(e.target.value) * 1000 })}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
        />
      </div>

      {/* Retries */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Nombre de retries
          </label>
          <input
            type="number"
            min="0"
            max="10"
            value={formData.executionOptions.maxRetries || 0}
            onChange={(e) => updateOptions({ maxRetries: parseInt(e.target.value) })}
            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
            Délai retry (secondes)
          </label>
          <input
            type="number"
            min="1"
            value={(formData.executionOptions.retryDelay || 5000) / 1000}
            onChange={(e) => updateOptions({ retryDelay: parseInt(e.target.value) * 1000 })}
            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
          />
        </div>
      </div>

      {/* Exponential backoff */}
      <div className="flex items-center space-x-3">
        <input
          type="checkbox"
          id="exponentialBackoff"
          checked={formData.executionOptions.exponentialBackoff}
          onChange={(e) => updateOptions({ exponentialBackoff: e.target.checked })}
          className="w-4 h-4 text-blue-600 rounded border-gray-300"
        />
        <label htmlFor="exponentialBackoff" className="text-sm text-gray-700 dark:text-gray-300">
          Backoff exponentiel (doubler le délai à chaque retry)
        </label>
      </div>

      {/* Tags */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Tags (séparés par virgules)
        </label>
        <input
          type="text"
          value={formData.executionOptions.tags?.join(', ') || ''}
          onChange={(e) => updateOptions({
            tags: e.target.value.split(',').map((t) => t.trim()).filter(Boolean)
          })}
          placeholder="Ex: production, critical, daily"
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
        />
      </div>

      {/* Notifications */}
      <div>
        <div className="flex items-center space-x-3 mb-3">
          <input
            type="checkbox"
            id="notificationsEnabled"
            checked={formData.notifications.enabled}
            onChange={(e) => onChange('notifications', {
              ...formData.notifications,
              enabled: e.target.checked,
            })}
            className="w-4 h-4 text-blue-600 rounded border-gray-300"
          />
          <label htmlFor="notificationsEnabled" className="text-sm font-medium text-gray-700 dark:text-gray-300">
            Activer les notifications
          </label>
        </div>
        {formData.notifications.enabled && (
          <div className="ml-7 space-y-2">
            {Object.values(NotificationEvent).map((event) => (
              <label key={event} className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  checked={formData.notifications.events.includes(event)}
                  onChange={(e) => {
                    const events = e.target.checked
                      ? [...formData.notifications.events, event]
                      : formData.notifications.events.filter((e) => e !== event);
                    onChange('notifications', { ...formData.notifications, events });
                  }}
                  className="w-4 h-4 text-blue-600 rounded border-gray-300"
                />
                <span className="text-sm text-gray-700 dark:text-gray-300">
                  {getNotificationEventLabel(event)}
                </span>
              </label>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// Config Forms
// =============================================================================

interface ConfigFormProps<T> {
  config: T;
  errors: Record<string, string>;
  onChange: (config: T) => void;
}

const SessionConfigForm: React.FC<ConfigFormProps<SessionConfig>> = ({
  config,
  errors,
  onChange,
}) => (
  <div className="space-y-4">
    <div className="grid grid-cols-2 gap-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Charge Point ID *
        </label>
        <input
          type="text"
          value={config.chargePointId}
          onChange={(e) => onChange({ ...config, chargePointId: e.target.value })}
          placeholder="CP001"
          className={`w-full px-3 py-2 border rounded-lg dark:bg-gray-700
            ${errors.chargePointId ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`}
        />
        {errors.chargePointId && (
          <p className="mt-1 text-sm text-red-500">{errors.chargePointId}</p>
        )}
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Connector ID
        </label>
        <input
          type="number"
          min="1"
          value={config.connectorId}
          onChange={(e) => onChange({ ...config, connectorId: parseInt(e.target.value) })}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
        />
      </div>
    </div>
    <div className="grid grid-cols-2 gap-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          ID Tag
        </label>
        <input
          type="text"
          value={config.idTag || ''}
          onChange={(e) => onChange({ ...config, idTag: e.target.value })}
          placeholder="TEST001"
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Énergie (kWh)
        </label>
        <input
          type="number"
          min="0"
          value={config.energyKwh || 0}
          onChange={(e) => onChange({ ...config, energyKwh: parseFloat(e.target.value) })}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
        />
      </div>
    </div>
    <div className="flex items-center space-x-3">
      <input
        type="checkbox"
        id="enableSmartCharging"
        checked={config.enableSmartCharging}
        onChange={(e) => onChange({ ...config, enableSmartCharging: e.target.checked })}
        className="w-4 h-4 text-blue-600 rounded border-gray-300"
      />
      <label htmlFor="enableSmartCharging" className="text-sm text-gray-700 dark:text-gray-300">
        Activer le Smart Charging
      </label>
    </div>
  </div>
);

const TnrConfigForm: React.FC<ConfigFormProps<TnrConfig>> = ({
  config,
  errors,
  onChange,
}) => (
  <div className="space-y-4">
    <div>
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
        Scénarios (IDs séparés par virgules) *
      </label>
      <textarea
        value={(config.scenarioIds || []).join(', ')}
        onChange={(e) => onChange({
          ...config,
          scenarioIds: e.target.value.split(',').map((s) => s.trim()).filter(Boolean)
        })}
        placeholder="SC001, SC002, SC003"
        rows={3}
        className={`w-full px-3 py-2 border rounded-lg dark:bg-gray-700
          ${errors.scenarioIds ? 'border-red-500' : 'border-gray-300 dark:border-gray-600'}`}
      />
      {errors.scenarioIds && (
        <p className="mt-1 text-sm text-red-500">{errors.scenarioIds}</p>
      )}
    </div>
    <div className="grid grid-cols-2 gap-4">
      <div className="flex items-center space-x-3">
        <input
          type="checkbox"
          id="generateReports"
          checked={config.generateReports}
          onChange={(e) => onChange({ ...config, generateReports: e.target.checked })}
          className="w-4 h-4 text-blue-600 rounded border-gray-300"
        />
        <label htmlFor="generateReports" className="text-sm text-gray-700 dark:text-gray-300">
          Générer les rapports
        </label>
      </div>
      <div className="flex items-center space-x-3">
        <input
          type="checkbox"
          id="parallel"
          checked={config.parallel}
          onChange={(e) => onChange({ ...config, parallel: e.target.checked })}
          className="w-4 h-4 text-blue-600 rounded border-gray-300"
        />
        <label htmlFor="parallel" className="text-sm text-gray-700 dark:text-gray-300">
          Exécution parallèle
        </label>
      </div>
    </div>
    <div className="flex items-center space-x-3">
      <input
        type="checkbox"
        id="failFast"
        checked={config.failFast}
        onChange={(e) => onChange({ ...config, failFast: e.target.checked })}
        className="w-4 h-4 text-blue-600 rounded border-gray-300"
      />
      <label htmlFor="failFast" className="text-sm text-gray-700 dark:text-gray-300">
        Arrêter au premier échec (Fail Fast)
      </label>
    </div>
  </div>
);

const PerformanceConfigForm: React.FC<ConfigFormProps<PerformanceConfig>> = ({
  config,
  errors,
  onChange,
}) => (
  <div className="space-y-4">
    <div>
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
        Type de test
      </label>
      <select
        value={config.testType}
        onChange={(e) => onChange({ ...config, testType: e.target.value as any })}
        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
      >
        <option value="load">Test de charge (Load)</option>
        <option value="stress">Test de stress</option>
        <option value="endurance">Test d'endurance</option>
        <option value="spike">Test de pic (Spike)</option>
      </select>
    </div>
    <div className="grid grid-cols-2 gap-4">
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Connexions concurrentes
        </label>
        <input
          type="number"
          min="1"
          value={config.concurrentConnections}
          onChange={(e) => onChange({ ...config, concurrentConnections: parseInt(e.target.value) })}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
        />
      </div>
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
          Durée (secondes)
        </label>
        <input
          type="number"
          min="1"
          value={config.durationSeconds}
          onChange={(e) => onChange({ ...config, durationSeconds: parseInt(e.target.value) })}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
        />
      </div>
    </div>
    <div>
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
        Ramp-up (secondes)
      </label>
      <input
        type="number"
        min="0"
        value={config.rampUpSeconds || 0}
        onChange={(e) => onChange({ ...config, rampUpSeconds: parseInt(e.target.value) })}
        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
      />
    </div>
  </div>
);

const CustomConfigForm: React.FC<ConfigFormProps<CustomConfig>> = ({
  config,
  errors,
  onChange,
}) => (
  <div className="space-y-4">
    <div>
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
        Commande
      </label>
      <input
        type="text"
        value={config.command || ''}
        onChange={(e) => onChange({ ...config, command: e.target.value })}
        placeholder="npm run test"
        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
      />
    </div>
    <div>
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
        Arguments (séparés par espaces)
      </label>
      <input
        type="text"
        value={config.args?.join(' ') || ''}
        onChange={(e) => onChange({ ...config, args: e.target.value.split(' ').filter(Boolean) })}
        placeholder="--verbose --config=prod.json"
        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
      />
    </div>
    <div>
      <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
        Répertoire de travail
      </label>
      <input
        type="text"
        value={config.workingDir || ''}
        onChange={(e) => onChange({ ...config, workingDir: e.target.value })}
        placeholder="/app/project"
        className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg dark:bg-gray-700"
      />
    </div>
  </div>
);

// =============================================================================
// Helpers
// =============================================================================

function getNotificationEventLabel(event: NotificationEvent): string {
  const labels: Record<NotificationEvent, string> = {
    [NotificationEvent.ON_START]: 'Au démarrage',
    [NotificationEvent.ON_COMPLETE]: 'À la fin (succès)',
    [NotificationEvent.ON_FAILURE]: 'En cas d\'échec',
    [NotificationEvent.ON_RETRY]: 'À chaque retry',
  };
  return labels[event] || event;
}

export default TaskFormModal;
