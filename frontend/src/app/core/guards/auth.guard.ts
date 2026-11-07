import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Exige usuário autenticado (com token válido e não expirado) para acessar rotas protegidas.
 * Redireciona para /login caso não autenticado.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login']);
};

/**
 * Permite acesso à gestão de usuários (/users) exclusivamente para ADMIN autenticado.
 * Redireciona não autenticados para /login e demais perfis autenticados para /home.
 */
export const userAdminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  if (authService.isAdmin()) {
    return true;
  }

  return router.createUrlTree(['/home']);
};
