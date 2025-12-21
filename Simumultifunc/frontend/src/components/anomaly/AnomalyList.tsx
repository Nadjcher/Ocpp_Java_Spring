/**
 * AnomalyList - Liste des anomalies détectées
 */

import React, { useState } from 'react';
import {
  Anomaly,
  AnomalyCategory,
  AnomalySeverity,
  SEVERITY_COLORS,
  CATEGORY_ICONS,
  CATEGORY_LABELS,
} from '@/types/anomaly.types';

interface Props {
  anomalies: Anomaly[];
  onResolve?: (id: string) => void;
  maxItems?: number;
  showFilters?: boolean;
}

export const AnomalyList: React.FC<Props> = ({
  anomalies,
  onResolve,
  maxItems = 20,
  showFilters = true,
}) => {
  const [categoryFilter, setCategoryFilter] = useState<AnomalyCategory | 'all'>(
    'all'
  );
  const [severityFilter, setSeverityFilter] = useState<AnomalySeverity | 'all'>(
    'all'
  );
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // Filter anomalies
  const filteredAnomalies = anomalies
    .filter((a) => categoryFilter === 'all' || a.category === categoryFilter)
    .filter((a) => severityFilter === 'all' || a.severity === severityFilter)
    .filter((a) => !a.resolved)
    .slice(0, maxItems);

  const formatTime = (timestamp: number): string => {
    return new Date(timestamp).toLocaleTimeString('fr-FR', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  const getSeverityBadge = (severity: AnomalySeverity): string => {
    const colors = SEVERITY_COLORS[severity];
    return `px-2 py-0.5 text-xs font-medium rounded-full border ${colors}`;
  };

  if (anomalies.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500">
        <span className="text-4xl">✓</span>
        <p className="mt-2">Aucune anomalie détectée</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {/* Filters */}
      {showFilters && (
        <div className="flex flex-wrap gap-2 pb-3 border-b border-gray-200">
          {/* Category filter */}
          <select
            value={categoryFilter}
            onChange={(e) =>
              setCategoryFilter(e.target.value as AnomalyCategory | 'all')
            }
            className="text-xs px-2 py-1 border border-gray-300 rounded bg-white"
          >
            <option value="all">Toutes catégories</option>
            {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
              <option key={key} value={key}>
                {CATEGORY_ICONS[key as AnomalyCategory]} {label}
              </option>
            ))}
          </select>

          {/* Severity filter */}
          <select
            value={severityFilter}
            onChange={(e) =>
              setSeverityFilter(e.target.value as AnomalySeverity | 'all')
            }
            className="text-xs px-2 py-1 border border-gray-300 rounded bg-white"
          >
            <option value="all">Toutes sévérités</option>
            <option value="critical">Critique</option>
            <option value="high">Haute</option>
            <option value="medium">Moyenne</option>
            <option value="low">Basse</option>
          </select>

          {/* Count */}
          <span className="text-xs text-gray-500 ml-auto self-center">
            {filteredAnomalies.length} anomalie(s)
          </span>
        </div>
      )}

      {/* List */}
      <div className="space-y-2 max-h-96 overflow-y-auto">
        {filteredAnomalies.map((anomaly) => (
          <div
            key={anomaly.id}
            className={`border rounded-lg p-3 transition-all ${
              expandedId === anomaly.id
                ? 'bg-gray-50 border-gray-300'
                : 'bg-white border-gray-200 hover:border-gray-300'
            }`}
          >
            {/* Header */}
            <div
              className="flex items-start gap-2 cursor-pointer"
              onClick={() =>
                setExpandedId(expandedId === anomaly.id ? null : anomaly.id)
              }
            >
              {/* Category icon */}
              <span className="text-lg">
                {CATEGORY_ICONS[anomaly.category]}
              </span>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className={getSeverityBadge(anomaly.severity)}>
                    {anomaly.severity}
                  </span>
                  <span className="text-xs text-gray-500">
                    {formatTime(anomaly.timestamp)}
                  </span>
                </div>
                <p className="text-sm font-medium text-gray-800 mt-1">
                  {anomaly.message}
                </p>
              </div>

              {/* Expand indicator */}
              <span className="text-gray-400 text-sm">
                {expandedId === anomaly.id ? '▼' : '▶'}
              </span>
            </div>

            {/* Expanded details */}
            {expandedId === anomaly.id && (
              <div className="mt-3 pt-3 border-t border-gray-200 space-y-2">
                {/* Details */}
                {anomaly.details && (
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    {anomaly.details.expectedValue !== undefined && (
                      <>
                        <span className="text-gray-500">Valeur attendue:</span>
                        <span className="font-medium">
                          {anomaly.details.expectedValue.toFixed(1)}
                        </span>
                      </>
                    )}
                    {anomaly.details.actualValue !== undefined && (
                      <>
                        <span className="text-gray-500">Valeur actuelle:</span>
                        <span className="font-medium">
                          {anomaly.details.actualValue.toFixed(1)}
                        </span>
                      </>
                    )}
                    {anomaly.details.deviationPercent !== undefined && (
                      <>
                        <span className="text-gray-500">Écart:</span>
                        <span className="font-medium">
                          {anomaly.details.deviationPercent.toFixed(1)}%
                        </span>
                      </>
                    )}
                  </div>
                )}

                {/* Suggestion */}
                {anomaly.suggestion && (
                  <div className="bg-blue-50 border border-blue-200 rounded p-2">
                    <span className="text-xs text-blue-700">
                      💡 {anomaly.suggestion}
                    </span>
                  </div>
                )}

                {/* Resolve button */}
                {onResolve && (
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      onResolve(anomaly.id);
                    }}
                    className="text-xs px-3 py-1 bg-green-100 text-green-700 rounded hover:bg-green-200 transition-colors"
                  >
                    ✓ Marquer comme résolu
                  </button>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Show more indicator */}
      {anomalies.length > maxItems && (
        <div className="text-center text-xs text-gray-500">
          +{anomalies.length - maxItems} autres anomalies
        </div>
      )}
    </div>
  );
};

export default AnomalyList;
