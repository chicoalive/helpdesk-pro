import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { App } from './app';
import { AuthService } from './core/services/auth.service';

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let component: App;
  let authServiceSpy: {
    isAuthenticated: ReturnType<typeof vi.fn>;
    getUserIdentity: ReturnType<typeof vi.fn>;
    getUserRole: ReturnType<typeof vi.fn>;
    getRoleLabel: ReturnType<typeof vi.fn>;
    canViewAssets: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let router: Router;

  beforeEach(async () => {
    authServiceSpy = {
      isAuthenticated: vi.fn().mockReturnValue(false),
      getUserIdentity: vi.fn().mockReturnValue('user@test.com'),
      getUserRole: vi.fn().mockReturnValue('ADMIN'),
      getRoleLabel: vi.fn().mockReturnValue('Administrador'),
      canViewAssets: vi.fn().mockReturnValue(true),
      logout: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(App);
    component = fixture.componentInstance;
  });

  it('should create the app', () => {
    expect(component).toBeTruthy();
  });

  it('should render router outlet', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('router-outlet')).toBeTruthy();
  });

  it('should NOT render header when user is unauthenticated', async () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);
    component.currentUrl.set('/home');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-header')).toBeNull();
  });

  it('should NOT render header when on /login even if authenticated', async () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    component.currentUrl.set('/login');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-header')).toBeNull();
  });

  it('should render header when user is authenticated on /home', async () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);
    component.currentUrl.set('/home');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-header')).toBeTruthy();
  });
});
