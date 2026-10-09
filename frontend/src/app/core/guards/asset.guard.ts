import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Permite acesso à listagem de ativos (/assets) para ADMIN e TECHNICIAN.
 * Redireciona REQUESTER para /home.
 */
export const assetsAccessGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.canViewAssets()) {
    return true;
  }

  return router.createUrlTree(['/home']);
};

/**
 * Permite acesso ao cadastro e edição (/assets/new, /assets/:id/edit) exclusivamente para ADMIN.
 * Redireciona TECHNICIAN para /assets.
 * Redireciona REQUESTER para /home.
 */
export const assetAdminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAdmin()) {
    return true;
  }

  if (authService.isTechnician()) {
    return router.createUrlTree(['/assets']);
  }

  return router.createUrlTree(['/home']);
};
