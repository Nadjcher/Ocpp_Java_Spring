import { Component } from '@angular/core';

@Component({
  selector: 'app-simulator',
  template: `
    <div class="simulator-container">
      <header class="simulator-header">
        <h1>GPM Simulator</h1>
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
    .simulator-content {
      padding: var(--tds-size-spacing-200);
    }
  `]
})
export class Simulator {}
