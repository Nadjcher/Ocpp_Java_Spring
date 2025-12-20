/**
 * Types pour le Scheduler Enterprise
 *
 * Système de planification avancé avec support cron,
 * dépendances entre tâches, notifications et historique.
 */

// =============================================================================
// Enums
// =============================================================================

export enum TaskType {
  SESSION = 'session',
  TNR = 'tnr',
  PERFORMANCE = 'performance',
  CUSTOM = 'custom'
}

export enum TaskStatus {
  PENDING = 'pending',
  SCHEDULED = 'scheduled',
  RUNNING = 'running',
  COMPLETED = 'completed',
  FAILED = 'failed',
  CANCELLED = 'cancelled',
  PAUSED = 'paused'
}

export enum TaskPriority {
  LOW = 'low',
  NORMAL = 'normal',
  HIGH = 'high',
  CRITICAL = 'critical'
}

export enum ScheduleType {
  ONCE = 'once',
  RECURRING = 'recurring',
  CRON = 'cron',
  DEPENDENT = 'dependent'
}

export enum RecurrencePattern {
  MINUTELY = 'minutely',
  HOURLY = 'hourly',
  DAILY = 'daily',
  WEEKLY = 'weekly',
  MONTHLY = 'monthly'
}

export enum NotificationType {
  EMAIL = 'email',
  WEBHOOK = 'webhook',
  SLACK = 'slack',
  CONSOLE = 'console'
}

export enum NotificationEvent {
  ON_START = 'onStart',
  ON_COMPLETE = 'onComplete',
  ON_FAILURE = 'onFailure',
  ON_RETRY = 'onRetry'
}

export enum DependencyCondition {
  SUCCESS = 'success',
  FAILURE = 'failure',
  COMPLETION = 'completion',
  ANY = 'any'
}

// =============================================================================
// Task Schedule
// =============================================================================

export interface TaskSchedule {
  type: ScheduleType;
  /** Pour ONCE: date/heure d'exécution */
  scheduledAt?: Date;
  /** Pour RECURRING */
  recurrence?: {
    pattern: RecurrencePattern;
    interval: number;
    startAt?: Date;
    endAt?: Date;
    daysOfWeek?: number[];  // 0-6, dimanche = 0
    daysOfMonth?: number[]; // 1-31
    monthsOfYear?: number[]; // 1-12
  };
  /** Pour CRON */
  cronExpression?: string;
  cronDescription?: string;
  /** Timezone */
  timezone?: string;
}

// =============================================================================
// Execution Options
// =============================================================================

export interface ExecutionOptions {
  /** Timeout en ms */
  timeout?: number;
  /** Nombre de retries */
  maxRetries?: number;
  /** Délai entre retries en ms */
  retryDelay?: number;
  /** Backoff exponentiel */
  exponentialBackoff?: boolean;
  /** Exécution parallèle autorisée */
  allowParallel?: boolean;
  /** Environnement d'exécution */
  environment?: 'dev' | 'staging' | 'prod';
  /** Variables d'environnement */
  envVars?: Record<string, string>;
  /** Tags pour filtrage */
  tags?: string[];
}

// =============================================================================
// Task Dependency
// =============================================================================

export interface TaskDependency {
  taskId: string;
  condition: DependencyCondition;
  /** Délai après la fin de la tâche dépendante */
  delayMs?: number;
}

// =============================================================================
// Notifications
// =============================================================================

export interface NotificationConfig {
  enabled: boolean;
  events: NotificationEvent[];
  channels: NotificationChannel[];
}

export interface NotificationChannel {
  type: NotificationType;
  config: EmailConfig | WebhookConfig | SlackConfig | ConsoleConfig;
}

export interface EmailConfig {
  recipients: string[];
  subject?: string;
  includeDetails?: boolean;
}

export interface WebhookConfig {
  url: string;
  method?: 'POST' | 'PUT';
  headers?: Record<string, string>;
  includePayload?: boolean;
}

export interface SlackConfig {
  webhookUrl: string;
  channel?: string;
  username?: string;
  iconEmoji?: string;
}

export interface ConsoleConfig {
  logLevel?: 'info' | 'warn' | 'error';
}

// =============================================================================
// Session & TNR Configs
// =============================================================================

export interface SessionConfig {
  /** Action: "create", "start", "stop", "delete", "plug", "unplug" (défaut: "create") */
  sessionAction?: 'create' | 'start' | 'stop' | 'delete' | 'plug' | 'unplug';
  /** ID de session existante (pour start/stop/delete/plug/unplug) */
  sessionId?: string;

  // ─────────────────────────────────────────────────────────────────────────
  // Configuration WebSocket OCPP (requis pour "create")
  // ─────────────────────────────────────────────────────────────────────────

  /** URL WebSocket OCPP (ex: wss://evse-test.total-ev-charge.com/ocpp/WebSocket) */
  wsUrl?: string;
  /** Titre de la session */
  title?: string;
  /** Charge Point ID */
  chargePointId?: string;
  /** Alias cpId pour compatibilité */
  cpId?: string;
  /** Connecteur (1 par défaut) */
  connectorId?: number;
  /** Badge RFID / ID Tag */
  idTag?: string;
  /** Token Bearer pour authentification */
  bearerToken?: string;

  // ─────────────────────────────────────────────────────────────────────────
  // Configuration véhicule et charge
  // ─────────────────────────────────────────────────────────────────────────

  /** ID du profil véhicule */
  vehicleId?: string;
  /** Type de chargeur: AC_MONO, AC_TRI, DC */
  chargerType?: 'AC_MONO' | 'AC_TRI' | 'DC';
  /** Type de phase: AC_MONO, AC_TRI, DC */
  phaseType?: 'AC_MONO' | 'AC_TRI' | 'DC';
  /** SoC initial (0-100) */
  initialSoc?: number;
  /** SoC cible (0-100) */
  targetSoc?: number;
  /** Puissance max en kW */
  maxPowerKw?: number;
  /** Courant max en A */
  maxCurrentA?: number;

  // ─────────────────────────────────────────────────────────────────────────
  // Configuration OCPP
  // ─────────────────────────────────────────────────────────────────────────

  /** Intervalle heartbeat en secondes */
  heartbeatInterval?: number;
  /** Intervalle MeterValues en secondes */
  meterValuesInterval?: number;
  /** Vendor du Charge Point */
  vendor?: string;
  /** Modèle du Charge Point */
  model?: string;
  /** Numéro de série */
  serialNumber?: string;
  /** Version firmware */
  firmwareVersion?: string;
  /** Version OCPP: "1.6" ou "2.0.1" */
  ocppVersion?: '1.6' | '2.0.1';

  // ─────────────────────────────────────────────────────────────────────────
  // Options legacy (compatibilité)
  // ─────────────────────────────────────────────────────────────────────────

  /** Configuration de la session */
  energyKwh?: number;
  durationMinutes?: number;
  /** Smart charging */
  enableSmartCharging?: boolean;
  chargingProfileId?: string;
  meterValuesSampledData?: string[];
}

export interface TnrConfig {
  /** ID du scénario à exécuter (prioritaire sur scenarioIds) */
  scenarioId?: string;
  /** Scénarios à exécuter (compatibilité) */
  scenarioIds?: string[];

  // ─────────────────────────────────────────────────────────────────────────
  // Configuration WebSocket TNR
  // ─────────────────────────────────────────────────────────────────────────

  /** URL WebSocket pour TNR (peut override celle du scénario) */
  tnrWsUrl?: string;
  /** Alias wsUrl pour compatibilité */
  wsUrl?: string;

  // ─────────────────────────────────────────────────────────────────────────
  // Configuration d'exécution
  // ─────────────────────────────────────────────────────────────────────────

  /** Paramètres du scénario */
  scenarioParams?: Record<string, string>;
  /** Variables du contexte TNR */
  tnrVariables?: Record<string, unknown>;
  /** Nombre de répétitions */
  repeatCount?: number;
  /** Continuer en cas d'erreur */
  continueOnError?: boolean;
  /** Timeout global en ms */
  globalTimeoutMs?: number;

  // ─────────────────────────────────────────────────────────────────────────
  // Options de rapport
  // ─────────────────────────────────────────────────────────────────────────

  /** Générer les rapports */
  generateReports?: boolean;
  /** Formats de rapport */
  reportFormats?: ('json' | 'html' | 'junit')[];

  // ─────────────────────────────────────────────────────────────────────────
  // Options legacy (compatibilité)
  // ─────────────────────────────────────────────────────────────────────────

  /** Tags de filtrage */
  tags?: string[];
  /** Environnement */
  environment?: string;
  /** Paralléliser les scénarios */
  parallel?: boolean;
  /** Nombre max de workers */
  maxWorkers?: number;
  /** Stopper au premier échec (inverse de continueOnError) */
  failFast?: boolean;
}

export interface PerformanceConfig {
  /** Type de test */
  testType: 'load' | 'stress' | 'endurance' | 'spike';
  /** Nombre de connexions concurrentes */
  concurrentConnections: number;
  /** Durée du test en secondes */
  durationSeconds: number;
  /** Ramp-up en secondes */
  rampUpSeconds?: number;
  /** Actions à exécuter */
  actions?: PerformanceAction[];
  /** Seuils de performance */
  thresholds?: PerformanceThreshold[];
}

export interface PerformanceAction {
  type: 'boot' | 'heartbeat' | 'authorize' | 'startTransaction' | 'meterValues' | 'stopTransaction';
  weight?: number;
  config?: Record<string, unknown>;
}

export interface PerformanceThreshold {
  metric: string;
  operator: 'lt' | 'lte' | 'gt' | 'gte' | 'eq';
  value: number;
  failOnBreach?: boolean;
}

export interface CustomConfig {
  /** Script ou commande personnalisée */
  script?: string;
  command?: string;
  args?: string[];
  workingDir?: string;
  /** Callback API */
  callbackUrl?: string;
  /** Données personnalisées */
  data?: Record<string, unknown>;
}

// =============================================================================
// Scheduled Task
// =============================================================================

export interface ScheduledTask {
  id: string;
  name: string;
  description?: string;
  type: TaskType;
  status: TaskStatus;
  priority: TaskPriority;

  /** Planification */
  schedule: TaskSchedule;

  /** Options d'exécution */
  executionOptions: ExecutionOptions;

  /** Dépendances */
  dependencies?: TaskDependency[];

  /** Notifications */
  notifications?: NotificationConfig;

  /** Configuration spécifique au type */
  config: SessionConfig | TnrConfig | PerformanceConfig | CustomConfig;

  /** Métadonnées */
  createdAt: Date;
  updatedAt: Date;
  createdBy?: string;

  /** Dernière exécution */
  lastExecution?: ExecutionSummary;

  /** Prochaine exécution */
  nextExecution?: Date;

  /** Statistiques */
  stats?: TaskStats;

  /** Actif/Inactif */
  enabled: boolean;
}

// =============================================================================
// Execution
// =============================================================================

export interface ExecutionSummary {
  id: string;
  startedAt: Date;
  completedAt?: Date;
  durationMs?: number;
  status: TaskStatus;
  result?: ExecutionResult;
  error?: string;
}

export interface ExecutionResult {
  success: boolean;
  message?: string;
  data?: Record<string, unknown>;
  metrics?: Record<string, number>;
}

export interface ExecutionLog {
  id: string;
  taskId: string;
  executionId: string;
  timestamp: Date;
  level: 'debug' | 'info' | 'warn' | 'error';
  message: string;
  data?: Record<string, unknown>;
  source?: string;
}

export interface ExecutionHistory {
  taskId: string;
  executions: ExecutionRecord[];
  totalExecutions: number;
  successCount: number;
  failureCount: number;
  averageDurationMs: number;
}

export interface ExecutionRecord {
  id: string;
  startedAt: Date;
  completedAt?: Date;
  durationMs?: number;
  status: TaskStatus;
  retryCount: number;
  result?: ExecutionResult;
  error?: string;
  logs?: ExecutionLog[];
  triggeredBy?: 'schedule' | 'manual' | 'dependency' | 'api';
}

// =============================================================================
// Task Stats
// =============================================================================

export interface TaskStats {
  totalExecutions: number;
  successCount: number;
  failureCount: number;
  averageDurationMs: number;
  minDurationMs: number;
  maxDurationMs: number;
  lastSuccessAt?: Date;
  lastFailureAt?: Date;
  successRate: number;
  /** Trend: amélioration ou dégradation */
  trend?: 'improving' | 'stable' | 'degrading';
}

// =============================================================================
// Dashboard Stats
// =============================================================================

export interface DashboardStats {
  /** Compteurs globaux */
  totalTasks: number;
  activeTasks: number;
  runningTasks: number;
  pendingTasks: number;
  failedTasks: number;

  /** Exécutions */
  todayExecutions: number;
  todaySuccessRate: number;
  weekExecutions: number;
  weekSuccessRate: number;

  /** Par type */
  tasksByType: Record<TaskType, number>;

  /** Par statut */
  tasksByStatus: Record<TaskStatus, number>;

  /** Prochaines exécutions */
  upcomingExecutions: UpcomingExecution[];

  /** Dernières exécutions */
  recentExecutions: RecentExecution[];

  /** Alertes */
  alerts: SchedulerAlert[];
}

export interface UpcomingExecution {
  taskId: string;
  taskName: string;
  taskType: TaskType;
  scheduledAt: Date;
  priority: TaskPriority;
}

export interface RecentExecution {
  taskId: string;
  taskName: string;
  taskType: TaskType;
  executedAt: Date;
  durationMs: number;
  status: TaskStatus;
  error?: string;
}

export interface SchedulerAlert {
  id: string;
  type: 'warning' | 'error' | 'info';
  message: string;
  taskId?: string;
  createdAt: Date;
  acknowledged?: boolean;
}

// =============================================================================
// Calendar View
// =============================================================================

export interface CalendarEvent {
  id: string;
  taskId: string;
  title: string;
  type: TaskType;
  priority: TaskPriority;
  start: Date;
  end?: Date;
  status?: TaskStatus;
  recurring?: boolean;
  color?: string;
}

export interface CalendarDay {
  date: Date;
  events: CalendarEvent[];
  isToday: boolean;
  isCurrentMonth: boolean;
}

// =============================================================================
// Timeline View
// =============================================================================

export interface TimelineEntry {
  id: string;
  taskId: string;
  taskName: string;
  type: TaskType;
  status: TaskStatus;
  startTime: Date;
  endTime?: Date;
  durationMs?: number;
  progress?: number; // 0-100
  dependencies?: string[];
}

// =============================================================================
// Cron Builder
// =============================================================================

export interface CronPart {
  value: string;
  label: string;
  options: CronOption[];
}

export interface CronOption {
  value: string;
  label: string;
}

export interface CronPreset {
  label: string;
  expression: string;
  description: string;
}

// =============================================================================
// Scheduler State (pour le store)
// =============================================================================

export interface SchedulerState {
  /** Tâches planifiées */
  tasks: ScheduledTask[];

  /** Tâche sélectionnée */
  selectedTaskId: string | null;

  /** Vue courante */
  view: 'dashboard' | 'list' | 'calendar' | 'timeline' | 'history';

  /** Filtres */
  filters: SchedulerFilters;

  /** Stats du dashboard */
  dashboardStats: DashboardStats | null;

  /** Logs d'exécution (en direct) */
  liveLogs: ExecutionLog[];

  /** Historique d'exécution */
  executionHistory: Record<string, ExecutionHistory>;

  /** État de chargement */
  loading: boolean;

  /** Erreur */
  error: string | null;

  /** WebSocket connecté */
  wsConnected: boolean;

  /** Modal ouverte */
  modalOpen: 'create' | 'edit' | 'delete' | 'logs' | null;

  /** Tâche en cours d'édition */
  editingTask: Partial<ScheduledTask> | null;
}

export interface SchedulerFilters {
  status?: TaskStatus[];
  type?: TaskType[];
  priority?: TaskPriority[];
  tags?: string[];
  search?: string;
  dateRange?: {
    start: Date;
    end: Date;
  };
  enabled?: boolean;
}

// =============================================================================
// Actions
// =============================================================================

export interface SchedulerActions {
  // CRUD
  createTask: (task: Omit<ScheduledTask, 'id' | 'createdAt' | 'updatedAt' | 'stats'>) => Promise<ScheduledTask>;
  updateTask: (id: string, updates: Partial<ScheduledTask>) => Promise<ScheduledTask>;
  deleteTask: (id: string) => Promise<void>;
  duplicateTask: (id: string) => Promise<ScheduledTask>;

  // Contrôle
  startTask: (id: string) => Promise<void>;
  stopTask: (id: string) => Promise<void>;
  pauseTask: (id: string) => Promise<void>;
  resumeTask: (id: string) => Promise<void>;
  retryTask: (id: string) => Promise<void>;

  // Sélection
  selectTask: (id: string | null) => void;
  setView: (view: SchedulerState['view']) => void;

  // Filtres
  setFilters: (filters: Partial<SchedulerFilters>) => void;
  clearFilters: () => void;

  // Modal
  openModal: (modal: SchedulerState['modalOpen'], task?: Partial<ScheduledTask>) => void;
  closeModal: () => void;

  // Data fetching
  fetchTasks: () => Promise<void>;
  fetchDashboardStats: () => Promise<void>;
  fetchExecutionHistory: (taskId: string) => Promise<void>;
  fetchLogs: (executionId: string) => Promise<ExecutionLog[]>;

  // WebSocket
  connectWs: () => void;
  disconnectWs: () => void;

  // Mise à jour en temps réel
  addLiveLog: (log: ExecutionLog) => void;
  updateTaskStatus: (taskId: string, status: TaskStatus) => void;
  updateExecutionProgress: (taskId: string, progress: number) => void;
}

// =============================================================================
// API Types
// =============================================================================

export interface CreateTaskRequest {
  name: string;
  description?: string;
  type: TaskType;
  priority: TaskPriority;
  schedule: TaskSchedule;
  executionOptions?: ExecutionOptions;
  dependencies?: TaskDependency[];
  notifications?: NotificationConfig;
  config: SessionConfig | TnrConfig | PerformanceConfig | CustomConfig;
  enabled?: boolean;
}

export interface UpdateTaskRequest {
  name?: string;
  description?: string;
  priority?: TaskPriority;
  schedule?: TaskSchedule;
  executionOptions?: ExecutionOptions;
  dependencies?: TaskDependency[];
  notifications?: NotificationConfig;
  config?: Partial<SessionConfig | TnrConfig | PerformanceConfig | CustomConfig>;
  enabled?: boolean;
}

export interface TaskListResponse {
  tasks: ScheduledTask[];
  total: number;
  page: number;
  pageSize: number;
}

export interface ExecutionLogsResponse {
  logs: ExecutionLog[];
  total: number;
  hasMore: boolean;
}

// =============================================================================
// Utility Types
// =============================================================================

export type TaskConfigByType = {
  [TaskType.SESSION]: SessionConfig;
  [TaskType.TNR]: TnrConfig;
  [TaskType.PERFORMANCE]: PerformanceConfig;
  [TaskType.CUSTOM]: CustomConfig;
};

export type TaskWithConfig<T extends TaskType> = Omit<ScheduledTask, 'config'> & {
  type: T;
  config: TaskConfigByType[T];
};

/** Helper pour créer une tâche typée */
export function isSessionTask(task: ScheduledTask): task is TaskWithConfig<TaskType.SESSION> {
  return task.type === TaskType.SESSION;
}

export function isTnrTask(task: ScheduledTask): task is TaskWithConfig<TaskType.TNR> {
  return task.type === TaskType.TNR;
}

export function isPerformanceTask(task: ScheduledTask): task is TaskWithConfig<TaskType.PERFORMANCE> {
  return task.type === TaskType.PERFORMANCE;
}

export function isCustomTask(task: ScheduledTask): task is TaskWithConfig<TaskType.CUSTOM> {
  return task.type === TaskType.CUSTOM;
}

// =============================================================================
// Constants
// =============================================================================

export const CRON_PRESETS: CronPreset[] = [
  { label: 'Toutes les minutes', expression: '* * * * *', description: 'Exécute chaque minute' },
  { label: 'Toutes les 5 minutes', expression: '*/5 * * * *', description: 'Exécute toutes les 5 minutes' },
  { label: 'Toutes les 15 minutes', expression: '*/15 * * * *', description: 'Exécute toutes les 15 minutes' },
  { label: 'Toutes les heures', expression: '0 * * * *', description: 'Exécute au début de chaque heure' },
  { label: 'Tous les jours à minuit', expression: '0 0 * * *', description: 'Exécute à 00:00 chaque jour' },
  { label: 'Tous les jours à 8h', expression: '0 8 * * *', description: 'Exécute à 08:00 chaque jour' },
  { label: 'Lundi à 9h', expression: '0 9 * * 1', description: 'Exécute chaque lundi à 09:00' },
  { label: 'Jours ouvrés à 9h', expression: '0 9 * * 1-5', description: 'Exécute du lundi au vendredi à 09:00' },
  { label: 'Premier du mois', expression: '0 0 1 * *', description: 'Exécute le 1er de chaque mois à minuit' },
  { label: 'Tous les dimanches', expression: '0 0 * * 0', description: 'Exécute chaque dimanche à minuit' },
];

export const PRIORITY_COLORS: Record<TaskPriority, string> = {
  [TaskPriority.LOW]: '#6b7280',
  [TaskPriority.NORMAL]: '#3b82f6',
  [TaskPriority.HIGH]: '#f59e0b',
  [TaskPriority.CRITICAL]: '#ef4444',
};

export const STATUS_COLORS: Record<TaskStatus, string> = {
  [TaskStatus.PENDING]: '#9ca3af',
  [TaskStatus.SCHEDULED]: '#3b82f6',
  [TaskStatus.RUNNING]: '#8b5cf6',
  [TaskStatus.COMPLETED]: '#10b981',
  [TaskStatus.FAILED]: '#ef4444',
  [TaskStatus.CANCELLED]: '#6b7280',
  [TaskStatus.PAUSED]: '#f59e0b',
};

export const TYPE_ICONS: Record<TaskType, string> = {
  [TaskType.SESSION]: 'Zap',
  [TaskType.TNR]: 'TestTube',
  [TaskType.PERFORMANCE]: 'Activity',
  [TaskType.CUSTOM]: 'Code',
};

export const DEFAULT_EXECUTION_OPTIONS: ExecutionOptions = {
  timeout: 300000, // 5 minutes
  maxRetries: 3,
  retryDelay: 5000,
  exponentialBackoff: true,
  allowParallel: false,
  environment: 'dev',
};
