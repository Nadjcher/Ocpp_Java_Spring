/**
 * Task Card Component
 *
 * Carte affichant les détails d'une tâche planifiée
 * avec actions contextuelles et indicateurs de statut.
 */

import React, { useState } from 'react';
import {
  Play,
  Pause,
  Square,
  RefreshCw,
  MoreVertical,
  Edit,
  Copy,
  Trash2,
  Clock,
  Calendar,
  Zap,
  TestTube,
  Activity,
  Code,
  ChevronDown,
  ChevronUp,
  ExternalLink,
  AlertCircle,
} from 'lucide-react';
import {
  ScheduledTask,
  TaskType,
  TaskStatus,
  TaskPriority,
  STATUS_COLORS,
  PRIORITY_COLORS,
} from '../../types/scheduler.types';
import { cronParser } from '../../services/cronParser';
import { formatDistanceToNow, format, fr } from '../../utils/dateUtils';

// =============================================================================
// Types
// =============================================================================

interface TaskCardProps {
  task: ScheduledTask;
  isSelected?: boolean;
  onSelect?: (task: ScheduledTask) => void;
  onStart?: (task: ScheduledTask) => void;
  onStop?: (task: ScheduledTask) => void;
  onPause?: (task: ScheduledTask) => void;
  onResume?: (task: ScheduledTask) => void;
  onRetry?: (task: ScheduledTask) => void;
  onEdit?: (task: ScheduledTask) => void;
  onDuplicate?: (task: ScheduledTask) => void;
  onDelete?: (task: ScheduledTask) => void;
  onViewLogs?: (task: ScheduledTask) => void;
  compact?: boolean;
}

// =============================================================================
// Helpers
// =============================================================================

const getTypeIcon = (type: TaskType) => {
  const icons = {
    [TaskType.SESSION]: Zap,
    [TaskType.TNR]: TestTube,
    [TaskType.PERFORMANCE]: Activity,
    [TaskType.CUSTOM]: Code,
  };
  return icons[type] || Activity;
};

const getTypeColor = (type: TaskType): string => {
  const colors = {
    [TaskType.SESSION]: '#3b82f6',
    [TaskType.TNR]: '#10b981',
    [TaskType.PERFORMANCE]: '#f59e0b',
    [TaskType.CUSTOM]: '#8b5cf6',
  };
  return colors[type] || '#6b7280';
};

const getStatusLabel = (status: TaskStatus): string => {
  const labels: Record<TaskStatus, string> = {
    [TaskStatus.PENDING]: 'En attente',
    [TaskStatus.SCHEDULED]: 'Planifié',
    [TaskStatus.RUNNING]: 'En cours',
    [TaskStatus.COMPLETED]: 'Terminé',
    [TaskStatus.FAILED]: 'Échec',
    [TaskStatus.CANCELLED]: 'Annulé',
    [TaskStatus.PAUSED]: 'En pause',
  };
  return labels[status] || status;
};

const getPriorityLabel = (priority: TaskPriority): string => {
  const labels: Record<TaskPriority, string> = {
    [TaskPriority.LOW]: 'Basse',
    [TaskPriority.NORMAL]: 'Normale',
    [TaskPriority.HIGH]: 'Haute',
    [TaskPriority.CRITICAL]: 'Critique',
  };
  return labels[priority] || priority;
};

// =============================================================================
// Component
// =============================================================================

export const TaskCard: React.FC<TaskCardProps> = ({
  task,
  isSelected = false,
  onSelect,
  onStart,
  onStop,
  onPause,
  onResume,
  onRetry,
  onEdit,
  onDuplicate,
  onDelete,
  onViewLogs,
  compact = false,
}) => {
  const [expanded, setExpanded] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  const TypeIcon = getTypeIcon(task.type);
  const typeColor = getTypeColor(task.type);
  const statusColor = STATUS_COLORS[task.status];
  const priorityColor = PRIORITY_COLORS[task.priority];

  const cronDescription = task.schedule.cronExpression
    ? cronParser.describe(task.schedule.cronExpression)
    : null;

  const handleCardClick = (e: React.MouseEvent) => {
    // Don't trigger select if clicking on buttons
    if ((e.target as HTMLElement).closest('button')) return;
    onSelect?.(task);
  };

  const canStart = task.status === TaskStatus.SCHEDULED || task.status === TaskStatus.PENDING;
  const canStop = task.status === TaskStatus.RUNNING;
  const canPause = task.status === TaskStatus.RUNNING;
  const canResume = task.status === TaskStatus.PAUSED;
  const canRetry = task.status === TaskStatus.FAILED;

  if (compact) {
    return (
      <div
        className={`flex items-center justify-between p-3 bg-white dark:bg-gray-800 rounded-lg border transition-all cursor-pointer
          ${isSelected ? 'border-blue-500 ring-2 ring-blue-200 dark:ring-blue-900' : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'}`}
        onClick={handleCardClick}
      >
        <div className="flex items-center space-x-3">
          <div
            className="w-2 h-2 rounded-full"
            style={{ backgroundColor: statusColor }}
          />
          <TypeIcon className="w-4 h-4" style={{ color: typeColor }} />
          <span className="font-medium text-gray-900 dark:text-white">{task.name}</span>
        </div>
        <div className="flex items-center space-x-2">
          {task.nextExecution && (
            <span className="text-xs text-gray-500 dark:text-gray-400">
              {formatDistanceToNow(new Date(task.nextExecution), { addSuffix: true, locale: fr })}
            </span>
          )}
          {canStart && onStart && (
            <button
              onClick={() => onStart(task)}
              className="p-1 text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20 rounded"
            >
              <Play className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    );
  }

  return (
    <div
      className={`bg-white dark:bg-gray-800 rounded-lg border shadow-sm transition-all
        ${isSelected ? 'border-blue-500 ring-2 ring-blue-200 dark:ring-blue-900' : 'border-gray-200 dark:border-gray-700 hover:shadow-md'}`}
      onClick={handleCardClick}
    >
      {/* Header */}
      <div className="p-4 border-b border-gray-100 dark:border-gray-700">
        <div className="flex items-start justify-between">
          <div className="flex items-start space-x-3">
            <div
              className="p-2 rounded-lg"
              style={{ backgroundColor: `${typeColor}15` }}
            >
              <TypeIcon className="w-5 h-5" style={{ color: typeColor }} />
            </div>
            <div>
              <h3 className="font-semibold text-gray-900 dark:text-white">{task.name}</h3>
              {task.description && (
                <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5 line-clamp-1">
                  {task.description}
                </p>
              )}
            </div>
          </div>
          <div className="relative">
            <button
              onClick={() => setMenuOpen(!menuOpen)}
              className="p-1.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
            >
              <MoreVertical className="w-4 h-4" />
            </button>
            {menuOpen && (
              <>
                <div
                  className="fixed inset-0 z-10"
                  onClick={() => setMenuOpen(false)}
                />
                <div className="absolute right-0 mt-1 w-48 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 z-20">
                  {onEdit && (
                    <button
                      onClick={() => { onEdit(task); setMenuOpen(false); }}
                      className="w-full flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
                    >
                      <Edit className="w-4 h-4 mr-2" />
                      Modifier
                    </button>
                  )}
                  {onDuplicate && (
                    <button
                      onClick={() => { onDuplicate(task); setMenuOpen(false); }}
                      className="w-full flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
                    >
                      <Copy className="w-4 h-4 mr-2" />
                      Dupliquer
                    </button>
                  )}
                  {onViewLogs && (
                    <button
                      onClick={() => { onViewLogs(task); setMenuOpen(false); }}
                      className="w-full flex items-center px-4 py-2 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
                    >
                      <ExternalLink className="w-4 h-4 mr-2" />
                      Voir les logs
                    </button>
                  )}
                  {onDelete && (
                    <button
                      onClick={() => { onDelete(task); setMenuOpen(false); }}
                      className="w-full flex items-center px-4 py-2 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20"
                    >
                      <Trash2 className="w-4 h-4 mr-2" />
                      Supprimer
                    </button>
                  )}
                </div>
              </>
            )}
          </div>
        </div>

        {/* Status badges */}
        <div className="flex items-center space-x-2 mt-3">
          <span
            className="px-2 py-1 text-xs font-medium rounded-full"
            style={{ backgroundColor: `${statusColor}20`, color: statusColor }}
          >
            {getStatusLabel(task.status)}
          </span>
          <span
            className="px-2 py-1 text-xs font-medium rounded-full"
            style={{ backgroundColor: `${priorityColor}20`, color: priorityColor }}
          >
            {getPriorityLabel(task.priority)}
          </span>
          {!task.enabled && (
            <span className="px-2 py-1 text-xs font-medium rounded-full bg-gray-100 dark:bg-gray-700 text-gray-500">
              Désactivé
            </span>
          )}
        </div>
      </div>

      {/* Info */}
      <div className="px-4 py-3 space-y-2">
        {/* Schedule */}
        <div className="flex items-center text-sm text-gray-600 dark:text-gray-400">
          <Calendar className="w-4 h-4 mr-2" />
          {cronDescription || (
            task.nextExecution
              ? `Prochaine: ${format(new Date(task.nextExecution), 'dd/MM/yyyy HH:mm', { locale: fr })}`
              : 'Non planifié'
          )}
        </div>

        {/* Last execution */}
        {task.lastExecution && (
          <div className="flex items-center text-sm text-gray-600 dark:text-gray-400">
            <Clock className="w-4 h-4 mr-2" />
            Dernière: {formatDistanceToNow(new Date(task.lastExecution.startedAt), { addSuffix: true, locale: fr })}
            {task.lastExecution.durationMs && (
              <span className="ml-1">({(task.lastExecution.durationMs / 1000).toFixed(1)}s)</span>
            )}
          </div>
        )}

        {/* Error message */}
        {task.status === TaskStatus.FAILED && task.lastExecution?.error && (
          <div className="flex items-start text-sm text-red-600 dark:text-red-400 mt-2">
            <AlertCircle className="w-4 h-4 mr-2 mt-0.5 flex-shrink-0" />
            <span className="line-clamp-2">{task.lastExecution.error}</span>
          </div>
        )}

        {/* Stats (expanded) */}
        {expanded && task.stats && (
          <div className="mt-4 pt-4 border-t border-gray-100 dark:border-gray-700">
            <div className="grid grid-cols-3 gap-4 text-center">
              <div>
                <p className="text-lg font-semibold text-gray-900 dark:text-white">
                  {task.stats.totalExecutions ?? 0}
                </p>
                <p className="text-xs text-gray-500 dark:text-gray-400">Exécutions</p>
              </div>
              <div>
                <p className="text-lg font-semibold text-green-600">
                  {(task.stats.successRate ?? 0).toFixed(0)}%
                </p>
                <p className="text-xs text-gray-500 dark:text-gray-400">Succès</p>
              </div>
              <div>
                <p className="text-lg font-semibold text-gray-900 dark:text-white">
                  {((task.stats.averageDurationMs ?? 0) / 1000).toFixed(1)}s
                </p>
                <p className="text-xs text-gray-500 dark:text-gray-400">Durée moy.</p>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="px-4 py-3 bg-gray-50 dark:bg-gray-750 border-t border-gray-100 dark:border-gray-700 flex items-center justify-between">
        <button
          onClick={() => setExpanded(!expanded)}
          className="flex items-center text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
        >
          {expanded ? (
            <>
              <ChevronUp className="w-4 h-4 mr-1" />
              Moins
            </>
          ) : (
            <>
              <ChevronDown className="w-4 h-4 mr-1" />
              Plus
            </>
          )}
        </button>

        <div className="flex items-center space-x-1">
          {canStart && onStart && (
            <button
              onClick={() => onStart(task)}
              className="p-2 text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20 rounded-lg"
              title="Démarrer"
            >
              <Play className="w-4 h-4" />
            </button>
          )}
          {canStop && onStop && (
            <button
              onClick={() => onStop(task)}
              className="p-2 text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg"
              title="Arrêter"
            >
              <Square className="w-4 h-4" />
            </button>
          )}
          {canPause && onPause && (
            <button
              onClick={() => onPause(task)}
              className="p-2 text-yellow-600 hover:bg-yellow-50 dark:hover:bg-yellow-900/20 rounded-lg"
              title="Pause"
            >
              <Pause className="w-4 h-4" />
            </button>
          )}
          {canResume && onResume && (
            <button
              onClick={() => onResume(task)}
              className="p-2 text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg"
              title="Reprendre"
            >
              <Play className="w-4 h-4" />
            </button>
          )}
          {canRetry && onRetry && (
            <button
              onClick={() => onRetry(task)}
              className="p-2 text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg"
              title="Réessayer"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default TaskCard;
