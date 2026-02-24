import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { TokenService } from './token.service';
import { tap } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // N'intercepter que les requêtes vers l'API
  if (!req.url.includes('/apigw/')) {
    return next(req);
  }

  const tokenService = inject(TokenService);
  const router = inject(Router);
  const token = tokenService.getToken();

  let authReq = req;
  if (token) {
    authReq = req.clone({
      setHeaders: {
        'Authorization': `Bearer ${token}`
      },
      withCredentials: true
    });
  }

  return next(authReq).pipe(
    tap({
      error: (err) => {
        if (err instanceof HttpErrorResponse && (err.status === 401 || err.status === 403)) {
          console.warn('[Auth] Token rejeté par la gateway (', err.status, ')');
          tokenService.clear();
          router.navigate(['/unauthorized']);
        }
      }
    })
  );
};
