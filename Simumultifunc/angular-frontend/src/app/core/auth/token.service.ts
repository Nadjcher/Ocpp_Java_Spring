import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class TokenService {

  getToken(): string | null {
    return this.getCookie('evp_access_token');
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token || !token.startsWith('eyJ')) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  /** Dev console only: pose le cookie evp_access_token */
  setToken(token: string): void {
    document.cookie = `evp_access_token=${encodeURIComponent(token)}; path=/; max-age=3600`;
  }

  private getCookie(name: string): string | null {
    const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'));
    return match ? decodeURIComponent(match[2]) : null;
  }
}
