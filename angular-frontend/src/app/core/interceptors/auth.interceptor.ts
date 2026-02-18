import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthenticationService } from '../../authentication/authentication.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthenticationService);
  const router = inject(Router);

  // Injecter le token uniquement sur les requetes API
  if (req.url.includes('/apigw/')) {
    const token = authService.getToken();
    if (token) {
      req = req.clone({
        setHeaders: {
          'Authorization': `Bearer ${token}`
        },
        withCredentials: true,
      });
    }
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 || error.status === 403) {
        console.error(`[AuthInterceptor] Requete rejetee (${error.status}):`, error.url);
        authService.logout();
      }
      return throwError(() => error);
    })
  );
};
