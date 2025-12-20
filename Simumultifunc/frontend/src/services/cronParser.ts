/**
 * Cron Parser Service
 *
 * Utilitaire pour parser, valider et décrire les expressions cron.
 * Support du format cron standard à 5 champs:
 * minute hour day-of-month month day-of-week
 */

import { CronPreset, CRON_PRESETS } from '../types/scheduler.types';

// =============================================================================
// Types
// =============================================================================

export interface CronField {
  name: string;
  min: number;
  max: number;
  names?: Record<number, string>;
}

export interface ParsedCron {
  minute: string;
  hour: string;
  dayOfMonth: string;
  month: string;
  dayOfWeek: string;
}

export interface CronValidationResult {
  valid: boolean;
  error?: string;
  parsed?: ParsedCron;
}

// =============================================================================
// Constants
// =============================================================================

const CRON_FIELDS: CronField[] = [
  { name: 'minute', min: 0, max: 59 },
  { name: 'hour', min: 0, max: 23 },
  { name: 'dayOfMonth', min: 1, max: 31 },
  {
    name: 'month',
    min: 1,
    max: 12,
    names: {
      1: 'JAN', 2: 'FEB', 3: 'MAR', 4: 'APR', 5: 'MAY', 6: 'JUN',
      7: 'JUL', 8: 'AUG', 9: 'SEP', 10: 'OCT', 11: 'NOV', 12: 'DEC',
    },
  },
  {
    name: 'dayOfWeek',
    min: 0,
    max: 6,
    names: {
      0: 'SUN', 1: 'MON', 2: 'TUE', 3: 'WED', 4: 'THU', 5: 'FRI', 6: 'SAT',
    },
  },
];

const MONTH_NAMES = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];

const DAY_NAMES = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];

// =============================================================================
// Validation
// =============================================================================

/**
 * Valide une expression cron
 */
export function validateCron(expression: string): CronValidationResult {
  if (!expression || typeof expression !== 'string') {
    return { valid: false, error: 'Expression cron requise' };
  }

  const parts = expression.trim().split(/\s+/);

  if (parts.length !== 5) {
    return {
      valid: false,
      error: `Format invalide: attendu 5 champs, reçu ${parts.length}`,
    };
  }

  for (let i = 0; i < parts.length; i++) {
    const field = CRON_FIELDS[i];
    const error = validateField(parts[i], field);
    if (error) {
      return { valid: false, error: `${field.name}: ${error}` };
    }
  }

  return {
    valid: true,
    parsed: {
      minute: parts[0],
      hour: parts[1],
      dayOfMonth: parts[2],
      month: parts[3],
      dayOfWeek: parts[4],
    },
  };
}

/**
 * Valide un champ cron individuel
 */
function validateField(value: string, field: CronField): string | null {
  // Wildcard
  if (value === '*') {
    return null;
  }

  // Step: */n ou n/m
  if (value.includes('/')) {
    const [range, step] = value.split('/');
    if (range !== '*') {
      const rangeError = validateRange(range, field);
      if (rangeError) return rangeError;
    }
    const stepNum = parseInt(step, 10);
    if (isNaN(stepNum) || stepNum < 1) {
      return `Step invalide: ${step}`;
    }
    return null;
  }

  // List: 1,2,3
  if (value.includes(',')) {
    const items = value.split(',');
    for (const item of items) {
      const error = validateFieldValue(item.trim(), field);
      if (error) return error;
    }
    return null;
  }

  // Range: 1-5
  if (value.includes('-')) {
    return validateRange(value, field);
  }

  // Single value
  return validateFieldValue(value, field);
}

/**
 * Valide une plage (1-5)
 */
function validateRange(value: string, field: CronField): string | null {
  const [start, end] = value.split('-').map((v) => parseValue(v, field));

  if (start === null || end === null) {
    return `Plage invalide: ${value}`;
  }

  if (start > end) {
    return `Début de plage > fin: ${value}`;
  }

  if (start < field.min || end > field.max) {
    return `Valeur hors limites [${field.min}-${field.max}]: ${value}`;
  }

  return null;
}

/**
 * Valide une valeur unique
 */
function validateFieldValue(value: string, field: CronField): string | null {
  const num = parseValue(value, field);

  if (num === null) {
    return `Valeur invalide: ${value}`;
  }

  if (num < field.min || num > field.max) {
    return `Valeur hors limites [${field.min}-${field.max}]: ${value}`;
  }

  return null;
}

/**
 * Parse une valeur (nombre ou nom)
 */
function parseValue(value: string, field: CronField): number | null {
  const num = parseInt(value, 10);
  if (!isNaN(num)) {
    return num;
  }

  // Check for named values (JAN, MON, etc.)
  if (field.names) {
    const upperValue = value.toUpperCase();
    for (const [num, name] of Object.entries(field.names)) {
      if (name === upperValue) {
        return parseInt(num, 10);
      }
    }
  }

  return null;
}

// =============================================================================
// Description
// =============================================================================

/**
 * Génère une description en français d'une expression cron
 */
export function describeCron(expression: string): string {
  const validation = validateCron(expression);
  if (!validation.valid || !validation.parsed) {
    return 'Expression invalide';
  }

  const { minute, hour, dayOfMonth, month, dayOfWeek } = validation.parsed;

  // Check for common patterns
  const preset = findMatchingPreset(expression);
  if (preset) {
    return preset.description;
  }

  let description = 'Exécute ';

  // Time description
  description += describeTime(minute, hour);

  // Day of week
  if (dayOfWeek !== '*') {
    description += ' ' + describeDayOfWeek(dayOfWeek);
  }

  // Day of month
  if (dayOfMonth !== '*') {
    description += ' le ' + describeDayOfMonth(dayOfMonth);
  }

  // Month
  if (month !== '*') {
    description += ' en ' + describeMonth(month);
  }

  return description;
}

function describeTime(minute: string, hour: string): string {
  // Every minute
  if (minute === '*' && hour === '*') {
    return 'chaque minute';
  }

  // Step minutes
  if (minute.startsWith('*/')) {
    const step = minute.split('/')[1];
    if (hour === '*') {
      return `toutes les ${step} minutes`;
    }
    return `toutes les ${step} minutes à ${formatHour(hour)}`;
  }

  // Specific hour
  if (minute === '0') {
    if (hour === '*') {
      return 'au début de chaque heure';
    }
    if (hour.startsWith('*/')) {
      const step = hour.split('/')[1];
      return `toutes les ${step} heures`;
    }
    return `à ${formatHour(hour)}`;
  }

  // Specific time
  if (!minute.includes(',') && !minute.includes('-') && !minute.includes('/')) {
    if (hour !== '*' && !hour.includes(',') && !hour.includes('-') && !hour.includes('/')) {
      return `à ${formatHour(hour)}:${minute.padStart(2, '0')}`;
    }
  }

  return `à la minute ${minute} de ${formatHour(hour)}`;
}

function formatHour(hour: string): string {
  if (hour === '*') return 'chaque heure';

  if (hour.includes('-')) {
    const [start, end] = hour.split('-');
    return `${start}h-${end}h`;
  }

  if (hour.includes(',')) {
    const hours = hour.split(',');
    return hours.map((h) => `${h}h`).join(', ');
  }

  return `${hour}h`;
}

function describeDayOfWeek(dayOfWeek: string): string {
  if (dayOfWeek === '*') return '';

  if (dayOfWeek.includes('-')) {
    const [start, end] = dayOfWeek.split('-').map((d) => parseInt(d, 10));
    if (start === 1 && end === 5) {
      return 'du lundi au vendredi';
    }
    return `du ${DAY_NAMES[start]} au ${DAY_NAMES[end]}`;
  }

  if (dayOfWeek.includes(',')) {
    const days = dayOfWeek.split(',').map((d) => DAY_NAMES[parseInt(d, 10)]);
    return 'les ' + days.join(', ');
  }

  const day = parseInt(dayOfWeek, 10);
  return `chaque ${DAY_NAMES[day]}`;
}

function describeDayOfMonth(dayOfMonth: string): string {
  if (dayOfMonth === '*') return '';

  if (dayOfMonth.includes('-')) {
    const [start, end] = dayOfMonth.split('-');
    return `${start} au ${end}`;
  }

  if (dayOfMonth.includes(',')) {
    return dayOfMonth.split(',').join(', ');
  }

  if (dayOfMonth === '1') {
    return 'premier du mois';
  }

  return dayOfMonth;
}

function describeMonth(month: string): string {
  if (month === '*') return '';

  if (month.includes('-')) {
    const [start, end] = month.split('-').map((m) => parseInt(m, 10));
    return `${MONTH_NAMES[start - 1]} à ${MONTH_NAMES[end - 1]}`;
  }

  if (month.includes(',')) {
    const months = month.split(',').map((m) => MONTH_NAMES[parseInt(m, 10) - 1]);
    return months.join(', ');
  }

  return MONTH_NAMES[parseInt(month, 10) - 1];
}

function findMatchingPreset(expression: string): CronPreset | undefined {
  return CRON_PRESETS.find((p) => p.expression === expression);
}

// =============================================================================
// Next Runs Calculation
// =============================================================================

/**
 * Calcule les prochaines exécutions d'une expression cron
 */
export function getNextRuns(expression: string, count = 5, fromDate = new Date()): Date[] {
  const validation = validateCron(expression);
  if (!validation.valid || !validation.parsed) {
    return [];
  }

  const runs: Date[] = [];
  let current = new Date(fromDate);
  current.setSeconds(0);
  current.setMilliseconds(0);

  // Start from next minute
  current.setMinutes(current.getMinutes() + 1);

  const maxIterations = 365 * 24 * 60; // 1 year of minutes
  let iterations = 0;

  while (runs.length < count && iterations < maxIterations) {
    if (matchesCron(current, validation.parsed)) {
      runs.push(new Date(current));
    }
    current.setMinutes(current.getMinutes() + 1);
    iterations++;
  }

  return runs;
}

/**
 * Vérifie si une date correspond à une expression cron parsée
 */
function matchesCron(date: Date, parsed: ParsedCron): boolean {
  return (
    matchesField(date.getMinutes(), parsed.minute, CRON_FIELDS[0]) &&
    matchesField(date.getHours(), parsed.hour, CRON_FIELDS[1]) &&
    matchesField(date.getDate(), parsed.dayOfMonth, CRON_FIELDS[2]) &&
    matchesField(date.getMonth() + 1, parsed.month, CRON_FIELDS[3]) &&
    matchesField(date.getDay(), parsed.dayOfWeek, CRON_FIELDS[4])
  );
}

/**
 * Vérifie si une valeur correspond à un champ cron
 */
function matchesField(value: number, field: string, fieldDef: CronField): boolean {
  // Wildcard
  if (field === '*') {
    return true;
  }

  // Step
  if (field.includes('/')) {
    const [range, stepStr] = field.split('/');
    const step = parseInt(stepStr, 10);

    if (range === '*') {
      return value % step === 0;
    }

    const [start, end] = range.split('-').map((v) => parseValue(v, fieldDef) || 0);
    return value >= start && value <= end && (value - start) % step === 0;
  }

  // List
  if (field.includes(',')) {
    const values = field.split(',').map((v) => parseValue(v.trim(), fieldDef));
    return values.includes(value);
  }

  // Range
  if (field.includes('-')) {
    const [start, end] = field.split('-').map((v) => parseValue(v, fieldDef) || 0);
    return value >= start && value <= end;
  }

  // Single value
  return parseValue(field, fieldDef) === value;
}

// =============================================================================
// Builder Helpers
// =============================================================================

/**
 * Construit une expression cron à partir de paramètres individuels
 */
export function buildCronExpression(params: {
  minute?: string;
  hour?: string;
  dayOfMonth?: string;
  month?: string;
  dayOfWeek?: string;
}): string {
  return [
    params.minute || '*',
    params.hour || '*',
    params.dayOfMonth || '*',
    params.month || '*',
    params.dayOfWeek || '*',
  ].join(' ');
}

/**
 * Parse une expression cron en objet
 */
export function parseCronExpression(expression: string): ParsedCron | null {
  const validation = validateCron(expression);
  return validation.parsed || null;
}

/**
 * Génère une expression cron pour une exécution quotidienne
 */
export function dailyAt(hour: number, minute = 0): string {
  return `${minute} ${hour} * * *`;
}

/**
 * Génère une expression cron pour une exécution hebdomadaire
 */
export function weeklyAt(dayOfWeek: number, hour: number, minute = 0): string {
  return `${minute} ${hour} * * ${dayOfWeek}`;
}

/**
 * Génère une expression cron pour une exécution mensuelle
 */
export function monthlyAt(dayOfMonth: number, hour: number, minute = 0): string {
  return `${minute} ${hour} ${dayOfMonth} * *`;
}

/**
 * Génère une expression cron pour une exécution toutes les N minutes
 */
export function everyNMinutes(n: number): string {
  return `*/${n} * * * *`;
}

/**
 * Génère une expression cron pour une exécution toutes les N heures
 */
export function everyNHours(n: number, minute = 0): string {
  return `${minute} */${n} * * *`;
}

// =============================================================================
// Export
// =============================================================================

export const cronParser = {
  validate: validateCron,
  describe: describeCron,
  getNextRuns,
  build: buildCronExpression,
  parse: parseCronExpression,
  dailyAt,
  weeklyAt,
  monthlyAt,
  everyNMinutes,
  everyNHours,
};

export default cronParser;
