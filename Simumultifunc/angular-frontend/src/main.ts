import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { Environment } from './app/core/config/environment.model';
import { TokenService } from './app/core/auth/token.service';
import { isDevMode } from '@angular/core';

declare global {
  interface Window {
    env: Environment;
    TokenService?: TokenService;
  }
}

bootstrapApplication(App, appConfig)
  .then(appRef => {
    // Expose TokenService globally for dev console and gpm-auth-proxy
    if (isDevMode()) {
      const tokenService = appRef.injector.get(TokenService);
      window.TokenService = tokenService;
      console.log('[Dev] TokenService exposed. Usage: TokenService.setToken("eyJ...")');
    }
  })
  .catch(err => console.error(err));
