// frontend/src/components/session/SessionLifecycleView.tsx
// Vue unifiee du cycle de vie d'une session de charge - Timeline/Journal
// Remplace les onglets multiples par une vue unique chronologique

import React, { useState, useMemo, useCallback, useRef, useEffect } from 'react';
import { useSession, useMultiSessionStore } from '@/store/multiSessionStore';
import type { SessionData, MultiSessionStatus } from '@/store/multiSessionStore';
import type {
  SessionLifecycleEvent,
  LifecycleEventType,
  EventSeverity,
  LifecycleFilters,
} from '@/types/lifecycle.types';
import {
  EVENT_TYPE_COLORS,
  SEVERITY_COLORS,
  SESSION_STATUS_INDICATOR,
  filterLifecycleEvents,
} from '@/types/lifecycle.types';

// ============================================================================
// Sub-components
// ============================================================================

/** Pastille de statut courant */
function StatusBadge({ status }: { status: MultiSessionStatus }) {
  const config = SESSION_STATUS_INDICATOR[status];
  return (
    <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-sm font-medium bg-gray-800 text-white">
      <span
        className={`w-2.5 h-2.5 rounded-full ${config.color} ${config.pulse ? 'animate-pulse' : ''}`}
      />
      {config.label}
    </span>
  );
}

/** Badge de type d'evenement */
function EventTypeBadge({ eventType }: { eventType: LifecycleEventType }) {
  const config = EVENT_TYPE_COLORS[eventType];
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${config.bg} ${config.text} border ${config.border}`}>
      {config.label}
    </span>
  );
}

/** Bloc JSON brut (expand/collapse) */
function JsonBlock({ data, label }: { data: unknown; label?: string }) {
  const [expanded, setExpanded] = useState(false);

  if (!data || (typeof data === 'object' && Object.keys(data as object).length === 0)) {
    return null;
  }

  return (
    <div className="mt-2">
      <button
        onClick={() => setExpanded(!expanded)}
        className="text-xs text-gray-500 hover:text-gray-700 flex items-center gap-1"
      >
        <svg
          className={`w-3 h-3 transition-transform ${expanded ? 'rotate-90' : ''}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
        {label || 'JSON brut'}
      </button>
      {expanded && (
        <pre className="mt-1 p-2 bg-gray-900 text-green-400 rounded text-xs overflow-x-auto max-h-60 overflow-y-auto font-mono">
          {JSON.stringify(data, null, 2)}
        </pre>
      )}
    </div>
  );
}

/** Un evenement dans la timeline */
function TimelineEvent({ event, isLast }: { event: SessionLifecycleEvent; isLast: boolean }) {
  const typeConfig = EVENT_TYPE_COLORS[event.eventType];
  const time = new Date(event.timestamp);
  const timeStr = time.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const dateStr = time.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });

  const isError = event.severity === 'error';
  const isSuccess = event.severity === 'success';

  return (
    <div className="flex gap-3 group">
      {/* Timeline dot + line */}
      <div className="flex flex-col items-center flex-shrink-0">
        <div
          className={`w-3 h-3 rounded-full border-2 mt-1.5 ${
            isError
              ? 'bg-red-500 border-red-300'
              : isSuccess
              ? 'bg-emerald-500 border-emerald-300'
              : `${typeConfig.dot} border-white`
          }`}
        />
        {!isLast && <div className="w-0.5 flex-1 bg-gray-200 min-h-[24px]" />}
      </div>

      {/* Event content */}
      <div className={`flex-1 pb-4 ${isLast ? '' : ''}`}>
        <div
          className={`rounded-lg border p-3 transition-colors ${
            isError
              ? 'border-red-200 bg-red-50'
              : isSuccess
              ? 'border-emerald-200 bg-emerald-50'
              : `${typeConfig.border} ${typeConfig.bg} group-hover:shadow-sm`
          }`}
        >
          {/* Header */}
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-2 flex-wrap">
              <EventTypeBadge eventType={event.eventType} />
              {event.ocppAction && (
                <span className="text-xs font-mono font-bold text-gray-800">
                  {event.ocppAction}
                </span>
              )}
            </div>
            <div className="flex items-center gap-2 text-xs text-gray-500 flex-shrink-0">
              <span>{dateStr}</span>
              <span className="font-mono">{timeStr}</span>
            </div>
          </div>

          {/* Summary */}
          <p className={`mt-1 text-sm ${isError ? 'text-red-700 font-medium' : 'text-gray-700'}`}>
            {event.summary}
          </p>

          {/* Details row */}
          <div className="flex flex-wrap gap-3 mt-2 text-xs text-gray-500">
            {event.ocppId && (
              <span className="flex items-center gap-1">
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2z" />
                </svg>
                {event.ocppId}
              </span>
            )}
            {event.connectorId !== undefined && (
              <span>Connecteur #{event.connectorId}</span>
            )}
            {event.transactionId && (
              <span className="font-mono">TX: {event.transactionId}</span>
            )}
            {event.soc !== undefined && (
              <span>SoC: {event.soc.toFixed(1)}%</span>
            )}
            {event.energy !== undefined && (
              <span>Energie: {event.energy.toFixed(2)} kWh</span>
            )}
            {event.power !== undefined && (
              <span>Puissance: {event.power.toFixed(1)} kW</span>
            )}
            {event.reason && (
              <span className={isError ? 'text-red-600 font-medium' : ''}>
                Raison: {event.reason}
              </span>
            )}
          </div>

          {/* Status change visualization */}
          {event.eventType === 'status_change' && event.previousStatus && event.newStatus && (
            <div className="flex items-center gap-2 mt-2">
              <span className={`px-2 py-0.5 rounded text-xs ${
                SESSION_STATUS_INDICATOR[event.previousStatus]?.color
              } text-white`}>
                {event.previousStatus}
              </span>
              <svg className="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7l5 5m0 0l-5 5m5-5H6" />
              </svg>
              <span className={`px-2 py-0.5 rounded text-xs ${
                SESSION_STATUS_INDICATOR[event.newStatus]?.color
              } text-white`}>
                {event.newStatus}
              </span>
            </div>
          )}

          {/* JSON payload */}
          {event.ocppPayload ? <JsonBlock data={event.ocppPayload} label="Payload OCPP" /> : null}
          {event.details && Object.keys(event.details).length > 0 ? (
            <JsonBlock data={event.details} label="Details" />
          ) : null}
        </div>
      </div>
    </div>
  );
}

/** Barre de filtres */
function FilterBar({
  filters,
  onFiltersChange,
  availableOcppIds,
}: {
  filters: LifecycleFilters;
  onFiltersChange: (filters: LifecycleFilters) => void;
  availableOcppIds: string[];
}) {
  const eventTypes: { value: LifecycleEventType; label: string }[] = [
    { value: 'status_change', label: 'Statut' },
    { value: 'ocpp_sent', label: 'OCPP Sent' },
    { value: 'ocpp_received', label: 'OCPP Recv' },
    { value: 'error', label: 'Erreurs' },
    { value: 'physical_action', label: 'Actions' },
    { value: 'config_change', label: 'Config' },
    { value: 'metric_update', label: 'Metriques' },
  ];

  const toggleEventType = (type: LifecycleEventType) => {
    const current = filters.eventTypes || [];
    const newTypes = current.includes(type)
      ? current.filter(t => t !== type)
      : [...current, type];
    onFiltersChange({ ...filters, eventTypes: newTypes.length > 0 ? newTypes : undefined });
  };

  return (
    <div className="space-y-3 p-3 bg-gray-50 rounded-lg border border-gray-200">
      {/* Search */}
      <div className="flex gap-2">
        <div className="relative flex-1">
          <svg
            className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          <input
            type="text"
            placeholder="Rechercher (action, message, OCPP ID...)"
            value={filters.searchText || ''}
            onChange={e => onFiltersChange({ ...filters, searchText: e.target.value || undefined })}
            className="w-full pl-9 pr-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>

        {/* OCPP ID filter */}
        {availableOcppIds.length > 1 && (
          <select
            value={filters.ocppId || ''}
            onChange={e => onFiltersChange({ ...filters, ocppId: e.target.value || undefined })}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Tous les OCPP ID</option>
            {availableOcppIds.map(id => (
              <option key={id} value={id}>{id}</option>
            ))}
          </select>
        )}
      </div>

      {/* Event type chips */}
      <div className="flex flex-wrap gap-1.5">
        {eventTypes.map(({ value, label }) => {
          const isActive = !filters.eventTypes || filters.eventTypes.includes(value);
          const config = EVENT_TYPE_COLORS[value];
          return (
            <button
              key={value}
              onClick={() => toggleEventType(value)}
              className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-all ${
                isActive
                  ? `${config.bg} ${config.text} ${config.border}`
                  : 'bg-gray-100 text-gray-400 border-gray-200'
              }`}
            >
              <span className={`inline-block w-1.5 h-1.5 rounded-full mr-1 ${isActive ? config.dot : 'bg-gray-300'}`} />
              {label}
            </button>
          );
        })}

        {/* Clear filters */}
        {(filters.searchText || filters.eventTypes || filters.ocppId) && (
          <button
            onClick={() => onFiltersChange({})}
            className="px-2.5 py-1 rounded-full text-xs font-medium text-red-600 border border-red-200 bg-red-50 hover:bg-red-100"
          >
            Effacer filtres
          </button>
        )}
      </div>
    </div>
  );
}

/** Panneau resume en haut */
function SessionSummaryHeader({ session }: { session: SessionData }) {
  const duration = session.chargingStartedAt
    ? Math.floor((Date.now() - session.chargingStartedAt) / 60000)
    : null;
  const connectedDuration = session.connectedAt
    ? Math.floor((Date.now() - session.connectedAt) / 60000)
    : null;

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-3">
          <h3 className="text-lg font-bold text-gray-900">
            {session.config.chargePointId}
          </h3>
          <StatusBadge status={session.state} />
        </div>
        <div className="text-sm text-gray-500">
          {session.config.evseType.replace('_', ' ')} | {session.config.maxPowerKw} kW max
        </div>
      </div>

      {/* Metriques en ligne */}
      <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3">
        <MetricCard label="SoC" value={`${session.metrics.soc.toFixed(1)}%`} sub={`Cible: ${session.metrics.socTarget}%`} />
        <MetricCard
          label="Puissance"
          value={`${session.metrics.activePowerKw.toFixed(1)} kW`}
          sub={`Offert: ${session.metrics.offeredPowerKw.toFixed(1)} kW`}
        />
        <MetricCard label="Energie" value={`${session.metrics.energyKwh.toFixed(2)} kWh`} />
        <MetricCard
          label="Duree charge"
          value={duration !== null ? `${duration} min` : '--'}
        />
        <MetricCard
          label="Connecte depuis"
          value={connectedDuration !== null ? `${connectedDuration} min` : '--'}
        />
        <MetricCard
          label="Transaction"
          value={session.transactionId || '--'}
          sub={session.authorized ? 'Autorise' : undefined}
        />
      </div>
    </div>
  );
}

function MetricCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="bg-gray-50 rounded-lg p-2.5 border border-gray-100">
      <div className="text-xs text-gray-500 mb-0.5">{label}</div>
      <div className="text-sm font-bold text-gray-900">{value}</div>
      {sub && <div className="text-xs text-gray-400">{sub}</div>}
    </div>
  );
}

// ============================================================================
// Main Component
// ============================================================================

interface SessionLifecycleViewProps {
  sessionId: string;
}

export function SessionLifecycleView({ sessionId }: SessionLifecycleViewProps) {
  const session = useSession(sessionId);
  const [filters, setFilters] = useState<LifecycleFilters>({});
  const [autoScroll, setAutoScroll] = useState(true);
  const timelineEndRef = useRef<HTMLDivElement>(null);
  const timelineContainerRef = useRef<HTMLDivElement>(null);

  // Get lifecycle events from the session
  const events = useMemo(() => {
    if (!session) return [];
    return session.lifecycleEvents || [];
  }, [session]);

  // Extract available OCPP IDs from events
  const availableOcppIds = useMemo(() => {
    const ids = new Set<string>();
    events.forEach(e => {
      if (e.ocppId) ids.add(e.ocppId);
    });
    return Array.from(ids).sort();
  }, [events]);

  // Apply filters
  const filteredEvents = useMemo(() => {
    return filterLifecycleEvents(events, filters);
  }, [events, filters]);

  // Auto-scroll to bottom when new events arrive
  useEffect(() => {
    if (autoScroll && timelineEndRef.current) {
      timelineEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [filteredEvents.length, autoScroll]);

  // Detect manual scrolling to disable auto-scroll
  const handleScroll = useCallback(() => {
    if (!timelineContainerRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = timelineContainerRef.current;
    const isAtBottom = scrollHeight - scrollTop - clientHeight < 50;
    setAutoScroll(isAtBottom);
  }, []);

  if (!session) {
    return (
      <div className="flex items-center justify-center h-full text-gray-500 p-8">
        <div className="text-center">
          <svg className="w-12 h-12 mx-auto mb-4 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
          </svg>
          <p className="text-lg">Session non trouvee</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Header: resume de la session */}
      <div className="flex-shrink-0 p-4 border-b border-gray-200">
        <SessionSummaryHeader session={session} />
      </div>

      {/* Filtres */}
      <div className="flex-shrink-0 px-4 pt-3">
        <FilterBar
          filters={filters}
          onFiltersChange={setFilters}
          availableOcppIds={availableOcppIds}
        />
      </div>

      {/* Stats bar */}
      <div className="flex-shrink-0 px-4 py-2 flex items-center justify-between text-xs text-gray-500 border-b border-gray-100">
        <span>
          {filteredEvents.length} evenement{filteredEvents.length !== 1 ? 's' : ''}
          {events.length !== filteredEvents.length && ` (${events.length} total)`}
        </span>
        <button
          onClick={() => setAutoScroll(!autoScroll)}
          className={`flex items-center gap-1 px-2 py-1 rounded ${
            autoScroll ? 'bg-blue-50 text-blue-600' : 'bg-gray-100 text-gray-500'
          }`}
        >
          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 14l-7 7m0 0l-7-7m7 7V3" />
          </svg>
          Auto-scroll
        </button>
      </div>

      {/* Timeline */}
      <div
        ref={timelineContainerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto px-4 py-3"
      >
        {filteredEvents.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-gray-400">
            <svg className="w-16 h-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p className="text-lg mb-1">Aucun evenement</p>
            <p className="text-sm">
              {events.length > 0
                ? 'Modifiez les filtres pour voir des evenements'
                : 'Les evenements apparaitront ici au fur et a mesure de la session'}
            </p>
          </div>
        ) : (
          <div className="max-w-3xl mx-auto">
            {filteredEvents.map((event, index) => (
              <TimelineEvent
                key={event.id}
                event={event}
                isLast={index === filteredEvents.length - 1}
              />
            ))}
          </div>
        )}
        <div ref={timelineEndRef} />
      </div>
    </div>
  );
}

export default SessionLifecycleView;
