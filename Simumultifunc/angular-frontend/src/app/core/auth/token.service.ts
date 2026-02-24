import { Injectable, signal, computed } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TokenService {
  // Signal réactif pour le token
  private tokenSignal = signal<string | null>(null);

  // État dérivé
  public token = this.tokenSignal.asReadonly();
  public isAuthenticated = computed(() => {
    const t = this.tokenSignal();
    return t !== null && !this.isExpired(t);
  });
  public tokenInfo = computed(() => {
    const t = this.tokenSignal();
    return t ? this.decodeJwt(t) : null;
  });

  constructor() {
    this.loadToken();
  }

  /**
   * Charge le token dans cet ordre :
   * 1. URL query param "?token=..." ou fragment "#access_token=..."
   * 2. Cookie "evp_access_token"
   * 3. localStorage "evp_access_token" (fallback dev)
   */
  loadToken(): void {
    // 1. Token dans l'URL (redirect depuis gpm-auth-proxy ou OAuth)
    const fromUrl = this.extractTokenFromUrl();
    if (fromUrl && !this.isExpired(fromUrl)) {
      this.setToken(fromUrl);
      this.cleanUrl();
      return;
    }

    // 2. Cookie EVP (production, meme domaine)
    const fromCookie = this.getCookie('evp_access_token');
    if (fromCookie && !this.isExpired(fromCookie)) {
      this.tokenSignal.set(fromCookie);
      return;
    }

    // 3. localStorage (dev fallback)
    const fromStorage = localStorage.getItem('evp_access_token');
    if (fromStorage && !this.isExpired(fromStorage)) {
      this.tokenSignal.set(fromStorage);
    }
  }

  /**
   * Injection manuelle du token (textarea fallback en dev)
   */
  setToken(token: string): void {
    localStorage.setItem('evp_access_token', token);
    this.tokenSignal.set(token);
  }

  /**
   * Retourne le token courant ou null
   */
  getToken(): string | null {
    // Re-vérifier le cookie à chaque appel (peut avoir été rafraîchi)
    const fromCookie = this.getCookie('evp_access_token');
    if (fromCookie && !this.isExpired(fromCookie)) {
      this.tokenSignal.set(fromCookie);
      return fromCookie;
    }
    return this.tokenSignal();
  }

  clear(): void {
    localStorage.removeItem('evp_access_token');
    this.tokenSignal.set(null);
  }

  /**
   * Temps restant avant expiration (en secondes)
   */
  getTimeRemaining(): number {
    const t = this.tokenSignal();
    if (!t) return 0;
    const payload = this.decodeJwt(t);
    if (!payload?.exp) return 0;
    return Math.max(0, payload.exp - Math.floor(Date.now() / 1000));
  }

  // --- Utilitaires privés ---

  private getCookie(name: string): string | null {
    const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'));
    return match ? decodeURIComponent(match[2]) : null;
  }

  private decodeJwt(token: string): any {
    try {
      const payload = token.split('.')[1];
      const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(decoded);
    } catch {
      return null;
    }
  }

  private isExpired(token: string): boolean {
    const payload = this.decodeJwt(token);
    if (!payload?.exp) return true;
    // Expiré si moins de 60 secondes restantes
    return payload.exp * 1000 < Date.now() + 60_000;
  }

  private extractTokenFromUrl(): string | null {
    const urlParams = new URLSearchParams(window.location.search);
    const queryToken = urlParams.get('token');
    if (queryToken) return queryToken;

    const hash = window.location.hash;
    if (hash) {
      const hashParams = new URLSearchParams(hash.substring(1));
      const fragmentToken = hashParams.get('access_token');
      if (fragmentToken) return fragmentToken;
    }
    return null;
  }

  private cleanUrl(): void {
    const url = new URL(window.location.href);
    url.searchParams.delete('token');
    url.hash = '';
    window.history.replaceState({}, document.title, url.toString());
  }
}
