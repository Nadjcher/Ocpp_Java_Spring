import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenService } from '../../authentication/token.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.includes('/apigw/')) {
    const token = inject(TokenService).getToken();
    if (token) {
      req = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` },
        withCredentials: true,
      });
    }
  }
  return next(req);
};
