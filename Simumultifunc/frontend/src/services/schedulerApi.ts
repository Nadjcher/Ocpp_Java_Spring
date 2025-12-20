/**
 * Scheduler API Service
 *
 * Service de communication avec le backend pour la gestion
 * des tâches planifiées.
 *
 * Adapté pour fonctionner avec le backend Spring Boot qui retourne ApiResponse<T>
 */

import { apiClient } from '../lib/apiClient';
import {
  ScheduledTask,
  CreateTaskRequest,
  UpdateTaskRequest,
  TaskListResponse,
  DashboardStats,
  ExecutionHistory,
  ExecutionLogsResponse,
  SchedulerFilters,
  ExecutionLog,
} from '../types/scheduler.types';

const BASE_URL = '/api/scheduler';

// =============================================================================
// Types pour les réponses du backend simplifié
// =============================================================================

interface SimpleScheduledTask {
  id: string;
  name: string;
  description?: string;
  scheduleType: string;
  cronExpression?: string;
  intervalSeconds?: number;
  runAt?: string;
  timezone?: string;
  actionType: string;
  actionConfig: Record<string, unknown>;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
  lastRunAt?: string;
  nextRunAt?: string;
  lastRunStatus?: string;
  lastRunError?: string;
  runCount: number;
  failCount: number;
}

interface SimpleTaskExecution {
  id: string;
  taskId: string;
  taskName: string;
  startedAt: string;
  completedAt?: string;
  durationMs?: number;
  status: string;
  error?: string;
  output?: string;
  triggeredBy: string;
}

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

// =============================================================================
// Helper pour convertir les tâches du backend vers le format frontend
// =============================================================================

function convertToFrontendTask(task: SimpleScheduledTask): ScheduledTask {
  return {
    id: task.id,
    name: task.name,
    description: task.description,
    type: (task.actionType as any) || 'session',
    status: task.lastRunStatus === 'running' ? 'running' :
            task.lastRunStatus === 'failed' ? 'failed' :
            task.enabled ? 'scheduled' : 'paused',
    priority: 'normal',
    schedule: {
      type: task.scheduleType === 'cron' ? 'cron' :
            task.scheduleType === 'interval' ? 'recurring' : 'once',
      cronExpression: task.cronExpression,
      recurrence: task.intervalSeconds ? {
        pattern: 'minutely',
        interval: task.intervalSeconds,
      } : undefined,
      scheduledAt: task.runAt ? new Date(task.runAt) : undefined,
      timezone: task.timezone,
    },
    executionOptions: {
      timeout: 300000,
      maxRetries: 3,
      retryDelay: 5000,
    },
    config: task.actionConfig as any,
    createdAt: new Date(task.createdAt),
    updatedAt: new Date(task.updatedAt),
    lastExecution: task.lastRunAt ? {
      id: '',
      startedAt: new Date(task.lastRunAt),
      status: task.lastRunStatus as any,
      error: task.lastRunError,
    } : undefined,
    nextExecution: task.nextRunAt ? new Date(task.nextRunAt) : undefined,
    stats: {
      totalExecutions: task.runCount,
      successCount: task.runCount - task.failCount,
      failureCount: task.failCount,
      averageDurationMs: 0,
      minDurationMs: 0,
      maxDurationMs: 0,
      successRate: task.runCount > 0 ? (task.runCount - task.failCount) / task.runCount : 0,
    },
    enabled: task.enabled,
  } as ScheduledTask;
}

// =============================================================================
// API Client
// =============================================================================

export const schedulerApi = {
  // ===========================================================================
  // Tasks CRUD
  // ===========================================================================

  /**
   * Récupère la liste des tâches avec filtres optionnels
   */
  async getTasks(filters?: SchedulerFilters): Promise<TaskListResponse> {
    try {
      const response = await apiClient.get<ApiResponse<SimpleScheduledTask[]>>(`${BASE_URL}/tasks`);
      const tasks = (response.data.data || []).map(convertToFrontendTask);
      return {
        tasks,
        total: tasks.length,
        page: 1,
        pageSize: 100,
      };
    } catch (error) {
      console.error('Failed to fetch tasks:', error);
      return { tasks: [], total: 0, page: 1, pageSize: 100 };
    }
  },

  /**
   * Récupère une tâche par son ID
   */
  async getTask(id: string): Promise<ScheduledTask> {
    const response = await apiClient.get<ApiResponse<SimpleScheduledTask>>(`${BASE_URL}/tasks/${id}`);
    return convertToFrontendTask(response.data.data);
  },

  /**
   * Crée une nouvelle tâche
   * Convertit le format frontend vers le format backend
   */
  async createTask(request: CreateTaskRequest): Promise<ScheduledTask> {
    const backendTask = {
      name: request.name,
      description: request.description,
      scheduleType: request.schedule.type === 'recurring' ? 'interval' : request.schedule.type,
      cronExpression: request.schedule.cronExpression,
      intervalSeconds: request.schedule.recurrence?.interval,
      runAt: request.schedule.scheduledAt?.toISOString(),
      timezone: request.schedule.timezone,
      actionType: request.type,
      actionConfig: request.config,
      enabled: request.enabled ?? true,
    };
    const response = await apiClient.post<ApiResponse<SimpleScheduledTask>>(`${BASE_URL}/tasks`, backendTask);
    return convertToFrontendTask(response.data.data);
  },

  /**
   * Met à jour une tâche existante
   */
  async updateTask(id: string, request: UpdateTaskRequest): Promise<ScheduledTask> {
    const backendTask: Record<string, unknown> = {};
    if (request.name) backendTask.name = request.name;
    if (request.description) backendTask.description = request.description;
    if (request.schedule) {
      backendTask.scheduleType = request.schedule.type === 'recurring' ? 'interval' : request.schedule.type;
      backendTask.cronExpression = request.schedule.cronExpression;
      backendTask.intervalSeconds = request.schedule.recurrence?.interval;
      backendTask.runAt = request.schedule.scheduledAt?.toISOString();
      backendTask.timezone = request.schedule.timezone;
    }
    if (request.config) backendTask.actionConfig = request.config;
    if (request.enabled !== undefined) backendTask.enabled = request.enabled;

    const response = await apiClient.put<ApiResponse<SimpleScheduledTask>>(`${BASE_URL}/tasks/${id}`, backendTask);
    return convertToFrontendTask(response.data.data);
  },

  /**
   * Supprime une tâche
   */
  async deleteTask(id: string): Promise<void> {
    await apiClient.delete(`${BASE_URL}/tasks/${id}`);
  },

  /**
   * Duplique une tâche (non supporté directement, on récupère et recrée)
   */
  async duplicateTask(id: string): Promise<ScheduledTask> {
    const task = await this.getTask(id);
    const newTask = await this.createTask({
      name: `${task.name} (Copy)`,
      description: task.description,
      type: task.type,
      priority: task.priority,
      schedule: task.schedule,
      config: task.config,
      enabled: false,
    });
    return newTask;
  },

  // ===========================================================================
  // Task Control
  // ===========================================================================

  /**
   * Démarre l'exécution d'une tâche (exécution manuelle)
   */
  async startTask(id: string): Promise<void> {
    await apiClient.post(`${BASE_URL}/tasks/${id}/run`);
  },

  /**
   * Arrête l'exécution d'une tâche (désactive)
   */
  async stopTask(id: string): Promise<void> {
    await apiClient.post(`${BASE_URL}/tasks/${id}/disable`);
  },

  /**
   * Met en pause une tâche planifiée (désactive)
   */
  async pauseTask(id: string): Promise<void> {
    await apiClient.post(`${BASE_URL}/tasks/${id}/disable`);
  },

  /**
   * Reprend une tâche en pause (active)
   */
  async resumeTask(id: string): Promise<void> {
    await apiClient.post(`${BASE_URL}/tasks/${id}/enable`);
  },

  /**
   * Relance une tâche échouée (exécution manuelle)
   */
  async retryTask(id: string): Promise<void> {
    await apiClient.post(`${BASE_URL}/tasks/${id}/run`);
  },

  /**
   * Active/Désactive une tâche
   */
  async toggleTask(id: string, enabled: boolean): Promise<ScheduledTask> {
    const endpoint = enabled ? 'enable' : 'disable';
    await apiClient.post(`${BASE_URL}/tasks/${id}/${endpoint}`);
    return this.getTask(id);
  },

  // ===========================================================================
  // Dashboard & Stats
  // ===========================================================================

  /**
   * Récupère les statistiques du dashboard
   */
  async getDashboardStats(): Promise<DashboardStats> {
    try {
      const response = await apiClient.get<{
        totalTasks: number;
        enabledTasks: number;
        runningExecutions: number;
        recentExecutions: SimpleTaskExecution[];
      }>(`${BASE_URL}/status`);

      const data = response.data;
      return {
        totalTasks: data.totalTasks,
        activeTasks: data.enabledTasks,
        runningTasks: data.runningExecutions,
        pendingTasks: 0,
        failedTasks: 0,
        todayExecutions: data.recentExecutions?.length || 0,
        todaySuccessRate: 0,
        weekExecutions: 0,
        weekSuccessRate: 0,
        tasksByType: {} as any,
        tasksByStatus: {} as any,
        upcomingExecutions: [],
        recentExecutions: (data.recentExecutions || []).map(e => ({
          taskId: e.taskId,
          taskName: e.taskName,
          taskType: 'session' as any,
          executedAt: new Date(e.startedAt),
          durationMs: e.durationMs || 0,
          status: e.status as any,
          error: e.error,
        })),
        alerts: [],
      };
    } catch (error) {
      console.error('Failed to fetch dashboard stats:', error);
      return {
        totalTasks: 0,
        activeTasks: 0,
        runningTasks: 0,
        pendingTasks: 0,
        failedTasks: 0,
        todayExecutions: 0,
        todaySuccessRate: 0,
        weekExecutions: 0,
        weekSuccessRate: 0,
        tasksByType: {} as any,
        tasksByStatus: {} as any,
        upcomingExecutions: [],
        recentExecutions: [],
        alerts: [],
      };
    }
  },

  /**
   * Récupère les statistiques d'une tâche
   */
  async getTaskStats(id: string): Promise<ScheduledTask['stats']> {
    const task = await this.getTask(id);
    return task.stats;
  },

  // ===========================================================================
  // Execution History & Logs
  // ===========================================================================

  /**
   * Récupère l'historique d'exécution d'une tâche
   */
  async getExecutionHistory(taskId: string, page = 1, pageSize = 20): Promise<ExecutionHistory> {
    try {
      const response = await apiClient.get<ApiResponse<SimpleTaskExecution[]>>(
        `${BASE_URL}/executions?taskId=${taskId}&limit=${pageSize}`
      );

      const executions = response.data.data || [];
      const successCount = executions.filter(e => e.status === 'success').length;
      const totalDuration = executions.reduce((sum, e) => sum + (e.durationMs || 0), 0);

      return {
        taskId,
        executions: executions.map(e => ({
          id: e.id,
          startedAt: new Date(e.startedAt),
          completedAt: e.completedAt ? new Date(e.completedAt) : undefined,
          durationMs: e.durationMs,
          status: e.status as any,
          retryCount: 0,
          error: e.error,
          triggeredBy: e.triggeredBy as any,
        })),
        totalExecutions: executions.length,
        successCount,
        failureCount: executions.length - successCount,
        averageDurationMs: executions.length > 0 ? totalDuration / executions.length : 0,
      };
    } catch (error) {
      console.error('Failed to fetch execution history:', error);
      return {
        taskId,
        executions: [],
        totalExecutions: 0,
        successCount: 0,
        failureCount: 0,
        averageDurationMs: 0,
      };
    }
  },

  /**
   * Récupère les logs d'une exécution (non implémenté côté backend)
   */
  async getExecutionLogs(
    executionId: string,
    limit = 100,
    offset = 0
  ): Promise<ExecutionLogsResponse> {
    return { logs: [], total: 0, hasMore: false };
  },

  /**
   * Récupère les logs en temps réel (non implémenté côté backend)
   */
  async getLiveLogs(taskId: string, since?: Date): Promise<ExecutionLog[]> {
    return [];
  },

  // ===========================================================================
  // Validation
  // ===========================================================================

  /**
   * Valide une expression cron
   */
  async validateCron(expression: string): Promise<{ valid: boolean; description?: string; nextRuns?: Date[]; error?: string }> {
    const response = await apiClient.post<{ valid: boolean; description?: string; nextRuns?: Date[]; error?: string }>(
      `${BASE_URL}/cron/validate`,
      { expression }
    );
    return response.data;
  },

  /**
   * Génère les prochaines exécutions d'une expression cron
   */
  async getNextCronRuns(expression: string, count = 5): Promise<Date[]> {
    const response = await apiClient.post<Date[]>(
      `${BASE_URL}/cron/next-runs`,
      { expression, count }
    );
    return response.data;
  },

  // ===========================================================================
  // Import/Export
  // ===========================================================================

  /**
   * Exporte les tâches en JSON
   */
  async exportTasks(taskIds?: string[]): Promise<Blob> {
    const response = await apiClient.post<Blob>(
      `${BASE_URL}/tasks/export`,
      { taskIds },
      { responseType: 'blob' }
    );
    return response.data;
  },

  /**
   * Importe des tâches depuis un fichier JSON
   */
  async importTasks(file: File): Promise<{ imported: number; errors: string[] }> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await apiClient.post<{ imported: number; errors: string[] }>(
      `${BASE_URL}/tasks/import`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return response.data;
  },

  // ===========================================================================
  // Bulk Operations
  // ===========================================================================

  /**
   * Supprime plusieurs tâches
   */
  async bulkDelete(taskIds: string[]): Promise<{ deleted: number; errors: string[] }> {
    const response = await apiClient.post<{ deleted: number; errors: string[] }>(
      `${BASE_URL}/tasks/bulk/delete`,
      { taskIds }
    );
    return response.data;
  },

  /**
   * Active/Désactive plusieurs tâches
   */
  async bulkToggle(taskIds: string[], enabled: boolean): Promise<{ updated: number }> {
    const response = await apiClient.post<{ updated: number }>(
      `${BASE_URL}/tasks/bulk/toggle`,
      { taskIds, enabled }
    );
    return response.data;
  },

  /**
   * Démarre plusieurs tâches
   */
  async bulkStart(taskIds: string[]): Promise<{ started: number; errors: string[] }> {
    const response = await apiClient.post<{ started: number; errors: string[] }>(
      `${BASE_URL}/tasks/bulk/start`,
      { taskIds }
    );
    return response.data;
  },
};

export default schedulerApi;
