/**
 * Scheduler Dashboard Component
 *
 * Vue d'ensemble du scheduler avec statistiques,
 * tâches à venir, exécutions récentes et alertes.
 */

import React, { useEffect, useMemo } from 'react';
import {
  Activity,
  Calendar,
  CheckCircle2,
  Clock,
  AlertTriangle,
  XCircle,
  Zap,
  TestTube,
  Code,
  TrendingUp,
  TrendingDown,
  Minus,
  Play,
  Pause,
  RefreshCw,
} from 'lucide-react';
import { useSchedulerStore } from '../../store/schedulerStore';
import { useShallow } from 'zustand/react/shallow';
import {
  TaskType,
  TaskStatus,
  TaskPriority,
  STATUS_COLORS,
  PRIORITY_COLORS,
  TYPE_ICONS,
  ScheduledTask,
  DashboardStats,
} from '../../types/scheduler.types';
import { formatDistanceToNow, format, fr } from '../../utils/dateUtils';

// =============================================================================
// Types
// =============================================================================

interface StatCardProps {
  title: string;
  value: number | string;
  icon: React.ReactNode;
  trend?: 'up' | 'down' | 'stable';
  trendValue?: string;
  color?: string;
  onClick?: () => void;
}

interface TaskItemProps {
  task: ScheduledTask;
  showTime?: boolean;
  onAction?: (action: string, task: ScheduledTask) => void;
}

// =============================================================================
// Sub-components
// =============================================================================

const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  icon,
  trend,
  trendValue,
  color = '#3b82f6',
  onClick,
}) => (
  <div
    className={`bg-white dark:bg-gray-800 rounded-lg shadow p-4 border-l-4 ${onClick ? 'cursor-pointer hover:shadow-md transition-shadow' : ''}`}
    style={{ borderLeftColor: color }}
    onClick={onClick}
  >
    <div className="flex items-center justify-between">
      <div>
        <p className="text-sm text-gray-500 dark:text-gray-400">{title}</p>
        <p className="text-2xl font-bold text-gray-900 dark:text-white">{value}</p>
        {trend && trendValue && (
          <div className="flex items-center mt-1 text-sm">
            {trend === 'up' && <TrendingUp className="w-4 h-4 text-green-500 mr-1" />}
            {trend === 'down' && <TrendingDown className="w-4 h-4 text-red-500 mr-1" />}
            {trend === 'stable' && <Minus className="w-4 h-4 text-gray-500 mr-1" />}
            <span className={trend === 'up' ? 'text-green-500' : trend === 'down' ? 'text-red-500' : 'text-gray-500'}>
              {trendValue}
            </span>
          </div>
        )}
      </div>
      <div className="p-3 rounded-full bg-opacity-10" style={{ backgroundColor: `${color}20` }}>
        {icon}
      </div>
    </div>
  </div>
);

const TaskItem: React.FC<TaskItemProps> = ({ task, showTime = true, onAction }) => {
  const TypeIcon = getTaskTypeIcon(task.type);

  return (
    <div className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 transition-colors">
      <div className="flex items-center space-x-3">
        <div
          className="p-2 rounded-full"
          style={{ backgroundColor: `${STATUS_COLORS[task.status]}20` }}
        >
          <TypeIcon className="w-4 h-4" style={{ color: STATUS_COLORS[task.status] }} />
        </div>
        <div>
          <p className="font-medium text-gray-900 dark:text-white">{task.name}</p>
          <div className="flex items-center space-x-2 text-sm text-gray-500 dark:text-gray-400">
            <span
              className="px-2 py-0.5 rounded text-xs"
              style={{ backgroundColor: `${PRIORITY_COLORS[task.priority]}20`, color: PRIORITY_COLORS[task.priority] }}
            >
              {task.priority}
            </span>
            {showTime && task.nextExecution && (
              <span className="flex items-center">
                <Clock className="w-3 h-3 mr-1" />
                {formatDistanceToNow(new Date(task.nextExecution), { addSuffix: true, locale: fr })}
              </span>
            )}
          </div>
        </div>
      </div>
      {onAction && (
        <div className="flex space-x-1">
          {task.status === TaskStatus.SCHEDULED && (
            <button
              onClick={() => onAction('start', task)}
              className="p-1.5 text-green-600 hover:bg-green-100 rounded"
              title="Démarrer"
            >
              <Play className="w-4 h-4" />
            </button>
          )}
          {task.status === TaskStatus.RUNNING && (
            <button
              onClick={() => onAction('pause', task)}
              className="p-1.5 text-yellow-600 hover:bg-yellow-100 rounded"
              title="Pause"
            >
              <Pause className="w-4 h-4" />
            </button>
          )}
          {task.status === TaskStatus.FAILED && (
            <button
              onClick={() => onAction('retry', task)}
              className="p-1.5 text-blue-600 hover:bg-blue-100 rounded"
              title="Réessayer"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          )}
        </div>
      )}
    </div>
  );
};

const AlertItem: React.FC<{ alert: DashboardStats['alerts'][0] }> = ({ alert }) => {
  const iconMap = {
    warning: <AlertTriangle className="w-5 h-5 text-yellow-500" />,
    error: <XCircle className="w-5 h-5 text-red-500" />,
    info: <Activity className="w-5 h-5 text-blue-500" />,
  };

  return (
    <div className="flex items-start space-x-3 p-3 bg-gray-50 dark:bg-gray-700 rounded-lg">
      {iconMap[alert.type]}
      <div className="flex-1">
        <p className="text-sm text-gray-900 dark:text-white">{alert.message}</p>
        <p className="text-xs text-gray-500 dark:text-gray-400">
          {formatDistanceToNow(new Date(alert.createdAt), { addSuffix: true, locale: fr })}
        </p>
      </div>
    </div>
  );
};

// =============================================================================
// Helpers
// =============================================================================

function getTaskTypeIcon(type: TaskType): React.FC<{ className?: string; style?: React.CSSProperties }> {
  const icons = {
    [TaskType.SESSION]: Zap,
    [TaskType.TNR]: TestTube,
    [TaskType.PERFORMANCE]: Activity,
    [TaskType.CUSTOM]: Code,
  };
  return icons[type] || Activity;
}

// =============================================================================
// Main Component
// =============================================================================

export const SchedulerDashboard: React.FC = () => {
  const {
    tasks,
    dashboardStats,
    fetchDashboardStats,
    fetchTasks,
    startTask,
    pauseTask,
    retryTask,
    setView,
    setFilters,
    loading,
  } = useSchedulerStore(
    useShallow((state) => ({
      tasks: state.tasks,
      dashboardStats: state.dashboardStats,
      fetchDashboardStats: state.fetchDashboardStats,
      fetchTasks: state.fetchTasks,
      startTask: state.startTask,
      pauseTask: state.pauseTask,
      retryTask: state.retryTask,
      setView: state.setView,
      setFilters: state.setFilters,
      loading: state.loading,
    }))
  );

  // Derive filtered task lists using useMemo to avoid infinite loops
  const upcomingTasks = useMemo(() => {
    return tasks
      .filter((t) => t.nextExecution && t.enabled && t.status === TaskStatus.SCHEDULED)
      .sort((a, b) => {
        const aTime = new Date(a.nextExecution!).getTime();
        const bTime = new Date(b.nextExecution!).getTime();
        return aTime - bTime;
      })
      .slice(0, 5);
  }, [tasks]);

  const runningTasks = useMemo(() => {
    return tasks.filter((t) => t.status === TaskStatus.RUNNING);
  }, [tasks]);

  const failedTasks = useMemo(() => {
    return tasks.filter((t) => t.status === TaskStatus.FAILED);
  }, [tasks]);

  useEffect(() => {
    fetchTasks();
    fetchDashboardStats();

    // Refresh stats every 30 seconds
    const interval = setInterval(() => {
      fetchDashboardStats();
    }, 30000);

    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleTaskAction = async (action: string, task: ScheduledTask) => {
    switch (action) {
      case 'start':
        await startTask(task.id);
        break;
      case 'pause':
        await pauseTask(task.id);
        break;
      case 'retry':
        await retryTask(task.id);
        break;
    }
    fetchDashboardStats();
  };

  const handleViewTasks = (status?: TaskStatus) => {
    if (status) {
      setFilters({ status: [status] });
    }
    setView('list');
  };

  const stats = dashboardStats;

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Dashboard</h2>
        <button
          onClick={() => fetchDashboardStats()}
          className="flex items-center space-x-2 px-4 py-2 text-sm bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700"
          disabled={loading}
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          <span>Actualiser</span>
        </button>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total des tâches"
          value={stats?.totalTasks || 0}
          icon={<Calendar className="w-6 h-6" style={{ color: '#3b82f6' }} />}
          color="#3b82f6"
          onClick={() => handleViewTasks()}
        />
        <StatCard
          title="En cours"
          value={stats?.runningTasks || runningTasks.length}
          icon={<Activity className="w-6 h-6" style={{ color: '#8b5cf6' }} />}
          color="#8b5cf6"
          onClick={() => handleViewTasks(TaskStatus.RUNNING)}
        />
        <StatCard
          title="Aujourd'hui"
          value={stats?.todayExecutions || 0}
          icon={<CheckCircle2 className="w-6 h-6" style={{ color: '#10b981' }} />}
          trend={stats?.todaySuccessRate !== undefined ? (stats.todaySuccessRate >= 90 ? 'up' : stats.todaySuccessRate >= 70 ? 'stable' : 'down') : undefined}
          trendValue={stats?.todaySuccessRate !== undefined ? `${stats.todaySuccessRate.toFixed(0)}% succès` : undefined}
          color="#10b981"
        />
        <StatCard
          title="Échecs"
          value={stats?.failedTasks || failedTasks.length}
          icon={<XCircle className="w-6 h-6" style={{ color: '#ef4444' }} />}
          color="#ef4444"
          onClick={() => handleViewTasks(TaskStatus.FAILED)}
        />
      </div>

      {/* Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Upcoming Tasks */}
        <div className="lg:col-span-1 bg-white dark:bg-gray-800 rounded-lg shadow">
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-white flex items-center">
              <Clock className="w-5 h-5 mr-2 text-blue-500" />
              Prochaines exécutions
            </h3>
          </div>
          <div className="p-4 space-y-3">
            {upcomingTasks.length > 0 ? (
              upcomingTasks.map((task) => (
                <TaskItem
                  key={task.id}
                  task={task}
                  onAction={handleTaskAction}
                />
              ))
            ) : (
              <p className="text-center text-gray-500 dark:text-gray-400 py-4">
                Aucune tâche planifiée
              </p>
            )}
          </div>
        </div>

        {/* Running Tasks */}
        <div className="lg:col-span-1 bg-white dark:bg-gray-800 rounded-lg shadow">
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-white flex items-center">
              <Activity className="w-5 h-5 mr-2 text-purple-500" />
              En cours d'exécution
            </h3>
          </div>
          <div className="p-4 space-y-3">
            {runningTasks.length > 0 ? (
              runningTasks.map((task) => (
                <TaskItem
                  key={task.id}
                  task={task}
                  showTime={false}
                  onAction={handleTaskAction}
                />
              ))
            ) : (
              <p className="text-center text-gray-500 dark:text-gray-400 py-4">
                Aucune tâche en cours
              </p>
            )}
          </div>
        </div>

        {/* Failed Tasks */}
        <div className="lg:col-span-1 bg-white dark:bg-gray-800 rounded-lg shadow">
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-white flex items-center">
              <AlertTriangle className="w-5 h-5 mr-2 text-red-500" />
              Échecs récents
            </h3>
          </div>
          <div className="p-4 space-y-3">
            {failedTasks.length > 0 ? (
              failedTasks.slice(0, 5).map((task) => (
                <TaskItem
                  key={task.id}
                  task={task}
                  showTime={false}
                  onAction={handleTaskAction}
                />
              ))
            ) : (
              <p className="text-center text-gray-500 dark:text-gray-400 py-4">
                Aucun échec récent
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Alerts */}
      {stats?.alerts && stats.alerts.length > 0 && (
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow">
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <h3 className="font-semibold text-gray-900 dark:text-white flex items-center">
              <AlertTriangle className="w-5 h-5 mr-2 text-yellow-500" />
              Alertes
            </h3>
          </div>
          <div className="p-4 space-y-3">
            {stats.alerts.map((alert) => (
              <AlertItem key={alert.id} alert={alert} />
            ))}
          </div>
        </div>
      )}

      {/* Stats by Type */}
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow">
        <div className="p-4 border-b border-gray-200 dark:border-gray-700">
          <h3 className="font-semibold text-gray-900 dark:text-white">Répartition par type</h3>
        </div>
        <div className="p-4">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {Object.values(TaskType).map((type) => {
              const count = stats?.tasksByType?.[type] || 0;
              const TypeIcon = getTaskTypeIcon(type);
              const colors: Record<TaskType, string> = {
                [TaskType.SESSION]: '#3b82f6',
                [TaskType.TNR]: '#10b981',
                [TaskType.PERFORMANCE]: '#f59e0b',
                [TaskType.CUSTOM]: '#8b5cf6',
              };

              return (
                <div
                  key={type}
                  className="flex items-center space-x-3 p-3 bg-gray-50 dark:bg-gray-700 rounded-lg cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-600"
                  onClick={() => {
                    setFilters({ type: [type] });
                    setView('list');
                  }}
                >
                  <div
                    className="p-2 rounded-full"
                    style={{ backgroundColor: `${colors[type]}20` }}
                  >
                    <TypeIcon className="w-5 h-5" style={{ color: colors[type] }} />
                  </div>
                  <div>
                    <p className="font-semibold text-gray-900 dark:text-white">{count}</p>
                    <p className="text-sm text-gray-500 dark:text-gray-400 capitalize">{type}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};

export default SchedulerDashboard;
