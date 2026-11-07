import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { UserManagement } from './user-management';
import { UserService } from '../../../core/services/user.service';
import { AuthService } from '../../../core/services/auth.service';
import { UserSummary } from '../../../core/models/ticket.model';

describe('UserManagement', () => {
  let component: UserManagement;
  let fixture: ComponentFixture<UserManagement>;
  let userServiceSpy: {
    getUsers: ReturnType<typeof vi.fn>;
    createUser: ReturnType<typeof vi.fn>;
  };
  let authServiceSpy: {
    isAdmin: ReturnType<typeof vi.fn>;
  };

  const mockUsers: UserSummary[] = [
    { id: 1, name: 'Admin TI', email: 'admin@helpdesk.com', role: 'ADMIN' },
    { id: 2, name: 'Ana Técnica', email: 'ana@helpdesk.com', role: 'TECHNICIAN' },
    { id: 3, name: 'Carlos Solicitante', email: 'carlos@helpdesk.com', role: 'REQUESTER' },
  ];

  const setupComponent = async (isAdmin = true) => {
    userServiceSpy = {
      getUsers: vi.fn().mockReturnValue(of(mockUsers)),
      createUser: vi.fn(),
    };
    authServiceSpy = {
      isAdmin: vi.fn().mockReturnValue(isAdmin),
    };

    await TestBed.configureTestingModule({
      imports: [UserManagement],
      providers: [
        provideRouter([]),
        { provide: UserService, useValue: userServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UserManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  };

  it('should create and load users for ADMIN', async () => {
    await setupComponent(true);
    expect(component).toBeTruthy();
    expect(userServiceSpy.getUsers).toHaveBeenCalled();
    expect(component.users.length).toBe(3);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Admin TI');
    expect(compiled.textContent).toContain('Ana Técnica');
    expect(compiled.textContent).toContain('Carlos Solicitante');
  });

  it('should block non-ADMIN and not call getUsers', async () => {
    await setupComponent(false);
    expect(userServiceSpy.getUsers).not.toHaveBeenCalled();
    expect(component.errorMessage).toContain('Acesso restrito a administradores');
  });

  it('should create a new user and append it to the list', async () => {
    await setupComponent(true);

    const createdUser: UserSummary = {
      id: 4,
      name: 'Mariana Souza',
      email: 'mariana@helpdesk.com',
      role: 'TECHNICIAN',
    };
    userServiceSpy.createUser.mockReturnValue(of(createdUser));

    component.userForm.setValue({
      name: 'Mariana Souza',
      email: 'Mariana@HelpDesk.com',
      password: '123',
      role: 'TECHNICIAN',
    });

    component.onSubmit();
    fixture.detectChanges();

    expect(userServiceSpy.createUser).toHaveBeenCalledWith({
      name: 'Mariana Souza',
      email: 'mariana@helpdesk.com',
      password: '123',
      role: 'TECHNICIAN',
    });
    expect(component.users.length).toBe(4);
    expect(component.successMessage).toContain('Mariana Souza');
  });

  it('should display backend duplicate email error message on 400', async () => {
    await setupComponent(true);

    userServiceSpy.createUser.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { message: 'Já existe um usuário cadastrado com este e-mail.' },
          }),
      ),
    );

    component.userForm.setValue({
      name: 'Duplicado',
      email: 'ana@helpdesk.com',
      password: '123',
      role: 'REQUESTER',
    });

    component.onSubmit();
    fixture.detectChanges();

    expect(component.formErrorMessage).toBe('Já existe um usuário cadastrado com este e-mail.');
  });

  it('should filter users by search term and role', async () => {
    await setupComponent(true);

    component.searchTerm = 'ana';
    expect(component.filteredUsers.length).toBe(1);
    expect(component.filteredUsers[0].email).toBe('ana@helpdesk.com');

    component.searchTerm = '';
    component.roleFilter = 'REQUESTER';
    expect(component.filteredUsers.length).toBe(1);
    expect(component.filteredUsers[0].email).toBe('carlos@helpdesk.com');
  });
});
