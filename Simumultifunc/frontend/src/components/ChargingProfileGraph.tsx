// frontend/src/components/ChargingProfileGraph.tsx
// Graphique Recharts pour visualiser les profils de charge OCPP 1.6

import React, { useMemo } from 'react';
import {
    AreaChart,
    Area,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    ReferenceLine,
    Legend
} from 'recharts';
import {
    useChargingProfileStore,
    type ChargingProfile,
    type EffectiveLimit,
    getPurposeColor
} from '@/store/chargingProfileStore';
import { useShallow } from 'zustand/react/shallow';

interface ChargingProfileGraphProps {
    sessionId: string;
    durationMinutes?: number;
    showLegend?: boolean;
    height?: number;
}

interface DataPoint {
    time: number;  // minutes depuis maintenant
    timeLabel: string;
    chargePointMax?: number;
    txDefault?: number;
    txProfile?: number;
    effective: number;
    current?: number;
}

export function ChargingProfileGraph({
    sessionId,
    durationMinutes = 60,
    showLegend = true,
    height = 200
}: ChargingProfileGraphProps) {
    // Access Maps directly with shallow comparison to avoid infinite loops
    const { profilesMap, effectiveLimitsMap, convertToWatts } = useChargingProfileStore(
        useShallow((state) => ({
            profilesMap: state.profiles,
            effectiveLimitsMap: state.effectiveLimits,
            convertToWatts: state.convertToWatts
        }))
    );

    // Derive data using useMemo
    const profiles = useMemo(() => {
        return profilesMap.get(sessionId) || [];
    }, [profilesMap, sessionId]);

    const effectiveLimit = useMemo(() => {
        return effectiveLimitsMap.get(sessionId) || null;
    }, [effectiveLimitsMap, sessionId]);

    // Construire les données du graphique
    const data = useMemo(() => {
        const points: DataPoint[] = [];
        const now = Date.now();
        const stepMinutes = Math.max(1, Math.floor(durationMinutes / 60));

        for (let minute = 0; minute <= durationMinutes; minute += stepMinutes) {
            const pointTime = now + minute * 60 * 1000;
            const point: DataPoint = {
                time: minute,
                timeLabel: minute === 0 ? 'Maintenant' : `+${minute}min`,
                effective: Infinity
            };

            // Calculer la limite pour chaque type de profil à ce moment
            for (const profile of profiles) {
                const limit = calculateLimitAtTime(profile, pointTime, convertToWatts);
                const limitKw = limit / 1000;

                switch (profile.chargingProfilePurpose) {
                    case 'ChargePointMaxProfile':
                        point.chargePointMax = limitKw;
                        break;
                    case 'TxDefaultProfile':
                        point.txDefault = limitKw;
                        break;
                    case 'TxProfile':
                        point.txProfile = limitKw;
                        break;
                }

                if (limitKw < point.effective) {
                    point.effective = limitKw;
                }
            }

            // Si aucun profil actif, mettre une valeur par défaut
            if (point.effective === Infinity) {
                point.effective = 0;
            }

            // Marquer le point actuel
            if (minute === 0 && effectiveLimit) {
                point.current = effectiveLimit.limitKw;
            }

            points.push(point);
        }

        return points;
    }, [profiles, effectiveLimit, durationMinutes, convertToWatts]);

    // Déterminer le max Y pour le graphique
    const maxY = useMemo(() => {
        let max = 0;
        for (const point of data) {
            if (point.chargePointMax && point.chargePointMax > max) max = point.chargePointMax;
            if (point.txDefault && point.txDefault > max) max = point.txDefault;
            if (point.txProfile && point.txProfile > max) max = point.txProfile;
            if (point.effective > max) max = point.effective;
        }
        return Math.ceil(max * 1.1) || 22; // Défaut 22 kW si pas de données
    }, [data]);

    // Tooltip personnalisé
    const CustomTooltip = ({ active, payload, label }: any) => {
        if (!active || !payload?.length) return null;

        return (
            <div className="bg-white border border-gray-200 shadow-lg rounded-lg p-3 text-sm">
                <p className="font-semibold text-gray-700 mb-2">{label}</p>
                {payload.map((entry: any, index: number) => (
                    <p key={index} style={{ color: entry.color }} className="flex justify-between gap-4">
                        <span>{entry.name}:</span>
                        <span className="font-mono">{entry.value?.toFixed(1) || '-'} kW</span>
                    </p>
                ))}
            </div>
        );
    };

    if (profiles.length === 0) {
        return (
            <div className="flex items-center justify-center h-32 bg-gray-50 rounded-lg border border-dashed border-gray-300">
                <p className="text-gray-500 text-sm">Aucun profil de charge actif</p>
            </div>
        );
    }

    return (
        <div className="w-full" style={{ height }}>
            <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={data} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                    <defs>
                        <linearGradient id="colorChargePointMax" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#ef4444" stopOpacity={0.3} />
                            <stop offset="95%" stopColor="#ef4444" stopOpacity={0} />
                        </linearGradient>
                        <linearGradient id="colorTxDefault" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#f97316" stopOpacity={0.3} />
                            <stop offset="95%" stopColor="#f97316" stopOpacity={0} />
                        </linearGradient>
                        <linearGradient id="colorTxProfile" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                            <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                        </linearGradient>
                        <linearGradient id="colorEffective" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#10b981" stopOpacity={0.5} />
                            <stop offset="95%" stopColor="#10b981" stopOpacity={0.1} />
                        </linearGradient>
                    </defs>

                    <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />

                    <XAxis
                        dataKey="timeLabel"
                        tick={{ fontSize: 10, fill: '#6b7280' }}
                        tickLine={false}
                        axisLine={{ stroke: '#d1d5db' }}
                    />

                    <YAxis
                        domain={[0, maxY]}
                        tick={{ fontSize: 10, fill: '#6b7280' }}
                        tickLine={false}
                        axisLine={{ stroke: '#d1d5db' }}
                        tickFormatter={(value) => `${value}kW`}
                        width={45}
                    />

                    <Tooltip content={<CustomTooltip />} />

                    {showLegend && (
                        <Legend
                            verticalAlign="top"
                            height={30}
                            iconType="square"
                            iconSize={10}
                            formatter={(value) => <span className="text-xs text-gray-600">{value}</span>}
                        />
                    )}

                    {/* Ligne de référence pour la limite actuelle */}
                    {effectiveLimit && effectiveLimit.limitKw > 0 && (
                        <ReferenceLine
                            y={effectiveLimit.limitKw}
                            stroke="#059669"
                            strokeDasharray="5 5"
                            strokeWidth={2}
                            label={{
                                value: `Actuel: ${effectiveLimit.limitKw.toFixed(1)} kW`,
                                position: 'right',
                                fill: '#059669',
                                fontSize: 10
                            }}
                        />
                    )}

                    {/* ChargePointMaxProfile - Rouge */}
                    <Area
                        type="stepAfter"
                        dataKey="chargePointMax"
                        name="ChargePointMax"
                        stroke="#ef4444"
                        strokeWidth={2}
                        fill="url(#colorChargePointMax)"
                        connectNulls
                    />

                    {/* TxDefaultProfile - Orange */}
                    <Area
                        type="stepAfter"
                        dataKey="txDefault"
                        name="TxDefault"
                        stroke="#f97316"
                        strokeWidth={2}
                        fill="url(#colorTxDefault)"
                        connectNulls
                    />

                    {/* TxProfile - Bleu */}
                    <Area
                        type="stepAfter"
                        dataKey="txProfile"
                        name="TxProfile"
                        stroke="#3b82f6"
                        strokeWidth={2}
                        fill="url(#colorTxProfile)"
                        connectNulls
                    />

                    {/* Limite effective - Vert */}
                    <Area
                        type="stepAfter"
                        dataKey="effective"
                        name="Effective"
                        stroke="#10b981"
                        strokeWidth={3}
                        fill="url(#colorEffective)"
                    />
                </AreaChart>
            </ResponsiveContainer>
        </div>
    );
}

/**
 * Calcule la limite d'un profil à un moment donné.
 */
function calculateLimitAtTime(
    profile: ChargingProfile,
    time: number,
    convertToWatts: (limitA: number, phaseType: string, voltageV: number) => number
): number {
    if (!profile.chargingSchedule?.chargingSchedulePeriod?.length) {
        return Infinity;
    }

    const startTime = profile.effectiveStartTime
        ? new Date(profile.effectiveStartTime).getTime()
        : profile.appliedAt
            ? new Date(profile.appliedAt).getTime()
            : time;

    const elapsedSeconds = Math.floor((time - startTime) / 1000);

    if (elapsedSeconds < 0) return Infinity;

    // Vérifier la durée
    if (profile.chargingSchedule.duration && elapsedSeconds > profile.chargingSchedule.duration) {
        return Infinity;
    }

    // Trouver la période active
    let activePeriod = null;
    for (const period of profile.chargingSchedule.chargingSchedulePeriod) {
        if (period.startPeriod <= elapsedSeconds) {
            activePeriod = period;
        } else {
            break;
        }
    }

    if (!activePeriod) return Infinity;

    // Convertir en Watts si nécessaire
    if (profile.chargingSchedule.chargingRateUnit === 'W') {
        return activePeriod.limit;
    }

    // Utiliser les valeurs par défaut pour la conversion
    return convertToWatts(activePeriod.limit, 'AC_TRI', 230);
}

export default ChargingProfileGraph;
