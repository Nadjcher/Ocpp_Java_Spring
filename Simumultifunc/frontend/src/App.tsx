// frontend/src/App.tsx
import React, { useState, useEffect } from "react";
import "./App.css";
import { TNRProvider } from "./contexts/TNRContext";
import { SessionsProvider } from "./contexts/SessionsContext";
import { SessionPersistenceProvider } from "./providers/SessionPersistenceProvider";
import { AuthProvider, useAuth } from "./auth";

import SimulGPMTab from "./tabs/SimulGPMTab";
import GPMDryRunTab from "./tabs/GPMDryRunTab";
import SimuEvseTab from "./tabs/SimuEvseTab";
import PerfOCPPTab from "./tabs/PerfOCPPTab";
import TnrTab from "./tabs/TnrTab";
import SmartChargingTab from "./tabs/SmartChargingTab";
import OCPPMessagesTab from "./tabs/OCPPMessagesTab";
import MLAnalysisTab from "./tabs/MLAnalysisTab";
import SchedulerTab from "./tabs/SchedulerTab";
import OCPITab from "./tabs/OCPITab";
import SettingsTab from "./tabs/SettingsTab";
import { MultiSessionDashboard } from "./components/session";
import { TokenStatusIndicator } from "./components/tte/TokenStatusIndicator";
import "@/styles/buttons.css";

type TabKey =
    | "simul-gpm"
    | "gpm-dryrun"
    | "simu-evse"
    | "perf-ocpp"
    | "tnr"
    | "smart-charging"
    | "ocpp-messages"
    | "ml-analysis"
    | "scheduler"
    | "ocpi"
    | "settings";

const TABS: { key: TabKey; label: string }[] = [
    { key: "simul-gpm", label: "Simul GPM (Multi)" },
    { key: "gpm-dryrun", label: "GPM Dry-Run" },
    { key: "simu-evse", label: "Simu EVSE" },
    { key: "perf-ocpp", label: "Perf OCPP (HTTP)" },
    { key: "tnr", label: "TNR" },
    { key: "smart-charging", label: "Smart Charging" },
    { key: "ocpp-messages", label: "OCPP Messages" },
    { key: "ml-analysis", label: "ML Analysis" },
    { key: "scheduler", label: "Scheduler" },
    { key: "ocpi", label: "OCPI Tests" },
    { key: "settings", label: "Settings (TTE)" },
];

// Wrapper principal avec AuthProvider
export default function App() {
    return (
        <AuthProvider>
            <AppContent />
        </AuthProvider>
    );
}

// Contenu de l'app avec vérification auth
function AppContent() {
    const { isAuthenticated, isLoading, redirectToLogin, tokenInfo } = useAuth();
    const [open, setOpen] = useState(true);
    const [active, setActive] = useState<TabKey>("simu-evse");

    // Écouter les logout forcés (401/403)
    useEffect(() => {
        const handler = () => redirectToLogin();
        window.addEventListener('auth:logout', handler);
        return () => window.removeEventListener('auth:logout', handler);
    }, [redirectToLogin]);

    // Afficher un loader pendant la vérification initiale
    if (isLoading) {
        return (
            <div style={{
                minHeight: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#f5f5f5'
            }}>
                <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: '18px', color: '#666' }}>Chargement...</div>
                </div>
            </div>
        );
    }

    // Non authentifié -> redirection vers EVP
    if (!isAuthenticated) {
        return (
            <div style={{
                minHeight: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#f5f5f5',
                flexDirection: 'column',
                gap: '16px'
            }}>
                <div style={{ fontSize: '18px', color: '#666' }}>
                    Session non authentifiee
                </div>
                <div style={{ fontSize: '14px', color: '#888' }}>
                    Redirection vers la plateforme EVP...
                </div>
                <button
                    onClick={redirectToLogin}
                    style={{
                        marginTop: '16px',
                        padding: '10px 24px',
                        backgroundColor: '#dc2626',
                        color: 'white',
                        border: 'none',
                        borderRadius: '8px',
                        fontSize: '14px',
                        cursor: 'pointer'
                    }}
                >
                    Se connecter sur EVP
                </button>
            </div>
        );
    }

    // Formater l'expiration du token
    const formatExpiration = () => {
        if (!tokenInfo?.exp) return null;
        const expDate = new Date(tokenInfo.exp * 1000);
        const remaining = expDate.getTime() - Date.now();
        if (remaining <= 0) return 'Expire';
        const minutes = Math.floor(remaining / 60000);
        if (minutes < 60) return `${minutes}min`;
        return `${Math.floor(minutes / 60)}h${minutes % 60}min`;
    };

    return (
        <SessionPersistenceProvider autoReconnect={true} keepaliveIntervalMs={15000}>
            <SessionsProvider>
                <TNRProvider>
                    <div className="root">
                        <header className="topbar">
                            <button className="toggle" onClick={() => setOpen((o) => !o)}>
                                ☰
                            </button>
                            <div className="title">GPM Simulator</div>
                            <div className="right" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                <TokenStatusIndicator showRefreshButton={true} />
                                {tokenInfo && (
                                    <span style={{ fontSize: '12px', color: '#888' }}>
                                        Token: {formatExpiration()}
                                    </span>
                                )}
                                <span>OCPP 1.6</span>
                            </div>
                        </header>

                        <div className="layout">
                            <aside className={`sidebar ${open ? "show" : "hide"}`}>
                                {TABS.map((t) => (
                                    <button
                                        key={t.key}
                                        className={`tabbtn ${active === t.key ? "active" : ""}`}
                                        onClick={() => setActive(t.key)}
                                    >
                                        {t.label}
                                    </button>
                                ))}
                            </aside>

                            <main className="content">
                                {active === "simul-gpm" && <SimulGPMTab />}
                                {active === "gpm-dryrun" && <GPMDryRunTab />}
                                {active === "simu-evse" && <SimuEvseTab />}
                                {active === "perf-ocpp" && <PerfOCPPTab />}
                                {active === "tnr" && <TnrTab />}
                                {active === "smart-charging" && <SmartChargingTab />}
                                {active === "ocpp-messages" && <OCPPMessagesTab />}
                                {active === "ml-analysis" && <MLAnalysisTab />}
                                {active === "scheduler" && <SchedulerTab />}
                                {active === "ocpi" && <OCPITab />}
                                {active === "settings" && <SettingsTab />}
                            </main>
                        </div>
                    </div>
                </TNRProvider>
            </SessionsProvider>
        </SessionPersistenceProvider>
    );
}
