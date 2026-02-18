import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, TranslocoPipe],
  template: `
    <div class="not-found-container">
      <h1>{{ 'PAGE_NOT_FOUND.TITLE' | transloco }}</h1>
      <p>{{ 'PAGE_NOT_FOUND.MESSAGE' | transloco }}</p>
      <a routerLink="/">
        <button class="btn-primary">{{ 'PAGE_NOT_FOUND.BUTTON' | transloco }}</button>
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
