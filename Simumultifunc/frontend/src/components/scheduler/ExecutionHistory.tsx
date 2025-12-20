/**
 * Execution History Component
 *
 * Affiche l'historique des exécutions d'une tâche avec graphiques et détails.
 */

import React, { useEffect, useMemo, useState } from 'react';
import {
  History,
  CheckCircle2,
  XCircle,
  Clock,
  TrendingUp,
  TrendingDown,
  Minus,
  Calendar,
  RefreshCw,
  ChevronDown,
  ChevronUp,
  ExternalLink,
  BarChart3,
} from 'lucide-react';
import { useSchedulerStore } from '../../store/schedulerStore';
import { useShallow } from 'zustand/react/shallow';
import {
  ExecutionRecord,
  ExecutionHistory as ExecutionHistoryType,
  TaskStatus,
  STATUS_COLORS,
} from '../../types/scheduler.types';
import { formatDistanceToNow, format, fr } from '../../utils/dateUtils';

// =============================================================================
// Types
// =============================================================================

interface ExecutionHistoryProps {
  taskId: string;
  onViewLogs?: (executionId: string) => void;
}

// =============================================================================
// Component
// =============================================================================

export const ExecutionHistory: React.FC<ExecutionHistoryProps> = ({
  taskId,
  onViewLogs,
}) => {
  const { executionHistory, fetchExecutionHistory, loading } = useSchedulerStore(
    useShallow((state) => ({
      executionHistory: state.executionHistory,
      fetchExecutionHistory: state.fetchExecutionHistory,
      loading: state.loading,
    }))
  );
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const history = executionHistory[taskId];

  useEffect(() => {
    fetchExecutionHistory(taskId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [taskId]);

  if (loading && !history) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw className="w-8 h-8 text-gray-400 animate-spin" />
      </div>
    );
  }

  if (!history || history.executions.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-64 text-gray-500 dark:text-gray-400">
        <History className="w-12 h-12 mb-4 opacity-50" />
        <p>Aucune exécution enregistrée</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Stats Overview */}
      <StatsOverview history={history} />

      {/* Duration Chart */}
      <DurationChart executions={history.executions} />

      {/* Executions List */}
      <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
        <div className="p-4 border-b border-gray-200 dark:border-gray-700">
          <h3 className="font-semibold text-gray-900 dark:text-white flex items-center">
            <History className="w-5 h-5 mr-2" />
            Historique des exécutions
          </h3>
        </div>
        <div className="divide-y divide-gray-200 dark:divide-gray-700">
          {history.executions.map((execution) => (
            <ExecutionRow
              key={execution.id}
              execution={execution}
              isExpanded={expandedId === execution.id}
              onToggle={() => setExpandedId(expandedId === execution.id ? null : execution.id)}
              onViewLogs={onViewLogs}
            />
          ))}
        </div>
      </div>
    </div>
  );
};

// =============================================================================
// Stats Overview
// =============================================================================

interface StatsOverviewProps {
  history: ExecutionHistoryType;
}

const StatsOverview: React.FC<StatsOverviewProps> = ({ history }) => {
  const successRate = history.totalExecutions > 0
    ? (history.successCount / history.totalExecutions * 100)
    : 0;

  const trend = useMemo(() => {
    if (history.executions.length < 5) return 'stable';
    const recent = history.executions.slice(0, 5);
    const older = history.executions.slice(5, 10);

    const recentSuccess = recent.filter((e) => e.status === TaskStatus.COMPLETED).length / recent.length;
    const olderSuccess = older.length > 0
      ? older.filter((e) => e.status === TaskStatus.COMPLETED).length / older.length
      : recentSuccess;

    if (recentSuccess > olderSuccess + 0.1) return 'improving';
    if (recentSuccess < olderSuccess - 0.1) return 'degrading';
    return 'stable';
  }, [history.executions]);

  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
      <StatCard
        label="Total exécutions"
        value={history.totalExecutions}
        icon={<History className="w-5 h-5 text-blue-500" />}
      />
      <StatCard
        label="Taux de succès"
        value={`${successRate.toFixed(1)}%`}
        icon={
          trend === 'improving' ? (
            <TrendingUp className="w-5 h-5 text-green-500" />
          ) : trend === 'degrading' ? (
            <TrendingDown className="w-5 h-5 text-red-500" />
          ) : (
            <Minus className="w-5 h-5 text-gray-500" />
          )
        }
        color={successRate >= 90 ? 'green' : successRate >= 70 ? 'yellow' : 'red'}
      />
      <StatCard
        label="Succès"
        value={history.successCount}
        icon={<CheckCircle2 className="w-5 h-5 text-green-500" />}
      />
      <StatCard
        label="Échecs"
        value={history.failureCount}
        icon={<XCircle className="w-5 h-5 text-red-500" />}
      />
    </div>
  );
};

interface StatCardProps {
  label: string;
  value: number | string;
  icon: React.ReactNode;
  color?: 'green' | 'yellow' | 'red';
}

const StatCard: React.FC<StatCardProps> = ({ label, value, icon, color }) => {
  const colorClasses = {
    green: 'text-green-600 dark:text-green-400',
    yellow: 'text-yellow-600 dark:text-yellow-400',
    red: 'text-red-600 dark:text-red-400',
  };

  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm text-gray-500 dark:text-gray-400">{label}</span>
        {icon}
      </div>
      <p className={`text-2xl font-bold ${color ? colorClasses[color] : 'text-gray-900 dark:text-white'}`}>
        {value}
      </p>
    </div>
  );
};

// =============================================================================
// Duration Chart
// =============================================================================

interface DurationChartProps {
  executions: ExecutionRecord[];
}

const DurationChart: React.FC<DurationChartProps> = ({ executions }) => {
  const chartData = useMemo(() => {
    // Get last 20 executions with duration
    return executions
      .filter((e) => e.durationMs !== undefined)
      .slice(0, 20)
      .reverse();
  }, [executions]);

  if (chartData.length < 2) return null;

  const maxDuration = Math.max(...chartData.map((e) => e.durationMs || 0));
  const avgDuration = chartData.reduce((sum, e) => sum + (e.durationMs || 0), 0) / chartData.length;

  return (
    <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-gray-900 dark:text-white flex items-center">
          <BarChart3 className="w-5 h-5 mr-2" />
          Durée des exécutions
        </h3>
        <span className="text-sm text-gray-500 dark:text-gray-400">
          Moyenne: {formatDuration(avgDuration)}
        </span>
      </div>

      <div className="flex items-end space-x-1 h-32">
        {chartData.map((execution, index) => {
          const height = maxDuration > 0 ? ((execution.durationMs || 0) / maxDuration) * 100 : 0;
          const isSuccess = execution.status === TaskStatus.COMPLETED;

          return (
            <div
              key={execution.id}
              className="flex-1 flex flex-col items-center group relative"
            >
              <div
                className={`w-full rounded-t transition-all ${
                  isSuccess ? 'bg-green-500 hover:bg-green-400' : 'bg-red-500 hover:bg-red-400'
                }`}
                style={{ height: `${Math.max(height, 4)}%` }}
              />

              {/* Tooltip */}
              <div className="absolute bottom-full mb-2 hidden group-hover:block bg-gray-900 text-white text-xs rounded px-2 py-1 whitespace-nowrap z-10">
                <div>{format(new Date(execution.startedAt), 'dd/MM HH:mm')}</div>
                <div>{formatDuration(execution.durationMs || 0)}</div>
              </div>
            </div>
          );
        })}
      </div>

      {/* X-axis labels */}
      <div className="flex justify-between mt-2 text-xs text-gray-500 dark:text-gray-400">
        {chartData.length > 0 && (
          <>
            <span>{format(new Date(chartData[0].startedAt), 'dd/MM')}</span>
            <span>{format(new Date(chartData[chartData.length - 1].startedAt), 'dd/MM')}</span>
          </>
        )}
      </div>
    </div>
  );
};

// =============================================================================
// Execution Row
// =============================================================================

interface ExecutionRowProps {
  execution: ExecutionRecord;
  isExpanded: boolean;
  onToggle: () => void;
  onViewLogs?: (executionId: string) => void;
}

const ExecutionRow: React.FC<ExecutionRowProps> = ({
  execution,
  isExpanded,
  onToggle,
  onViewLogs,
}) => {
  const statusColor = STATUS_COLORS[execution.status];
  const isSuccess = execution.status === TaskStatus.COMPLETED;
  const isFailed = execution.status === TaskStatus.FAILED;

  return (
    <div className="hover:bg-gray-50 dark:hover:bg-gray-750">
      {/* Main row */}
      <div
        className="flex items-center justify-between p-4 cursor-pointer"
        onClick={onToggle}
      >
        <div className="flex items-center space-x-4">
          {/* Status indicator */}
          <div
            className="w-3 h-3 rounded-full"
            style={{ backgroundColor: statusColor }}
          />

          {/* Date/Time */}
          <div>
            <p className="font-medium text-gray-900 dark:text-white">
              {format(new Date(execution.startedAt), 'dd MMMM yyyy à HH:mm', { locale: fr })}
            </p>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {formatDistanceToNow(new Date(execution.startedAt), { addSuffix: true, locale: fr })}
            </p>
          </div>
        </div>

        <div className="flex items-center space-x-4">
          {/* Duration */}
          <div className="text-right">
            <div className="flex items-center text-sm text-gray-600 dark:text-gray-400">
              <Clock className="w-4 h-4 mr-1" />
              {execution.durationMs ? formatDuration(execution.durationMs) : '-'}
            </div>
            {execution.retryCount > 0 && (
              <div className="text-xs text-yellow-600">
                {execution.retryCount} retry(s)
              </div>
            )}
          </div>

          {/* Status badge */}
          <span
            className="px-2 py-1 text-xs font-medium rounded"
            style={{ backgroundColor: `${statusColor}20`, color: statusColor }}
          >
            {getStatusLabel(execution.status)}
          </span>

          {/* Expand/Collapse */}
          {isExpanded ? (
            <ChevronUp className="w-5 h-5 text-gray-400" />
          ) : (
            <ChevronDown className="w-5 h-5 text-gray-400" />
          )}
        </div>
      </div>

      {/* Expanded details */}
      {isExpanded && (
        <div className="px-4 pb-4 pt-0 border-t border-gray-100 dark:border-gray-700">
          <div className="grid grid-cols-2 gap-4 mt-4">
            {/* Info */}
            <div className="space-y-2">
              <DetailRow label="ID" value={execution.id} mono />
              <DetailRow
                label="Déclencheur"
                value={getTriggerLabel(execution.triggeredBy)}
              />
              <DetailRow
                label="Début"
                value={format(new Date(execution.startedAt), 'dd/MM/yyyy HH:mm:ss')}
              />
              {execution.completedAt && (
                <DetailRow
                  label="Fin"
                  value={format(new Date(execution.completedAt), 'dd/MM/yyyy HH:mm:ss')}
                />
              )}
            </div>

            {/* Result */}
            <div className="space-y-2">
              {execution.result && (
                <>
                  <DetailRow
                    label="Succès"
                    value={execution.result.success ? 'Oui' : 'Non'}
                  />
                  {execution.result.message && (
                    <DetailRow label="Message" value={execution.result.message} />
                  )}
                </>
              )}
              {execution.error && (
                <div className="p-2 bg-red-50 dark:bg-red-900/20 rounded text-sm text-red-700 dark:text-red-300">
                  {execution.error}
                </div>
              )}
            </div>
          </div>

          {/* Actions */}
          {onViewLogs && (
            <div className="mt-4 flex justify-end">
              <button
                onClick={(e) => { e.stopPropagation(); onViewLogs(execution.id); }}
                className="flex items-center space-x-1 px-3 py-1.5 text-sm text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded"
              >
                <ExternalLink className="w-4 h-4" />
                <span>Voir les logs</span>
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Detail Row
// =============================================================================

interface DetailRowProps {
  label: string;
  value: string;
  mono?: boolean;
}

const DetailRow: React.FC<DetailRowProps> = ({ label, value, mono }) => (
  <div className="flex items-start">
    <span className="text-sm text-gray-500 dark:text-gray-400 w-24 flex-shrink-0">
      {label}:
    </span>
    <span className={`text-sm text-gray-900 dark:text-white ${mono ? 'font-mono text-xs' : ''}`}>
      {value}
    </span>
  </div>
);

// =============================================================================
// Helpers
// =============================================================================

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
}

function getStatusLabel(status: TaskStatus): string {
  const labels: Record<TaskStatus, string> = {
    [TaskStatus.PENDING]: 'En attente',
    [TaskStatus.SCHEDULED]: 'Planifié',
    [TaskStatus.RUNNING]: 'En cours',
    [TaskStatus.COMPLETED]: 'Succès',
    [TaskStatus.FAILED]: 'Échec',
    [TaskStatus.CANCELLED]: 'Annulé',
    [TaskStatus.PAUSED]: 'En pause',
  };
  return labels[status] || status;
}

function getTriggerLabel(trigger?: 'schedule' | 'manual' | 'dependency' | 'api'): string {
  const labels = {
    schedule: 'Planifié',
    manual: 'Manuel',
    dependency: 'Dépendance',
    api: 'API',
  };
  return trigger ? labels[trigger] : 'Inconnu';
}

export default ExecutionHistory;
