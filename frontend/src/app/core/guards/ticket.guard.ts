import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Permite acesso à tela de abertura de chamado (/tickets/new) exclusivamente para REQUESTER.
 * Redireciona ADMIN, TECHNICIAN ou demais perfis para /home.
 */
export const ticketCreateGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isRequester()) {
    return true;
  }

  return router.createUrlTree(['/home']);
};
