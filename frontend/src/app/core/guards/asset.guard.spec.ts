import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { vi } from 'vitest';
import { assetsAccessGuard, assetAdminGuard } from './asset.guard';
import { AuthService } from '../services/auth.service';

describe('Asset Guards', () => {
  let authServiceSpy: {
    isAdmin: ReturnType<typeof vi.fn>;
    isTechnician: ReturnType<typeof vi.fn>;
    isRequester: ReturnType<typeof vi.fn>;
    canViewAssets: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    authServiceSpy = {
      isAdmin: vi.fn(),
      isTechnician: vi.fn(),
      isRequester: vi.fn(),
      canViewAssets: vi.fn(),
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

  describe('assetsAccessGuard', () => {
    it('should allow access for ADMIN', () => {
      authServiceSpy.canViewAssets.mockReturnValue(true);
      const result = TestBed.runInInjectionContext(() =>
        assetsAccessGuard({} as any, {} as any)
      );
      expect(result).toBe(true);
    });

    it('should allow access for TECHNICIAN', () => {
      authServiceSpy.canViewAssets.mockReturnValue(true);
      const result = TestBed.runInInjectionContext(() =>
        assetsAccessGuard({} as any, {} as any)
      );
      expect(result).toBe(true);
    });

    it('should redirect REQUESTER to /home', () => {
      authServiceSpy.canViewAssets.mockReturnValue(false);
      const result = TestBed.runInInjectionContext(() =>
        assetsAccessGuard({} as any, {} as any)
      );
      expect(router.createUrlTree).toHaveBeenCalledWith(['/home']);
      expect((result as any).url).toBe('/home');
    });
  });

  describe('assetAdminGuard', () => {
    it('should allow access for ADMIN', () => {
      authServiceSpy.isAdmin.mockReturnValue(true);
      const result = TestBed.runInInjectionContext(() =>
        assetAdminGuard({} as any, {} as any)
      );
      expect(result).toBe(true);
    });

    it('should redirect TECHNICIAN to /assets', () => {
      authServiceSpy.isAdmin.mockReturnValue(false);
      authServiceSpy.isTechnician.mockReturnValue(true);
      const result = TestBed.runInInjectionContext(() =>
        assetAdminGuard({} as any, {} as any)
      );
      expect(router.createUrlTree).toHaveBeenCalledWith(['/assets']);
      expect((result as any).url).toBe('/assets');
    });

    it('should redirect REQUESTER to /home', () => {
      authServiceSpy.isAdmin.mockReturnValue(false);
      authServiceSpy.isTechnician.mockReturnValue(false);
      const result = TestBed.runInInjectionContext(() =>
        assetAdminGuard({} as any, {} as any)
      );
      expect(router.createUrlTree).toHaveBeenCalledWith(['/home']);
      expect((result as any).url).toBe('/home');
    });
  });
});
