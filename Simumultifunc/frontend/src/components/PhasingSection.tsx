
import React, { useState, useEffect, useMemo } from 'react';
import { config } from "@/config/env";
import { NumericInput } from "@/components/ui/NumericInput";

interface PhasingConfig {
    evsePhases: number;
    vehicleActivePhases: number;
    powerPerPhase: number;
}

/**
 * Configuration des limites du véhicule pour le phasage
 */
interface VehiclePhasingLimits {
    /** Nombre de phases AC supportées par le chargeur embarqué (1, 2, ou 3) */
    acPhases: number;
    /** Courant max par phase du chargeur embarqué (A) */
    acMaxA: number;
}

interface PhasingSectionProps {
    sessionId: string | null;
    disabled?: boolean;
    apiBase?: string;
    /** Configuration EVSE actuelle */
    evseConfig?: {
        phases: number;
        maxA: number;
    };
    /** Limites du véhicule sélectionné */
    vehicleLimits?: VehiclePhasingLimits;
}

// En développement, utiliser le proxy Vite (chaîne vide)
const getDefaultApiBase = () => {
    const isDev = typeof import.meta !== "undefined" && import.meta.env?.DEV;
    return isDev ? "" : (config.apiUrl || "http://localhost:8887");
};

const PhasingSection: React.FC<PhasingSectionProps> = ({
                                                           sessionId,
                                                           disabled = false,
                                                           apiBase = getDefaultApiBase(),
                                                           evseConfig,
                                                           vehicleLimits
                                                       }) => {
    // Mode test: ignore les limites véhicule, utilise uniquement les limites EVSE
    const [testMode, setTestMode] = useState(false);

    // Calculer les limites effectives basées sur EVSE et véhicule (ou EVSE seul en mode test)
    const effectiveLimits = useMemo(() => {
        const evsePhases = evseConfig?.phases ?? 3;
        const evseMaxA = evseConfig?.maxA ?? 32;
        const vehiclePhases = vehicleLimits?.acPhases ?? 3;
        const vehicleMaxA = vehicleLimits?.acMaxA ?? 32;

        // En mode test, on utilise uniquement les limites EVSE
        if (testMode) {
            return {
                maxPhases: evsePhases,
                maxCurrentA: evseMaxA,
                evsePhases,
                vehiclePhases
            };
        }

        return {
            maxPhases: Math.min(evsePhases, vehiclePhases),
            maxCurrentA: Math.min(evseMaxA, vehicleMaxA),
            evsePhases,
            vehiclePhases
        };
    }, [evseConfig, vehicleLimits, testMode]);

    const [phasingConfig, setPhasingConfig] = useState<PhasingConfig>({
        evsePhases: effectiveLimits.evsePhases,
        vehicleActivePhases: effectiveLimits.maxPhases,
        powerPerPhase: Math.min(16, effectiveLimits.maxCurrentA)
    });

    const [loading, setLoading] = useState(false);
    const [applied, setApplied] = useState(false);

    const updatePhasing = async () => {
        if (!sessionId) return;

        setLoading(true);
        try {
            const response = await fetch(`${apiBase}/api/simu/${sessionId}/phasing`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(phasingConfig)
            });
            const result = await response.json();
            console.log('Phasage configuré:', result);
            setApplied(true);
            setTimeout(() => setApplied(false), 2000);
        } catch (error) {
            console.error('Erreur configuration phasage:', error);
        }
        setLoading(false);
    };

    // Synchroniser quand les configs EVSE ou véhicule changent (sauf en mode test pour powerPerPhase)
    useEffect(() => {
        setPhasingConfig(prev => {
            const newEvsePhases = effectiveLimits.evsePhases;
            const newVehiclePhases = Math.min(prev.vehicleActivePhases, effectiveLimits.maxPhases);
            // En mode test, ne pas forcer le powerPerPhase au max
            const newPowerPerPhase = testMode
                ? prev.powerPerPhase
                : Math.min(prev.powerPerPhase, effectiveLimits.maxCurrentA);

            if (prev.evsePhases !== newEvsePhases ||
                prev.vehicleActivePhases !== newVehiclePhases ||
                prev.powerPerPhase !== newPowerPerPhase) {
                return {
                    evsePhases: newEvsePhases,
                    vehicleActivePhases: newVehiclePhases,
                    powerPerPhase: newPowerPerPhase
                };
            }
            return prev;
        });
    }, [effectiveLimits, testMode]);

    // Validation pour empêcher véhicule > min(EVSE, véhicule limite) - désactivé pour powerPerPhase en mode test
    useEffect(() => {
        if (phasingConfig.vehicleActivePhases > effectiveLimits.maxPhases) {
            setPhasingConfig(prev => ({
                ...prev,
                vehicleActivePhases: effectiveLimits.maxPhases
            }));
        }
        // En mode test, on ne force pas la limite sur powerPerPhase
        if (!testMode && phasingConfig.powerPerPhase > effectiveLimits.maxCurrentA) {
            setPhasingConfig(prev => ({
                ...prev,
                powerPerPhase: effectiveLimits.maxCurrentA
            }));
        }
    }, [phasingConfig.evsePhases, effectiveLimits, testMode]);

    return (
        <div className="rounded border bg-white p-4 shadow-sm">
            <div className="font-semibold mb-3 text-purple-700">
                [POWER] Test Régulation par Phasage
            </div>

            {/* Indicateur des limites effectives + Mode test */}
            <div className="mb-3 text-xs text-slate-500 bg-slate-50 p-2 rounded flex items-center justify-between">
                <div className="flex gap-4">
                    <span>EVSE: {effectiveLimits.evsePhases}ph / {evseConfig?.maxA ?? 32}A</span>
                    <span>•</span>
                    <span className={testMode ? "line-through text-slate-400" : ""}>
                        Véhicule: {effectiveLimits.vehiclePhases}ph / {vehicleLimits?.acMaxA ?? 32}A
                    </span>
                    <span>•</span>
                    <span className="font-medium text-purple-600">
                        Effectif: {effectiveLimits.maxPhases}ph / {effectiveLimits.maxCurrentA}A
                    </span>
                </div>
                <label className="flex items-center gap-2 cursor-pointer ml-4">
                    <input
                        type="checkbox"
                        checked={testMode}
                        onChange={(e) => setTestMode(e.target.checked)}
                        className="w-4 h-4 accent-orange-500"
                    />
                    <span className={`font-medium ${testMode ? "text-orange-600" : "text-slate-500"}`}>
                        Mode test
                    </span>
                </label>
            </div>

            <div className="grid grid-cols-3 gap-3">
                <div>
                    <div className="text-xs mb-1 text-slate-600">
                        EVSE Phases
                        <span className="text-slate-400 ml-1">(config)</span>
                    </div>
                    <select
                        className="w-full border rounded px-2 py-1 text-sm bg-slate-100"
                        value={phasingConfig.evsePhases}
                        disabled={true}
                        title="Phases EVSE configurées (lecture seule)"
                    >
                        <option value="1">Mono (1ph)</option>
                        <option value="2">Bi (2ph)</option>
                        <option value="3">Tri (3ph)</option>
                    </select>
                </div>

                <div>
                    <div className="text-xs mb-1 text-slate-600">
                        Phases Actives
                        <span className="text-slate-400 ml-1">(max {effectiveLimits.maxPhases})</span>
                    </div>
                    <select
                        className="w-full border rounded px-2 py-1 text-sm"
                        value={phasingConfig.vehicleActivePhases}
                        onChange={(e) => setPhasingConfig({
                            ...phasingConfig,
                            vehicleActivePhases: parseInt(e.target.value)
                        })}
                        disabled={disabled || loading}
                    >
                        <option value="1">1 phase</option>
                        {effectiveLimits.maxPhases >= 2 &&
                            <option value="2">2 phases</option>
                        }
                        {effectiveLimits.maxPhases >= 3 &&
                            <option value="3">3 phases</option>
                        }
                    </select>
                </div>

                <div>
                    <div className="text-xs mb-1 text-slate-600">
                        Courant/phase
                        <span className={`ml-1 ${testMode ? "text-orange-500" : "text-slate-400"}`}>
                            {testMode ? "(libre)" : `(max ${effectiveLimits.maxCurrentA}A)`}
                        </span>
                    </div>
                    <NumericInput
                        min={6}
                        max={testMode ? undefined : effectiveLimits.maxCurrentA}
                        className={`w-full border rounded px-2 py-1 text-sm ${testMode ? "border-orange-400" : ""}`}
                        value={phasingConfig.powerPerPhase}
                        onChange={(value) => {
                            setPhasingConfig({
                                ...phasingConfig,
                                powerPerPhase: value
                            });
                        }}
                        disabled={disabled || loading}
                    />
                </div>
            </div>

            <div className="mt-3 flex items-center justify-between">
                <div className="text-sm">
                    <span className="text-slate-600">Puissance: </span>
                    <span className="font-bold text-purple-700">
            {(phasingConfig.vehicleActivePhases *
                phasingConfig.powerPerPhase * 230 / 1000).toFixed(1)} kW
          </span>
                </div>

                <button
                    onClick={updatePhasing}
                    disabled={disabled || loading || !sessionId}
                    className={`px-4 py-2 rounded text-sm font-medium transition-colors ${
                        applied
                            ? 'bg-green-600 text-white'
                            : 'bg-purple-600 text-white hover:bg-purple-700'
                    } disabled:opacity-50 disabled:cursor-not-allowed`}
                >
                    {loading ? 'Application...' : applied ? '[OK] Appliqué' : 'Appliquer Phasage'}
                </button>
            </div>

            <div className={`mt-2 text-xs p-2 rounded ${testMode ? "bg-orange-50 text-orange-700" : "bg-purple-50 text-slate-500"}`}>
                {testMode && (
                    <div className="mb-1 font-medium text-orange-600">
                        Mode test actif - Limites véhicule ignorées (EVSE uniquement)
                    </div>
                )}
                <div className="mb-1">
                    Test régulation phasage: le véhicule simulera{' '}
                    <span className={`font-medium ${testMode ? "text-orange-700" : "text-purple-700"}`}>
                        {phasingConfig.vehicleActivePhases} phase(s)
                    </span>
                    {' '}avec{' '}
                    <span className={`font-medium ${testMode ? "text-orange-700" : "text-purple-700"}`}>
                        {phasingConfig.powerPerPhase}A/phase
                    </span>
                </div>
                {!testMode && effectiveLimits.vehiclePhases < effectiveLimits.evsePhases && (
                    <div className="text-amber-600 mt-1">
                        Véhicule limité à {effectiveLimits.vehiclePhases}ph
                        (chargeur embarqué: {vehicleLimits?.acMaxA ?? 32}A max)
                    </div>
                )}
            </div>
        </div>
    );
};

export default PhasingSection;