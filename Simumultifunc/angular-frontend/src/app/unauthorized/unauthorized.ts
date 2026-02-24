import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TokenService } from '../core/auth/token.service';

@Component({
  selector: 'app-unauthorized',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="unauthorized-container">
      <h1>401</h1>
      <h2>Session non authentifiee</h2>
      <p>Aucun token d'acces valide n'a ete trouve.</p>
      <p>Veuillez vous connecter sur la plateforme EVP puis revenir ici.</p>
      <a href="https://pp.total-ev-charge.com" class="evp-link">
        Se connecter sur EVP
      </a>

      <hr />

      <details>
        <summary>Mode developpeur</summary>

        <div class="dev-section">
          <p>Collez votre token JWT ci-dessous :</p>
          <small>Recuperez-le depuis EVP (F12 → Application → Cookies → evp_access_token)</small>

          <textarea
            [(ngModel)]="tokenValue"
            placeholder="eyJraWQ..."
            rows="4"
            class="token-textarea"
          ></textarea>

          @if (error) {
            <p class="error">{{ error }}</p>
          }

          <button class="btn-primary" (click)="submitToken()">
            Valider le token
          </button>

          <hr />

          <p>Ou via la console (F12) :</p>
          <code>localStorage.setItem('evp_access_token', 'votre_token_jwt')</code>
          <p><small>Puis rechargez la page (F5).</small></p>
        </div>
      </details>
    </div>
  `,
  styles: [`
    .unauthorized-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 60vh;
      text-align: center;
      font-family: Arial, sans-serif;
    }
    h1 { font-size: 4rem; margin-bottom: 0; color: #e74c3c; }
    h2 { color: #333; }
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
    hr { width: 200px; margin: 2rem 0; border: none; border-top: 1px solid #ddd; }
    details { color: #666; font-size: 0.9rem; max-width: 500px; width: 100%; }
    summary { cursor: pointer; color: #999; margin-bottom: 1rem; }
    .dev-section { text-align: left; padding: 0 1rem; }
    .token-textarea {
      width: 100%;
      font-family: monospace;
      font-size: 12px;
      border: 1px solid #ddd;
      border-radius: 4px;
      padding: 8px;
      resize: vertical;
      margin: 8px 0;
    }
    .error { color: #e53935; margin: 8px 0; }
    .btn-primary {
      padding: 10px 24px;
      background: #1976d2;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 14px;
    }
    .btn-primary:hover { background: #1565c0; }
    code {
      display: block;
      background: #f4f4f4;
      padding: 8px 12px;
      margin: 8px 0;
      border-radius: 4px;
      font-size: 0.85rem;
      text-align: left;
    }
  `]
})
export class UnauthorizedComponent {
  private tokenService = inject(TokenService);
  private router = inject(Router);

  tokenValue = '';
  error = '';

  submitToken(): void {
    const token = this.tokenValue.trim();

    if (!token.startsWith('eyJ')) {
      this.error = 'Le token doit commencer par "eyJ..."';
      return;
    }

    try {
      const parts = token.split('.');
      if (parts.length !== 3) {
        this.error = 'Token JWT invalide (format: header.payload.signature).';
        return;
      }
      const payload = JSON.parse(
        atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
      );
      if (!payload.exp) {
        this.error = 'Token invalide: champ "exp" manquant.';
        return;
      }
      if (payload.exp * 1000 < Date.now()) {
        this.error = 'Ce token est expire.';
        return;
      }
    } catch {
      this.error = 'Token JWT invalide.';
      return;
    }

    this.tokenService.setToken(token);
    this.error = '';
    this.router.navigate(['/simulator']);
  }
}
