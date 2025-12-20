/**
 * Scheduler Tab - Enterprise Edition
 *
 * Onglet principal de planification des tâches avec dashboard,
 * liste des tâches, calendrier, historique et logs.
 */

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  LayoutDashboard,
  List,
  Calendar as CalendarIcon,
  History,
  Terminal,
  Settings,
  Wifi,
  WifiOff,
  Bell,
  BellOff,
  Moon,
  Sun,
  ChevronLeft,
  X,
} from 'lucide-react';
import { useSchedulerStore, selectSelectedTask } from '../store/schedulerStore';
import { useShallow } from 'zustand/react/shallow';
import {
  SchedulerDashboard,
  TaskList,
  TaskFormModal,
  ExecutionLogs,
  ExecutionHistory,
} from '../components/scheduler';
import { schedulerNotifier } from '../services/schedulerNotifier';

// =============================================================================
// Types
// =============================================================================

type View = 'dashboard' | 'list' | 'calendar' | 'timeline' | 'history';

interface ViewConfig {
  id: View;
  label: string;
  icon: React.FC<{ className?: string }>;
}

// =============================================================================
// Constants
// =============================================================================

const VIEWS: ViewConfig[] = [
  { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { id: 'list', label: 'Tâches', icon: List },
  { id: 'calendar', label: 'Calendrier', icon: CalendarIcon },
  { id: 'history', label: 'Historique', icon: History },
];

// =============================================================================
// Main Component
// =============================================================================

export const SchedulerTab: React.FC = () => {
  const {
    view,
    setView,
    modalOpen,
    editingTask,
    closeModal,
    openModal,
    wsConnected,
    connectWs,
    disconnectWs,
    fetchTasks,
    selectedTaskId,
    selectTask,
  } = useSchedulerStore(
    useShallow((state) => ({
      view: state.view,
      setView: state.setView,
      modalOpen: state.modalOpen,
      editingTask: state.editingTask,
      closeModal: state.closeModal,
      openModal: state.openModal,
      wsConnected: state.wsConnected,
      connectWs: state.connectWs,
      disconnectWs: state.disconnectWs,
      fetchTasks: state.fetchTasks,
      selectedTaskId: state.selectedTaskId,
      selectTask: state.selectTask,
    }))
  );

  const selectedTask = useSchedulerStore(selectSelectedTask);

  const [notificationsEnabled, setNotificationsEnabled] = useState(false);
  const [showDetailPanel, setShowDetailPanel] = useState(false);

  // Initialize
  useEffect(() => {
    fetchTasks();
    connectWs();

    // Initialize notifications
    schedulerNotifier.init().then(() => {
      setNotificationsEnabled(schedulerNotifier.hasPermission());
    });

    return () => {
      disconnectWs();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Show detail panel when task selected
  useEffect(() => {
    if (selectedTaskId) {
      setShowDetailPanel(true);
    }
  }, [selectedTaskId]);

  const handleNotificationToggle = async () => {
    if (notificationsEnabled) {
      setNotificationsEnabled(false);
    } else {
      const permission = await schedulerNotifier.requestPermission();
      setNotificationsEnabled(permission === 'granted');
    }
  };

  const handleViewLogs = () => {
    if (selectedTask) {
      openModal('logs', selectedTask);
    }
  };

  const closeDetailPanel = () => {
    setShowDetailPanel(false);
    selectTask(null);
  };

  return (
    <div className="h-full flex flex-col bg-gray-100 dark:bg-gray-900">
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center space-x-4">
          <h1 className="text-xl font-bold text-gray-900 dark:text-white">
            Scheduler
          </h1>

          {/* View tabs */}
          <div className="flex items-center border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden">
            {VIEWS.map((v) => (
              <button
                key={v.id}
                onClick={() => setView(v.id)}
                className={`flex items-center space-x-2 px-4 py-2 text-sm transition-colors
                  ${view === v.id
                    ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                    : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'}`}
              >
                <v.icon className="w-4 h-4" />
                <span className="hidden sm:inline">{v.label}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center space-x-3">
          {/* WebSocket status */}
          <div
            className={`flex items-center space-x-1 px-2 py-1 rounded text-xs
              ${wsConnected
                ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300'
                : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300'}`}
          >
            {wsConnected ? (
              <>
                <Wifi className="w-3 h-3" />
                <span>Connecté</span>
              </>
            ) : (
              <>
                <WifiOff className="w-3 h-3" />
                <span>Déconnecté</span>
              </>
            )}
          </div>

          {/* Notifications toggle */}
          <button
            onClick={handleNotificationToggle}
            className={`p-2 rounded-lg transition-colors
              ${notificationsEnabled
                ? 'text-blue-600 bg-blue-50 dark:bg-blue-900/30'
                : 'text-gray-400 hover:text-gray-600 hover:bg-gray-100 dark:hover:bg-gray-700'}`}
            title={notificationsEnabled ? 'Désactiver les notifications' : 'Activer les notifications'}
          >
            {notificationsEnabled ? (
              <Bell className="w-5 h-5" />
            ) : (
              <BellOff className="w-5 h-5" />
            )}
          </button>

          {/* Settings */}
          <button
            className="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
            title="Paramètres"
          >
            <Settings className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 flex overflow-hidden">
        {/* Main content */}
        <div className={`flex-1 overflow-y-auto transition-all ${showDetailPanel && selectedTask ? 'mr-96' : ''}`}>
          {view === 'dashboard' && <SchedulerDashboard />}
          {view === 'list' && <TaskList />}
          {view === 'calendar' && <CalendarView />}
          {view === 'history' && <HistoryView />}
        </div>

        {/* Detail Panel */}
        {showDetailPanel && selectedTask && (
          <div className="fixed right-0 top-0 h-full w-96 bg-white dark:bg-gray-800 border-l border-gray-200 dark:border-gray-700 shadow-lg z-40 overflow-y-auto">
            <div className="sticky top-0 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-4 py-3 flex items-center justify-between">
              <h3 className="font-semibold text-gray-900 dark:text-white truncate">
                {selectedTask.name}
              </h3>
              <button
                onClick={closeDetailPanel}
                className="p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-4">
              <ExecutionHistory
                taskId={selectedTask.id}
                onViewLogs={handleViewLogs}
              />
            </div>
          </div>
        )}
      </div>

      {/* Modals */}
      {(modalOpen === 'create' || modalOpen === 'edit') && (
        <TaskFormModal
          isOpen={true}
          task={editingTask || undefined}
          onClose={closeModal}
        />
      )}

      {modalOpen === 'logs' && editingTask && (
        <LogsModal
          taskId={editingTask.id || ''}
          taskName={editingTask.name || ''}
          onClose={closeModal}
        />
      )}
    </div>
  );
};

// =============================================================================
// Calendar View (Placeholder)
// =============================================================================

const CalendarView: React.FC = () => {
  return (
    <div className="p-6">
      <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-8 text-center">
        <CalendarIcon className="w-16 h-16 text-gray-400 mx-auto mb-4" />
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
          Vue Calendrier
        </h3>
        <p className="text-gray-500 dark:text-gray-400">
          La vue calendrier sera disponible dans une prochaine version.
          Elle affichera toutes les tâches planifiées sur un calendrier interactif.
        </p>
      </div>
    </div>
  );
};

// =============================================================================
// History View
// =============================================================================

const HistoryView: React.FC = () => {
  const tasks = useSchedulerStore(useShallow((state) => state.tasks));
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  return (
    <div className="p-6 space-y-6">
      {/* Task selector */}
      <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Sélectionnez une tâche
        </label>
        <select
          value={selectedTaskId || ''}
          onChange={(e) => setSelectedTaskId(e.target.value || null)}
          className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700"
        >
          <option value="">-- Choisir une tâche --</option>
          {tasks.map((task) => (
            <option key={task.id} value={task.id}>
              {task.name}
            </option>
          ))}
        </select>
      </div>

      {/* History */}
      {selectedTaskId ? (
        <ExecutionHistory taskId={selectedTaskId} />
      ) : (
        <div className="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 p-8 text-center">
          <History className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <p className="text-gray-500 dark:text-gray-400">
            Sélectionnez une tâche pour voir son historique d'exécution.
          </p>
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Logs Modal
// =============================================================================

interface LogsModalProps {
  taskId: string;
  taskName: string;
  onClose: () => void;
}

const LogsModal: React.FC<LogsModalProps> = ({ taskId, taskName, onClose }) => {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-4xl h-[80vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200 dark:border-gray-700">
          <div className="flex items-center space-x-2">
            <Terminal className="w-5 h-5 text-green-500" />
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
              Logs - {taskName}
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Logs */}
        <div className="flex-1 overflow-hidden">
          <ExecutionLogs taskId={taskId} />
        </div>
      </div>
    </div>
  );
};

export default SchedulerTab;
