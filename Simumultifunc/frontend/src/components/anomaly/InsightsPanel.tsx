/**
 * InsightsPanel - Panneau d'insights et recommandations
 */

import React from 'react';
import { Insight } from '@/types/anomaly.types';

interface Props {
  insights: Insight[];
  compact?: boolean;
}

export const InsightsPanel: React.FC<Props> = ({
  insights,
  compact = false,
}) => {
  const getInsightStyle = (
    type: Insight['type']
  ): { bg: string; border: string; icon: string; text: string } => {
    switch (type) {
      case 'info':
        return {
          bg: 'bg-blue-50',
          border: 'border-blue-200',
          icon: 'ℹ️',
          text: 'text-blue-800',
        };
      case 'warning':
        return {
          bg: 'bg-yellow-50',
          border: 'border-yellow-200',
          icon: '⚠️',
          text: 'text-yellow-800',
        };
      case 'recommendation':
        return {
          bg: 'bg-purple-50',
          border: 'border-purple-200',
          icon: '💡',
          text: 'text-purple-800',
        };
      case 'alert':
        return {
          bg: 'bg-red-50',
          border: 'border-red-200',
          icon: '🚨',
          text: 'text-red-800',
        };
      default:
        return {
          bg: 'bg-gray-50',
          border: 'border-gray-200',
          icon: '📝',
          text: 'text-gray-800',
        };
    }
  };

  if (insights.length === 0) {
    return (
      <div className="text-center py-4 text-gray-500 text-sm">
        Aucun insight disponible
      </div>
    );
  }

  // Sort by priority: alert > warning > recommendation > info
  const sortedInsights = [...insights].sort((a, b) => {
    const priority = { alert: 0, warning: 1, recommendation: 2, info: 3 };
    return priority[a.type] - priority[b.type];
  });

  if (compact) {
    return (
      <div className="space-y-1">
        {sortedInsights.slice(0, 3).map((insight) => {
          const style = getInsightStyle(insight.type);
          return (
            <div
              key={insight.id}
              className={`flex items-center gap-2 px-2 py-1 rounded ${style.bg} border ${style.border}`}
            >
              <span className="text-sm">{style.icon}</span>
              <span className={`text-xs ${style.text} truncate`}>
                {insight.title}
              </span>
            </div>
          );
        })}
        {insights.length > 3 && (
          <div className="text-xs text-gray-500 text-center">
            +{insights.length - 3} autres
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {sortedInsights.map((insight) => {
        const style = getInsightStyle(insight.type);
        return (
          <div
            key={insight.id}
            className={`rounded-lg border p-3 ${style.bg} ${style.border}`}
          >
            <div className="flex items-start gap-2">
              <span className="text-lg">{style.icon}</span>
              <div className="flex-1">
                <h4 className={`font-medium ${style.text}`}>{insight.title}</h4>
                <p className={`text-sm mt-1 ${style.text} opacity-80`}>
                  {insight.description}
                </p>

                {insight.actionable && insight.action && (
                  <div className="mt-2">
                    <button
                      className={`text-xs px-3 py-1 rounded-full border ${style.border} ${style.text} hover:opacity-80 transition-opacity`}
                    >
                      → {insight.action}
                    </button>
                  </div>
                )}

                {insight.relatedAnomalies &&
                  insight.relatedAnomalies.length > 0 && (
                    <div className="mt-2 text-xs opacity-60">
                      {insight.relatedAnomalies.length} anomalie(s) liée(s)
                    </div>
                  )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default InsightsPanel;
