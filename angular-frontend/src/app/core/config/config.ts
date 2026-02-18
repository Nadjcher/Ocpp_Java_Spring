import { Injectable } from '@angular/core';
import { AuthenticationConfig } from './authentication-config.model';

@Injectable({ providedIn: 'root' })
export class Config {
  get authentication(): AuthenticationConfig {
    return window.env.authentication;
  }
  get portalSdkUrl(): string {
    return window.env.portalSdkUrl;
  }
}
