// frontend/src/services/index.ts
// Export centralisé des services

export { api } from './api';
export { wsManager, useWebSocketManager, default as WebSocketManager } from './WebSocketManager';
export { subscribeToWebSocket, getWebSocketService } from './websocket';
export { VehicleProfileService } from './VehicleProfileService';
export { OCPPChargingProfilesManager } from './OCPPChargingProfilesManager';
