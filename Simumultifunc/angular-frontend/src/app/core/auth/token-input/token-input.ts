import { Component, inject } from '@angular/core';
import { TokenService } from '../token.service';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-token-input',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="token-input-overlay">
      <div class="token-input-card">
        <h2>Session non authentifiée</h2>
        <p>
          Collez votre token EVP ci-dessous.<br>
          <small>Récupérez-le depuis les cookies EVP (F12 → Application → Cookies → evp_access_token)</small>
        </p>
        <textarea
          [(ngModel)]="tokenValue"
          placeholder="eyJraWQ..."
          rows="6"
        ></textarea>
        @if (error) {
          <p class="error">{{ error }}</p>
        }
        <button (click)="submit()">Valider le token</button>
      </div>
    </div>
  `,
  styles: [`
    .token-input-overlay {
      position: fixed; inset: 0;
      display: flex; align-items: center; justify-content: center;
      background: rgba(0,0,0,0.5); z-index: 9999;
    }
    .token-input-card {
      background: white; border-radius: 8px; padding: 32px;
      max-width: 600px; width: 90%; box-shadow: 0 4px 24px rgba(0,0,0,0.2);
    }
    h2 { margin: 0 0 8px; color: #e53935; }
    p { margin: 0 0 16px; color: #666; }
    small { color: #999; }
    textarea {
      width: 100%; font-family: monospace; font-size: 12px;
      border: 1px solid #ddd; border-radius: 4px; padding: 8px;
      resize: vertical;
    }
    .error { color: #e53935; margin: 8px 0; }
    button {
      margin-top: 12px; padding: 10px 24px;
      background: #1976d2; color: white; border: none;
      border-radius: 4px; cursor: pointer; font-size: 14px;
    }
    button:hover { background: #1565c0; }
  `]
})
export class TokenInput {
  private tokenService = inject(TokenService);

  tokenValue = '';
  error = '';

  submit(): void {
    const token = this.tokenValue.trim();
    if (!token.startsWith('eyJ')) {
      this.error = 'Le token doit commencer par "eyJ..."';
      return;
    }

    // Décoder pour vérifier la validité
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      if (payload.exp * 1000 < Date.now()) {
        this.error = 'Ce token est expiré.';
        return;
      }
    } catch {
      this.error = 'Token JWT invalide.';
      return;
    }

    this.tokenService.setToken(token);
    this.error = '';
  }
}
