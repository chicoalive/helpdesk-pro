import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { LoginRequest, LoginResponse } from '../models/auth.model';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly apiUrl = '/api/auth/login';

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.apiUrl, credentials);
  }

  getToken(): string | null {
    return localStorage.getItem('helpdesk_token');
  }

  getUserPayload(): any {
    const token = this.getToken();
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length < 2) return null;
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(base64));
      if (payload && typeof payload.exp === 'number') {
        const nowInSeconds = Math.floor(Date.now() / 1000);
        if (payload.exp <= nowInSeconds) {
          localStorage.removeItem('helpdesk_token');
          return null;
        }
      }
      return payload;
    } catch {
      return null;
    }
  }

  getUserRole(): string | null {
    const payload = this.getUserPayload();
    return payload?.role ?? null;
  }

  getUserId(): number | null {
    const payload = this.getUserPayload();
    if (!payload || payload.id === undefined || payload.id === null) {
      return null;
    }
    const parsed = Number(payload.id);
    return Number.isNaN(parsed) ? null : parsed;
  }

  getUserIdentity(): string | null {
    const payload = this.getUserPayload();
    if (!payload) return null;
    return payload.name || payload.email || payload.sub || null;
  }

  getRoleLabel(): string {
    const role = this.getUserRole();
    switch (role) {
      case 'ADMIN':
        return 'Administrador';
      case 'TECHNICIAN':
        return 'Técnico';
      case 'REQUESTER':
        return 'Solicitante';
      default:
        return '';
    }
  }

  isAdmin(): boolean {
    return this.getUserRole() === 'ADMIN';
  }

  isTechnician(): boolean {
    return this.getUserRole() === 'TECHNICIAN';
  }

  isRequester(): boolean {
    return this.getUserRole() === 'REQUESTER';
  }

  canViewAssets(): boolean {
    const role = this.getUserRole();
    return role === 'ADMIN' || role === 'TECHNICIAN';
  }

  canViewDashboard(): boolean {
    const role = this.getUserRole();
    return role === 'ADMIN' || role === 'TECHNICIAN';
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) {
      return false;
    }

    if (token.includes('.')) {
      return this.getUserPayload() !== null;
    }

    return true;
  }

  logout(): void {
    localStorage.removeItem('helpdesk_token');
    this.router.navigate(['/login']);
  }
}
