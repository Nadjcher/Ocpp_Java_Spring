import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <main class="app-container">
      <router-outlet />
    </main>
  `,
  styles: [`
    .app-container {
      min-height: 100vh;
      padding: var(--tds-size-spacing-200);
    }
  `]
})
export class App {}
