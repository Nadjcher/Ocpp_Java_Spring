import { IdentityProvider } from '../../core/config/identity-provider-config.model';

export class AuthenticationUrl {
  private readonly authorizeUrl: string;
  private readonly clientId: string;
  private readonly identityProvider: string;
  private readonly redirectUri: string;
  private readonly responseType: 'code';

  constructor(authorizeUrl: string, redirectUri: string, identityProvider: IdentityProvider) {
    this.authorizeUrl = authorizeUrl;
    this.clientId = identityProvider.clientId;
    this.identityProvider = identityProvider.id;
    this.redirectUri = redirectUri;
    this.responseType = 'code';
  }

  toString(): string {
    const url = new URL(this.authorizeUrl);
    const params = url.searchParams;
    params.append('client_id', this.clientId);
    params.append('identity_provider', this.identityProvider);
    params.append('redirect_uri', this.redirectUri);
    params.append('response_type', this.responseType);
    return url.toString();
  }
}
