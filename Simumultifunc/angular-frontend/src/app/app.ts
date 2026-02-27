import { Component, inject, isDevMode } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TokenService } from './core/auth/token.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    @if (tokenService.isAuthenticated()) {
      <main class="app-container">
        <router-outlet />
      </main>
    } @else {
      <div class="unauthorized-container">
        <h1>401 — Non authentifie</h1>
        <p>Connectez-vous sur la plateforme EVP puis rechargez cette page.</p>
        <a href="https://evplatform.evcharge-test.totalenergies.com" target="_blank" class="evp-link">
          Se connecter sur EVP
        </a>
      </div>
    }
  `,
  styles: [`
    .app-container {
      min-height: 100vh;
      padding: var(--tds-size-spacing-200);
    }
    .unauthorized-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 60vh;
      text-align: center;
      font-family: Arial, sans-serif;
    }
    h1 { font-size: 2rem; color: #e74c3c; }
    p { color: #666; max-width: 400px; }
    .evp-link {
      display: inline-block;
      margin-top: 1rem;
      padding: 12px 24px;
      background: #e74c3c;
      color: white;
      text-decoration: none;
      border-radius: 4px;
      font-weight: bold;
    }
    .evp-link:hover { background: #c0392b; }
  `]
})
export class App {
  protected tokenService = inject(TokenService);

  constructor() {
    if (isDevMode()) {
      (window as any)['TokenService'] = this.tokenService;
    }
  }
}
