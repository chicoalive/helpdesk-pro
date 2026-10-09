import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Home } from './features/home/home';
import { TicketDetail } from './features/ticket-detail/ticket-detail';
import { AssetList } from './features/assets/asset-list/asset-list';
import { AssetForm } from './features/assets/asset-form/asset-form';
import { assetsAccessGuard, assetAdminGuard } from './core/guards/asset.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: Login },
  { path: 'home', component: Home },
  { path: 'tickets/:id', component: TicketDetail },
  { path: 'assets', component: AssetList, canActivate: [assetsAccessGuard] },
  { path: 'assets/new', component: AssetForm, canActivate: [assetAdminGuard] },
  { path: 'assets/:id/edit', component: AssetForm, canActivate: [assetAdminGuard] },
];
