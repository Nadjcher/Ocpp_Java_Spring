/**
 * Cron Builder Component
 *
 * Interface visuelle pour construire des expressions cron.
 * Permet de sélectionner minute, heure, jour, mois, jour de semaine.
 */

import React, { useState, useMemo, useEffect } from 'react';
import { Clock, Calendar, Info, AlertCircle } from 'lucide-react';
import { cronParser } from '../../services/cronParser';
import { format, fr } from '../../utils/dateUtils';

// =============================================================================
// Types
// =============================================================================

interface CronBuilderProps {
  value: string;
  onChange: (expression: string) => void;
}

type CronMode = 'simple' | 'advanced';

interface CronParts {
  minute: string;
  hour: string;
  dayOfMonth: string;
  month: string;
  dayOfWeek: string;
}

// =============================================================================
// Constants
// =============================================================================

const MINUTES = Array.from({ length: 60 }, (_, i) => i);
const HOURS = Array.from({ length: 24 }, (_, i) => i);
const DAYS_OF_MONTH = Array.from({ length: 31 }, (_, i) => i + 1);
const MONTHS = [
  { value: 1, label: 'Janvier' },
  { value: 2, label: 'Février' },
  { value: 3, label: 'Mars' },
  { value: 4, label: 'Avril' },
  { value: 5, label: 'Mai' },
  { value: 6, label: 'Juin' },
  { value: 7, label: 'Juillet' },
  { value: 8, label: 'Août' },
  { value: 9, label: 'Septembre' },
  { value: 10, label: 'Octobre' },
  { value: 11, label: 'Novembre' },
  { value: 12, label: 'Décembre' },
];
const DAYS_OF_WEEK = [
  { value: 0, label: 'Dimanche', short: 'Dim' },
  { value: 1, label: 'Lundi', short: 'Lun' },
  { value: 2, label: 'Mardi', short: 'Mar' },
  { value: 3, label: 'Mercredi', short: 'Mer' },
  { value: 4, label: 'Jeudi', short: 'Jeu' },
  { value: 5, label: 'Vendredi', short: 'Ven' },
  { value: 6, label: 'Samedi', short: 'Sam' },
];

// =============================================================================
// Component
// =============================================================================

export const CronBuilder: React.FC<CronBuilderProps> = ({ value, onChange }) => {
  const [mode, setMode] = useState<CronMode>('simple');

  // Parse current cron expression
  const parts = useMemo<CronParts>(() => {
    const parsed = cronParser.parse(value);
    if (parsed) {
      return parsed;
    }
    return {
      minute: '*',
      hour: '*',
      dayOfMonth: '*',
      month: '*',
      dayOfWeek: '*',
    };
  }, [value]);

  // Validation
  const validation = useMemo(() => cronParser.validate(value), [value]);

  // Next runs
  const nextRuns = useMemo(() => {
    if (!validation.valid) return [];
    return cronParser.getNextRuns(value, 5);
  }, [value, validation.valid]);

  // Update handler
  const updatePart = (part: keyof CronParts, newValue: string) => {
    const newExpression = [
      part === 'minute' ? newValue : parts.minute,
      part === 'hour' ? newValue : parts.hour,
      part === 'dayOfMonth' ? newValue : parts.dayOfMonth,
      part === 'month' ? newValue : parts.month,
      part === 'dayOfWeek' ? newValue : parts.dayOfWeek,
    ].join(' ');
    onChange(newExpression);
  };

  return (
    <div className="space-y-4 p-4 bg-gray-50 dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700">
      {/* Mode toggle */}
      <div className="flex items-center justify-between">
        <h4 className="text-sm font-medium text-gray-700 dark:text-gray-300">
          Constructeur Cron
        </h4>
        <div className="flex items-center border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden text-sm">
          <button
            onClick={() => setMode('simple')}
            className={`px-3 py-1 ${mode === 'simple' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'hover:bg-gray-100 dark:hover:bg-gray-800'}`}
          >
            Simple
          </button>
          <button
            onClick={() => setMode('advanced')}
            className={`px-3 py-1 ${mode === 'advanced' ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'hover:bg-gray-100 dark:hover:bg-gray-800'}`}
          >
            Avancé
          </button>
        </div>
      </div>

      {mode === 'simple' ? (
        <SimpleCronBuilder
          parts={parts}
          onUpdate={updatePart}
        />
      ) : (
        <AdvancedCronBuilder
          parts={parts}
          onUpdate={updatePart}
        />
      )}

      {/* Preview */}
      <div className="pt-4 border-t border-gray-200 dark:border-gray-700">
        <div className="flex items-center space-x-2 mb-2">
          <Clock className="w-4 h-4 text-gray-500" />
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            Expression: <code className="ml-1 px-2 py-0.5 bg-gray-200 dark:bg-gray-700 rounded">{value}</code>
          </span>
        </div>

        {!validation.valid && (
          <div className="flex items-center space-x-2 text-sm text-red-600 dark:text-red-400 mt-2">
            <AlertCircle className="w-4 h-4" />
            <span>{validation.error}</span>
          </div>
        )}

        {validation.valid && (
          <>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              {cronParser.describe(value)}
            </p>

            {nextRuns.length > 0 && (
              <div className="mt-3">
                <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">
                  Prochaines exécutions:
                </p>
                <div className="space-y-1">
                  {nextRuns.map((date, i) => (
                    <div key={i} className="flex items-center text-xs text-gray-600 dark:text-gray-400">
                      <Calendar className="w-3 h-3 mr-2" />
                      {format(date, "EEEE d MMMM yyyy 'à' HH:mm", { locale: fr })}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// Simple Builder
// =============================================================================

interface BuilderProps {
  parts: CronParts;
  onUpdate: (part: keyof CronParts, value: string) => void;
}

const SimpleCronBuilder: React.FC<BuilderProps> = ({ parts, onUpdate }) => {
  const [frequency, setFrequency] = useState<'minutely' | 'hourly' | 'daily' | 'weekly' | 'monthly'>('daily');
  const [minute, setMinute] = useState(0);
  const [hour, setHour] = useState(9);
  const [dayOfWeek, setDayOfWeek] = useState<number[]>([1]); // Monday
  const [dayOfMonth, setDayOfMonth] = useState(1);

  // Apply changes when settings change
  useEffect(() => {
    let expr = '* * * * *';
    switch (frequency) {
      case 'minutely':
        expr = '* * * * *';
        break;
      case 'hourly':
        expr = `${minute} * * * *`;
        break;
      case 'daily':
        expr = `${minute} ${hour} * * *`;
        break;
      case 'weekly':
        expr = `${minute} ${hour} * * ${dayOfWeek.join(',')}`;
        break;
      case 'monthly':
        expr = `${minute} ${hour} ${dayOfMonth} * *`;
        break;
    }
    // Parse and update all parts
    const parsed = cronParser.parse(expr);
    if (parsed) {
      onUpdate('minute', parsed.minute);
      setTimeout(() => onUpdate('hour', parsed.hour), 0);
      setTimeout(() => onUpdate('dayOfMonth', parsed.dayOfMonth), 0);
      setTimeout(() => onUpdate('month', parsed.month), 0);
      setTimeout(() => onUpdate('dayOfWeek', parsed.dayOfWeek), 0);
    }
  }, [frequency, minute, hour, dayOfWeek, dayOfMonth]);

  return (
    <div className="space-y-4">
      {/* Frequency */}
      <div>
        <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-2">
          Fréquence
        </label>
        <div className="flex flex-wrap gap-2">
          {[
            { value: 'minutely', label: 'Chaque minute' },
            { value: 'hourly', label: 'Chaque heure' },
            { value: 'daily', label: 'Chaque jour' },
            { value: 'weekly', label: 'Chaque semaine' },
            { value: 'monthly', label: 'Chaque mois' },
          ].map((opt) => (
            <button
              key={opt.value}
              onClick={() => setFrequency(opt.value as any)}
              className={`px-3 py-1.5 text-sm rounded-lg border transition-colors
                ${frequency === opt.value
                  ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                  : 'border-gray-200 dark:border-gray-700 hover:bg-gray-100 dark:hover:bg-gray-800'}`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {/* Time (for hourly, daily, weekly, monthly) */}
      {frequency !== 'minutely' && (
        <div className="grid grid-cols-2 gap-4">
          {frequency !== 'hourly' && (
            <div>
              <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
                Heure
              </label>
              <select
                value={hour}
                onChange={(e) => setHour(parseInt(e.target.value))}
                className="w-full px-3 py-2 text-sm border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800"
              >
                {HOURS.map((h) => (
                  <option key={h} value={h}>
                    {h.toString().padStart(2, '0')}:00
                  </option>
                ))}
              </select>
            </div>
          )}
          <div>
            <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
              Minute
            </label>
            <select
              value={minute}
              onChange={(e) => setMinute(parseInt(e.target.value))}
              className="w-full px-3 py-2 text-sm border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800"
            >
              {[0, 15, 30, 45].map((m) => (
                <option key={m} value={m}>
                  :{m.toString().padStart(2, '0')}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      {/* Day of week (for weekly) */}
      {frequency === 'weekly' && (
        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-2">
            Jours de la semaine
          </label>
          <div className="flex flex-wrap gap-2">
            {DAYS_OF_WEEK.map((day) => (
              <button
                key={day.value}
                onClick={() => {
                  if (dayOfWeek.includes(day.value)) {
                    setDayOfWeek(dayOfWeek.filter((d) => d !== day.value));
                  } else {
                    setDayOfWeek([...dayOfWeek, day.value].sort());
                  }
                }}
                className={`px-3 py-1.5 text-sm rounded-lg border transition-colors
                  ${dayOfWeek.includes(day.value)
                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                    : 'border-gray-200 dark:border-gray-700 hover:bg-gray-100 dark:hover:bg-gray-800'}`}
              >
                {day.short}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Day of month (for monthly) */}
      {frequency === 'monthly' && (
        <div>
          <label className="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
            Jour du mois
          </label>
          <select
            value={dayOfMonth}
            onChange={(e) => setDayOfMonth(parseInt(e.target.value))}
            className="w-full px-3 py-2 text-sm border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800"
          >
            {DAYS_OF_MONTH.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Advanced Builder
// =============================================================================

const AdvancedCronBuilder: React.FC<BuilderProps> = ({ parts, onUpdate }) => {
  return (
    <div className="space-y-4">
      {/* Minute */}
      <CronFieldInput
        label="Minute"
        value={parts.minute}
        onChange={(v) => onUpdate('minute', v)}
        options={MINUTES.map((m) => ({ value: m.toString(), label: m.toString().padStart(2, '0') }))}
        placeholder="0-59"
        helpText="*, */5, 0, 0,30"
      />

      {/* Hour */}
      <CronFieldInput
        label="Heure"
        value={parts.hour}
        onChange={(v) => onUpdate('hour', v)}
        options={HOURS.map((h) => ({ value: h.toString(), label: `${h}h` }))}
        placeholder="0-23"
        helpText="*, */2, 9, 9-17"
      />

      {/* Day of Month */}
      <CronFieldInput
        label="Jour du mois"
        value={parts.dayOfMonth}
        onChange={(v) => onUpdate('dayOfMonth', v)}
        options={DAYS_OF_MONTH.map((d) => ({ value: d.toString(), label: d.toString() }))}
        placeholder="1-31"
        helpText="*, 1, 1,15, 1-7"
      />

      {/* Month */}
      <CronFieldInput
        label="Mois"
        value={parts.month}
        onChange={(v) => onUpdate('month', v)}
        options={MONTHS.map((m) => ({ value: m.value.toString(), label: m.label }))}
        placeholder="1-12"
        helpText="*, 1, 1-6, 1,4,7,10"
      />

      {/* Day of Week */}
      <CronFieldInput
        label="Jour de la semaine"
        value={parts.dayOfWeek}
        onChange={(v) => onUpdate('dayOfWeek', v)}
        options={DAYS_OF_WEEK.map((d) => ({ value: d.value.toString(), label: d.label }))}
        placeholder="0-6 (0=Dim)"
        helpText="*, 1-5, 0,6"
      />
    </div>
  );
};

// =============================================================================
// Field Input
// =============================================================================

interface CronFieldInputProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  placeholder: string;
  helpText: string;
}

const CronFieldInput: React.FC<CronFieldInputProps> = ({
  label,
  value,
  onChange,
  options,
  placeholder,
  helpText,
}) => {
  const [mode, setMode] = useState<'input' | 'select'>(value === '*' || value.includes('/') || value.includes('-') || value.includes(',') ? 'input' : 'select');
  const [showAll, setShowAll] = useState(false);

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <label className="text-xs font-medium text-gray-600 dark:text-gray-400">
          {label}
        </label>
        <div className="flex items-center space-x-2 text-xs">
          <button
            onClick={() => setMode('input')}
            className={`${mode === 'input' ? 'text-blue-600' : 'text-gray-400 hover:text-gray-600'}`}
          >
            Expression
          </button>
          <span className="text-gray-300">|</span>
          <button
            onClick={() => setMode('select')}
            className={`${mode === 'select' ? 'text-blue-600' : 'text-gray-400 hover:text-gray-600'}`}
          >
            Sélection
          </button>
        </div>
      </div>

      {mode === 'input' ? (
        <div>
          <input
            type="text"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={placeholder}
            className="w-full px-3 py-2 text-sm font-mono border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800"
          />
          <p className="mt-1 text-xs text-gray-500">Exemples: {helpText}</p>
        </div>
      ) : (
        <div>
          <div className="flex flex-wrap gap-1">
            <button
              onClick={() => onChange('*')}
              className={`px-2 py-1 text-xs rounded border transition-colors
                ${value === '*'
                  ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-700'
                  : 'border-gray-200 dark:border-gray-700 hover:bg-gray-100 dark:hover:bg-gray-800'}`}
            >
              Tous
            </button>
            {(showAll ? options : options.slice(0, 12)).map((opt) => {
              const isSelected = value.split(',').includes(opt.value);
              return (
                <button
                  key={opt.value}
                  onClick={() => {
                    if (value === '*') {
                      onChange(opt.value);
                    } else {
                      const values = value.split(',').filter((v) => v !== '*');
                      if (isSelected) {
                        const newValues = values.filter((v) => v !== opt.value);
                        onChange(newValues.length ? newValues.join(',') : '*');
                      } else {
                        onChange([...values, opt.value].sort((a, b) => parseInt(a) - parseInt(b)).join(','));
                      }
                    }
                  }}
                  className={`px-2 py-1 text-xs rounded border transition-colors
                    ${isSelected
                      ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30 text-blue-700'
                      : 'border-gray-200 dark:border-gray-700 hover:bg-gray-100 dark:hover:bg-gray-800'}`}
                >
                  {opt.label}
                </button>
              );
            })}
            {options.length > 12 && (
              <button
                onClick={() => setShowAll(!showAll)}
                className="px-2 py-1 text-xs text-blue-600 hover:text-blue-700"
              >
                {showAll ? 'Moins' : `+${options.length - 12} plus`}
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default CronBuilder;
