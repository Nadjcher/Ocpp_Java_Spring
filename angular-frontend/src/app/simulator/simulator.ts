import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TokenService } from '../authentication/token.service';
import { DecimalPipe } from '@angular/common';

@Component({
  selector: 'app-simulator',
  imports: [DecimalPipe],
  template: `
    <div class="simulator-container">
      <header class="simulator-header">
        <h1>GPM Simulator</h1>
        <div class="header-actions">
          <span class="token-info">
            Token expire dans {{ timeRemaining | number:'1.0-0' }}s
          </span>
          <button class="btn-secondary" (click)="logout()">
            Deconnexion
          </button>
        </div>
      </header>

      <main class="simulator-content">
        <p>Bienvenue dans le simulateur GPM.</p>
        <p>Vous etes authentifie avec succes.</p>

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
  `]
})
export class Simulator implements OnInit {
  private tokenService = inject(TokenService);
  private router = inject(Router);
  timeRemaining = 0;

  ngOnInit(): void {
    this.timeRemaining = this.tokenService.getTimeRemaining();
  }

  logout(): void {
    this.tokenService.clear();
    this.router.navigate(['/unauthorized']);
  }
}
