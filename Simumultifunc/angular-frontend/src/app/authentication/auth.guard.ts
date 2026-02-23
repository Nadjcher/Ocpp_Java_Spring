import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenService } from '../core/auth/token.service';

export const authGuard: CanActivateFn = () => {
  const tokenService = inject(TokenService);
  const router = inject(Router);

  // Check local : token present et non expire ?
  if (!tokenService.isAuthenticated()) {
    console.warn('[AuthGuard] Token absent ou expire');
    return router.createUrlTree(['/unauthorized']);
  }

  console.log('[AuthGuard] Token valide');
  return true;
};
