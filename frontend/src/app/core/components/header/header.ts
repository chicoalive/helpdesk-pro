import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-header',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './header.html',
  styleUrl: './header.css',
})
export class Header {
  constructor(private readonly authService: AuthService) {}

  get identity(): string {
    return this.authService.getUserIdentity() ?? '';
  }

  get userRole(): string {
    return this.authService.getUserRole() ?? '';
  }

  get roleLabel(): string {
    return this.authService.getRoleLabel();
  }

  get canViewAssets(): boolean {
    return this.authService.canViewAssets();
  }

  onLogout(): void {
    this.authService.logout();
  }
}
