// src/auth/TokenService.ts
// Lecture automatique du token Cognito depuis localStorage ou cookies
// Le token est partagé avec la plateforme EVP (même domaine en déploiement)

const EVP_LOGIN_URL = 'https://pp.total-ev-charge.com/powerplatform/';

export const TokenService = {
  /**
   * Récupère le token d'accès. Priorité :
   * 1. localStorage (format Cognito SDK)
   * 2. cookies (access_token ou JWT générique)
   */
  getAccessToken(): string | null {
    // 1. Chercher dans localStorage (format Cognito SDK)
    const lsToken = this.findInLocalStorage();
    if (lsToken && !this.isExpired(lsToken)) {
      return lsToken;
    }

    // 2. Chercher dans les cookies
    const cookieToken = this.findInCookies();
    if (cookieToken && !this.isExpired(cookieToken)) {
      return cookieToken;
    }

    return null;
  },

  isAuthenticated(): boolean {
    return this.getAccessToken() !== null;
  },

  /**
   * Redirige vers la page de login EVP.
   * L'utilisateur se connecte sur EVP, puis revient au simulateur.
   */
  redirectToEvpLogin(): void {
    window.location.href = EVP_LOGIN_URL;
  },

  getTokenInfo(): { exp: number; sub?: string; email?: string } | null {
    const token = this.getAccessToken();
    if (!token) return null;
    try {
      return JSON.parse(atob(token.split('.')[1]));
    } catch {
      return null;
    }
  },

  getExpirationDate(): Date | null {
    const info = this.getTokenInfo();
    if (!info?.exp) return null;
    return new Date(info.exp * 1000);
  },

  getRemainingTime(): number | null {
    const expDate = this.getExpirationDate();
    if (!expDate) return null;
    return Math.max(0, expDate.getTime() - Date.now());
  },

  isExpired(token: string): boolean {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  },

  /**
   * Cherche le token dans localStorage (format Cognito SDK).
   * Clés au format: CognitoIdentityServiceProvider.<clientId>.<userId>.accessToken
   */
  findInLocalStorage(): string | null {
    try {
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key && key.includes('accessToken') && key.includes('CognitoIdentityServiceProvider')) {
          const value = localStorage.getItem(key);
          if (value && value.startsWith('eyJ')) {
            return value;
          }
        }
      }

      // Fallback: clé simple "access_token"
      const simple = localStorage.getItem('access_token');
      if (simple && simple.startsWith('eyJ')) {
        return simple;
      }
    } catch {
      // localStorage peut être inaccessible (iframe, etc.)
    }

    return null;
  },

  /**
   * Cherche le token dans les cookies.
   */
  findInCookies(): string | null {
    try {
      const cookies = document.cookie.split(';');
      for (const cookie of cookies) {
        const [key, ...valueParts] = cookie.trim().split('=');
        const value = decodeURIComponent(valueParts.join('='));
        if (!value) continue;

        // Cookie nommé explicitement
        if (key.trim() === 'access_token') {
          return value;
        }

        // Format Cognito
        if (key.trim().includes('accessToken') || key.trim().includes('AccessToken')) {
          return value;
        }

        // JWT générique (commence par eyJ)
        if (value.startsWith('eyJ') && value.split('.').length === 3) {
          return value;
        }
      }
    } catch {
      // cookies peuvent être inaccessibles
    }

    return null;
  },
};
