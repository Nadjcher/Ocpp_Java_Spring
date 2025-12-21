/**
 * HealthScoreCard - Affichage du score de santé
 */

import React from 'react';

interface Props {
  score: number;
  trend?: 'improving' | 'stable' | 'degrading';
  compact?: boolean;
}

export const HealthScoreCard: React.FC<Props> = ({
  score,
  trend = 'stable',
  compact = false,
}) => {
  const getScoreColor = (s: number): string => {
    if (s >= 90) return 'text-green-600';
    if (s >= 70) return 'text-lime-600';
    if (s >= 50) return 'text-yellow-600';
    if (s >= 30) return 'text-orange-600';
    return 'text-red-600';
  };

  const getScoreBg = (s: number): string => {
    if (s >= 90) return 'bg-green-50 border-green-200';
    if (s >= 70) return 'bg-lime-50 border-lime-200';
    if (s >= 50) return 'bg-yellow-50 border-yellow-200';
    if (s >= 30) return 'bg-orange-50 border-orange-200';
    return 'bg-red-50 border-red-200';
  };

  const getScoreLabel = (s: number): string => {
    if (s >= 90) return 'Excellent';
    if (s >= 70) return 'Bon';
    if (s >= 50) return 'Moyen';
    if (s >= 30) return 'Faible';
    return 'Critique';
  };

  const getTrendIcon = (): string => {
    switch (trend) {
      case 'improving':
        return '↗';
      case 'degrading':
        return '↘';
      default:
        return '→';
    }
  };

  const getTrendColor = (): string => {
    switch (trend) {
      case 'improving':
        return 'text-green-500';
      case 'degrading':
        return 'text-red-500';
      default:
        return 'text-gray-400';
    }
  };

  if (compact) {
    return (
      <div
        className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border ${getScoreBg(score)}`}
      >
        <span className={`text-lg font-bold ${getScoreColor(score)}`}>
          {score.toFixed(0)}%
        </span>
        <span className={`text-sm ${getTrendColor()}`}>{getTrendIcon()}</span>
      </div>
    );
  }

  return (
    <div
      className={`rounded-lg border p-4 ${getScoreBg(score)}`}
    >
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-medium text-gray-600">Score de santé</span>
        <span className={`text-sm ${getTrendColor()}`}>
          {getTrendIcon()} {trend === 'improving' ? 'En hausse' : trend === 'degrading' ? 'En baisse' : 'Stable'}
        </span>
      </div>

      <div className="flex items-baseline gap-2">
        <span className={`text-4xl font-bold ${getScoreColor(score)}`}>
          {score.toFixed(0)}
        </span>
        <span className={`text-lg ${getScoreColor(score)}`}>%</span>
      </div>

      <div className="mt-2">
        <span className={`text-sm font-medium ${getScoreColor(score)}`}>
          {getScoreLabel(score)}
        </span>
      </div>

      {/* Progress bar */}
      <div className="mt-3 h-2 bg-gray-200 rounded-full overflow-hidden">
        <div
          className={`h-full transition-all duration-500 ${
            score >= 90
              ? 'bg-green-500'
              : score >= 70
              ? 'bg-lime-500'
              : score >= 50
              ? 'bg-yellow-500'
              : score >= 30
              ? 'bg-orange-500'
              : 'bg-red-500'
          }`}
          style={{ width: `${score}%` }}
        />
      </div>
    </div>
  );
};

export default HealthScoreCard;
