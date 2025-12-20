/**
 * Task List Component
 *
 * Liste des tâches avec filtres, recherche, tri et pagination.
 */

import React, { useMemo, useState } from 'react';
import {
  Search,
  Filter,
  Plus,
  Grid,
  List,
  SortAsc,
  SortDesc,
  X,
  ChevronDown,
  Download,
  Upload,
  Trash2,
  PlayCircle,
  PauseCircle,
} from 'lucide-react';
import { useSchedulerStore } from '../../store/schedulerStore';
import { useShallow } from 'zustand/react/shallow';
import {
  ScheduledTask,
  TaskType,
  TaskStatus,
  TaskPriority,
  STATUS_COLORS,
  PRIORITY_COLORS,
} from '../../types/scheduler.types';
import { TaskCard } from './TaskCard';

// =============================================================================
// Types
// =============================================================================

type SortField = 'name' | 'status' | 'priority' | 'nextExecution' | 'createdAt';
type SortDirection = 'asc' | 'desc';
type ViewMode = 'grid' | 'list';

interface FilterDropdownProps {
  label: string;
  options: { value: string; label: string; color?: string }[];
  selected: string[];
  onChange: (selected: string[]) => void;
}

// =============================================================================
// Sub-components
// =============================================================================

const FilterDropdown: React.FC<FilterDropdownProps> = ({
  label,
  options,
  selected,
  onChange,
}) => {
  const [open, setOpen] = useState(false);

  const handleToggle = (value: string) => {
    if (selected.includes(value)) {
      onChange(selected.filter((v) => v !== value));
    } else {
      onChange([...selected, value]);
    }
  };

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className={`flex items-center space-x-2 px-3 py-2 text-sm border rounded-lg transition-colors
          ${selected.length > 0
            ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300'
            : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'}`}
      >
        <span>{label}</span>
        {selected.length > 0 && (
          <span className="px-1.5 py-0.5 text-xs bg-blue-500 text-white rounded-full">
            {selected.length}
          </span>
        )}
        <ChevronDown className="w-4 h-4" />
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute top-full left-0 mt-1 w-48 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-20 py-1">
            {options.map((option) => (
              <label
                key={option.value}
                className="flex items-center px-4 py-2 hover:bg-gray-50 dark:hover:bg-gray-700 cursor-pointer"
              >
                <input
                  type="checkbox"
                  checked={selected.includes(option.value)}
                  onChange={() => handleToggle(option.value)}
                  className="w-4 h-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
                />
                <span className="ml-2 text-sm text-gray-700 dark:text-gray-300 flex items-center">
                  {option.color && (
                    <span
                      className="w-2 h-2 rounded-full mr-2"
                      style={{ backgroundColor: option.color }}
                    />
                  )}
                  {option.label}
                </span>
              </label>
            ))}
            {selected.length > 0 && (
              <button
                onClick={() => { onChange([]); setOpen(false); }}
                className="w-full px-4 py-2 text-sm text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700 border-t border-gray-100 dark:border-gray-700"
              >
                Effacer
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const TaskList: React.FC = () => {
  const {
    tasks,
    filters,
    setFilters,
    clearFilters,
    selectedTaskId,
    selectTask,
    openModal,
    startTask,
    stopTask,
    pauseTask,
    resumeTask,
    retryTask,
    deleteTask,
    duplicateTask,
    loading,
  } = useSchedulerStore(
    useShallow((state) => ({
      tasks: state.tasks,
      filters: state.filters,
      setFilters: state.setFilters,
      clearFilters: state.clearFilters,
      selectedTaskId: state.selectedTaskId,
      selectTask: state.selectTask,
      openModal: state.openModal,
      startTask: state.startTask,
      stopTask: state.stopTask,
      pauseTask: state.pauseTask,
      resumeTask: state.resumeTask,
      retryTask: state.retryTask,
      deleteTask: state.deleteTask,
      duplicateTask: state.duplicateTask,
      loading: state.loading,
    }))
  );

  // Compute filtered tasks using useMemo to avoid creating new array references
  const filteredTasks = useMemo(() => {
    let result = tasks;

    // Filter by status
    if (filters.status?.length) {
      result = result.filter((t) => filters.status!.includes(t.status));
    }

    // Filter by type
    if (filters.type?.length) {
      result = result.filter((t) => filters.type!.includes(t.type));
    }

    // Filter by priority
    if (filters.priority?.length) {
      result = result.filter((t) => filters.priority!.includes(t.priority));
    }

    // Filter by search
    if (filters.search) {
      const searchLower = filters.search.toLowerCase();
      result = result.filter((t) =>
        t.name.toLowerCase().includes(searchLower) ||
        t.description?.toLowerCase().includes(searchLower)
      );
    }

    return result;
  }, [tasks, filters]);

  // Local state
  const [viewMode, setViewMode] = useState<ViewMode>('grid');
  const [sortField, setSortField] = useState<SortField>('nextExecution');
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc');
  const [bulkSelection, setBulkSelection] = useState<string[]>([]);
  const [bulkMode, setBulkMode] = useState(false);

  // Filter options
  const statusOptions = Object.values(TaskStatus).map((status) => ({
    value: status,
    label: getStatusLabel(status),
    color: STATUS_COLORS[status],
  }));

  const typeOptions = Object.values(TaskType).map((type) => ({
    value: type,
    label: getTypeLabel(type),
  }));

  const priorityOptions = Object.values(TaskPriority).map((priority) => ({
    value: priority,
    label: getPriorityLabel(priority),
    color: PRIORITY_COLORS[priority],
  }));

  // Sorted tasks
  const sortedTasks = useMemo(() => {
    return [...filteredTasks].sort((a, b) => {
      let comparison = 0;

      switch (sortField) {
        case 'name':
          comparison = a.name.localeCompare(b.name);
          break;
        case 'status':
          comparison = a.status.localeCompare(b.status);
          break;
        case 'priority':
          const priorityOrder = { critical: 0, high: 1, normal: 2, low: 3 };
          comparison = (priorityOrder[a.priority] || 99) - (priorityOrder[b.priority] || 99);
          break;
        case 'nextExecution':
          const aTime = a.nextExecution ? new Date(a.nextExecution).getTime() : Infinity;
          const bTime = b.nextExecution ? new Date(b.nextExecution).getTime() : Infinity;
          comparison = aTime - bTime;
          break;
        case 'createdAt':
          comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
          break;
      }

      return sortDirection === 'asc' ? comparison : -comparison;
    });
  }, [filteredTasks, sortField, sortDirection]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
  };

  const handleBulkToggle = (taskId: string) => {
    if (bulkSelection.includes(taskId)) {
      setBulkSelection(bulkSelection.filter((id) => id !== taskId));
    } else {
      setBulkSelection([...bulkSelection, taskId]);
    }
  };

  const handleSelectAll = () => {
    if (bulkSelection.length === sortedTasks.length) {
      setBulkSelection([]);
    } else {
      setBulkSelection(sortedTasks.map((t) => t.id));
    }
  };

  const handleBulkDelete = async () => {
    if (!confirm(`Supprimer ${bulkSelection.length} tâche(s) ?`)) return;
    for (const id of bulkSelection) {
      await deleteTask(id);
    }
    setBulkSelection([]);
    setBulkMode(false);
  };

  const hasActiveFilters = filters.status?.length || filters.type?.length || filters.priority?.length || filters.search;

  return (
    <div className="space-y-4 p-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-900 dark:text-white">
          Tâches planifiées
          <span className="ml-2 text-sm font-normal text-gray-500 dark:text-gray-400">
            ({sortedTasks.length})
          </span>
        </h2>
        <div className="flex items-center space-x-2">
          <button
            onClick={() => openModal('create')}
            className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>Nouvelle tâche</span>
          </button>
        </div>
      </div>

      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-3 bg-white dark:bg-gray-800 p-4 rounded-lg border border-gray-200 dark:border-gray-700">
        {/* Search */}
        <div className="relative flex-1 min-w-[200px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            type="text"
            placeholder="Rechercher..."
            value={filters.search || ''}
            onChange={(e) => setFilters({ search: e.target.value })}
            className="w-full pl-9 pr-4 py-2 text-sm border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-800 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>

        {/* Filters */}
        <FilterDropdown
          label="Statut"
          options={statusOptions}
          selected={filters.status || []}
          onChange={(status) => setFilters({ status: status.length ? status as TaskStatus[] : undefined })}
        />
        <FilterDropdown
          label="Type"
          options={typeOptions}
          selected={filters.type || []}
          onChange={(type) => setFilters({ type: type.length ? type as TaskType[] : undefined })}
        />
        <FilterDropdown
          label="Priorité"
          options={priorityOptions}
          selected={filters.priority || []}
          onChange={(priority) => setFilters({ priority: priority.length ? priority as TaskPriority[] : undefined })}
        />

        {/* Clear filters */}
        {hasActiveFilters && (
          <button
            onClick={clearFilters}
            className="flex items-center space-x-1 px-3 py-2 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300"
          >
            <X className="w-4 h-4" />
            <span>Effacer</span>
          </button>
        )}

        {/* Divider */}
        <div className="h-6 w-px bg-gray-200 dark:bg-gray-700" />

        {/* Sort */}
        <div className="flex items-center space-x-1">
          <select
            value={sortField}
            onChange={(e) => setSortField(e.target.value as SortField)}
            className="text-sm border border-gray-200 dark:border-gray-700 rounded-lg px-3 py-2 bg-white dark:bg-gray-800"
          >
            <option value="nextExecution">Prochaine exécution</option>
            <option value="name">Nom</option>
            <option value="status">Statut</option>
            <option value="priority">Priorité</option>
            <option value="createdAt">Date création</option>
          </select>
          <button
            onClick={() => setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc')}
            className="p-2 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
          >
            {sortDirection === 'asc' ? (
              <SortAsc className="w-4 h-4" />
            ) : (
              <SortDesc className="w-4 h-4" />
            )}
          </button>
        </div>

        {/* View mode */}
        <div className="flex items-center border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
          <button
            onClick={() => setViewMode('grid')}
            className={`p-2 ${viewMode === 'grid' ? 'bg-gray-100 dark:bg-gray-700' : 'hover:bg-gray-50 dark:hover:bg-gray-700'}`}
          >
            <Grid className="w-4 h-4" />
          </button>
          <button
            onClick={() => setViewMode('list')}
            className={`p-2 ${viewMode === 'list' ? 'bg-gray-100 dark:bg-gray-700' : 'hover:bg-gray-50 dark:hover:bg-gray-700'}`}
          >
            <List className="w-4 h-4" />
          </button>
        </div>

        {/* Bulk mode toggle */}
        <button
          onClick={() => { setBulkMode(!bulkMode); setBulkSelection([]); }}
          className={`px-3 py-2 text-sm rounded-lg transition-colors
            ${bulkMode ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'}`}
        >
          Sélection multiple
        </button>
      </div>

      {/* Bulk actions bar */}
      {bulkMode && bulkSelection.length > 0 && (
        <div className="flex items-center justify-between bg-blue-50 dark:bg-blue-900/20 p-4 rounded-lg border border-blue-200 dark:border-blue-800">
          <div className="flex items-center space-x-4">
            <label className="flex items-center space-x-2 cursor-pointer">
              <input
                type="checkbox"
                checked={bulkSelection.length === sortedTasks.length}
                onChange={handleSelectAll}
                className="w-4 h-4 text-blue-600 rounded"
              />
              <span className="text-sm text-blue-700 dark:text-blue-300">
                {bulkSelection.length} sélectionné(s)
              </span>
            </label>
          </div>
          <div className="flex items-center space-x-2">
            <button
              onClick={() => { /* TODO: bulk start */ }}
              className="flex items-center space-x-1 px-3 py-1.5 text-sm text-green-700 dark:text-green-300 bg-green-100 dark:bg-green-900/30 rounded-lg hover:bg-green-200 dark:hover:bg-green-900/50"
            >
              <PlayCircle className="w-4 h-4" />
              <span>Démarrer</span>
            </button>
            <button
              onClick={() => { /* TODO: bulk pause */ }}
              className="flex items-center space-x-1 px-3 py-1.5 text-sm text-yellow-700 dark:text-yellow-300 bg-yellow-100 dark:bg-yellow-900/30 rounded-lg hover:bg-yellow-200 dark:hover:bg-yellow-900/50"
            >
              <PauseCircle className="w-4 h-4" />
              <span>Pause</span>
            </button>
            <button
              onClick={handleBulkDelete}
              className="flex items-center space-x-1 px-3 py-1.5 text-sm text-red-700 dark:text-red-300 bg-red-100 dark:bg-red-900/30 rounded-lg hover:bg-red-200 dark:hover:bg-red-900/50"
            >
              <Trash2 className="w-4 h-4" />
              <span>Supprimer</span>
            </button>
          </div>
        </div>
      )}

      {/* Task grid/list */}
      {sortedTasks.length > 0 ? (
        <div
          className={viewMode === 'grid'
            ? 'grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4'
            : 'space-y-3'
          }
        >
          {sortedTasks.map((task) => (
            <div key={task.id} className={bulkMode ? 'relative' : ''}>
              {bulkMode && (
                <div className="absolute top-4 left-4 z-10">
                  <input
                    type="checkbox"
                    checked={bulkSelection.includes(task.id)}
                    onChange={() => handleBulkToggle(task.id)}
                    className="w-5 h-5 text-blue-600 rounded border-2 border-gray-300 dark:border-gray-600"
                  />
                </div>
              )}
              <TaskCard
                task={task}
                isSelected={selectedTaskId === task.id}
                onSelect={(t) => selectTask(t.id)}
                onStart={(t) => startTask(t.id)}
                onStop={(t) => stopTask(t.id)}
                onPause={(t) => pauseTask(t.id)}
                onResume={(t) => resumeTask(t.id)}
                onRetry={(t) => retryTask(t.id)}
                onEdit={(t) => openModal('edit', t)}
                onDuplicate={(t) => duplicateTask(t.id)}
                onDelete={(t) => {
                  if (confirm(`Supprimer "${t.name}" ?`)) {
                    deleteTask(t.id);
                  }
                }}
                onViewLogs={(t) => openModal('logs', t)}
                compact={viewMode === 'list'}
              />
            </div>
          ))}
        </div>
      ) : (
        <div className="text-center py-12 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700">
          {hasActiveFilters ? (
            <>
              <Filter className="w-12 h-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-500 dark:text-gray-400">
                Aucune tâche ne correspond aux filtres
              </p>
              <button
                onClick={clearFilters}
                className="mt-4 text-blue-600 hover:text-blue-700 text-sm"
              >
                Effacer les filtres
              </button>
            </>
          ) : (
            <>
              <Plus className="w-12 h-12 text-gray-400 mx-auto mb-4" />
              <p className="text-gray-500 dark:text-gray-400">
                Aucune tâche planifiée
              </p>
              <button
                onClick={() => openModal('create')}
                className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
              >
                Créer une tâche
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Helpers
// =============================================================================

function getStatusLabel(status: TaskStatus): string {
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
}

function getTypeLabel(type: TaskType): string {
  const labels: Record<TaskType, string> = {
    [TaskType.SESSION]: 'Session',
    [TaskType.TNR]: 'TNR',
    [TaskType.PERFORMANCE]: 'Performance',
    [TaskType.CUSTOM]: 'Personnalisé',
  };
  return labels[type] || type;
}

function getPriorityLabel(priority: TaskPriority): string {
  const labels: Record<TaskPriority, string> = {
    [TaskPriority.LOW]: 'Basse',
    [TaskPriority.NORMAL]: 'Normale',
    [TaskPriority.HIGH]: 'Haute',
    [TaskPriority.CRITICAL]: 'Critique',
  };
  return labels[priority] || priority;
}

export default TaskList;
