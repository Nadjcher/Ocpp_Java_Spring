// frontend/src/components/index.ts
// Export centralisé des composants

// =============================================================================
// PERFORMANCE OCPP
// =============================================================================
// PerfOCPPPanel: Composant principal avec pool navigateur + runner HTTP
// PerformanceOCPP: Dashboard métriques simple
// PerfOCPP: Version basique (DEPRECATED - utiliser PerfOCPPPanel)
export { default as PerfOCPPPanel } from './PerfOCPPPanel';
export { PerformanceOCPP } from './PerformanceOCPP';
export { PerfOCPP } from './PerfOCPP';  // DEPRECATED

// =============================================================================
// SMART CHARGING
// =============================================================================
// SmartChargingPanel: Gestionnaire de profils local avec UI moderne
// SmartCharging: Intégration backend API
export { SmartChargingPanel } from './SmartChargingPanel';
export { SmartCharging } from './SmartCharging';

// =============================================================================
// TNR (TEST NON-RÉGRESSION)
// =============================================================================
export { TNRPanel } from './TNRPanel';
export { default as TnrPlusPanel } from './TnrPlusPanel';
export { default as TnrComparePanel } from './TnrComparePanel';
export { TNRManager } from './TNRManager';

// =============================================================================
// SESSIONS
// =============================================================================
export { SessionOverview } from './SessionOverview';
export { SessionList } from './SessionList';
export { SessionPanel } from './SessionPanel';

// =============================================================================
// OCPP
// =============================================================================
export { OCPPMessages } from './OCPPMessages';
export { OCPPMessagePanel } from './OCPPMessagePanel';
export { OCPPMonitor } from './OCPPMonitor';

// =============================================================================
// PERFORMANCE / MONITORING
// =============================================================================
export { default as PerformancePanel } from './PerformancePanel';
export { default as PerfRunner } from './PerfRunner';
export { default as PerfHttpControl } from './PerfHttpControl';

// =============================================================================
// SIMULATION
// =============================================================================
export { SimuEVSE } from './SimuEVSE';
export { SimulGPM } from './SimulGPM';
export { GPMSimulator } from './GPMSimulator';
export { SimulationPanel } from './SimulationPanel';
export { default as ChargingProfilesPanel } from './ChargingProfilesPanel';
export { default as PhasingSection } from './PhasingSection';

// =============================================================================
// UI COMPONENTS
// =============================================================================
export { default as ErrorBoundary } from './ErrorBoundary';
export { default as LogView } from './LogView';
export { LogViewer } from './LogViewer';

// =============================================================================
// SMART CHARGING GRAPH
// =============================================================================
export { ChargingProfileGraph } from './ChargingProfileGraph';
export { ChargingProfileIndicator } from './ChargingProfileIndicator';

// =============================================================================
// SIMU-EVSE SUB-COMPONENTS
// =============================================================================
export * from './simu-evse';
export * from './evse';
