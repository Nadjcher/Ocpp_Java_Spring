// frontend/src/App.tsx
import React, { useState } from "react";
import "./App.css";
import { TNRProvider } from "./contexts/TNRContext";
import { SessionsProvider } from "./contexts/SessionsContext";
import { SessionPersistenceProvider } from "./providers/SessionPersistenceProvider";

import SimulGPMTab from "./tabs/SimulGPMTab";
import SimuEvseTab from "./tabs/SimuEvseTab";
import PerfOCPPTab from "./tabs/PerfOCPPTab";
import TnrTab from "./tabs/TnrTab";
import SmartChargingTab from "./tabs/SmartChargingTab";
import OCPPMessagesTab from "./tabs/OCPPMessagesTab";
import MLAnalysisTab from "./tabs/MLAnalysisTab";
import SchedulerTab from "./tabs/SchedulerTab";
import { MultiSessionDashboard } from "./components/session";
import { TokenStatusIndicator } from "./components/tte/TokenStatusIndicator";
import "@/styles/buttons.css";

type TabKey =
    | "simul-gpm"
    | "simu-evse"
    | "perf-ocpp"
    | "tnr"
    | "smart-charging"
    | "ocpp-messages"
    | "ml-analysis"
    | "scheduler";

const TABS: { key: TabKey; label: string }[] = [
    { key: "simul-gpm", label: "Simul GPM (Multi)" },
    { key: "simu-evse", label: "Simu EVSE" },
    { key: "perf-ocpp", label: "Perf OCPP (HTTP)" },
    { key: "tnr", label: "TNR" },
    { key: "smart-charging", label: "Smart Charging" },
    { key: "ocpp-messages", label: "OCPP Messages" },
    { key: "ml-analysis", label: "ML Analysis" },
    { key: "scheduler", label: "Scheduler" },
];

export default function App() {
    const [open, setOpen] = useState(true);
    const [active, setActive] = useState<TabKey>("simu-evse");

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
                                {active === "simu-evse" && <SimuEvseTab />}
                                {active === "perf-ocpp" && <PerfOCPPTab />}
                                {active === "tnr" && <TnrTab />}
                                {active === "smart-charging" && <SmartChargingTab />}
                                {active === "ocpp-messages" && <OCPPMessagesTab />}
                                {active === "ml-analysis" && <MLAnalysisTab />}
                                {active === "scheduler" && <SchedulerTab />}
                            </main>
                        </div>
                    </div>
                </TNRProvider>
            </SessionsProvider>
        </SessionPersistenceProvider>
    );
}