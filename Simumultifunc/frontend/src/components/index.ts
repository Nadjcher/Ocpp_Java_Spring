// frontend/src/components/index.ts
// Index principal des composants

// Error Boundary
export { ErrorBoundary } from './ErrorBoundary';

// OCPP Components
export { OCPPMessagePanel } from './OCPPMessagePanel';
export { OCPPMessages } from './OCPPMessages';

// Simulator Components
export { GPMSimulator } from './GPMSimulator';
export { SimulGPM } from './SimulGPM';

// Smart Charging
export { SmartChargingPanel } from './SmartChargingPanel';

// Charging Profile
export { ChargingProfileGraph } from './ChargingProfileGraph';
export { ChargingProfileIndicator } from './ChargingProfileIndicator';

// Session Components
export { SessionPanel } from './SessionPanel';

// Performance
export { default as PerfRunner } from './PerfRunner';
export { PerfOCPP } from './PerfOCPP';

// TNR
export { TNRPanel } from './TNRPanel';
export { default as TnrPlusPanel } from './TnrPlusPanel';

// Phasing
export { default as PhasingSection } from './PhasingSection';
