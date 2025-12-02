// frontend/src/components/ChargingProfileIndicator.tsx
// Indicateur compact de la limite SCP active pour les sessions

import React, { useEffect, useState } from 'react';
import {
    useChargingProfileStore,
    formatLimit,
    formatTimeUntilNextPeriod,
    getPurposeColor,
    type EffectiveLimit
} from '@/store/chargingProfileStore';

interface ChargingProfileIndicatorProps {
    sessionId: string;
    showDetails?: boolean;
    compact?: boolean;
    onViewProfile?: () => void;
}

export function ChargingProfileIndicator({
    sessionId,
    showDetails = false,
    compact = false,
    onViewProfile
}: ChargingProfileIndicatorProps) {
    const effectiveLimit = useChargingProfileStore(state => state.getEffectiveLimit(sessionId));
    const profiles = useChargingProfileStore(state => state.getProfiles(sessionId));
    const [countdown, setCountdown] = useState<number | null>(null);

    // Mettre à jour le countdown
    useEffect(() => {
        if (!effectiveLimit?.nextPeriod?.secondsUntilStart) {
            setCountdown(null);
            return;
        }

        setCountdown(effectiveLimit.nextPeriod.secondsUntilStart);

        const interval = setInterval(() => {
            setCountdown(prev => {
                if (prev === null || prev <= 0) return null;
                return prev - 1;
            });
        }, 1000);

        return () => clearInterval(interval);
    }, [effectiveLimit?.nextPeriod?.secondsUntilStart]);

    // Pas de limite active
    if (!effectiveLimit || effectiveLimit.limitW === Infinity || effectiveLimit.limitW === 0) {
        if (compact) {
            return (
                <span className="text-xs text-gray-400">-</span>
            );
        }
        return (
            <div className="flex items-center gap-2 text-sm text-gray-500">
                <span className="w-2 h-2 rounded-full bg-gray-300" />
                <span>Aucune limite SCP</span>
            </div>
        );
    }

    // Version compacte
    if (compact) {
        return (
            <div className="flex items-center gap-1.5">
                <span className={`w-2 h-2 rounded-full ${getStatusColor(effectiveLimit.source)}`} />
                <span className="text-sm font-medium text-gray-900">
                    {effectiveLimit.limitKw.toFixed(1)} kW
                </span>
                {effectiveLimit.source && (
                    <span className={`text-xs px-1.5 py-0.5 rounded ${getPurposeColor(effectiveLimit.source)}`}>
                        {getShortPurpose(effectiveLimit.source)}
                    </span>
                )}
            </div>
        );
    }

    // Version détaillée
    return (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3">
            <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                    <span className={`w-3 h-3 rounded-full ${getStatusColor(effectiveLimit.source)} animate-pulse`} />
                    <span className="text-sm font-semibold text-emerald-800">
                        Limite SCP Active
                    </span>
                </div>
                {onViewProfile && (
                    <button
                        onClick={onViewProfile}
                        className="text-xs text-emerald-600 hover:text-emerald-800 underline"
                    >
                        Voir profil
                    </button>
                )}
            </div>

            <div className="grid grid-cols-2 gap-3">
                {/* Limite actuelle */}
                <div>
                    <div className="text-xs text-gray-500 mb-0.5">Limite actuelle</div>
                    <div className="text-xl font-bold text-gray-900">
                        {effectiveLimit.limitKw.toFixed(1)} <span className="text-sm font-normal">kW</span>
                    </div>
                    <div className="text-xs text-gray-500">
                        {effectiveLimit.limitA.toFixed(1)} A
                    </div>
                </div>

                {/* Source */}
                <div>
                    <div className="text-xs text-gray-500 mb-0.5">Source</div>
                    <span className={`inline-block px-2 py-1 rounded text-xs font-medium ${getPurposeColor(effectiveLimit.source)}`}>
                        {effectiveLimit.source || 'N/A'}
                    </span>
                    <div className="text-xs text-gray-500 mt-1">
                        Profil #{effectiveLimit.profileId} (Stack: {effectiveLimit.stackLevel})
                    </div>
                </div>
            </div>

            {/* Prochaine période */}
            {effectiveLimit.nextPeriod && countdown !== null && countdown > 0 && (
                <div className="mt-3 pt-2 border-t border-emerald-200">
                    <div className="flex items-center justify-between">
                        <div className="text-xs text-gray-500">
                            Prochaine période dans:
                        </div>
                        <div className="text-sm font-mono font-medium text-amber-600">
                            {formatTimeUntilNextPeriod(countdown)}
                        </div>
                    </div>
                    <div className="text-xs text-gray-500 mt-1">
                        Nouvelle limite: {(effectiveLimit.nextPeriod.limit / 1000).toFixed(1)} kW
                    </div>
                </div>
            )}

            {/* Liste des profils actifs */}
            {showDetails && profiles.length > 0 && (
                <div className="mt-3 pt-2 border-t border-emerald-200">
                    <div className="text-xs text-gray-500 mb-1">Profils actifs ({profiles.length})</div>
                    <div className="space-y-1">
                        {profiles.map(p => (
                            <div
                                key={p.chargingProfileId}
                                className="flex items-center justify-between text-xs"
                            >
                                <span className={`px-1.5 py-0.5 rounded ${getPurposeColor(p.chargingProfilePurpose)}`}>
                                    {getShortPurpose(p.chargingProfilePurpose)}
                                </span>
                                <span className="text-gray-600">
                                    #{p.chargingProfileId} - Stack {p.stackLevel}
                                </span>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * Obtient la couleur de statut selon le purpose.
 */
function getStatusColor(purpose: string | null): string {
    switch (purpose) {
        case 'ChargePointMaxProfile':
            return 'bg-red-500';
        case 'TxDefaultProfile':
            return 'bg-orange-500';
        case 'TxProfile':
            return 'bg-blue-500';
        default:
            return 'bg-emerald-500';
    }
}

/**
 * Raccourcit le nom du purpose.
 */
function getShortPurpose(purpose: string | null): string {
    switch (purpose) {
        case 'ChargePointMaxProfile':
            return 'CPMax';
        case 'TxDefaultProfile':
            return 'TxDef';
        case 'TxProfile':
            return 'Tx';
        default:
            return purpose || 'N/A';
    }
}

export default ChargingProfileIndicator;
