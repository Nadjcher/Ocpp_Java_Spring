// frontend/src/tabs/SmartChargingTab.tsx
import React, { useMemo, useRef, useState, useCallback, useEffect } from "react";
import {
    useChargingProfileStore,
    type ChargingProfile as StoreChargingProfile,
    type EffectiveLimit,
    formatLimit,
    getPurposeColor
} from "@/store/chargingProfileStore";
import { ChargingProfileGraph } from "@/components/ChargingProfileGraph";
import { ChargingProfileIndicator } from "@/components/ChargingProfileIndicator";
import { config } from "@/config/env";
import { useShallow } from "zustand/react/shallow";

const nextId = (() => {
    let i = 1;
    return () => `${Date.now()}-${i++}`;
})();

type WsRef = { ws: WebSocket | null; open: boolean };

type Period = { start: number; limit: number };

// Session ID pour le SmartChargingTab (simulation locale)
const SMART_CHARGING_SESSION_ID = "smart-charging-tab";

export default function SmartChargingTab() {
    // Store Zustand pour Smart Charging - use shallow comparison and access Maps directly
    const {
        addProfile,
        removeProfile,
        clearProfiles,
        updateEffectiveLimit,
        updateCompositeSchedule,
        profilesMap,
        effectiveLimitsMap,
        convertToWatts,
        convertToAmps
    } = useChargingProfileStore(
        useShallow((state) => ({
            addProfile: state.addProfile,
            removeProfile: state.removeProfile,
            clearProfiles: state.clearProfiles,
            updateEffectiveLimit: state.updateEffectiveLimit,
            updateCompositeSchedule: state.updateCompositeSchedule,
            profilesMap: state.profiles,
            effectiveLimitsMap: state.effectiveLimits,
            convertToWatts: state.convertToWatts,
            convertToAmps: state.convertToAmps
        }))
    );

    // Derive profiles and effectiveLimit using useMemo
    const profiles = useMemo(() => {
        return profilesMap.get(SMART_CHARGING_SESSION_ID) || [];
    }, [profilesMap]);

    const effectiveLimit = useMemo(() => {
        return effectiveLimitsMap.get(SMART_CHARGING_SESSION_ID) || null;
    }, [effectiveLimitsMap]);

    // Connexion
    const wsRef = useRef<WsRef>({ ws: null, open: false });
    const [urlBase, setUrlBase] = useState(config.ocppUrls.test);
    const [cpId, setCpId] = useState(config.defaults.cpId);
    const [evpId, setEvpId] = useState("EVP-FLOW-001"); // info visuelle
    const [status, setStatus] = useState<"Déconnecté" | "Connexion…" | "Connecté">(
        "Déconnecté"
    );

    // Affichage
    const [showGraph, setShowGraph] = useState(true);
    const [graphDuration, setGraphDuration] = useState(60); // minutes

    // Paramètres de ChargingProfile
    const [connectorId, setConnectorId] = useState<number>(1);
    const [profileId, setProfileId] = useState<number>(1);
    const [stackLevel, setStackLevel] = useState<number>(0);
    const [purpose, setPurpose] = useState<
        "TxProfile" | "TxDefaultProfile" | "ChargePointMaxProfile"
    >("TxProfile");
    const [kind, setKind] = useState<"Absolute" | "Recurring" | "Relative">(
        "Absolute"
    );
    const [unit, setUnit] = useState<"W" | "A" | "Wh">("W");
    const [validFrom, setValidFrom] = useState<string>("");
    const [validTo, setValidTo] = useState<string>("");
    const [recurrence, setRecurrence] = useState<"" | "Daily" | "Weekly">("");

    // Périodes
    const [newStart, setNewStart] = useState<number>(0);
    const [newLimit, setNewLimit] = useState<number>(10000);
    const [periods, setPeriods] = useState<Period[]>([]);

    // zones texte
    const [preview, setPreview] = useState("");
    const [log, setLog] = useState("");

    function appendLog(msg: string) {
        const ts = new Date().toLocaleTimeString();
        setLog((p) => `[${ts}] ${msg}\n` + p);
    }

    function urlWs() {
        const base = urlBase.trim().replace(/\/+$/, "");
        return `${base}/${encodeURIComponent(cpId.trim())}`;
    }

    // Traitement des messages OCPP reçus
    const handleOcppMessage = useCallback((data: string) => {
        try {
            const msg = JSON.parse(data);
            if (!Array.isArray(msg) || msg.length < 3) return;

            const [msgType, msgId, actionOrPayload, payloadOrUndefined] = msg;

            // CALL (2) - Message entrant du serveur
            if (msgType === 2) {
                const action = actionOrPayload as string;
                const payload = payloadOrUndefined;

                appendLog(`<<< [CALL] ${action}\n${JSON.stringify(payload, null, 2)}`);

                // Traiter SetChargingProfile du serveur
                if (action === "SetChargingProfile" && payload?.csChargingProfiles) {
                    const cp = payload.csChargingProfiles;
                    const profile: StoreChargingProfile = {
                        chargingProfileId: cp.chargingProfileId,
                        transactionId: cp.transactionId,
                        stackLevel: cp.stackLevel || 0,
                        chargingProfilePurpose: cp.chargingProfilePurpose,
                        chargingProfileKind: cp.chargingProfileKind,
                        recurrencyKind: cp.recurrencyKind,
                        validFrom: cp.validFrom,
                        validTo: cp.validTo,
                        chargingSchedule: {
                            duration: cp.chargingSchedule?.duration,
                            startSchedule: cp.chargingSchedule?.startSchedule,
                            chargingRateUnit: cp.chargingSchedule?.chargingRateUnit || 'W',
                            minChargingRate: cp.chargingSchedule?.minChargingRate,
                            chargingSchedulePeriod: cp.chargingSchedule?.chargingSchedulePeriod || []
                        },
                        connectorId: payload.connectorId
                    };

                    addProfile(SMART_CHARGING_SESSION_ID, profile);
                    recalculateEffectiveLimit();

                    // Répondre Accepted
                    const response = [3, msgId, { status: "Accepted" }];
                    wsRef.current.ws?.send(JSON.stringify(response));
                    appendLog(`>>> [CALLRESULT] SetChargingProfile -> Accepted`);
                }

                // Traiter ClearChargingProfile du serveur
                if (action === "ClearChargingProfile") {
                    if (payload?.id) {
                        removeProfile(SMART_CHARGING_SESSION_ID, payload.id);
                    } else if (payload?.chargingProfilePurpose) {
                        clearProfiles(SMART_CHARGING_SESSION_ID, payload.chargingProfilePurpose);
                    }
                    recalculateEffectiveLimit();

                    const response = [3, msgId, { status: "Accepted" }];
                    wsRef.current.ws?.send(JSON.stringify(response));
                    appendLog(`>>> [CALLRESULT] ClearChargingProfile -> Accepted`);
                }
            }

            // CALLRESULT (3) - Réponse à nos CALL
            if (msgType === 3) {
                const payload = actionOrPayload;
                appendLog(`<<< [CALLRESULT]\n${JSON.stringify(payload, null, 2)}`);

                // Réponse GetCompositeSchedule
                if (payload?.chargingSchedule) {
                    updateCompositeSchedule(SMART_CHARGING_SESSION_ID, {
                        connectorId: payload.connectorId || connectorId,
                        scheduleStart: payload.scheduleStart || new Date().toISOString(),
                        duration: payload.chargingSchedule.duration || 3600,
                        chargingRateUnit: payload.chargingSchedule.chargingRateUnit || 'W',
                        chargingSchedulePeriod: payload.chargingSchedule.chargingSchedulePeriod || []
                    });
                    appendLog(`[SCP] CompositeSchedule reçu: ${payload.chargingSchedule.chargingSchedulePeriod?.length || 0} périodes`);
                }

                // Réponse SetChargingProfile
                if (payload?.status) {
                    appendLog(`[SCP] Status: ${payload.status}`);
                }
            }

            // CALLERROR (4)
            if (msgType === 4) {
                const errorCode = actionOrPayload;
                const errorDesc = payloadOrUndefined;
                appendLog(`<<< [CALLERROR] ${errorCode}: ${errorDesc}`);
            }
        } catch (e) {
            appendLog(`<<< ${data}`);
        }
    }, [addProfile, removeProfile, clearProfiles, updateCompositeSchedule, connectorId]);

    // Recalculer la limite effective localement
    const recalculateEffectiveLimit = useCallback(() => {
        const currentProfiles = useChargingProfileStore.getState().getProfiles(SMART_CHARGING_SESSION_ID);
        if (currentProfiles.length === 0) {
            updateEffectiveLimit(SMART_CHARGING_SESSION_ID, {
                limitW: Infinity,
                limitA: Infinity,
                limitKw: Infinity,
                source: null,
                profileId: 0,
                stackLevel: 0,
                currentPeriodStart: 0
            });
            return;
        }

        const now = Date.now();
        let effectiveLimitW = Infinity;
        let activeProfile: StoreChargingProfile | null = null;
        let activePeriodStart = 0;

        // Trier par priorité: ChargePointMaxProfile > TxDefaultProfile > TxProfile
        // puis par stackLevel (plus haut = plus prioritaire)
        const sortedProfiles = [...currentProfiles].sort((a, b) => {
            const purposeOrder: Record<string, number> = {
                'ChargePointMaxProfile': 0,
                'TxDefaultProfile': 1,
                'TxProfile': 2
            };
            const pa = purposeOrder[a.chargingProfilePurpose] ?? 3;
            const pb = purposeOrder[b.chargingProfilePurpose] ?? 3;
            if (pa !== pb) return pa - pb;
            return b.stackLevel - a.stackLevel;
        });

        for (const profile of sortedProfiles) {
            if (!profile.chargingSchedule?.chargingSchedulePeriod?.length) continue;

            const startTime = profile.effectiveStartTime
                ? new Date(profile.effectiveStartTime).getTime()
                : profile.appliedAt
                    ? new Date(profile.appliedAt).getTime()
                    : now;

            const elapsedSeconds = Math.floor((now - startTime) / 1000);
            if (elapsedSeconds < 0) continue;

            // Vérifier la durée
            if (profile.chargingSchedule.duration && elapsedSeconds > profile.chargingSchedule.duration) {
                continue;
            }

            // Trouver la période active
            let activePeriod = null;
            for (const period of profile.chargingSchedule.chargingSchedulePeriod) {
                if (period.startPeriod <= elapsedSeconds) {
                    activePeriod = period;
                    activePeriodStart = period.startPeriod;
                }
            }

            if (!activePeriod) continue;

            // Convertir en Watts si nécessaire
            let limitW: number;
            if (profile.chargingSchedule.chargingRateUnit === 'W') {
                limitW = activePeriod.limit;
            } else {
                limitW = convertToWatts(activePeriod.limit, 'AC_TRI', 230);
            }

            // Prendre le minimum (profils se combinent)
            if (limitW < effectiveLimitW) {
                effectiveLimitW = limitW;
                activeProfile = profile;
            }
        }

        if (activeProfile && effectiveLimitW !== Infinity) {
            const limitKw = effectiveLimitW / 1000;
            const limitA = convertToAmps(effectiveLimitW, 'AC_TRI', 230);

            updateEffectiveLimit(SMART_CHARGING_SESSION_ID, {
                limitW: effectiveLimitW,
                limitA,
                limitKw,
                source: activeProfile.chargingProfilePurpose,
                profileId: activeProfile.chargingProfileId,
                stackLevel: activeProfile.stackLevel,
                currentPeriodStart: activePeriodStart
            });
        }
    }, [updateEffectiveLimit, convertToWatts, convertToAmps]);

    function connectToggle() {
        const cur = wsRef.current;
        if (cur.ws && cur.ws.readyState !== WebSocket.CLOSED) {
            try {
                cur.ws.close();
            } catch {}
            cur.ws = null;
            cur.open = false;
            setStatus("Déconnecté");
            appendLog("WS fermé.");
            return;
        }
        const url = urlWs();
        appendLog(`Connexion → ${url}`);
        setStatus("Connexion…");
        try {
            const ws = new WebSocket(url, ["ocpp1.6"]);
            cur.ws = ws;
            ws.onopen = () => {
                cur.open = true;
                setStatus("Connecté");
                appendLog("WS ouvert.");
                // Boot auto pour synchro, comme JavaFX
                const msgId = nextId();
                const payload = {
                    chargePointVendor: "EVSE Simulator",
                    chargePointModel: "SmartCharging",
                    chargePointSerialNumber: cpId,
                    chargeBoxSerialNumber: cpId,
                };
                ws.send(JSON.stringify([2, msgId, "BootNotification", payload]));
                appendLog(`>>> BootNotification ${msgId}`);
            };
            ws.onclose = () => {
                cur.open = false;
                setStatus("Déconnecté");
                appendLog("WS fermé.");
            };
            ws.onerror = () => appendLog("Erreur WS.");
            ws.onmessage = (ev) => handleOcppMessage(ev.data);
        } catch (e: any) {
            appendLog(`Erreur: ${e?.message || e}`);
            setStatus("Déconnecté");
        }
    }

    function ensureOpen(): WebSocket | null {
        const cur = wsRef.current;
        if (cur.ws && cur.open && cur.ws.readyState === WebSocket.OPEN) return cur.ws;
        return null;
    }

    // Périodes UI
    function addPeriod() {
        setPeriods((p) => [...p, { start: Math.max(0, newStart), limit: Math.max(0, newLimit) }]);
    }
    function clearPeriods() {
        setPeriods([]);
    }
    function removeLast() {
        setPeriods((p) => p.slice(0, Math.max(0, p.length - 1)));
    }

    // Construction du SetChargingProfile
    const setChargingProfilePayload = useMemo(() => {
        const schedule = {
            chargingRateUnit: unit,
            chargingSchedulePeriod: periods.map((x) => ({
                startPeriod: x.start,
                limit: x.limit,
            })),
        };
        const csChargingProfiles: any = {
            chargingProfileId: profileId,
            stackLevel: stackLevel,
            chargingProfilePurpose: purpose,
            chargingProfileKind: kind,
            chargingSchedule: schedule,
        };
        if (validFrom) csChargingProfiles.validFrom = new Date(validFrom).toISOString();
        if (validTo) csChargingProfiles.validTo = new Date(validTo).toISOString();
        if (kind === "Recurring" && recurrence) {
            csChargingProfiles.recurrencyKind = recurrence;
        }
        return {
            connectorId: connectorId,
            csChargingProfiles,
        };
    }, [
        connectorId,
        profileId,
        stackLevel,
        purpose,
        kind,
        unit,
        periods,
        validFrom,
        validTo,
        recurrence,
    ]);

    // Preview
    function doPreview() {
        const frame = [2, "<msgId>", "SetChargingProfile", setChargingProfilePayload];
        setPreview(JSON.stringify(frame, null, 2));
    }

    // Envois
    function sendSetChargingProfile() {
        const ws = ensureOpen();
        if (!ws) return appendLog("Connecte-toi d'abord.");
        const msgId = nextId();
        ws.send(JSON.stringify([2, msgId, "SetChargingProfile", setChargingProfilePayload]));
        appendLog(`>>> SetChargingProfile ${msgId}\n${JSON.stringify(setChargingProfilePayload, null, 2)}`);

        // Ajouter également au store local pour la visualisation
        const cp = setChargingProfilePayload.csChargingProfiles;
        const profile: StoreChargingProfile = {
            chargingProfileId: cp.chargingProfileId,
            stackLevel: cp.stackLevel || 0,
            chargingProfilePurpose: cp.chargingProfilePurpose,
            chargingProfileKind: cp.chargingProfileKind,
            recurrencyKind: cp.recurrencyKind,
            validFrom: cp.validFrom,
            validTo: cp.validTo,
            chargingSchedule: {
                chargingRateUnit: cp.chargingSchedule.chargingRateUnit,
                chargingSchedulePeriod: cp.chargingSchedule.chargingSchedulePeriod
            },
            connectorId: setChargingProfilePayload.connectorId
        };
        addProfile(SMART_CHARGING_SESSION_ID, profile);
        recalculateEffectiveLimit();
    }

    function sendClearProfile() {
        const ws = ensureOpen();
        if (!ws) return appendLog("Connecte-toi d'abord.");
        const msgId = nextId();
        const payload: any = { connectorId };
        // on utilise profileId comme identifiant optionnel
        if (profileId) payload.id = profileId;
        // optionnellement filtrer par purpose
        payload.chargingProfilePurpose = purpose;
        ws.send(JSON.stringify([2, msgId, "ClearChargingProfile", payload]));
        appendLog(`>>> ClearChargingProfile ${msgId}\n${JSON.stringify(payload, null, 2)}`);

        // Supprimer également du store local
        if (profileId) {
            removeProfile(SMART_CHARGING_SESSION_ID, profileId);
        } else {
            clearProfiles(SMART_CHARGING_SESSION_ID, purpose);
        }
        recalculateEffectiveLimit();
    }

    function sendGetComposite() {
        const ws = ensureOpen();
        if (!ws) return appendLog("Connecte-toi d'abord.");
        const msgId = nextId();
        // durée : si validTo est défini on calcule, sinon 3600
        let duration = 3600;
        if (validTo) {
            const to = new Date(validTo).getTime();
            const now = Date.now();
            if (to > now) duration = Math.round((to - now) / 1000);
        }
        const payload = { connectorId, duration, chargingRateUnit: unit };
        ws.send(JSON.stringify([2, msgId, "GetCompositeSchedule", payload]));
        appendLog(`>>> GetCompositeSchedule ${msgId}\n${JSON.stringify(payload, null, 2)}`);
    }

    // Fonction pour vider tous les profils locaux
    function clearAllLocalProfiles() {
        clearProfiles(SMART_CHARGING_SESSION_ID);
        recalculateEffectiveLimit();
        appendLog("[Local] Tous les profils locaux ont été supprimés");
    }

    return (
        <div className="page">
            {/* Barre de connexion (comme JavaFX) */}
            <div className="card p16 mb16">
                <div className="grid4">
                    <div>
                        <label>URL OCPP :</label>
                        <input
                            value={urlBase}
                            onChange={(e) => setUrlBase(e.target.value)}
                            placeholder="wss://.../ocpp/WebSocket"
                        />
                    </div>
                    <div>
                        <label>CP-ID :</label>
                        <input value={cpId} onChange={(e) => setCpId(e.target.value)} />
                    </div>
                    <div>
                        <label>EvP-ID :</label>
                        <input value={evpId} onChange={(e) => setEvpId(e.target.value)} />
                    </div>
                </div>
                <div className="row mt8">
                    <button className="btn" onClick={connectToggle}>
                        {status === "Déconnecté" ? "Connect" : "Disconnect"}
                    </button>
                    <div className="ml8 muted">{status}</div>
                    {/* Indicateur de limite active */}
                    <div className="ml-auto">
                        <ChargingProfileIndicator
                            sessionId={SMART_CHARGING_SESSION_ID}
                            compact={true}
                        />
                    </div>
                </div>
            </div>

            {/* Section Graphique et Profils Actifs */}
            <div className="grid2 mb16" style={{ gap: '16px' }}>
                {/* Graphique Smart Charging */}
                <div className="card p16">
                    <div className="row mb8">
                        <h3 style={{ margin: 0, flex: 1 }}>Graphique Smart Charging</h3>
                        <label className="mr8" style={{ fontSize: '12px' }}>
                            <input
                                type="checkbox"
                                checked={showGraph}
                                onChange={(e) => setShowGraph(e.target.checked)}
                            /> Afficher
                        </label>
                        <select
                            value={graphDuration}
                            onChange={(e) => setGraphDuration(Number(e.target.value))}
                            style={{ width: 100 }}
                        >
                            <option value={30}>30 min</option>
                            <option value={60}>1 heure</option>
                            <option value={120}>2 heures</option>
                            <option value={240}>4 heures</option>
                        </select>
                    </div>
                    {showGraph && (
                        <ChargingProfileGraph
                            sessionId={SMART_CHARGING_SESSION_ID}
                            durationMinutes={graphDuration}
                            height={220}
                            showLegend={true}
                        />
                    )}
                </div>

                {/* Profils Actifs */}
                <div className="card p16">
                    <div className="row mb8">
                        <h3 style={{ margin: 0, flex: 1 }}>Profils Actifs ({profiles.length})</h3>
                        <button
                            className="btn danger"
                            onClick={clearAllLocalProfiles}
                            disabled={profiles.length === 0}
                            style={{ fontSize: '12px', padding: '4px 8px' }}
                        >
                            Vider tout
                        </button>
                    </div>

                    {/* Indicateur détaillé */}
                    <ChargingProfileIndicator
                        sessionId={SMART_CHARGING_SESSION_ID}
                        showDetails={true}
                    />

                    {/* Liste des profils */}
                    {profiles.length > 0 && (
                        <div className="mt12" style={{ maxHeight: 150, overflow: 'auto' }}>
                            <table style={{ fontSize: '11px', width: '100%' }}>
                                <thead>
                                    <tr>
                                        <th>ID</th>
                                        <th>Purpose</th>
                                        <th>Stack</th>
                                        <th>Unit</th>
                                        <th>Periods</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {profiles.map(p => (
                                        <tr key={p.chargingProfileId}>
                                            <td>{p.chargingProfileId}</td>
                                            <td>
                                                <span className={`px1 rounded ${getPurposeColor(p.chargingProfilePurpose)}`} style={{ fontSize: '10px' }}>
                                                    {p.chargingProfilePurpose?.replace('Profile', '')}
                                                </span>
                                            </td>
                                            <td>{p.stackLevel}</td>
                                            <td>{p.chargingSchedule?.chargingRateUnit}</td>
                                            <td>{p.chargingSchedule?.chargingSchedulePeriod?.length || 0}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </div>

            {/* Paramétrage du profile */}
            <div className="card p16 mb16">
                <div className="grid6">
                    <div>
                        <label>connectorId</label>
                        <input
                            type="number"
                            value={connectorId}
                            onChange={(e) => setConnectorId(Number(e.target.value))}
                        />
                    </div>
                    <div>
                        <label>profileId</label>
                        <input
                            type="number"
                            value={profileId}
                            onChange={(e) => setProfileId(Number(e.target.value))}
                        />
                    </div>
                    <div>
                        <label>stackLevel</label>
                        <input
                            type="number"
                            value={stackLevel}
                            onChange={(e) => setStackLevel(Number(e.target.value))}
                        />
                    </div>
                    <div>
                        <label>purpose</label>
                        <select
                            value={purpose}
                            onChange={(e) =>
                                setPurpose(e.target.value as typeof purpose)
                            }
                        >
                            <option>TxProfile</option>
                            <option>TxDefaultProfile</option>
                            <option>ChargePointMaxProfile</option>
                        </select>
                    </div>
                    <div>
                        <label>kind</label>
                        <select
                            value={kind}
                            onChange={(e) => setKind(e.target.value as typeof kind)}
                        >
                            <option>Absolute</option>
                            <option>Recurring</option>
                            <option>Relative</option>
                        </select>
                    </div>
                    <div>
                        <label>unit</label>
                        <select value={unit} onChange={(e) => setUnit(e.target.value as any)}>
                            <option>W</option>
                            <option>A</option>
                            <option>Wh</option>
                        </select>
                    </div>

                    <div>
                        <label>validFrom</label>
                        <input
                            type="datetime-local"
                            value={validFrom}
                            onChange={(e) => setValidFrom(e.target.value)}
                        />
                    </div>
                    <div>
                        <label>validTo</label>
                        <input
                            type="datetime-local"
                            value={validTo}
                            onChange={(e) => setValidTo(e.target.value)}
                        />
                    </div>
                    <div>
                        <label>recurrence</label>
                        <select
                            value={recurrence}
                            onChange={(e) => setRecurrence(e.target.value as any)}
                            disabled={kind !== "Recurring"}
                        >
                            <option value="">(aucun)</option>
                            <option value="Daily">Daily</option>
                            <option value="Weekly">Weekly</option>
                        </select>
                    </div>
                </div>

                {/* Ajout de périodes */}
                <div className="row mt12">
                    <label className="mr8">start(s):</label>
                    <input
                        type="number"
                        value={newStart}
                        onChange={(e) => setNewStart(Number(e.target.value))}
                        style={{ width: 120 }}
                    />
                    <label className="ml12 mr8">limit:</label>
                    <input
                        type="number"
                        value={newLimit}
                        onChange={(e) => setNewLimit(Number(e.target.value))}
                        style={{ width: 140 }}
                    />
                    <button className="btn ml12" onClick={addPeriod}>
                        Ajouter période
                    </button>
                    <button className="btn secondary ml8" onClick={removeLast}>
                        Retirer dernière
                    </button>
                    <button className="btn danger ml8" onClick={clearPeriods}>
                        Vider
                    </button>
                </div>

                {periods.length > 0 && (
                    <div className="mt12 surface p12">
                        <table>
                            <thead>
                            <tr>
                                <th>#</th>
                                <th>start(s)</th>
                                <th>limit</th>
                            </tr>
                            </thead>
                            <tbody>
                            {periods.map((p, i) => (
                                <tr key={i}>
                                    <td>{i + 1}</td>
                                    <td>{p.start}</td>
                                    <td>{p.limit}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}

                <div className="row mt12">
                    <button className="btn secondary" onClick={doPreview}>
                        Preview JSON
                    </button>
                    <button className="btn ml8" onClick={sendSetChargingProfile}>
                        SetChargingProfile
                    </button>
                    <button className="btn ml8 warning" onClick={sendClearProfile}>
                        ClearChargingProfile
                    </button>
                    <button className="btn ml8" onClick={sendGetComposite}>
                        GetCompositeSchedule
                    </button>
                </div>
            </div>

            {/* Prévisualisation et Logs côte à côte */}
            <div className="grid2 mb16" style={{ gap: '16px' }}>
                <div className="card p16">
                    <label>Preview JSON OCPP :</label>
                    <textarea
                        rows={12}
                        value={preview}
                        readOnly
                        style={{ fontFamily: 'monospace', fontSize: '11px' }}
                    />
                </div>

                <div className="card p16">
                    <div className="row mb8">
                        <label style={{ flex: 1 }}>Logs Smart Charging :</label>
                        <button
                            className="btn secondary"
                            onClick={() => setLog("")}
                            style={{ fontSize: '11px', padding: '2px 8px' }}
                        >
                            Clear
                        </button>
                    </div>
                    <textarea
                        rows={12}
                        value={log}
                        readOnly
                        style={{ fontFamily: 'monospace', fontSize: '11px' }}
                    />
                </div>
            </div>
        </div>
    );
}
