import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Login } from './login';
import { AuthService } from '../../../core/services/auth.service';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authServiceSpy: { login: ReturnType<typeof vi.fn> };
  let routerSpy: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    authServiceSpy = {
      login: vi.fn(),
    };
    routerSpy = {
      navigate: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    localStorage.clear();
    await fixture.whenStable();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should have invalid form initially', () => {
    expect(component.loginForm.invalid).toBe(true);
  });

  it('should validate email and password controls', () => {
    const emailControl = component.loginForm.controls.email;
    const passwordControl = component.loginForm.controls.password;

    expect(emailControl.valid).toBe(false);
    expect(passwordControl.valid).toBe(false);

    emailControl.setValue('invalido');
    expect(emailControl.hasError('email')).toBe(true);

    emailControl.setValue('admin@helpdeskpro.com');
    expect(emailControl.valid).toBe(true);

    passwordControl.setValue('Admin123');
    expect(passwordControl.valid).toBe(true);

    expect(component.loginForm.valid).toBe(true);
  });

  it('should disable submit button when form is invalid', async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-submit');
    expect(button.disabled).toBe(true);

    component.loginForm.controls.email.setValue('user@empresa.com');
    component.loginForm.controls.password.setValue('123456');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(button.disabled).toBe(false);
  });

  it('should store token in localStorage and navigate to /home on successful login', () => {
    authServiceSpy.login.mockReturnValue(of({ token: 'jwt-token-valido' }));

    component.loginForm.controls.email.setValue('admin@helpdesk.com');
    component.loginForm.controls.password.setValue('123');

    component.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      email: 'admin@helpdesk.com',
      password: '123',
    });
    expect(localStorage.getItem('helpdesk_token')).toBe('jwt-token-valido');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/home']);
    expect(component.isLoading).toBe(false);
    expect(component.errorMessage).toBeNull();
  });

  it('should display error message on login failure', () => {
    authServiceSpy.login.mockReturnValue(throwError(() => new Error('401 Unauthorized')));

    component.loginForm.controls.email.setValue('admin@helpdesk.com');
    component.loginForm.controls.password.setValue('senha-errada');

    component.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalled();
    expect(localStorage.getItem('helpdesk_token')).toBeNull();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
    expect(component.isLoading).toBe(false);
    expect(component.errorMessage).toBe('Falha ao autenticar. Verifique suas credenciais.');
  });

  it('should prevent duplicate submission while isLoading is true', () => {
    component.isLoading = true;
    component.loginForm.controls.email.setValue('admin@helpdesk.com');
    component.loginForm.controls.password.setValue('123');

    component.onSubmit();

    expect(authServiceSpy.login).not.toHaveBeenCalled();
  });
});
