/**
 * Hook React pour l'API TTE (Token Cognito)
 *
 * Fonctionnalités:
 * - Gestion du statut du token
 * - Appels API pricing et smart charging
 * - Refresh automatique du statut
 */

import { useState, useEffect, useCallback } from 'react';
import { apiClient } from '@/lib/apiClient';

// =============================================================================
// Types
// =============================================================================

export interface TokenInfo {
  configured: boolean;
  enabled: boolean;
  hasValidToken: boolean;
  tokenObtainedAt: string | null;
  tokenExpiresAt: string | null;
  secondsRemaining: number;
  refreshCount: number;
  errorCount: number;
  lastError: string | null;
  status: 'VALID' | 'EXPIRING_SOON' | 'EXPIRED' | 'NOT_CONFIGURED' | 'DISABLED';
  remainingFormatted: string;
}

export interface PricingData {
  sessionId: string;
  chargePointId: string;
  transactionId?: number;
  totalPrice: number;
  currency: string;
  pricePerKwh: number;
  energyDelivered: number;
  durationSeconds: number;
  pricePerMinute?: number;
  fixedFee?: number;
  calculatedAt: string;
}

export interface ChargingProfileRequest {
  chargePointId: string;
  connectorId?: number;
  chargingProfileId?: number;
  transactionId?: number;
  stackLevel?: number;
  chargingProfilePurpose?: string;
  chargingProfileKind?: string;
  chargingSchedule?: {
    duration?: number;
    chargingRateUnit?: 'W' | 'A';
    chargingSchedulePeriod?: Array<{
      startPeriod: number;
      limit: number;
      numberPhases?: number;
    }>;
  };
}

export interface ChargingProfileResponse {
  chargingProfileId: number;
  chargePointId: string;
  connectorId: number;
  status: string;
  statusMessage?: string;
  chargingProfilePurpose: string;
  stackLevel: number;
  currentLimit?: number;
  limitUnit?: string;
}

export interface TTEHealthInfo {
  available: boolean;
  tokenInfo: TokenInfo;
  apiBaseUrl: string;
}

// =============================================================================
// Hook
// =============================================================================

export function useTTEApi(autoRefreshInterval = 60000) {
  const [tokenInfo, setTokenInfo] = useState<TokenInfo | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ---------------------------------------------------------------------------
  // Token Management
  // ---------------------------------------------------------------------------

  const fetchTokenStatus = useCallback(async (): Promise<TokenInfo | null> => {
    try {
      const response = await apiClient.get<TokenInfo>('/api/tte/token/status');
      setTokenInfo(response.data);
      setError(null);
      return response.data;
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to fetch token status';
      setError(message);
      return null;
    }
  }, []);

  const refreshToken = useCallback(async (): Promise<boolean> => {
    setLoading(true);
    try {
      const response = await apiClient.post<TokenInfo>('/api/tte/token/refresh');
      setTokenInfo(response.data);
      setError(null);
      return true;
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to refresh token';
      setError(message);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  // ---------------------------------------------------------------------------
  // Health
  // ---------------------------------------------------------------------------

  const getHealth = useCallback(async (): Promise<TTEHealthInfo | null> => {
    try {
      const response = await apiClient.get<TTEHealthInfo>('/api/tte/health');
      return response.data;
    } catch (err: any) {
      console.error('Failed to get TTE health:', err);
      return null;
    }
  }, []);

  // ---------------------------------------------------------------------------
  // Pricing API
  // ---------------------------------------------------------------------------

  /**
   * Récupère la tarification par OCPP ID et transaction ID
   */
  const getTransactionPricing = useCallback(async (
    ocppId: string,
    transactionId: number
  ): Promise<PricingData | null> => {
    setLoading(true);
    try {
      const response = await apiClient.get<PricingData>(
        `/api/tte/pricing/transaction/${ocppId}/${transactionId}`
      );
      return response.data;
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to get pricing';
      setError(message);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * Récupère la tarification par session ID (interne)
   */
  const getSessionPricing = useCallback(async (sessionId: string): Promise<PricingData | null> => {
    setLoading(true);
    try {
      const response = await apiClient.get<PricingData>(`/api/tte/pricing/session/${sessionId}`);
      return response.data;
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to get pricing';
      setError(message);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  // ---------------------------------------------------------------------------
  // Smart Charging API
  // ---------------------------------------------------------------------------

  const sendChargingProfile = useCallback(async (
    request: ChargingProfileRequest
  ): Promise<ChargingProfileResponse | null> => {
    setLoading(true);
    try {
      const response = await apiClient.post<ChargingProfileResponse>(
        '/api/tte/smart-charging/profile',
        request
      );
      return response.data;
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to send profile';
      setError(message);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const getActiveProfiles = useCallback(async (cpId: string): Promise<ChargingProfileResponse[]> => {
    setLoading(true);
    try {
      const response = await apiClient.get<ChargingProfileResponse[]>(
        `/api/tte/smart-charging/profiles/${cpId}`
      );
      return response.data || [];
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to get profiles';
      setError(message);
      return [];
    } finally {
      setLoading(false);
    }
  }, []);

  const clearChargingProfile = useCallback(async (
    cpId: string,
    profileId: string
  ): Promise<boolean> => {
    setLoading(true);
    try {
      await apiClient.delete(`/api/tte/smart-charging/profile/${cpId}/${profileId}`);
      return true;
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to clear profile';
      setError(message);
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  const setQuickLimit = useCallback(async (
    cpId: string,
    limitWatts: number,
    connectorId = 1
  ): Promise<ChargingProfileResponse | null> => {
    setLoading(true);
    try {
      const response = await apiClient.post<ChargingProfileResponse>(
        `/api/tte/smart-charging/set-limit/${cpId}?limitWatts=${limitWatts}&connectorId=${connectorId}`
      );
      return response.data;
    } catch (err: any) {
      const message = err?.response?.data?.message || err?.message || 'Failed to set limit';
      setError(message);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  // ---------------------------------------------------------------------------
  // Auto-refresh
  // ---------------------------------------------------------------------------

  useEffect(() => {
    // Initial fetch
    fetchTokenStatus();

    // Set up auto-refresh
    if (autoRefreshInterval > 0) {
      const interval = setInterval(fetchTokenStatus, autoRefreshInterval);
      return () => clearInterval(interval);
    }
  }, [fetchTokenStatus, autoRefreshInterval]);

  // ---------------------------------------------------------------------------
  // Return
  // ---------------------------------------------------------------------------

  return {
    // Token
    tokenInfo,
    loading,
    error,
    fetchTokenStatus,
    refreshToken,

    // Health
    getHealth,

    // Pricing
    getTransactionPricing,
    getSessionPricing,

    // Smart Charging
    sendChargingProfile,
    getActiveProfiles,
    clearChargingProfile,
    setQuickLimit,

    // Computed
    isConfigured: tokenInfo?.configured ?? false,
    isEnabled: tokenInfo?.enabled ?? false,
    hasValidToken: tokenInfo?.hasValidToken ?? false,
    tokenStatus: tokenInfo?.status ?? 'NOT_CONFIGURED',
  };
}

export default useTTEApi;
