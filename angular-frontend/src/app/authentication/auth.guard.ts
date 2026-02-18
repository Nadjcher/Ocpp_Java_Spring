import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenService } from './token.service';

export const authGuard: CanActivateFn = async () => {
  const tokenService = inject(TokenService);
  const router = inject(Router);

  // 1. Check local : token present et non expire ?
  if (!tokenService.isTokenValid()) {
    console.warn('[AuthGuard] Token absent ou expire');
    return router.createUrlTree(['/unauthorized']);
  }

  // 2. Check serveur : la gateway accepte le token ?
  const isValid = await tokenService.verifyWithGateway();
  if (!isValid) {
    console.warn('[AuthGuard] Token rejete par la gateway');
    return router.createUrlTree(['/unauthorized']);
  }

  console.log('[AuthGuard] Token valide');
  return true;
};
