import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TokenService } from './core/auth/token.service';
import { TokenInput } from './core/auth/token-input/token-input';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, TokenInput],
  template: `
    <main class="app-container">
      <!-- Afficher le simulateur normalement -->
      <router-outlet />
    </main>

    <!-- Overlay token uniquement si pas authentifié -->
    @if (!tokenService.isAuthenticated()) {
      <app-token-input />
    }
  `,
  styles: [`
    .app-container {
      min-height: 100vh;
      padding: var(--tds-size-spacing-200);
    }
  `]
})
export class App {
  protected tokenService = inject(TokenService);
}
