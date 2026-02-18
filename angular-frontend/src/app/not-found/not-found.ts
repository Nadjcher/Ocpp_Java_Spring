import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonComponent } from '@totalenergiescode/tds-angular';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, ButtonComponent, TranslocoPipe],
  template: `
    <div class="not-found-container">
      <h1>{{ 'PAGE_NOT_FOUND.TITLE' | transloco }}</h1>
      <p>{{ 'PAGE_NOT_FOUND.MESSAGE' | transloco }}</p>
      <a routerLink="/">
        <button tds-button>{{ 'PAGE_NOT_FOUND.BUTTON' | transloco }}</button>
      </a>
    </div>
  `,
  styles: [`
    .not-found-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 50vh;
      text-align: center;
    }
  `]
})
export class NotFound {}
