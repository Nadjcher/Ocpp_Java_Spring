import { Component, inject, OnInit } from '@angular/core';
import { AuthenticationService } from '../authentication/authentication.service';
import { ButtonComponent } from '@totalenergiescode/tds-angular';

@Component({
  selector: 'app-simulator',
  imports: [ButtonComponent],
  template: `
    <div class="simulator-container">
      <header class="simulator-header">
        <h1>GPM Simulator</h1>
        <div class="header-actions">
          @if (tokenInfo) {
            <span class="token-info">
              Token expire dans {{ tokenInfo.expiresIn | number:'1.0-0' }}s
            </span>
          }
          <button tds-button variant="secondary" (click)="logout()">
            Deconnexion
          </button>
        </div>
      </header>

      <main class="simulator-content">
        <p>Bienvenue dans le simulateur GPM.</p>
        <p>Vous etes authentifie avec succes.</p>

        @if (tokenInfo) {
          <div class="token-details">
            <h3>Informations du token</h3>
            <pre>{{ tokenInfo | json }}</pre>
          </div>
        }

        <!-- TODO: Ajouter les composants du simulateur ici -->
      </main>
    </div>
  `,
  styles: [`
    .simulator-container {
      max-width: 1200px;
      margin: 0 auto;
    }
    .simulator-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: var(--tds-size-spacing-200);
      border-bottom: 1px solid var(--tds-color-border-default);
    }
    .header-actions {
      display: flex;
      gap: var(--tds-size-spacing-100);
      align-items: center;
    }
    .token-info {
      font-size: 0.875rem;
      color: var(--tds-color-text-secondary);
    }
    .simulator-content {
      padding: var(--tds-size-spacing-200);
    }
    .token-details {
      margin-top: var(--tds-size-spacing-200);
      padding: var(--tds-size-spacing-100);
      background: var(--tds-color-surface-secondary);
      border-radius: var(--tds-size-radius-100);
    }
    .token-details pre {
      font-size: 0.75rem;
      overflow-x: auto;
    }
  `]
})
export class Simulator implements OnInit {
  private authService = inject(AuthenticationService);
  tokenInfo: { sub: string; exp: number; scope: string; expiresIn: number } | null = null;

  ngOnInit(): void {
    this.tokenInfo = this.authService.getTokenInfo();
  }

  logout(): void {
    this.authService.logout();
  }
}
