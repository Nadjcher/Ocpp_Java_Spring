import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { Environment } from './app/core/config/environment.model';

declare global {
  interface Window {
    env: Environment;
  }
}

bootstrapApplication(App, appConfig)
  .catch(err => console.error(err));
