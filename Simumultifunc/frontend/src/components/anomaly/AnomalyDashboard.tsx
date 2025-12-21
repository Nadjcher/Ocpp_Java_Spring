/**
 * AnomalyDashboard - Dashboard principal de détection d'anomalies
 */

import React, { useState } from 'react';
import {
  AnalysisResult,
  AnalysisStats,
  CATEGORY_ICONS,
  CATEGORY_LABELS,
  AnomalyCategory,
} from '@/types/anomaly.types';
import { HealthScoreCard } from './HealthScoreCard';
import { AnomalyList } from './AnomalyList';
import { InsightsPanel } from './InsightsPanel';

interface Props {
  result: AnalysisResult | null;
  enabled: boolean;
  onToggle: (enabled: boolean) => void;
  onResolve?: (id: string) => void;
  onRefresh?: () => void;
  onClear?: () => void;
  compact?: boolean;
}

export const AnomalyDashboard: React.FC<Props> = ({
  result,
  enabled,
  onToggle,
  onResolve,
  onRefresh,
  onClear,
  compact = false,
}) => {
  const [activeTab, setActiveTab] = useState<'anomalies' | 'insights' | 'stats'>(
    'anomalies'
  );

  if (compact) {
    return (
      <div className="bg-white rounded-lg border border-gray-200 p-3">
        {/* Compact header */}
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-700">
              Analyse ML
            </span>
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => onToggle(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-8 h-4 bg-gray-200 peer-focus:ring-2 peer-focus:ring-blue-300 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-3 after:w-3 after:transition-all peer-checked:bg-blue-500" />
            </label>
          </div>

          {result && (
            <HealthScoreCard
              score={result.healthScore}
              trend={result.stats.trendDirection}
              compact
            />
          )}
        </div>

        {/* Compact insights */}
        {enabled && result && result.insights.length > 0 && (
          <InsightsPanel insights={result.insights} compact />
        )}

        {/* Anomaly count */}
        {enabled && result && result.anomalies.length > 0 && (
          <div className="mt-2 text-xs text-gray-500">
            {result.anomalies.length} anomalie(s) récente(s)
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
      {/* Header */}
      <div className="bg-gradient-to-r from-indigo-500 to-purple-500 p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-2xl">🔬</span>
            <div>
              <h3 className="text-lg font-semibold text-white">
                Analyse ML d'anomalies
              </h3>
              <p className="text-xs text-indigo-100">
                Détection en temps réel des comportements anormaux
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Toggle */}
            <label className="relative inline-flex items-center cursor-pointer">
              <input
                type="checkbox"
                checked={enabled}
                onChange={(e) => onToggle(e.target.checked)}
                className="sr-only peer"
              />
              <div className="w-11 h-6 bg-white/30 peer-focus:ring-2 peer-focus:ring-white/50 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-white/50" />
              <span className="ml-2 text-sm text-white">
                {enabled ? 'Actif' : 'Inactif'}
              </span>
            </label>

            {/* Actions */}
            {onRefresh && (
              <button
                onClick={onRefresh}
                className="p-2 bg-white/20 hover:bg-white/30 rounded-lg text-white transition-colors"
                title="Rafraîchir"
              >
                ↻
              </button>
            )}
            {onClear && (
              <button
                onClick={onClear}
                className="p-2 bg-white/20 hover:bg-white/30 rounded-lg text-white transition-colors"
                title="Effacer l'historique"
              >
                🗑
              </button>
            )}
          </div>
        </div>
      </div>

      {!enabled ? (
        <div className="p-8 text-center text-gray-500">
          <span className="text-4xl">🔇</span>
          <p className="mt-2">Analyse désactivée</p>
          <p className="text-sm">Activez pour détecter les anomalies</p>
        </div>
      ) : !result ? (
        <div className="p-8 text-center text-gray-500">
          <span className="text-4xl animate-pulse">⏳</span>
          <p className="mt-2">En attente de données...</p>
        </div>
      ) : (
        <>
          {/* Health Score */}
          <div className="p-4 border-b border-gray-200">
            <HealthScoreCard
              score={result.healthScore}
              trend={result.stats.trendDirection}
            />
          </div>

          {/* Category summary */}
          <div className="px-4 py-3 border-b border-gray-200 bg-gray-50">
            <div className="flex gap-4 overflow-x-auto">
              {(Object.entries(result.stats.byCategory) as [AnomalyCategory, number][])
                .filter(([, count]) => count > 0)
                .map(([category, count]) => (
                  <div
                    key={category}
                    className="flex items-center gap-1 px-2 py-1 bg-white rounded-full border border-gray-200 text-xs whitespace-nowrap"
                  >
                    <span>{CATEGORY_ICONS[category]}</span>
                    <span className="font-medium">{count}</span>
                    <span className="text-gray-500">
                      {CATEGORY_LABELS[category]}
                    </span>
                  </div>
                ))}
              {Object.values(result.stats.byCategory).every((c) => c === 0) && (
                <span className="text-xs text-gray-500">
                  Aucune anomalie par catégorie
                </span>
              )}
            </div>
          </div>

          {/* Tabs */}
          <div className="flex border-b border-gray-200">
            <button
              onClick={() => setActiveTab('anomalies')}
              className={`flex-1 px-4 py-2 text-sm font-medium transition-colors ${
                activeTab === 'anomalies'
                  ? 'text-indigo-600 border-b-2 border-indigo-600 bg-indigo-50'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              Anomalies ({result.anomalies.length})
            </button>
            <button
              onClick={() => setActiveTab('insights')}
              className={`flex-1 px-4 py-2 text-sm font-medium transition-colors ${
                activeTab === 'insights'
                  ? 'text-indigo-600 border-b-2 border-indigo-600 bg-indigo-50'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              Insights ({result.insights.length})
            </button>
            <button
              onClick={() => setActiveTab('stats')}
              className={`flex-1 px-4 py-2 text-sm font-medium transition-colors ${
                activeTab === 'stats'
                  ? 'text-indigo-600 border-b-2 border-indigo-600 bg-indigo-50'
                  : 'text-gray-500 hover:text-gray-700'
              }`}
            >
              Statistiques
            </button>
          </div>

          {/* Tab content */}
          <div className="p-4">
            {activeTab === 'anomalies' && (
              <AnomalyList
                anomalies={result.anomalies}
                onResolve={onResolve}
                showFilters
              />
            )}

            {activeTab === 'insights' && (
              <InsightsPanel insights={result.insights} />
            )}

            {activeTab === 'stats' && (
              <StatsPanel stats={result.stats} />
            )}
          </div>
        </>
      )}
    </div>
  );
};

// Stats panel component
const StatsPanel: React.FC<{ stats: AnalysisStats }> = ({ stats }) => {
  return (
    <div className="space-y-4">
      {/* Overview */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatCard
          label="Total anomalies"
          value={stats.totalAnomalies}
          icon="📊"
        />
        <StatCard
          label="Score moyen"
          value={`${stats.averageHealthScore.toFixed(0)}%`}
          icon="📈"
        />
        <StatCard
          label="Tendance"
          value={
            stats.trendDirection === 'improving'
              ? 'En hausse'
              : stats.trendDirection === 'degrading'
              ? 'En baisse'
              : 'Stable'
          }
          icon={
            stats.trendDirection === 'improving'
              ? '↗️'
              : stats.trendDirection === 'degrading'
              ? '↘️'
              : '➡️'
          }
        />
        <StatCard
          label="Durée analyse"
          value={`${stats.analysisWindowMinutes.toFixed(0)} min`}
          icon="⏱️"
        />
      </div>

      {/* By severity */}
      <div>
        <h4 className="text-sm font-medium text-gray-700 mb-2">Par sévérité</h4>
        <div className="grid grid-cols-4 gap-2">
          <SeverityBar label="Critique" count={stats.bySeverity.critical} color="bg-red-500" />
          <SeverityBar label="Haute" count={stats.bySeverity.high} color="bg-orange-500" />
          <SeverityBar label="Moyenne" count={stats.bySeverity.medium} color="bg-yellow-500" />
          <SeverityBar label="Basse" count={stats.bySeverity.low} color="bg-blue-500" />
        </div>
      </div>

      {/* By category */}
      <div>
        <h4 className="text-sm font-medium text-gray-700 mb-2">Par catégorie</h4>
        <div className="space-y-2">
          {(Object.entries(stats.byCategory) as [AnomalyCategory, number][]).map(
            ([category, count]) => (
              <div key={category} className="flex items-center gap-2">
                <span className="w-6 text-center">
                  {CATEGORY_ICONS[category]}
                </span>
                <span className="text-sm text-gray-600 w-24">
                  {CATEGORY_LABELS[category]}
                </span>
                <div className="flex-1 h-4 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-indigo-500 transition-all"
                    style={{
                      width: `${
                        stats.totalAnomalies > 0
                          ? (count / stats.totalAnomalies) * 100
                          : 0
                      }%`,
                    }}
                  />
                </div>
                <span className="text-sm font-medium text-gray-700 w-8 text-right">
                  {count}
                </span>
              </div>
            )
          )}
        </div>
      </div>
    </div>
  );
};

// Stat card component
const StatCard: React.FC<{
  label: string;
  value: string | number;
  icon: string;
}> = ({ label, value, icon }) => (
  <div className="bg-gray-50 rounded-lg p-3 border border-gray-200">
    <div className="flex items-center gap-2 mb-1">
      <span className="text-lg">{icon}</span>
      <span className="text-xs text-gray-500">{label}</span>
    </div>
    <div className="text-xl font-bold text-gray-800">{value}</div>
  </div>
);

// Severity bar component
const SeverityBar: React.FC<{
  label: string;
  count: number;
  color: string;
}> = ({ label, count, color }) => (
  <div className="text-center">
    <div className={`h-2 ${color} rounded-full mb-1`} />
    <div className="text-lg font-bold text-gray-800">{count}</div>
    <div className="text-xs text-gray-500">{label}</div>
  </div>
);

export default AnomalyDashboard;
