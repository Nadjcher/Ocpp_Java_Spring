// src/auth/TokenService.ts
// Lecture du cookie evp_access_token posé par EVP (même domaine)
// Pas de token → page 401 → l'utilisateur va sur EVP → refresh

const EVP_URL = 'https://pp.total-ev-charge.com/powerplatform/';
const COOKIE_NAME = 'evp_access_token';

const DEV_MODE = import.meta.env.DEV;

export const TokenService = {
  getAccessToken(): string | null {
    // Mode dev bypass
    if (DEV_MODE && localStorage.getItem('dev_bypass_auth') === 'true') {
      return 'dev-bypass';
    }

    // Lire le cookie evp_access_token
    const token = this.readCookie(COOKIE_NAME);
    if (token && !this.isExpired(token)) {
      return token;
    }

    return null;
  },

  isAuthenticated(): boolean {
    return this.getAccessToken() !== null;
  },

  getEvpUrl(): string {
    return EVP_URL;
  },

  getTokenInfo(): { exp: number; sub?: string; email?: string } | null {
    const token = this.readCookie(COOKIE_NAME);
    if (!token) return null;
    try {
      return JSON.parse(atob(token.split('.')[1]));
    } catch {
      return null;
    }
  },

  isExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  },

  readCookie(name: string): string | null {
    try {
      const match = document.cookie
        .split(';')
        .map(c => c.trim())
        .find(c => c.startsWith(name + '='));
      if (!match) return null;
      return decodeURIComponent(match.substring(name.length + 1));
    } catch {
      return null;
    }
  },

  // Dev helpers
  enableDevBypass(): void {
    if (DEV_MODE) {
      localStorage.setItem('dev_bypass_auth', 'true');
      console.log('[Auth] Dev bypass enabled. Reload the page.');
    }
  },

  disableDevBypass(): void {
    localStorage.removeItem('dev_bypass_auth');
    console.log('[Auth] Dev bypass disabled.');
  },

  setToken(token: string): void {
    document.cookie = `${COOKIE_NAME}=${token}; path=/`;
    console.log('[Auth] Cookie set. Reload the page.');
  },
};

// Exposer en dev pour debug console
if (DEV_MODE && typeof window !== 'undefined') {
  (window as any).TokenService = TokenService;
  console.log('[Auth] Dev mode - TokenService.enableDevBypass() / TokenService.setToken("eyJ...")');
}
