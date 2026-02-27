import { Component } from '@angular/core';

@Component({
  selector: 'app-unauthorized',
  standalone: true,
  template: `
    <div class="unauthorized-container">
      <h1>401</h1>
      <h2>Session non authentifiee</h2>
      <p>Aucun token d'acces valide n'a ete trouve.</p>
      <p>Veuillez vous connecter sur la plateforme EVP puis revenir ici.</p>
      <a href="https://evplatform.evcharge-pp.totalenergies.com" class="evp-link">
        Se connecter sur EVP
      </a>
      <hr />
      <details>
        <summary>Mode developpeur</summary>
        <p>Dans la console (F12) :</p>
        <code>TokenService.setToken("votre_token_jwt")</code>
        <p>Puis rechargez la page (F5).</p>
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
    details { color: #999; font-size: 0.9rem; }
    code {
      display: block;
      background: #f4f4f4;
      padding: 8px 12px;
      margin: 8px 0;
      border-radius: 4px;
      font-size: 0.85rem;
    }
  `]
})
export class UnauthorizedComponent {}
