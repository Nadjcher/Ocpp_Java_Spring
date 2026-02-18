import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class TokenService {
  private http = inject(HttpClient);
  private token: string | null = null;
  private gatewayVerified = false;

  constructor() {
    // 1. Chercher dans localStorage (injection manuelle dev)
    this.token = localStorage.getItem('auth_token');
    // 2. Si pas trouve, chercher dans les cookies (EVP)
    if (!this.token) {
      this.token = this.extractTokenFromCookies();
    }
    // Exposer en dev mode pour la console
    if (typeof window !== 'undefined') {
      (window as any).TokenService = {
        setToken: (t: string) => {
          this.setToken(t);
          console.log('[Auth] Token set. Reload the page.');
        },
        getToken: () => this.token,
        clear: () => this.clear(),
      };
    }
  }

  // Lire le token depuis document.cookie
  private extractTokenFromCookies(): string | null {
    if (typeof document === 'undefined') return null;
    const cookies = document.cookie.split(';').map(c => c.trim());

    // Chercher "access_token=eyJ..."
    for (const cookie of cookies) {
      if (cookie.startsWith('access_token=')) {
        return cookie.substring('access_token='.length);
      }
    }

    // Chercher CognitoIdentityServiceProvider.*accessToken
    for (const cookie of cookies) {
      if (cookie.includes('accessToken=')) {
        const value = cookie.split('=').slice(1).join('=');
        if (value.startsWith('eyJ')) return value;
      }
    }

    // Chercher tout cookie dont la valeur est un JWT
    for (const cookie of cookies) {
      const [, ...valueParts] = cookie.split('=');
      const value = valueParts.join('=');
      if (value.startsWith('eyJ') && value.includes('.')) return value;
    }

    return null;
  }

  setToken(token: string): void {
    this.token = token;
    this.gatewayVerified = false;
    localStorage.setItem('auth_token', token);
  }

  getToken(): string | null {
    return this.token;
  }

  clear(): void {
    this.token = null;
    this.gatewayVerified = false;
    localStorage.removeItem('auth_token');
  }

  // Verifier si le token est present et non expire (check local)
  isTokenValid(): boolean {
    if (!this.token) return false;
    try {
      const payload = JSON.parse(atob(this.token.split('.')[1]));
      return Date.now() < payload.exp * 1000;
    } catch {
      return false;
    }
  }

  // Verifier que la gateway accepte le token (check serveur)
  // Appelle GET /apigw/energy-service-manager/tree-roots
  // Retourne true si 200, false sinon
  async verifyWithGateway(): Promise<boolean> {
    if (!this.token) return false;
    if (this.gatewayVerified) return true; // deja verifie dans cette session

    try {
      await firstValueFrom(
        this.http.get('/apigw/energy-service-manager/tree-roots', {
          headers: { Authorization: `Bearer ${this.token}` },
        })
      );
      this.gatewayVerified = true;
      return true;
    } catch {
      return false;
    }
  }

  // Temps restant avant expiration (en secondes)
  getTimeRemaining(): number {
    if (!this.token) return 0;
    try {
      const payload = JSON.parse(atob(this.token.split('.')[1]));
      return Math.max(0, payload.exp - Math.floor(Date.now() / 1000));
    } catch {
      return 0;
    }
  }
}
