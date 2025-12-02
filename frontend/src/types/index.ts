// frontend/src/types/index.ts
// Fichier d'export principal des types

// Exporter tous les types depuis le fichier unifié
export * from './session.types';

// Réexporter les types de status mapping pour compatibilité
export type { FrontendStatus, BackendStatus } from '@/utils/statusMapping';
