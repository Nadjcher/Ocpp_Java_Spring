// ═══════════════════════════════════════════════════════════════════════════
// OCPI Module Types - Refonte complète
// ═══════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
// Enums & Constantes
// ─────────────────────────────────────────────────────────────────────────────

export type OCPIRole = 'CPO' | 'MSP' | 'HUB';
export type OCPIVersion = '2.1.1' | '2.2' | '2.2.1';
export type AuthType = 'token' | 'oauth2' | 'cognito';
export type TestStatus = 'passed' | 'failed' | 'error' | 'skipped' | 'running' | 'pending';

export type OCPIModule =
  | 'credentials'
  | 'locations'
  | 'sessions'
  | 'cdrs'
  | 'tariffs'
  | 'tokens'
  | 'commands'
  | 'chargingprofiles'
  | 'hubclientinfo';

export const OCPI_MODULES: { id: OCPIModule; label: string; description: string }[] = [
  { id: 'credentials', label: 'Credentials', description: 'Gestion des credentials OCPI' },
  { id: 'locations', label: 'Locations', description: 'Points de charge et EVSEs' },
  { id: 'sessions', label: 'Sessions', description: 'Sessions de charge actives' },
  { id: 'cdrs', label: 'CDRs', description: 'Charge Detail Records' },
  { id: 'tariffs', label: 'Tariffs', description: 'Tarification' },
  { id: 'tokens', label: 'Tokens', description: 'Badges et tokens' },
  { id: 'commands', label: 'Commands', description: 'Commandes (Start/Stop/Reserve)' },
  { id: 'chargingprofiles', label: 'Charging Profiles', description: 'Profils de charge' },
  { id: 'hubclientinfo', label: 'Hub Client Info', description: 'Info client hub' },
];

export const OCPI_ROLES: { id: OCPIRole; label: string; color: string }[] = [
  { id: 'CPO', label: 'CPO', color: '#3b82f6' },
  { id: 'MSP', label: 'MSP', color: '#10b981' },
  { id: 'HUB', label: 'HUB', color: '#8b5cf6' },
];

// ─────────────────────────────────────────────────────────────────────────────
// Configuration Globale Environnements
// ─────────────────────────────────────────────────────────────────────────────

export type GlobalEnvId = 'pp' | 'test' | 'prod';
export type ConnectionStatus = 'connected' | 'error' | 'unknown' | 'testing';
export type OcppProtocol = 'ocpp1.6' | 'ocpp2.0.1';

export interface GlobalEnvironment {
  id: GlobalEnvId;
  name: string;                      // "PP", "TEST", "PROD"
  displayName: string;               // "Pré-Production", "Test", "Production"
  color: string;                     // Pour l'UI

  // Connexion OCPI
  ocpiBaseUrl: string;
  ocpiVersion: OCPIVersion;
  ocpiToken: string;
  ocpiTokenType: AuthType;

  // Cognito (si tokenType = 'cognito')
  cognitoClientId?: string;
  cognitoClientSecret?: string;
  cognitoTokenUrl?: string;

  // Connexion WebSocket OCPP (CSMS)
  wsUrl: string;
  wsProtocol: OcppProtocol;

  // État
  isActive: boolean;
  lastTestedAt?: string;
  ocpiStatus: ConnectionStatus;
  wsStatus: ConnectionStatus;
  activeWsConnections: number;
  lastError?: string;
}

export interface GlobalConfig {
  activeEnvironmentId: GlobalEnvId;
  environments: GlobalEnvironment[];
}

export const DEFAULT_GLOBAL_CONFIG: GlobalConfig = {
  activeEnvironmentId: 'pp',
  environments: [
    {
      id: 'pp',
      name: 'PP',
      displayName: 'Pré-Production',
      color: '#3b82f6',
      ocpiBaseUrl: 'https://pp-api.tte.total.com/ocpi',
      ocpiVersion: '2.2.1',
      ocpiToken: '',
      ocpiTokenType: 'cognito',
      cognitoClientId: '',
      cognitoClientSecret: '',
      cognitoTokenUrl: 'https://cognito-idp.eu-west-1.amazonaws.com/xxx/oauth2/token',
      wsUrl: 'wss://pp-ocpp.tte.total.com/ocpp',
      wsProtocol: 'ocpp2.0.1',
      isActive: true,
      ocpiStatus: 'unknown',
      wsStatus: 'unknown',
      activeWsConnections: 0,
    },
    {
      id: 'test',
      name: 'TEST',
      displayName: 'Test',
      color: '#f59e0b',
      ocpiBaseUrl: 'https://test-api.tte.total.com/ocpi',
      ocpiVersion: '2.2.1',
      ocpiToken: '',
      ocpiTokenType: 'token',
      wsUrl: 'wss://test-ocpp.tte.total.com/ocpp',
      wsProtocol: 'ocpp1.6',
      isActive: false,
      ocpiStatus: 'unknown',
      wsStatus: 'unknown',
      activeWsConnections: 0,
    },
    {
      id: 'prod',
      name: 'PROD',
      displayName: 'Production',
      color: '#ef4444',
      ocpiBaseUrl: 'https://api.tte.total.com/ocpi',
      ocpiVersion: '2.2.1',
      ocpiToken: '',
      ocpiTokenType: 'cognito',
      cognitoClientId: '',
      cognitoClientSecret: '',
      cognitoTokenUrl: 'https://cognito-idp.eu-west-1.amazonaws.com/prod/oauth2/token',
      wsUrl: 'wss://ocpp.tte.total.com/ocpp',
      wsProtocol: 'ocpp2.0.1',
      isActive: false,
      ocpiStatus: 'unknown',
      wsStatus: 'unknown',
      activeWsConnections: 0,
    },
  ],
};

// ─────────────────────────────────────────────────────────────────────────────
// Partenaires
// ─────────────────────────────────────────────────────────────────────────────

export interface OCPIPartner {
  id: string;
  name: string;
  code: string;                    // Ex: "SHELL", "IONITY", "FASTNED"
  countryCode: string;             // Ex: "FR", "DE", "NL"
  partyId: string;                 // Ex: "SHR", "ION", "FNE"
  role: OCPIRole;
  ocpiVersion: OCPIVersion;

  // Environnements
  environments: OCPIEnvironment[];
  activeEnvironmentId: string;

  // Modules supportés
  modules: OCPIModule[];

  // Endpoints découverts
  endpoints?: Record<string, string>;

  // Métadonnées
  logo?: string;
  color?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface OCPIEnvironment {
  id: string;
  name: string;                    // "Production", "Staging", "Test", "Local"
  baseUrl: string;
  versionsUrl?: string;

  // Authentification
  authType: AuthType;
  token?: string;                  // Token OCPI direct (Token A/B/C)
  tokenA?: string;
  tokenB?: string;
  tokenC?: string;
  oauth2?: OAuth2Config;
  cognito?: CognitoConfig;

  // Headers additionnels
  customHeaders?: Record<string, string>;

  // État
  isActive: boolean;
  lastTestedAt?: string;
  lastTestResult?: 'success' | 'failed' | 'unknown';
  lastTestError?: string;
}

export interface OAuth2Config {
  tokenUrl: string;
  clientId: string;
  clientSecret: string;
  scope?: string;
  grantType?: 'client_credentials' | 'password';
}

export interface CognitoConfig {
  tokenUrl: string;
  clientId: string;
  clientSecret: string;
  region?: string;
  userPoolId?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Tests
// ─────────────────────────────────────────────────────────────────────────────

export interface OCPIQuickTest {
  id: string;
  name: string;
  description?: string;
  module: OCPIModule;
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

  // Path/Endpoint (supports both names)
  path: string;                    // Ex: "/locations", "/sessions/{session_id}"
  endpoint?: string;               // Alias for path

  // Paramètres configurables (unified)
  params?: QuickTestParam[];       // All params (path + query)
  pathParams?: QuickTestParam[];   // Legacy: path params only
  queryParams?: QuickTestParam[];  // Legacy: query params only
  bodyTemplate?: string;           // JSON template avec {{variables}}

  // Validation
  expectedStatus?: number;
  validations?: OCPIValidation[];

  // Métadonnées
  category?: 'connectivity' | 'crud' | 'business' | 'edge-case';
  isBuiltIn?: boolean;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface QuickTestParam {
  // Identification (supports both naming conventions)
  name: string;                    // Parameter name
  key?: string;                    // Alias for name
  label?: string;                  // Display label

  // Type
  type: 'path' | 'query' | 'header' | 'string' | 'number' | 'date' | 'datetime' | 'select' | 'boolean';
  required: boolean;

  // Value
  value?: string;                  // Current value
  defaultValue?: string;           // Default value

  // UI configuration
  options?: { value: string; label: string }[];
  placeholder?: string;
  description?: string;
}

export interface OCPIValidation {
  field: string;                   // JSONPath: "$.data.status", "$.data[0].id"
  operator: ValidationOperator;
  expected: any;
  message?: string;
}

export type ValidationOperator =
  | 'equals'
  | 'not_equals'
  | 'notEquals'
  | 'contains'
  | 'not_contains'
  | 'notContains'
  | 'exists'
  | 'not_exists'
  | 'notNull'
  | 'isNull'
  | 'isArray'
  | 'isObject'
  | 'arrayLength'
  | 'arrayLengthGreaterThan'
  | 'arrayLengthLessThan'
  | 'greater_than'
  | 'greaterThan'
  | 'less_than'
  | 'lessThan'
  | 'matches';    // regex

// ─────────────────────────────────────────────────────────────────────────────
// Scénarios de Tests
// ─────────────────────────────────────────────────────────────────────────────

export interface OCPITestScenario {
  id: string;
  name: string;
  description: string;
  partnerId?: string;              // Optionnel: lié à un partenaire spécifique

  // Configuration
  tags: string[];                  // Ex: ["smoke", "roaming", "regression"]
  category: ScenarioCategory;
  priority: 'high' | 'medium' | 'low';

  // Étapes
  steps?: OCPITestStep[];

  // Variables globales du scénario
  variables?: Record<string, string>;

  // Exécution
  continueOnError?: boolean;
  timeout?: number;                // secondes
  retryCount?: number;
  retryDelay?: number;             // ms

  // Métadonnées
  isBuiltIn?: boolean;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
  lastRunAt?: string;
  lastRunResult?: TestStatus;
}

export type ScenarioCategory = 'smoke' | 'integration' | 'e2e' | 'regression' | 'performance' | 'security' | 'custom';

export const SCENARIO_CATEGORIES: { id: ScenarioCategory; label: string }[] = [
  { id: 'smoke', label: 'Smoke' },
  { id: 'integration', label: 'Integration' },
  { id: 'e2e', label: 'E2E' },
  { id: 'regression', label: 'Regression' },
  { id: 'performance', label: 'Performance' },
  { id: 'security', label: 'Security' },
  { id: 'custom', label: 'Custom' },
];

export interface OCPITestStep {
  id: string;
  order: number;
  name: string;
  description?: string;

  // Action
  module: OCPIModule;
  action?: string;                 // Ex: "getLocations", "startSession", "postCredentials"
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  endpoint: string;

  // Direct properties (simplified)
  headers?: Record<string, string>;
  body?: string;
  expectedStatus?: number;
  continueOnFailure?: boolean;
  saveResponseAs?: Record<string, string>;

  // Configuration (alternative)
  config?: StepConfig;

  // Variables (input/output)
  inputVariables?: Record<string, string>;   // Utiliser des variables du contexte
  outputVariables?: Record<string, string>;  // Sauvegarder dans le contexte (jsonPath -> varName)

  // Validation
  validations?: OCPIValidation[];

  // Comportement
  optional?: boolean;              // Si true, échec n'arrête pas le scénario
  delayAfter?: number;             // ms avant la prochaine étape
  condition?: string;              // Expression pour exécution conditionnelle
  timeoutMs?: number;              // Timeout spécifique pour cette étape
}

export interface StepConfig {
  pathParams?: Record<string, string>;
  queryParams?: Record<string, string>;
  body?: any;
  headers?: Record<string, string>;
}

// ─────────────────────────────────────────────────────────────────────────────
// Résultats
// ─────────────────────────────────────────────────────────────────────────────

export interface OCPITestResult {
  id: string;
  type: 'quick' | 'scenario';
  testId: string;                  // ID du QuickTest ou Scenario
  testName: string;
  partnerId: string;
  partnerName: string;
  environmentId: string;
  environmentName: string;

  // Timing
  startedAt: string;
  completedAt: string;
  durationMs: number;

  // Résultat
  status: TestStatus;
  errorMessage?: string;

  // Détails requête/réponse
  request?: RequestDetails;
  response?: ResponseDetails;

  // Validations
  validationResults?: ValidationResult[];

  // Pour les scénarios
  stepResults?: OCPIStepResult[];

  // Contexte (variables extraites)
  context?: Record<string, any>;
}

export interface RequestDetails {
  method: string;
  url: string;
  headers: Record<string, string>;
  body?: any;
}

export interface ResponseDetails {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  body: any;
  durationMs: number;
}

export interface ValidationResult {
  validation: OCPIValidation;
  passed: boolean;
  actualValue?: any;
  message?: string;
}

export interface OCPIStepResult {
  stepId: string;
  stepName: string;
  stepOrder: number;
  status: TestStatus;
  durationMs: number;
  request?: RequestDetails;
  response?: ResponseDetails;
  validationResults?: ValidationResult[];
  errorMessage?: string;
  extractedVariables?: Record<string, any>;
}

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

export type OCPITab = 'partners' | 'quicktests' | 'scenarios' | 'history';

export interface OCPIFilters {
  partners: {
    search: string;
    roles: OCPIRole[];
    versions: OCPIVersion[];
  };
  quickTests: {
    modules: OCPIModule[];
    categories: string[];
  };
  scenarios: {
    search: string;
    tags: string[];
    categories: ScenarioCategory[];
    priorities: ('high' | 'medium' | 'low')[];
  };
  history: {
    partnerId?: string;
    status?: TestStatus;
    type?: 'quick' | 'scenario';
    dateFrom?: string;
    dateTo?: string;
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// API Request/Response Types
// ─────────────────────────────────────────────────────────────────────────────

export interface ExecuteQuickTestRequest {
  partnerId: string;
  environmentId: string;
  testId: string;
  params?: {
    pathParams?: Record<string, string>;
    queryParams?: Record<string, string>;
    body?: any;
  };
}

export interface ExecuteScenarioRequest {
  partnerId: string;
  environmentId: string;
  scenarioId: string;
  variables?: Record<string, string>;
}

export interface TestConnectionRequest {
  partnerId: string;
  environmentId: string;
}

export interface TestConnectionResponse {
  success: boolean;
  latencyMs?: number;
  versions?: string[];
  endpoints?: Record<string, string>;
  error?: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

export function getPartnerStatusColor(partner: OCPIPartner): 'green' | 'yellow' | 'red' {
  const activeEnv = partner.environments.find(e => e.id === partner.activeEnvironmentId);
  if (!activeEnv) return 'red';
  if (activeEnv.lastTestResult === 'success') return 'green';
  if (activeEnv.lastTestResult === 'failed') return 'red';
  return 'yellow';
}

export function getTestStatusColor(status: TestStatus): string {
  switch (status) {
    case 'passed': return '#10b981';
    case 'failed': return '#ef4444';
    case 'error': return '#f97316';
    case 'skipped': return '#6b7280';
    case 'running': return '#3b82f6';
    case 'pending': return '#9ca3af';
    default: return '#6b7280';
  }
}

export function getTestStatusLabel(status: TestStatus): string {
  switch (status) {
    case 'passed': return 'Passed';
    case 'failed': return 'Failed';
    case 'error': return 'Error';
    case 'skipped': return 'Skipped';
    case 'running': return 'Running';
    case 'pending': return 'Pending';
    default: return status;
  }
}

export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = ((ms % 60000) / 1000).toFixed(0);
  return `${minutes}m ${seconds}s`;
}

// Création d'un partenaire vide
export function createEmptyPartner(): Omit<OCPIPartner, 'id' | 'createdAt' | 'updatedAt'> {
  return {
    name: '',
    code: '',
    countryCode: 'FR',
    partyId: '',
    role: 'CPO',
    ocpiVersion: '2.2.1',
    environments: [createEmptyEnvironment('test', 'Test')],
    activeEnvironmentId: 'test',
    modules: ['credentials', 'locations'],
  };
}

export function createEmptyEnvironment(id: string, name: string): OCPIEnvironment {
  return {
    id,
    name,
    baseUrl: '',
    authType: 'token',
    isActive: id === 'test',
  };
}

export function createEmptyQuickTest(): Omit<OCPIQuickTest, 'id'> {
  return {
    name: '',
    description: '',
    module: 'locations',
    method: 'GET',
    path: '',
    endpoint: '',
    params: [],
    pathParams: [],
    queryParams: [],
    expectedStatus: 200,
    validations: [],
    category: 'crud',
    isBuiltIn: false,
    enabled: true,
  };
}

export function createEmptyScenario(): Omit<OCPITestScenario, 'id' | 'createdAt' | 'updatedAt'> {
  return {
    name: '',
    description: '',
    tags: [],
    category: 'custom',
    priority: 'medium',
    steps: [],
    continueOnError: false,
    timeout: 60,
    retryCount: 0,
    retryDelay: 1000,
    isBuiltIn: false,
    enabled: true,
  };
}

export function createEmptyStep(): Omit<OCPITestStep, 'id' | 'order'> {
  return {
    name: '',
    module: 'locations',
    action: 'get',
    method: 'GET',
    endpoint: '',
    config: {},
    validations: [],
    optional: false,
  };
}
