/**
 * Date Utilities
 *
 * Utilitaires de formatage de dates (remplacement léger de date-fns)
 */

// =============================================================================
// Locales
// =============================================================================

const MONTHS_FR = [
  'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'
];

const MONTHS_SHORT_FR = [
  'janv.', 'févr.', 'mars', 'avr.', 'mai', 'juin',
  'juil.', 'août', 'sept.', 'oct.', 'nov.', 'déc.'
];

const DAYS_FR = [
  'dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'
];

const DAYS_SHORT_FR = ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'];

// =============================================================================
// Format
// =============================================================================

/**
 * Formate une date selon un pattern
 * Patterns supportés:
 * - yyyy: année complète (2024)
 * - MM: mois sur 2 chiffres (01-12)
 * - dd: jour sur 2 chiffres (01-31)
 * - HH: heure sur 2 chiffres (00-23)
 * - mm: minute sur 2 chiffres (00-59)
 * - ss: seconde sur 2 chiffres (00-59)
 * - SSS: millisecondes (000-999)
 * - MMMM: nom du mois (janvier)
 * - MMM: nom du mois court (janv.)
 * - EEEE: nom du jour (lundi)
 * - EEE: nom du jour court (lun.)
 * - d: jour sans zéro (1-31)
 */
export function format(date: Date | string | number, pattern: string, options?: { locale?: any }): string {
  const d = new Date(date);

  if (isNaN(d.getTime())) {
    return 'Invalid Date';
  }

  const tokens: Record<string, string> = {
    yyyy: d.getFullYear().toString(),
    MM: (d.getMonth() + 1).toString().padStart(2, '0'),
    dd: d.getDate().toString().padStart(2, '0'),
    d: d.getDate().toString(),
    HH: d.getHours().toString().padStart(2, '0'),
    mm: d.getMinutes().toString().padStart(2, '0'),
    ss: d.getSeconds().toString().padStart(2, '0'),
    SSS: d.getMilliseconds().toString().padStart(3, '0'),
    MMMM: MONTHS_FR[d.getMonth()],
    MMM: MONTHS_SHORT_FR[d.getMonth()],
    EEEE: DAYS_FR[d.getDay()],
    EEE: DAYS_SHORT_FR[d.getDay()],
  };

  let result = pattern;

  // Handle escaped text with single quotes
  result = result.replace(/'([^']+)'/g, '\0$1\0');

  // Replace tokens (order matters - longer tokens first)
  const sortedTokens = Object.keys(tokens).sort((a, b) => b.length - a.length);
  for (const token of sortedTokens) {
    result = result.replace(new RegExp(token, 'g'), tokens[token]);
  }

  // Restore escaped text
  result = result.replace(/\0([^\0]+)\0/g, '$1');

  return result;
}

// =============================================================================
// Relative Time
// =============================================================================

interface FormatDistanceOptions {
  addSuffix?: boolean;
  locale?: any;
}

/**
 * Retourne une description relative de la date par rapport à maintenant
 * Ex: "il y a 5 minutes", "dans 2 heures"
 */
export function formatDistanceToNow(date: Date | string | number, options?: FormatDistanceOptions): string {
  const d = new Date(date);
  const now = new Date();
  const diffMs = d.getTime() - now.getTime();
  const absDiffMs = Math.abs(diffMs);
  const isPast = diffMs < 0;

  let distance: string;

  const seconds = Math.floor(absDiffMs / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);
  const months = Math.floor(days / 30);
  const years = Math.floor(days / 365);

  if (seconds < 30) {
    distance = 'quelques secondes';
  } else if (seconds < 60) {
    distance = 'moins d\'une minute';
  } else if (minutes === 1) {
    distance = 'une minute';
  } else if (minutes < 60) {
    distance = `${minutes} minutes`;
  } else if (hours === 1) {
    distance = 'une heure';
  } else if (hours < 24) {
    distance = `${hours} heures`;
  } else if (days === 1) {
    distance = 'un jour';
  } else if (days < 30) {
    distance = `${days} jours`;
  } else if (months === 1) {
    distance = 'un mois';
  } else if (months < 12) {
    distance = `${months} mois`;
  } else if (years === 1) {
    distance = 'un an';
  } else {
    distance = `${years} ans`;
  }

  if (options?.addSuffix) {
    return isPast ? `il y a ${distance}` : `dans ${distance}`;
  }

  return distance;
}

// =============================================================================
// Duration
// =============================================================================

/**
 * Formate une durée en ms en texte lisible
 */
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;

  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);

  if (minutes < 60) {
    return seconds > 0 ? `${minutes}m ${seconds}s` : `${minutes}m`;
  }

  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;

  if (hours < 24) {
    return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
  }

  const days = Math.floor(hours / 24);
  const remainingHours = hours % 24;

  return remainingHours > 0 ? `${days}j ${remainingHours}h` : `${days}j`;
}

// =============================================================================
// Helpers
// =============================================================================

/**
 * Vérifie si deux dates sont le même jour
 */
export function isSameDay(date1: Date, date2: Date): boolean {
  return (
    date1.getFullYear() === date2.getFullYear() &&
    date1.getMonth() === date2.getMonth() &&
    date1.getDate() === date2.getDate()
  );
}

/**
 * Vérifie si une date est aujourd'hui
 */
export function isToday(date: Date): boolean {
  return isSameDay(date, new Date());
}

/**
 * Retourne le début du jour
 */
export function startOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(0, 0, 0, 0);
  return d;
}

/**
 * Retourne la fin du jour
 */
export function endOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(23, 59, 59, 999);
  return d;
}

/**
 * Ajoute des jours à une date
 */
export function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

/**
 * Ajoute des mois à une date
 */
export function addMonths(date: Date, months: number): Date {
  const d = new Date(date);
  d.setMonth(d.getMonth() + months);
  return d;
}

// =============================================================================
// Export fr locale placeholder
// =============================================================================

export const fr = { code: 'fr' };

// =============================================================================
// Default export
// =============================================================================

export default {
  format,
  formatDistanceToNow,
  formatDuration,
  isSameDay,
  isToday,
  startOfDay,
  endOfDay,
  addDays,
  addMonths,
};
