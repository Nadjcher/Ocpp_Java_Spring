/**
 * Scheduler Store - Zustand
 *
 * Store centralisé pour la gestion des tâches planifiées
 * avec support temps réel via WebSocket.
 */

import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';
import {
  ScheduledTask,
  SchedulerState,
  SchedulerFilters,
  SchedulerActions,
  ExecutionLog,
  ExecutionHistory,
  DashboardStats,
  TaskStatus,
  TaskType,
  TaskPriority,
  CreateTaskRequest,
  UpdateTaskRequest,
  DEFAULT_EXECUTION_OPTIONS,
} from '../types/scheduler.types';
import { schedulerApi } from '../services/schedulerApi';

// =============================================================================
// Initial State
// =============================================================================

const initialFilters: SchedulerFilters = {
  status: undefined,
  type: undefined,
  priority: undefined,
  tags: undefined,
  search: undefined,
  dateRange: undefined,
  enabled: undefined,
};

const initialState: SchedulerState = {
  tasks: [],
  selectedTaskId: null,
  view: 'dashboard',
  filters: initialFilters,
  dashboardStats: null,
  liveLogs: [],
  executionHistory: {},
  loading: false,
  error: null,
  wsConnected: false,
  modalOpen: null,
  editingTask: null,
};

// =============================================================================
// Store Type
// =============================================================================

type SchedulerStore = SchedulerState & SchedulerActions;

// =============================================================================
// WebSocket Instance
// =============================================================================

let wsInstance: WebSocket | null = null;
let wsRetryCount = 0;
const WS_MAX_RETRIES = 3; // Stop retrying after 3 failures
let wsEnabled = false; // WebSocket is disabled by default until backend supports it

// =============================================================================
// Store
// =============================================================================

export const useSchedulerStore = create<SchedulerStore>()(
  devtools(
    persist(
      (set, get) => ({
        ...initialState,

        // =====================================================================
        // CRUD Operations
        // =====================================================================

        createTask: async (taskData) => {
          set({ loading: true, error: null });

          try {
            const request: CreateTaskRequest = {
              name: taskData.name,
              description: taskData.description,
              type: taskData.type,
              priority: taskData.priority,
              schedule: taskData.schedule,
              executionOptions: taskData.executionOptions || DEFAULT_EXECUTION_OPTIONS,
              dependencies: taskData.dependencies,
              notifications: taskData.notifications,
              config: taskData.config,
              enabled: taskData.enabled ?? true,
            };

            const newTask = await schedulerApi.createTask(request);

            set((state) => ({
              tasks: [...state.tasks, newTask],
              loading: false,
              modalOpen: null,
              editingTask: null,
            }));

            return newTask;
          } catch (error) {
            set({
              loading: false,
              error: error instanceof Error ? error.message : 'Failed to create task',
            });
            throw error;
          }
        },

        updateTask: async (id, updates) => {
          set({ loading: true, error: null });

          try {
            const request: UpdateTaskRequest = {
              name: updates.name,
              description: updates.description,
              priority: updates.priority,
              schedule: updates.schedule,
              executionOptions: updates.executionOptions,
              dependencies: updates.dependencies,
              notifications: updates.notifications,
              config: updates.config,
              enabled: updates.enabled,
            };

            const updatedTask = await schedulerApi.updateTask(id, request);

            set((state) => ({
              tasks: state.tasks.map((t) => (t.id === id ? updatedTask : t)),
              loading: false,
              modalOpen: null,
              editingTask: null,
            }));

            return updatedTask;
          } catch (error) {
            set({
              loading: false,
              error: error instanceof Error ? error.message : 'Failed to update task',
            });
            throw error;
          }
        },

        deleteTask: async (id) => {
          set({ loading: true, error: null });

          try {
            await schedulerApi.deleteTask(id);

            set((state) => ({
              tasks: state.tasks.filter((t) => t.id !== id),
              selectedTaskId: state.selectedTaskId === id ? null : state.selectedTaskId,
              loading: false,
              modalOpen: null,
            }));
          } catch (error) {
            set({
              loading: false,
              error: error instanceof Error ? error.message : 'Failed to delete task',
            });
            throw error;
          }
        },

        duplicateTask: async (id) => {
          const task = get().tasks.find((t) => t.id === id);
          if (!task) {
            throw new Error('Task not found');
          }

          const duplicatedTask = await get().createTask({
            ...task,
            name: `${task.name} (Copy)`,
            status: TaskStatus.PENDING,
            enabled: false,
          });

          return duplicatedTask;
        },

        // =====================================================================
        // Task Control
        // =====================================================================

        startTask: async (id) => {
          try {
            await schedulerApi.startTask(id);
            set((state) => ({
              tasks: state.tasks.map((t) =>
                t.id === id ? { ...t, status: TaskStatus.RUNNING } : t
              ),
            }));
          } catch (error) {
            set({ error: error instanceof Error ? error.message : 'Failed to start task' });
            throw error;
          }
        },

        stopTask: async (id) => {
          try {
            await schedulerApi.stopTask(id);
            set((state) => ({
              tasks: state.tasks.map((t) =>
                t.id === id ? { ...t, status: TaskStatus.CANCELLED } : t
              ),
            }));
          } catch (error) {
            set({ error: error instanceof Error ? error.message : 'Failed to stop task' });
            throw error;
          }
        },

        pauseTask: async (id) => {
          try {
            await schedulerApi.pauseTask(id);
            set((state) => ({
              tasks: state.tasks.map((t) =>
                t.id === id ? { ...t, status: TaskStatus.PAUSED } : t
              ),
            }));
          } catch (error) {
            set({ error: error instanceof Error ? error.message : 'Failed to pause task' });
            throw error;
          }
        },

        resumeTask: async (id) => {
          try {
            await schedulerApi.resumeTask(id);
            set((state) => ({
              tasks: state.tasks.map((t) =>
                t.id === id ? { ...t, status: TaskStatus.SCHEDULED } : t
              ),
            }));
          } catch (error) {
            set({ error: error instanceof Error ? error.message : 'Failed to resume task' });
            throw error;
          }
        },

        retryTask: async (id) => {
          try {
            await schedulerApi.retryTask(id);
            set((state) => ({
              tasks: state.tasks.map((t) =>
                t.id === id ? { ...t, status: TaskStatus.PENDING } : t
              ),
            }));
          } catch (error) {
            set({ error: error instanceof Error ? error.message : 'Failed to retry task' });
            throw error;
          }
        },

        // =====================================================================
        // Selection & View
        // =====================================================================

        selectTask: (id) => {
          set({ selectedTaskId: id });
        },

        setView: (view) => {
          set({ view });
        },

        // =====================================================================
        // Filters
        // =====================================================================

        setFilters: (filters) => {
          set((state) => ({
            filters: { ...state.filters, ...filters },
          }));
        },

        clearFilters: () => {
          set({ filters: initialFilters });
        },

        // =====================================================================
        // Modal
        // =====================================================================

        openModal: (modal, task) => {
          set({ modalOpen: modal, editingTask: task ?? null });
        },

        closeModal: () => {
          set({ modalOpen: null, editingTask: null });
        },

        // =====================================================================
        // Data Fetching
        // =====================================================================

        fetchTasks: async () => {
          set({ loading: true, error: null });

          try {
            const response = await schedulerApi.getTasks(get().filters);
            set({ tasks: response.tasks, loading: false });
          } catch (error) {
            set({
              loading: false,
              error: error instanceof Error ? error.message : 'Failed to fetch tasks',
            });
          }
        },

        fetchDashboardStats: async () => {
          try {
            const stats = await schedulerApi.getDashboardStats();
            set({ dashboardStats: stats });
          } catch (error) {
            console.error('Failed to fetch dashboard stats:', error);
          }
        },

        fetchExecutionHistory: async (taskId) => {
          try {
            const history = await schedulerApi.getExecutionHistory(taskId);
            set((state) => ({
              executionHistory: {
                ...state.executionHistory,
                [taskId]: history,
              },
            }));
          } catch (error) {
            console.error('Failed to fetch execution history:', error);
          }
        },

        fetchLogs: async (executionId) => {
          try {
            const response = await schedulerApi.getExecutionLogs(executionId);
            return response.logs;
          } catch (error) {
            console.error('Failed to fetch logs:', error);
            return [];
          }
        },

        // =====================================================================
        // WebSocket
        // =====================================================================

        connectWs: () => {
          // WebSocket is disabled until backend implements the endpoint
          if (!wsEnabled) {
            console.debug('[Scheduler WS] WebSocket disabled - backend endpoint not available');
            return;
          }

          if (wsInstance?.readyState === WebSocket.OPEN) {
            return;
          }

          if (wsRetryCount >= WS_MAX_RETRIES) {
            console.warn('[Scheduler WS] Max retries reached, giving up');
            return;
          }

          const wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/api/scheduler/ws`;

          wsInstance = new WebSocket(wsUrl);

          wsInstance.onopen = () => {
            wsRetryCount = 0; // Reset retry count on successful connection
            set({ wsConnected: true });
            console.log('[Scheduler WS] Connected');
          };

          wsInstance.onclose = () => {
            set({ wsConnected: false });
            console.log('[Scheduler WS] Disconnected');

            // Auto-reconnect after 5 seconds, with max retries
            if (wsRetryCount < WS_MAX_RETRIES) {
              setTimeout(() => {
                if (!get().wsConnected) {
                  get().connectWs();
                }
              }, 5000);
            }
          };

          wsInstance.onerror = () => {
            wsRetryCount++;
            console.warn(`[Scheduler WS] Connection failed (attempt ${wsRetryCount}/${WS_MAX_RETRIES})`);
          };

          wsInstance.onmessage = (event) => {
            try {
              const message = JSON.parse(event.data);
              handleWsMessage(message, set, get);
            } catch (error) {
              console.error('[Scheduler WS] Failed to parse message:', error);
            }
          };
        },

        disconnectWs: () => {
          if (wsInstance) {
            wsInstance.close();
            wsInstance = null;
          }
          wsRetryCount = 0; // Reset retry count when manually disconnected
          set({ wsConnected: false });
        },

        // =====================================================================
        // Real-time Updates
        // =====================================================================

        addLiveLog: (log) => {
          set((state) => {
            const newLogs = [log, ...state.liveLogs];
            // Keep only last 1000 logs
            return { liveLogs: newLogs.slice(0, 1000) };
          });
        },

        updateTaskStatus: (taskId, status) => {
          set((state) => ({
            tasks: state.tasks.map((t) =>
              t.id === taskId ? { ...t, status } : t
            ),
          }));
        },

        updateExecutionProgress: (taskId, progress) => {
          // Progress can be stored temporarily for UI updates
          // This is typically used for long-running tasks
          console.log(`[Scheduler] Task ${taskId} progress: ${progress}%`);
        },
      }),
      {
        name: 'scheduler-storage',
        partialize: (state) => ({
          // Only persist these fields
          view: state.view,
          filters: state.filters,
        }),
      }
    ),
    { name: 'SchedulerStore' }
  )
);

// =============================================================================
// WebSocket Message Handler
// =============================================================================

type WsMessage =
  | { type: 'TASK_STATUS_CHANGED'; taskId: string; status: TaskStatus }
  | { type: 'EXECUTION_STARTED'; taskId: string; executionId: string }
  | { type: 'EXECUTION_COMPLETED'; taskId: string; executionId: string; status: TaskStatus }
  | { type: 'EXECUTION_LOG'; log: ExecutionLog }
  | { type: 'EXECUTION_PROGRESS'; taskId: string; progress: number }
  | { type: 'TASK_CREATED'; task: ScheduledTask }
  | { type: 'TASK_UPDATED'; task: ScheduledTask }
  | { type: 'TASK_DELETED'; taskId: string }
  | { type: 'STATS_UPDATED'; stats: DashboardStats };

function handleWsMessage(
  message: WsMessage,
  set: (partial: Partial<SchedulerStore> | ((state: SchedulerStore) => Partial<SchedulerStore>)) => void,
  get: () => SchedulerStore
) {
  switch (message.type) {
    case 'TASK_STATUS_CHANGED':
      get().updateTaskStatus(message.taskId, message.status);
      break;

    case 'EXECUTION_STARTED':
      set((state) => ({
        tasks: state.tasks.map((t) =>
          t.id === message.taskId ? { ...t, status: TaskStatus.RUNNING } : t
        ),
      }));
      break;

    case 'EXECUTION_COMPLETED':
      set((state) => ({
        tasks: state.tasks.map((t) =>
          t.id === message.taskId ? { ...t, status: message.status } : t
        ),
      }));
      // Refresh stats
      get().fetchDashboardStats();
      break;

    case 'EXECUTION_LOG':
      get().addLiveLog(message.log);
      break;

    case 'EXECUTION_PROGRESS':
      get().updateExecutionProgress(message.taskId, message.progress);
      break;

    case 'TASK_CREATED':
      set((state) => ({
        tasks: [...state.tasks, message.task],
      }));
      break;

    case 'TASK_UPDATED':
      set((state) => ({
        tasks: state.tasks.map((t) =>
          t.id === message.task.id ? message.task : t
        ),
      }));
      break;

    case 'TASK_DELETED':
      set((state) => ({
        tasks: state.tasks.filter((t) => t.id !== message.taskId),
      }));
      break;

    case 'STATS_UPDATED':
      set({ dashboardStats: message.stats });
      break;
  }
}

// =============================================================================
// Selectors
// =============================================================================

export const selectFilteredTasks = (state: SchedulerStore): ScheduledTask[] => {
  let tasks = [...state.tasks];
  const { filters } = state;

  // Filter by status
  if (filters.status?.length) {
    tasks = tasks.filter((t) => filters.status!.includes(t.status));
  }

  // Filter by type
  if (filters.type?.length) {
    tasks = tasks.filter((t) => filters.type!.includes(t.type));
  }

  // Filter by priority
  if (filters.priority?.length) {
    tasks = tasks.filter((t) => filters.priority!.includes(t.priority));
  }

  // Filter by tags
  if (filters.tags?.length) {
    tasks = tasks.filter((t) =>
      t.executionOptions?.tags?.some((tag) => filters.tags!.includes(tag))
    );
  }

  // Filter by search
  if (filters.search) {
    const searchLower = filters.search.toLowerCase();
    tasks = tasks.filter(
      (t) =>
        t.name.toLowerCase().includes(searchLower) ||
        t.description?.toLowerCase().includes(searchLower)
    );
  }

  // Filter by enabled
  if (filters.enabled !== undefined) {
    tasks = tasks.filter((t) => t.enabled === filters.enabled);
  }

  // Filter by date range
  if (filters.dateRange) {
    tasks = tasks.filter((t) => {
      if (!t.nextExecution) return false;
      const execDate = new Date(t.nextExecution);
      return execDate >= filters.dateRange!.start && execDate <= filters.dateRange!.end;
    });
  }

  return tasks;
};

export const selectSelectedTask = (state: SchedulerStore): ScheduledTask | null => {
  if (!state.selectedTaskId) return null;
  return state.tasks.find((t) => t.id === state.selectedTaskId) || null;
};

export const selectTasksByStatus = (state: SchedulerStore): Record<TaskStatus, ScheduledTask[]> => {
  const result: Record<TaskStatus, ScheduledTask[]> = {
    [TaskStatus.PENDING]: [],
    [TaskStatus.SCHEDULED]: [],
    [TaskStatus.RUNNING]: [],
    [TaskStatus.COMPLETED]: [],
    [TaskStatus.FAILED]: [],
    [TaskStatus.CANCELLED]: [],
    [TaskStatus.PAUSED]: [],
  };

  state.tasks.forEach((task) => {
    result[task.status].push(task);
  });

  return result;
};

export const selectTasksByType = (state: SchedulerStore): Record<TaskType, ScheduledTask[]> => {
  const result: Record<TaskType, ScheduledTask[]> = {
    [TaskType.SESSION]: [],
    [TaskType.TNR]: [],
    [TaskType.PERFORMANCE]: [],
    [TaskType.CUSTOM]: [],
  };

  state.tasks.forEach((task) => {
    result[task.type].push(task);
  });

  return result;
};

export const selectUpcomingTasks = (state: SchedulerStore, limit = 5): ScheduledTask[] => {
  return state.tasks
    .filter((t) => t.nextExecution && t.enabled && t.status === TaskStatus.SCHEDULED)
    .sort((a, b) => {
      const aTime = new Date(a.nextExecution!).getTime();
      const bTime = new Date(b.nextExecution!).getTime();
      return aTime - bTime;
    })
    .slice(0, limit);
};

export const selectRunningTasks = (state: SchedulerStore): ScheduledTask[] => {
  return state.tasks.filter((t) => t.status === TaskStatus.RUNNING);
};

export const selectFailedTasks = (state: SchedulerStore): ScheduledTask[] => {
  return state.tasks.filter((t) => t.status === TaskStatus.FAILED);
};

export const selectTasksForCalendar = (
  state: SchedulerStore,
  startDate: Date,
  endDate: Date
): ScheduledTask[] => {
  return state.tasks.filter((t) => {
    if (!t.nextExecution) return false;
    const execDate = new Date(t.nextExecution);
    return execDate >= startDate && execDate <= endDate;
  });
};
