import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Header } from './header';
import { AuthService } from '../../services/auth.service';

describe('Header', () => {
  let component: Header;
  let fixture: ComponentFixture<Header>;
  let authServiceSpy: {
    getUserIdentity: ReturnType<typeof vi.fn>;
    getUserRole: ReturnType<typeof vi.fn>;
    getRoleLabel: ReturnType<typeof vi.fn>;
    canViewAssets: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  const setupComponent = async (
    role: 'ADMIN' | 'TECHNICIAN' | 'REQUESTER',
    identity: string,
    roleLabel: string,
    canViewAssets: boolean,
  ) => {
    authServiceSpy = {
      getUserRole: vi.fn().mockReturnValue(role),
      getUserIdentity: vi.fn().mockReturnValue(identity),
      getRoleLabel: vi.fn().mockReturnValue(roleLabel),
      canViewAssets: vi.fn().mockReturnValue(canViewAssets),
      logout: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  };

  it('should create', async () => {
    await setupComponent('ADMIN', 'admin@helpdesk.com', 'Administrador', true);
    expect(component).toBeTruthy();
  });

  it('should render brand and Chamados links leading to /home', async () => {
    await setupComponent('ADMIN', 'admin@helpdesk.com', 'Administrador', true);

    const compiled = fixture.nativeElement as HTMLElement;
    const brand = compiled.querySelector('.navbar-brand') as HTMLAnchorElement;
    expect(brand.textContent).toContain('HelpDeskPro');
    expect(brand.getAttribute('href')).toBe('/home');

    const navLinks = compiled.querySelectorAll('.nav-link');
    const chamadosLink = Array.from(navLinks).find((el) => el.textContent?.trim() === 'Chamados') as HTMLAnchorElement;
    expect(chamadosLink).toBeTruthy();
    expect(chamadosLink.getAttribute('href')).toBe('/home');
  });

  it('should display "Ativos de TI", user email, role "Administrador", and Sair button for ADMIN', async () => {
    await setupComponent('ADMIN', 'admin@helpdesk.com', 'Administrador', true);

    const compiled = fixture.nativeElement as HTMLElement;
    const navLinks = Array.from(compiled.querySelectorAll('.nav-link'));
    const assetsLink = navLinks.find((el) => el.textContent?.includes('Ativos de TI'));
    expect(assetsLink).toBeTruthy();
    expect(assetsLink?.getAttribute('href')).toBe('/assets');

    expect(compiled.querySelector('.user-identity')?.textContent).toBe('admin@helpdesk.com');
    expect(compiled.querySelector('.role-badge')?.textContent).toBe('Administrador');
    expect(compiled.querySelector('.role-badge')?.getAttribute('data-role')).toBe('ADMIN');
    expect(compiled.querySelector('.btn-logout')).toBeTruthy();
  });

  it('should display "Ativos de TI", user email, role "Técnico", and Sair button for TECHNICIAN', async () => {
    await setupComponent('TECHNICIAN', 'ana@helpdesk.com', 'Técnico', true);

    const compiled = fixture.nativeElement as HTMLElement;
    const navLinks = Array.from(compiled.querySelectorAll('.nav-link'));
    const assetsLink = navLinks.find((el) => el.textContent?.includes('Ativos de TI'));
    expect(assetsLink).toBeTruthy();
    expect(assetsLink?.getAttribute('href')).toBe('/assets');

    expect(compiled.querySelector('.user-identity')?.textContent).toBe('ana@helpdesk.com');
    expect(compiled.querySelector('.role-badge')?.textContent).toBe('Técnico');
    expect(compiled.querySelector('.role-badge')?.getAttribute('data-role')).toBe('TECHNICIAN');
    expect(compiled.querySelector('.btn-logout')).toBeTruthy();
  });

  it('should HIDE "Ativos de TI", display user email, role "Solicitante", and Sair button for REQUESTER', async () => {
    await setupComponent('REQUESTER', 'carlos@helpdesk.com', 'Solicitante', false);

    const compiled = fixture.nativeElement as HTMLElement;
    const navLinks = Array.from(compiled.querySelectorAll('.nav-link'));
    const assetsLink = navLinks.find((el) => el.textContent?.includes('Ativos de TI'));
    expect(assetsLink).toBeUndefined();

    expect(compiled.querySelector('.user-identity')?.textContent).toBe('carlos@helpdesk.com');
    expect(compiled.querySelector('.role-badge')?.textContent).toBe('Solicitante');
    expect(compiled.querySelector('.role-badge')?.getAttribute('data-role')).toBe('REQUESTER');
    expect(compiled.querySelector('.btn-logout')).toBeTruthy();
  });

  it('should call authService.logout() when clicking Sair button', async () => {
    await setupComponent('REQUESTER', 'carlos@helpdesk.com', 'Solicitante', false);

    const logoutBtn = fixture.nativeElement.querySelector('.btn-logout') as HTMLButtonElement;
    logoutBtn.click();

    expect(authServiceSpy.logout).toHaveBeenCalled();
  });
});
