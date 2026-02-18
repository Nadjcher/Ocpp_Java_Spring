import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
  isDevMode,
} from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { routes } from './app.routes';
import { initializePortalSdk } from 'portal-sdk-loader';
import { Config } from './core/config/config';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { TranslocoHttpLoader } from './transloco-loader';
import { provideTransloco, TranslocoService } from '@jsverse/transloco';
import { authInterceptor } from './core/interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(
      withInterceptors([authInterceptor])
    ),
    provideTransloco({
      config: {
        availableLangs: ['fr-FR', 'en-GB'],
        defaultLang: 'fr-FR',
        fallbackLang: ['en-GB', 'fr-FR'],
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader
    }),
    provideAppInitializer(() => {
      const config = inject(Config);
      const router = inject(Router);
      const transloco = inject(TranslocoService);
      initializePortalSdk(config.portalSdkUrl, {
        linkBehaviorOverride: {
          linkKeys: [],
          onClick: link => router.navigate([link.path]),
        },
        onLanguageChange: language => transloco.setActiveLang(language),
      });
    }),
  ],
};
