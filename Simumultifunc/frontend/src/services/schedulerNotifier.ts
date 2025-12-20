/**
 * Scheduler Notifier Service
 *
 * Service de notifications pour le scheduler.
 * Gère les notifications navigateur, sonores et visuelles.
 */

import {
  ScheduledTask,
  TaskStatus,
  NotificationEvent,
  NotificationType,
} from '../types/scheduler.types';

// =============================================================================
// Types
// =============================================================================

export interface NotificationOptions {
  title: string;
  body: string;
  icon?: string;
  tag?: string;
  requireInteraction?: boolean;
  actions?: NotificationAction[];
  onClick?: () => void;
}

export interface NotificationAction {
  action: string;
  title: string;
}

// =============================================================================
// State
// =============================================================================

let notificationPermission: NotificationPermission = 'default';
let audioEnabled = true;

// Audio files for notifications
const sounds = {
  success: new Audio('/sounds/success.mp3'),
  error: new Audio('/sounds/error.mp3'),
  warning: new Audio('/sounds/warning.mp3'),
  info: new Audio('/sounds/info.mp3'),
};

// =============================================================================
// Permission
// =============================================================================

/**
 * Demande la permission pour les notifications
 */
export async function requestNotificationPermission(): Promise<NotificationPermission> {
  if (!('Notification' in window)) {
    console.warn('[Notifier] Notifications not supported');
    return 'denied';
  }

  if (Notification.permission !== 'default') {
    notificationPermission = Notification.permission;
    return notificationPermission;
  }

  try {
    const permission = await Notification.requestPermission();
    notificationPermission = permission;
    return permission;
  } catch (error) {
    console.error('[Notifier] Failed to request permission:', error);
    return 'denied';
  }
}

/**
 * Vérifie si les notifications sont autorisées
 */
export function hasNotificationPermission(): boolean {
  return notificationPermission === 'granted';
}

// =============================================================================
// Browser Notifications
// =============================================================================

/**
 * Affiche une notification navigateur
 */
export function showNotification(options: NotificationOptions): Notification | null {
  if (!hasNotificationPermission()) {
    console.warn('[Notifier] No permission for notifications');
    return null;
  }

  try {
    const notification = new Notification(options.title, {
      body: options.body,
      icon: options.icon || '/favicon.ico',
      tag: options.tag,
      requireInteraction: options.requireInteraction,
    });

    if (options.onClick) {
      notification.onclick = () => {
        window.focus();
        options.onClick?.();
        notification.close();
      };
    }

    return notification;
  } catch (error) {
    console.error('[Notifier] Failed to show notification:', error);
    return null;
  }
}

// =============================================================================
// Task Notifications
// =============================================================================

/**
 * Notifie du démarrage d'une tâche
 */
export function notifyTaskStarted(task: ScheduledTask): void {
  if (!shouldNotify(task, NotificationEvent.ON_START)) return;

  showNotification({
    title: 'Tâche démarrée',
    body: `${task.name} est en cours d'exécution`,
    tag: `task-${task.id}`,
    icon: getIconForTaskType(task.type),
  });

  playSound('info');
}

/**
 * Notifie de la fin d'une tâche
 */
export function notifyTaskCompleted(task: ScheduledTask): void {
  if (!shouldNotify(task, NotificationEvent.ON_COMPLETE)) return;

  showNotification({
    title: 'Tâche terminée',
    body: `${task.name} s'est terminée avec succès`,
    tag: `task-${task.id}`,
    icon: getIconForTaskType(task.type),
  });

  playSound('success');
}

/**
 * Notifie de l'échec d'une tâche
 */
export function notifyTaskFailed(task: ScheduledTask, error?: string): void {
  if (!shouldNotify(task, NotificationEvent.ON_FAILURE)) return;

  showNotification({
    title: 'Tâche échouée',
    body: error ? `${task.name}: ${error}` : `${task.name} a échoué`,
    tag: `task-${task.id}`,
    icon: getIconForTaskType(task.type),
    requireInteraction: true,
  });

  playSound('error');
}

/**
 * Notifie d'un retry de tâche
 */
export function notifyTaskRetry(task: ScheduledTask, retryCount: number): void {
  if (!shouldNotify(task, NotificationEvent.ON_RETRY)) return;

  showNotification({
    title: 'Nouvelle tentative',
    body: `${task.name} - tentative ${retryCount}`,
    tag: `task-${task.id}`,
    icon: getIconForTaskType(task.type),
  });

  playSound('warning');
}

/**
 * Notifie d'un statut de tâche générique
 */
export function notifyTaskStatus(task: ScheduledTask, status: TaskStatus): void {
  switch (status) {
    case TaskStatus.RUNNING:
      notifyTaskStarted(task);
      break;
    case TaskStatus.COMPLETED:
      notifyTaskCompleted(task);
      break;
    case TaskStatus.FAILED:
      notifyTaskFailed(task);
      break;
  }
}

// =============================================================================
// Toast Notifications (in-app)
// =============================================================================

type ToastType = 'success' | 'error' | 'warning' | 'info';

interface ToastOptions {
  message: string;
  type?: ToastType;
  duration?: number;
  action?: {
    label: string;
    onClick: () => void;
  };
}

// Toast event emitter
type ToastListener = (toast: ToastOptions & { id: string }) => void;
const toastListeners: ToastListener[] = [];

/**
 * Ajoute un listener pour les toasts
 */
export function onToast(listener: ToastListener): () => void {
  toastListeners.push(listener);
  return () => {
    const index = toastListeners.indexOf(listener);
    if (index !== -1) {
      toastListeners.splice(index, 1);
    }
  };
}

/**
 * Affiche un toast
 */
export function showToast(options: ToastOptions): string {
  const id = `toast-${Date.now()}`;
  const toast = { ...options, id };

  toastListeners.forEach((listener) => listener(toast));

  return id;
}

/**
 * Raccourcis pour les toasts
 */
export const toast = {
  success: (message: string, options?: Partial<ToastOptions>) =>
    showToast({ message, type: 'success', ...options }),

  error: (message: string, options?: Partial<ToastOptions>) =>
    showToast({ message, type: 'error', duration: 5000, ...options }),

  warning: (message: string, options?: Partial<ToastOptions>) =>
    showToast({ message, type: 'warning', ...options }),

  info: (message: string, options?: Partial<ToastOptions>) =>
    showToast({ message, type: 'info', ...options }),
};

// =============================================================================
// Sound
// =============================================================================

/**
 * Active/désactive les sons
 */
export function setAudioEnabled(enabled: boolean): void {
  audioEnabled = enabled;
}

/**
 * Joue un son de notification
 */
export function playSound(type: 'success' | 'error' | 'warning' | 'info'): void {
  if (!audioEnabled) return;

  const audio = sounds[type];
  if (audio) {
    audio.currentTime = 0;
    audio.play().catch((err) => {
      // Audio playback might be blocked by browser
      console.debug('[Notifier] Audio playback blocked:', err);
    });
  }
}

// =============================================================================
// Helpers
// =============================================================================

/**
 * Vérifie si la tâche doit déclencher une notification pour cet événement
 */
function shouldNotify(task: ScheduledTask, event: NotificationEvent): boolean {
  if (!task.notifications?.enabled) return false;
  if (!task.notifications.events.includes(event)) return false;

  // Check if any channel is browser/console
  return task.notifications.channels.some(
    (ch) => ch.type === NotificationType.CONSOLE
  );
}

/**
 * Retourne l'icône pour un type de tâche
 */
function getIconForTaskType(type: string): string {
  const icons: Record<string, string> = {
    session: '/icons/zap.png',
    tnr: '/icons/test.png',
    performance: '/icons/activity.png',
    custom: '/icons/code.png',
  };
  return icons[type] || '/favicon.ico';
}

// =============================================================================
// Initialization
// =============================================================================

/**
 * Initialise le service de notifications
 */
export async function initNotifier(): Promise<void> {
  // Request permission
  await requestNotificationPermission();

  // Preload sounds
  Object.values(sounds).forEach((audio) => {
    audio.load();
  });

  console.log('[Notifier] Initialized, permission:', notificationPermission);
}

// =============================================================================
// Export
// =============================================================================

export const schedulerNotifier = {
  init: initNotifier,
  requestPermission: requestNotificationPermission,
  hasPermission: hasNotificationPermission,
  show: showNotification,
  taskStarted: notifyTaskStarted,
  taskCompleted: notifyTaskCompleted,
  taskFailed: notifyTaskFailed,
  taskRetry: notifyTaskRetry,
  taskStatus: notifyTaskStatus,
  toast,
  onToast,
  setAudioEnabled,
  playSound,
};

export default schedulerNotifier;
