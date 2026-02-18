import { AuthenticationConfig } from './authentication-config.model';

export interface Environment {
  authentication: AuthenticationConfig;
  portalSdkUrl: string;
}
