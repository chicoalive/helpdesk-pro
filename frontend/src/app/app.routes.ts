import { PasswordRecovery } from './features/auth/password-recovery/password-recovery';
import { PasswordRecoveryAdmin } from './features/users/password-recovery-admin/password-recovery-admin';
import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Home } from './features/home/home';
import { TicketDetail } from './features/ticket-detail/ticket-detail';
import { TicketForm } from './features/tickets/ticket-form/ticket-form';
import { AssetList } from './features/assets/asset-list/asset-list';
import { AssetForm } from './features/assets/asset-form/asset-form';
import { UserManagement } from './features/users/user-management/user-management';
import { assetsAccessGuard, assetAdminGuard } from './core/guards/asset.guard';
import { ticketCreateGuard } from './core/guards/ticket.guard';
import { authGuard, userAdminGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: Login },
  { path: 'password-recovery', component: PasswordRecovery },
  { path: 'password-reset', component: PasswordRecovery, data: { mode: 'reset' } },
  { path: 'password-recoveries', component: PasswordRecoveryAdmin, canActivate: [userAdminGuard] },
  { path: 'home', component: Home, canActivate: [authGuard] },
  { path: 'tickets/new', component: TicketForm, canActivate: [ticketCreateGuard] },
  { path: 'tickets/:id', component: TicketDetail, canActivate: [authGuard] },
  { path: 'assets', component: AssetList, canActivate: [assetsAccessGuard] },
  { path: 'assets/new', component: AssetForm, canActivate: [assetAdminGuard] },
  { path: 'assets/:id/edit', component: AssetForm, canActivate: [assetAdminGuard] },
  { path: 'users', component: UserManagement, canActivate: [userAdminGuard] },
];
