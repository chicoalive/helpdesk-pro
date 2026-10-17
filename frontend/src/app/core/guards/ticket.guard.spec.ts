import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { vi } from 'vitest';
import { ticketCreateGuard } from './ticket.guard';
import { AuthService } from '../services/auth.service';

describe('Ticket Guards', () => {
  let authServiceSpy: {
    isRequester: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(() => {
    authServiceSpy = {
      isRequester: vi.fn(),
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

  describe('ticketCreateGuard', () => {
    it('should allow access for REQUESTER', () => {
      authServiceSpy.isRequester.mockReturnValue(true);
      const result = TestBed.runInInjectionContext(() =>
        ticketCreateGuard({} as any, {} as any)
      );
      expect(result).toBe(true);
    });

    it('should redirect ADMIN to /home', () => {
      authServiceSpy.isRequester.mockReturnValue(false);
      const result = TestBed.runInInjectionContext(() =>
        ticketCreateGuard({} as any, {} as any)
      );
      expect(router.createUrlTree).toHaveBeenCalledWith(['/home']);
      expect((result as any).url).toBe('/home');
    });

    it('should redirect TECHNICIAN to /home', () => {
      authServiceSpy.isRequester.mockReturnValue(false);
      const result = TestBed.runInInjectionContext(() =>
        ticketCreateGuard({} as any, {} as any)
      );
      expect(router.createUrlTree).toHaveBeenCalledWith(['/home']);
      expect((result as any).url).toBe('/home');
    });
  });
});
