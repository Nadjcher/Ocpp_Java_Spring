import { IdentityProvider } from './identity-provider-config.model';

export interface AuthenticationConfig {
  authorizeUrl: string;
  identityProviders: Array<IdentityProvider>;
  redirectUri: string;
}
