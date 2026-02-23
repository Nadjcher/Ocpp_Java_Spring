// frontend/src/App.tsx
import React, { useState } from "react";
import "./App.css";
import { TNRProvider } from "./contexts/TNRContext";
import { SessionsProvider } from "./contexts/SessionsContext";
import { SessionPersistenceProvider } from "./providers/SessionPersistenceProvider";
import { AuthProvider, useAuth } from "./auth";
import { TokenService } from "./auth";

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
    const { isAuthenticated, isLoading } = useAuth();
    const [open, setOpen] = useState(true);
    const [active, setActive] = useState<TabKey>("simu-evse");

    // Loader pendant le check initial
    if (isLoading) {
        return (
            <div style={{
                minHeight: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#f5f5f5'
            }}>
                <div style={{ fontSize: '18px', color: '#666' }}>Chargement...</div>
            </div>
        );
    }

    // Page 401 — pas de token
    if (!isAuthenticated) {
        return (
            <div style={{
                minHeight: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: '#f5f5f5',
                flexDirection: 'column',
                gap: '20px',
                fontFamily: 'system-ui, sans-serif',
            }}>
                <div style={{
                    backgroundColor: 'white',
                    borderRadius: '12px',
                    padding: '40px',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                    textAlign: 'center',
                    maxWidth: '400px',
                }}>
                    <div style={{ fontSize: '48px', marginBottom: '16px' }}>401</div>
                    <div style={{ fontSize: '18px', color: '#333', marginBottom: '8px' }}>
                        Non authentifie
                    </div>
                    <div style={{ fontSize: '14px', color: '#888', marginBottom: '24px' }}>
                        Connectez-vous sur EVP puis rafraichissez cette page.
                    </div>
                    <a
                        href={TokenService.getEvpUrl()}
                        style={{
                            display: 'inline-block',
                            padding: '10px 24px',
                            backgroundColor: '#dc2626',
                            color: 'white',
                            borderRadius: '8px',
                            fontSize: '14px',
                            textDecoration: 'none',
                        }}
                    >
                        Aller sur EVP
                    </a>
                </div>
            </div>
        );
    }

    // App normale
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
                            <div className="right">
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
