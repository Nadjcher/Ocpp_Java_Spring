/**
 * TokenStatusIndicator - Affiche le statut du token Cognito TTE
 *
 * Indicateurs visuels:
 * - Vert: Token valide
 * - Jaune: Expire bientôt (< 5min)
 * - Rouge: Expiré ou erreur
 * - Gris: Non configuré
 */

import React from 'react';
import { useTTEApi, type TokenInfo } from '@/hooks/useTTEApi';

interface TokenStatusIndicatorProps {
  /** Afficher le bouton de refresh */
  showRefreshButton?: boolean;
  /** Afficher les détails */
  showDetails?: boolean;
  /** Mode compact (juste l'indicateur) */
  compact?: boolean;
  /** Classe CSS additionnelle */
  className?: string;
}

export function TokenStatusIndicator({
  showRefreshButton = true,
  showDetails = false,
  compact = false,
  className = '',
}: TokenStatusIndicatorProps) {
  const {
    tokenInfo,
    loading,
    error,
    refreshToken,
    isConfigured,
    isEnabled,
    hasValidToken,
    tokenStatus,
  } = useTTEApi();

  // Couleurs selon le statut
  const getStatusColor = () => {
    if (!isEnabled) return 'bg-gray-400';
    if (!isConfigured) return 'bg-gray-400';
    if (!hasValidToken) return 'bg-red-500';
    if (tokenInfo && tokenInfo.secondsRemaining < 300) return 'bg-yellow-500';
    return 'bg-green-500';
  };

  const getStatusIcon = () => {
    if (!isEnabled) return '[PAUSE]';
    if (!isConfigured) return '[CONF]';
    if (!hasValidToken) return '[ERR]';
    if (tokenInfo && tokenInfo.secondsRemaining < 300) return '[WARN]';
    return '[OK]';
  };

  const getStatusText = () => {
    if (!isEnabled) return 'TTE désactivé';
    if (!isConfigured) return 'Non configuré';
    if (!hasValidToken) return 'Token expiré';
    if (tokenInfo && tokenInfo.secondsRemaining < 300) {
      return `Expire bientôt (${tokenInfo.remainingFormatted})`;
    }
    return `Token valide (${tokenInfo?.remainingFormatted || ''})`;
  };

  // Mode compact - juste l'indicateur
  if (compact) {
    return (
      <div
        className={`inline-flex items-center gap-1 ${className}`}
        title={getStatusText()}
      >
        <span className={`w-2 h-2 rounded-full ${getStatusColor()}`} />
        <span className="text-xs text-gray-600">{getStatusIcon()}</span>
      </div>
    );
  }

  return (
    <div className={`inline-flex items-center gap-2 ${className}`}>
      {/* Indicateur LED */}
      <div className="flex items-center gap-2">
        <span
          className={`w-3 h-3 rounded-full ${getStatusColor()} ${
            loading ? 'animate-pulse' : ''
          }`}
        />
        <span className="text-sm font-medium">{getStatusText()}</span>
      </div>

      {/* Bouton refresh */}
      {showRefreshButton && isConfigured && (
        <button
          onClick={refreshToken}
          disabled={loading}
          className={`px-2 py-1 text-xs rounded border ${
            loading
              ? 'bg-gray-100 text-gray-400 cursor-not-allowed'
              : 'bg-white hover:bg-gray-50 text-gray-700 border-gray-300'
          }`}
          title="Rafraîchir le token"
        >
          {loading ? '...' : '↻'}
        </button>
      )}

      {/* Erreur */}
      {error && (
        <span className="text-xs text-red-600" title={error}>
          ⚠
        </span>
      )}

      {/* Détails */}
      {showDetails && tokenInfo && (
        <div className="text-xs text-gray-500 ml-2">
          <span>Refresh: {tokenInfo.refreshCount}</span>
          {tokenInfo.errorCount > 0 && (
            <span className="text-red-500 ml-2">
              Erreurs: {tokenInfo.errorCount}
            </span>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Version panneau avec plus de détails
 */
export function TokenStatusPanel({ className = '' }: { className?: string }) {
  const {
    tokenInfo,
    loading,
    error,
    refreshToken,
    isConfigured,
    isEnabled,
    hasValidToken,
  } = useTTEApi();

  return (
    <div className={`rounded border bg-white p-4 shadow-sm ${className}`}>
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold">Token TTE Cognito</h3>
        <TokenStatusIndicator compact showRefreshButton={false} />
      </div>

      <div className="space-y-2 text-sm">
        <div className="flex justify-between">
          <span className="text-gray-600">Statut:</span>
          <span className={`font-medium ${hasValidToken ? 'text-green-600' : 'text-red-600'}`}>
            {tokenInfo?.status || 'N/A'}
          </span>
        </div>

        {tokenInfo?.secondsRemaining !== undefined && (
          <div className="flex justify-between">
            <span className="text-gray-600">Expire dans:</span>
            <span>{tokenInfo.remainingFormatted}</span>
          </div>
        )}

        {tokenInfo?.refreshCount !== undefined && (
          <div className="flex justify-between">
            <span className="text-gray-600">Renouvellements:</span>
            <span>{tokenInfo.refreshCount}</span>
          </div>
        )}

        {tokenInfo?.errorCount !== undefined && tokenInfo.errorCount > 0 && (
          <div className="flex justify-between">
            <span className="text-gray-600">Erreurs:</span>
            <span className="text-red-600">{tokenInfo.errorCount}</span>
          </div>
        )}

        {tokenInfo?.lastError && (
          <div className="mt-2 p-2 bg-red-50 rounded text-xs text-red-700">
            {tokenInfo.lastError}
          </div>
        )}

        {error && (
          <div className="mt-2 p-2 bg-red-50 rounded text-xs text-red-700">
            {error}
          </div>
        )}
      </div>

      {isConfigured && (
        <button
          onClick={refreshToken}
          disabled={loading}
          className={`mt-4 w-full py-2 rounded text-sm font-medium ${
            loading
              ? 'bg-gray-200 text-gray-500 cursor-not-allowed'
              : 'bg-blue-600 text-white hover:bg-blue-700'
          }`}
        >
          {loading ? 'Rafraîchissement...' : 'Rafraîchir le token'}
        </button>
      )}

      {!isEnabled && (
        <div className="mt-4 p-2 bg-gray-100 rounded text-xs text-gray-600 text-center">
          L'intégration TTE est désactivée
        </div>
      )}

      {isEnabled && !isConfigured && (
        <div className="mt-4 p-2 bg-yellow-50 rounded text-xs text-yellow-700 text-center">
          Configurez TTE_CLIENT_ID et TTE_CLIENT_SECRET
        </div>
      )}
    </div>
  );
}

export default TokenStatusIndicator;
