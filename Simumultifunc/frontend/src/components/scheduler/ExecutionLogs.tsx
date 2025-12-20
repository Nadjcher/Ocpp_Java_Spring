/**
 * Execution Logs Component
 *
 * Affiche les logs d'exécution en temps réel avec filtrage et recherche.
 */

import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Terminal,
  Search,
  Filter,
  Download,
  Trash2,
  Pause,
  Play,
  ChevronDown,
  AlertCircle,
  AlertTriangle,
  Info,
  Bug,
  RefreshCw,
} from 'lucide-react';
import { useSchedulerStore } from '../../store/schedulerStore';
import { useShallow } from 'zustand/react/shallow';
import { ExecutionLog } from '../../types/scheduler.types';
import { format } from '../../utils/dateUtils';

// =============================================================================
// Types
// =============================================================================

interface ExecutionLogsProps {
  taskId?: string;
  executionId?: string;
  autoScroll?: boolean;
  maxLines?: number;
}

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

// =============================================================================
// Constants
// =============================================================================

const LOG_COLORS: Record<LogLevel, { bg: string; text: string; icon: React.FC<{ className?: string }> }> = {
  debug: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-600 dark:text-gray-400', icon: Bug },
  info: { bg: 'bg-blue-50 dark:bg-blue-900/20', text: 'text-blue-700 dark:text-blue-400', icon: Info },
  warn: { bg: 'bg-yellow-50 dark:bg-yellow-900/20', text: 'text-yellow-700 dark:text-yellow-400', icon: AlertTriangle },
  error: { bg: 'bg-red-50 dark:bg-red-900/20', text: 'text-red-700 dark:text-red-400', icon: AlertCircle },
};

// =============================================================================
// Component
// =============================================================================

export const ExecutionLogs: React.FC<ExecutionLogsProps> = ({
  taskId,
  executionId,
  autoScroll = true,
  maxLines = 1000,
}) => {
  const { liveLogs, fetchLogs } = useSchedulerStore(
    useShallow((state) => ({
      liveLogs: state.liveLogs,
      fetchLogs: state.fetchLogs,
    }))
  );

  const [logs, setLogs] = useState<ExecutionLog[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [levelFilter, setLevelFilter] = useState<LogLevel[]>([]);
  const [isPaused, setIsPaused] = useState(false);
  const [showFilters, setShowFilters] = useState(false);

  const logsEndRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Load logs if executionId provided
  useEffect(() => {
    if (executionId) {
      fetchLogs(executionId).then(setLogs);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [executionId]);

  // Use live logs if taskId provided
  useEffect(() => {
    if (taskId) {
      const filteredLogs = liveLogs.filter((log) => log.taskId === taskId);
      setLogs(filteredLogs.slice(0, maxLines));
    }
  }, [taskId, liveLogs, maxLines]);

  // Auto-scroll
  useEffect(() => {
    if (autoScroll && !isPaused && logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll, isPaused]);

  // Filter logs
  const filteredLogs = useMemo(() => {
    let result = logs;

    if (levelFilter.length > 0) {
      result = result.filter((log) => levelFilter.includes(log.level));
    }

    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter((log) =>
        log.message.toLowerCase().includes(query) ||
        log.source?.toLowerCase().includes(query)
      );
    }

    return result;
  }, [logs, levelFilter, searchQuery]);

  // Export logs
  const handleExport = () => {
    const content = filteredLogs
      .map((log) => `[${format(new Date(log.timestamp), 'yyyy-MM-dd HH:mm:ss')}] [${log.level.toUpperCase()}] ${log.message}`)
      .join('\n');

    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `logs-${executionId || taskId || 'all'}-${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Clear logs
  const handleClear = () => {
    setLogs([]);
  };

  return (
    <div className="flex flex-col h-full bg-gray-900 rounded-lg overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 bg-gray-800 border-b border-gray-700">
        <div className="flex items-center space-x-2">
          <Terminal className="w-5 h-5 text-green-500" />
          <span className="text-sm font-medium text-white">Logs d'exécution</span>
          <span className="text-xs text-gray-400">
            ({filteredLogs.length} lignes)
          </span>
        </div>

        <div className="flex items-center space-x-2">
          {/* Search */}
          <div className="relative">
            <Search className="absolute left-2 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Rechercher..."
              className="pl-8 pr-3 py-1 text-sm bg-gray-700 border border-gray-600 rounded text-white placeholder-gray-500 focus:ring-1 focus:ring-blue-500"
            />
          </div>

          {/* Filter toggle */}
          <button
            onClick={() => setShowFilters(!showFilters)}
            className={`p-1.5 rounded ${showFilters ? 'bg-blue-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-700'}`}
          >
            <Filter className="w-4 h-4" />
          </button>

          {/* Pause/Play */}
          <button
            onClick={() => setIsPaused(!isPaused)}
            className={`p-1.5 rounded ${isPaused ? 'bg-yellow-600 text-white' : 'text-gray-400 hover:text-white hover:bg-gray-700'}`}
            title={isPaused ? 'Reprendre' : 'Pause'}
          >
            {isPaused ? <Play className="w-4 h-4" /> : <Pause className="w-4 h-4" />}
          </button>

          {/* Export */}
          <button
            onClick={handleExport}
            className="p-1.5 text-gray-400 hover:text-white hover:bg-gray-700 rounded"
            title="Exporter"
          >
            <Download className="w-4 h-4" />
          </button>

          {/* Clear */}
          <button
            onClick={handleClear}
            className="p-1.5 text-gray-400 hover:text-white hover:bg-gray-700 rounded"
            title="Effacer"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Filters */}
      {showFilters && (
        <div className="flex items-center space-x-2 px-4 py-2 bg-gray-800 border-b border-gray-700">
          <span className="text-xs text-gray-400">Niveau:</span>
          {(['debug', 'info', 'warn', 'error'] as LogLevel[]).map((level) => {
            const isSelected = levelFilter.includes(level);
            const config = LOG_COLORS[level];
            return (
              <button
                key={level}
                onClick={() => {
                  if (isSelected) {
                    setLevelFilter(levelFilter.filter((l) => l !== level));
                  } else {
                    setLevelFilter([...levelFilter, level]);
                  }
                }}
                className={`px-2 py-0.5 text-xs rounded border transition-colors
                  ${isSelected
                    ? `${config.bg} ${config.text} border-current`
                    : 'border-gray-600 text-gray-400 hover:text-white'}`}
              >
                {level.toUpperCase()}
              </button>
            );
          })}
          {levelFilter.length > 0 && (
            <button
              onClick={() => setLevelFilter([])}
              className="text-xs text-gray-500 hover:text-white"
            >
              Effacer
            </button>
          )}
        </div>
      )}

      {/* Logs */}
      <div
        ref={containerRef}
        className="flex-1 overflow-y-auto font-mono text-sm"
      >
        {filteredLogs.length === 0 ? (
          <div className="flex items-center justify-center h-full text-gray-500">
            <div className="text-center">
              <Terminal className="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p>Aucun log disponible</p>
            </div>
          </div>
        ) : (
          <div className="p-2 space-y-0.5">
            {filteredLogs.map((log, index) => (
              <LogLine key={log.id || index} log={log} />
            ))}
            <div ref={logsEndRef} />
          </div>
        )}
      </div>

      {/* Paused indicator */}
      {isPaused && (
        <div className="px-4 py-1 bg-yellow-600 text-white text-xs text-center">
          Logs en pause - Cliquez sur Play pour reprendre
        </div>
      )}
    </div>
  );
};

// =============================================================================
// Log Line Component
// =============================================================================

interface LogLineProps {
  log: ExecutionLog;
}

const LogLine: React.FC<LogLineProps> = ({ log }) => {
  const [expanded, setExpanded] = useState(false);
  const config = LOG_COLORS[log.level];
  const Icon = config.icon;

  const hasData = log.data && Object.keys(log.data).length > 0;

  return (
    <div
      className={`group flex items-start hover:bg-gray-800 rounded px-2 py-0.5 ${config.bg}`}
    >
      {/* Timestamp */}
      <span className="flex-shrink-0 text-gray-500 text-xs mr-2">
        {format(new Date(log.timestamp), 'HH:mm:ss.SSS')}
      </span>

      {/* Level icon */}
      <Icon className={`flex-shrink-0 w-4 h-4 mr-2 mt-0.5 ${config.text}`} />

      {/* Message */}
      <div className="flex-1 min-w-0">
        <span className={`${config.text}`}>{log.message}</span>

        {/* Source */}
        {log.source && (
          <span className="ml-2 text-xs text-gray-500">
            [{log.source}]
          </span>
        )}

        {/* Expandable data */}
        {hasData && (
          <button
            onClick={() => setExpanded(!expanded)}
            className="ml-2 text-xs text-blue-400 hover:text-blue-300"
          >
            <ChevronDown className={`inline w-3 h-3 transition-transform ${expanded ? 'rotate-180' : ''}`} />
            données
          </button>
        )}

        {expanded && hasData && (
          <pre className="mt-1 p-2 bg-gray-800 rounded text-xs text-gray-300 overflow-x-auto">
            {JSON.stringify(log.data, null, 2)}
          </pre>
        )}
      </div>
    </div>
  );
};

export default ExecutionLogs;
