import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { vi } from 'vitest';
import { authGuard, userAdminGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('Auth Guards', () => {
  let authServiceSpy: {
    isAuthenticated: ReturnType<typeof vi.fn>;
    isAdmin: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    authServiceSpy = {
      isAuthenticated: vi.fn(),
      isAdmin: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: Router,
          useValue: {
            createUrlTree: vi.fn((commands: string[]) => ({ url: commands.join('/') } as unknown as UrlTree)),
          },
        },
      ],
    });

    router = TestBed.inject(Router);
  });

  describe('authGuard', () => {
    it('should allow access when user is authenticated', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(true);
      const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
      expect(result).toBe(true);
    });

    it('should redirect to /login when user is not authenticated', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(false);
      const result = TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));
      expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
      expect((result as any).url).toBe('/login');
    });
  });

  describe('userAdminGuard', () => {
    it('should allow access for authenticated ADMIN', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(true);
      authServiceSpy.isAdmin.mockReturnValue(true);
      const result = TestBed.runInInjectionContext(() => userAdminGuard({} as any, {} as any));
      expect(result).toBe(true);
    });

    it('should redirect to /login when user is not authenticated', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(false);
      authServiceSpy.isAdmin.mockReturnValue(false);
      const result = TestBed.runInInjectionContext(() => userAdminGuard({} as any, {} as any));
      expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
      expect((result as any).url).toBe('/login');
    });

    it('should redirect authenticated non-ADMIN (TECHNICIAN/REQUESTER) to /home', () => {
      authServiceSpy.isAuthenticated.mockReturnValue(true);
      authServiceSpy.isAdmin.mockReturnValue(false);
      const result = TestBed.runInInjectionContext(() => userAdminGuard({} as any, {} as any));
      expect(router.createUrlTree).toHaveBeenCalledWith(['/home']);
      expect((result as any).url).toBe('/home');
    });
  });
});
