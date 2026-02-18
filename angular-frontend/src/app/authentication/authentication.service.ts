import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IdentityProvider } from '../core/config/identity-provider-config.model';
import { AuthenticationUrl } from './models/authentication-url.model';
import { Config } from '../core/config/config';
import { Observable, switchMap, tap, catchError, of } from 'rxjs';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class AuthenticationService {
  private static readonly USER_TYPE_KEY = 'AUTHENTICATION_USER_TYPE';
  private static readonly TOKEN_KEY = 'auth_token';

  private config = inject(Config);
  private http = inject(HttpClient);
  private router = inject(Router);

  private baseUri = '/apigw/auth';
  private verifyUri = '/apigw/energy-service-manager/tree-roots';

  /** Redirige vers Cognito pour login */
  initiateLogin(identityProvider: IdentityProvider): void {
    const config = this.config.authentication;
    const authenticationUrl = new AuthenticationUrl(
      config.authorizeUrl,
      config.redirectUri,
      identityProvider,
    );
    localStorage.setItem(AuthenticationService.USER_TYPE_KEY, identityProvider.userType);
    location.href = authenticationUrl.toString();
  }

  /** Appele par le callback apres redirect Cognito. Echange le code puis verifie le token. */
  completeLogin(authorizationCode: string): Observable<boolean> {
    const userType = localStorage.getItem(AuthenticationService.USER_TYPE_KEY)!;
    const params: Record<string, string> = {
      code: authorizationCode,
      'user-type': userType,
      'redirect-uri': this.config.authentication.redirectUri,
    };

    return this.http.get<any>(`${this.baseUri}/token`, { params, withCredentials: true }).pipe(
      tap(response => {
        // Extraire et stocker le token (adapter selon la reponse de l'API Gateway)
        const token = response?.access_token || response?.token || response;
        if (typeof token === 'string' && token.startsWith('eyJ')) {
          localStorage.setItem(AuthenticationService.TOKEN_KEY, token);
          console.log('[Auth] Token recu et stocke');
        }
      }),
      // Verifier le token aupres de la gateway
      switchMap(() => this.verifyToken()),
      tap(valid => {
        if (valid) {
          console.log('[Auth] Token verifie par la gateway');
          this.router.navigate(['/simulator']);
        } else {
          console.error('[Auth] Token rejete par la gateway');
        }
      }),
      catchError(err => {
        console.error('[Auth] Erreur lors du login:', err);
        return of(false);
      })
    );
  }

  /** Verifie que le token est accepte par la gateway en appelant un endpoint reel */
  verifyToken(): Observable<boolean> {
    const token = this.getToken();
    if (!token) return of(false);

    return this.http.get(this.verifyUri, {
      headers: { 'Authorization': `Bearer ${token}` },
      observe: 'response',
    }).pipe(
      switchMap(response => {
        if (response.status === 200) {
          console.log('[Auth] Gateway a valide le token (HTTP 200)');
          return of(true);
        }
        console.warn('[Auth] Gateway a repondu:', response.status);
        return of(false);
      }),
      catchError(err => {
        const status = err?.status || 'inconnu';
        console.error(`[Auth] Gateway a rejete le token (HTTP ${status})`);
        return of(false);
      })
    );
  }

  /** Recuperer le token stocke */
  getToken(): string | null {
    return localStorage.getItem(AuthenticationService.TOKEN_KEY);
  }

  /** Verifier si le token existe et n'est pas expire */
  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const now = Math.floor(Date.now() / 1000);
      return payload.exp > now;
    } catch {
      return false;
    }
  }

  /** Infos sur le token (pour affichage debug/status) */
  getTokenInfo(): { sub: string; exp: number; scope: string; expiresIn: number } | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return {
        sub: payload.sub,
        exp: payload.exp,
        scope: payload.scope,
        expiresIn: payload.exp - Math.floor(Date.now() / 1000),
      };
    } catch {
      return null;
    }
  }

  /** Logout : supprime le token */
  logout(): void {
    localStorage.removeItem(AuthenticationService.TOKEN_KEY);
    localStorage.removeItem(AuthenticationService.USER_TYPE_KEY);
    this.router.navigate(['/login']);
  }
}
