import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { LoginRequest, LoginResponse } from '../models/auth.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem('helpdesk_token');
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should send POST request to /api/auth/login and return token', () => {
    const mockCredentials: LoginRequest = {
      email: 'admin@helpdesk.com',
      password: '123',
    };

    const mockResponse: LoginResponse = {
      token: 'jwt-test-token-123',
    };

    service.login(mockCredentials).subscribe((response) => {
      expect(response).toEqual(mockResponse);
      expect(response.token).toBe('jwt-test-token-123');
    });

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(mockCredentials);

    req.flush(mockResponse);
  });

  describe('role helpers and session', () => {
    const createTokenWithClaims = (claims: Record<string, unknown>) => {
      const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
      const payload = btoa(JSON.stringify(claims));
      return `${header}.${payload}.signature`;
    };

    it('should decode ADMIN role correctly and return Portuguese label', () => {
      localStorage.setItem('helpdesk_token', createTokenWithClaims({ sub: 'admin@helpdesk.com', role: 'ADMIN' }));
      expect(service.getUserRole()).toBe('ADMIN');
      expect(service.isAdmin()).toBe(true);
      expect(service.isTechnician()).toBe(false);
      expect(service.isRequester()).toBe(false);
      expect(service.canViewAssets()).toBe(true);
      expect(service.canViewDashboard()).toBe(true);
      expect(service.getRoleLabel()).toBe('Administrador');
      expect(service.getUserIdentity()).toBe('admin@helpdesk.com');
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should decode TECHNICIAN role correctly and return Portuguese label', () => {
      localStorage.setItem('helpdesk_token', createTokenWithClaims({ sub: 'ana@helpdesk.com', role: 'TECHNICIAN' }));
      expect(service.getUserRole()).toBe('TECHNICIAN');
      expect(service.isAdmin()).toBe(false);
      expect(service.isTechnician()).toBe(true);
      expect(service.isRequester()).toBe(false);
      expect(service.canViewAssets()).toBe(true);
      expect(service.canViewDashboard()).toBe(true);
      expect(service.getRoleLabel()).toBe('Técnico');
      expect(service.getUserIdentity()).toBe('ana@helpdesk.com');
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should decode REQUESTER role correctly and return Portuguese label', () => {
      localStorage.setItem('helpdesk_token', createTokenWithClaims({ sub: 'carlos@helpdesk.com', role: 'REQUESTER' }));
      expect(service.getUserRole()).toBe('REQUESTER');
      expect(service.isAdmin()).toBe(false);
      expect(service.isTechnician()).toBe(false);
      expect(service.isRequester()).toBe(true);
      expect(service.canViewAssets()).toBe(false);
      expect(service.canViewDashboard()).toBe(false);
      expect(service.getRoleLabel()).toBe('Solicitante');
      expect(service.getUserIdentity()).toBe('carlos@helpdesk.com');
      expect(service.isAuthenticated()).toBe(true);
    });

    it('should prefer name if present, otherwise fallback to sub/email', () => {
      localStorage.setItem('helpdesk_token', createTokenWithClaims({ sub: 'carlos@helpdesk.com', name: 'Carlos Solicitante', role: 'REQUESTER' }));
      expect(service.getUserIdentity()).toBe('Carlos Solicitante');
    });

    it('should return null and false when no token is present', () => {
      expect(service.getUserRole()).toBeNull();
      expect(service.isAdmin()).toBe(false);
      expect(service.isTechnician()).toBe(false);
      expect(service.isRequester()).toBe(false);
      expect(service.canViewAssets()).toBe(false);
      expect(service.getRoleLabel()).toBe('');
      expect(service.getUserIdentity()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should remove token and navigate to /login on logout', () => {
      localStorage.setItem('helpdesk_token', 'valid-token');
      const navigateSpy = vi.spyOn(router, 'navigate');

      service.logout();

      expect(localStorage.getItem('helpdesk_token')).toBeNull();
      expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    });
  });
});
