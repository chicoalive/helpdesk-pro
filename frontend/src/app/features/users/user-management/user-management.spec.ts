import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError, Subject } from 'rxjs';
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
    changeState: ReturnType<typeof vi.fn>;
    updateUser: ReturnType<typeof vi.fn>;
  };
  let authServiceSpy: {
    isAdmin: ReturnType<typeof vi.fn>;
    getUserId: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
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
      updateUser: vi.fn(),
      changeState: vi.fn(),
    };
    authServiceSpy = {
      isAdmin: vi.fn().mockReturnValue(isAdmin),
      getUserId: vi.fn().mockReturnValue(1),
      logout: vi.fn(),
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
      password: 'test-only-strong-password',
      role: 'TECHNICIAN',
    });

    component.onSubmit();
    fixture.detectChanges();

    expect(userServiceSpy.createUser).toHaveBeenCalledWith({
      name: 'Mariana Souza',
      email: 'mariana@helpdesk.com',
      password: 'test-only-strong-password',
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
      password: 'test-only-strong-password',
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
  it.each(['ADMIN', 'TECHNICIAN', 'REQUESTER'] as const)('should create %s through the existing form', async (role) => {
    await setupComponent();
    const created: UserSummary = { id: 4, name: 'Novo Usuário', email: 'novo@example.com', role };
    userServiceSpy.createUser.mockReturnValue(of(created));
    component.userForm.setValue({
      name: '  Novo Usuário  ', email: ' NOVO@example.com ', password: 'test-only-strong-password', role,
    });
    component.onSubmit();
    expect(userServiceSpy.createUser).toHaveBeenCalledWith({
      name: 'Novo Usuário', email: 'novo@example.com', password: 'test-only-strong-password', role,
    });
    expect(component.users.at(-1)).toEqual(created);
    expect(component.userForm.controls.password.value).toBe('');
    expect(component.successMessage).toContain(component.getRoleLabel(role));
  });

  it('should include all role options with Portuguese labels', async () => {
    await setupComponent();
    const select = fixture.nativeElement.querySelector('#userRoleSelect') as HTMLSelectElement;
    expect(Array.from(select.options).map((option) => [option.value, option.textContent?.trim()])).toEqual([
      ['ADMIN', 'Administrador'], ['REQUESTER', 'Solicitante'], ['TECHNICIAN', 'Técnico'],
    ]);
  });

  it('should not submit invalid fields or passwords beyond the UTF-8 byte limit', async () => {
    await setupComponent();
    component.userForm.setValue({ name: 'A', email: 'invalid', password: 'short', role: 'ADMIN' });
    component.onSubmit();
    expect(userServiceSpy.createUser).not.toHaveBeenCalled();
    component.userForm.setValue({
      name: 'Employee', email: 'employee@example.com', password: 'á'.repeat(37), role: 'ADMIN',
    });
    component.onSubmit();
    expect(component.password?.hasError('passwordBytes')).toBe(true);
    expect(userServiceSpy.createUser).not.toHaveBeenCalled();
  });

  it('should prevent duplicate requests while saving and preserve password whitespace', async () => {
    await setupComponent();
    const pending = new Subject<UserSummary>();
    userServiceSpy.createUser.mockReturnValue(pending);
    component.userForm.setValue({
      name: 'Employee', email: 'employee@example.com', password: '  strong-exact-password  ', role: 'ADMIN',
    });
    component.onSubmit();
    component.onSubmit();
    fixture.detectChanges();
    expect(userServiceSpy.createUser).toHaveBeenCalledTimes(1);
    expect(userServiceSpy.createUser.mock.calls[0][0].password).toBe('  strong-exact-password  ');
    expect(fixture.nativeElement.querySelector('#btnCreateUser').disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
    pending.next({ id: 4, name: 'Employee', email: 'employee@example.com', role: 'ADMIN' });
    pending.complete();
    expect(component.isSaving).toBe(false);
  });

  it('should display field-specific validation errors returned by the API', async () => {
    await setupComponent();
    component.userForm.setValue({
      name: 'Employee', email: 'employee@example.com', password: 'strong-test-password', role: 'ADMIN',
    });
    userServiceSpy.createUser.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 400, error: { message: 'Erro de validação', validationErrors: { password: 'Senha inválida.' } },
    })));
    component.onSubmit();
    expect(component.formErrorMessage).toBe('Senha inválida.');
    expect(component.isSaving).toBe(false);
  });
  it('should prefill editing, hide password and update the existing row', async () => {
    await setupComponent();
    const target = { ...mockUsers[1], version: 7 };
    component.startEdit(target);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#userPasswordInput')).toBeNull();
    expect(component.userForm.controls.password.disabled).toBe(true);
    component.userForm.patchValue({ name: 'Nome Editado' });
    userServiceSpy.updateUser.mockReturnValue(of({ ...target, name: 'Nome Editado', version: 8 }));
    component.onSubmit();
    expect(userServiceSpy.updateUser).toHaveBeenCalledWith(2, {
      name: 'Nome Editado', email: target.email, role: 'TECHNICIAN', expectedVersion: 7,
    });
    expect(component.users).toHaveLength(3);
    expect(component.users[1].name).toBe('Nome Editado');
    expect(component.editingUser).toBeNull();
    expect(component.userForm.controls.password.enabled).toBe(true);
  });

  it('should cancel editing without sending a request', async () => {
    await setupComponent();
    component.startEdit(mockUsers[1]);
    component.userForm.patchValue({ name: 'Unsaved' });
    component.cancelEdit();
    expect(component.editingUser).toBeNull();
    expect(component.users[1].name).toBe('Ana Técnica');
    expect(userServiceSpy.updateUser).not.toHaveBeenCalled();
  });

  it('should reject invalid edits and prevent duplicate saves', async () => {
    await setupComponent();
    component.startEdit(mockUsers[1]);
    component.userForm.patchValue({ email: 'invalid' });
    component.onSubmit();
    expect(userServiceSpy.updateUser).not.toHaveBeenCalled();
    component.userForm.patchValue({ email: 'valid@example.com' });
    const pending = new Subject<UserSummary>();
    userServiceSpy.updateUser.mockReturnValue(pending);
    component.onSubmit();
    component.onSubmit();
    expect(userServiceSpy.updateUser).toHaveBeenCalledTimes(1);
    pending.next({ ...mockUsers[1], email: 'valid@example.com', version: 1 });
    pending.complete();
  });

  it.each(['email', 'role'] as const)('should log out after a sensitive self %s edit', async (field) => {
    await setupComponent();
    component.startEdit({ ...mockUsers[0], version: 0 });
    if (field === 'email') component.userForm.patchValue({ email: 'new-admin@example.com' });
    else component.userForm.patchValue({ role: 'REQUESTER' });
    const raw = component.userForm.getRawValue();
    userServiceSpy.updateUser.mockReturnValue(of({ id: 1, name: raw.name, email: raw.email, role: raw.role, version: 1 }));
    component.onSubmit();
    expect(authServiceSpy.logout).toHaveBeenCalledOnce();
  });

  it('should preserve a name-only self session', async () => {
    await setupComponent();
    component.startEdit({ ...mockUsers[0], version: 0 });
    component.userForm.patchValue({ name: 'Updated Admin' });
    userServiceSpy.updateUser.mockReturnValue(of({ ...mockUsers[0], name: 'Updated Admin', version: 1 }));
    component.onSubmit();
    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });

  it('should show conflicts and refresh the list without overwriting pending input', async () => {
    await setupComponent();
    component.startEdit({ ...mockUsers[1], version: 0 });
    component.userForm.patchValue({ name: 'Pending Edit' });
    userServiceSpy.updateUser.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { message: 'Recarregue a conta para editar novamente.' },
    })));
    component.onSubmit();
    expect(component.formErrorMessage).toContain('Recarregue');
    expect(userServiceSpy.getUsers).toHaveBeenCalledTimes(2);
    expect(component.userForm.controls.name.value).toBe('Pending Edit');
  });

  it('should require a new login on a revoked session', async () => {
    await setupComponent();
    component.startEdit(mockUsers[1]);
    userServiceSpy.updateUser.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    component.onSubmit();
    expect(authServiceSpy.logout).toHaveBeenCalledOnce();
  });
  it('should open the populated editing form through the row action', async () => {
    await setupComponent();
    const button = fixture.nativeElement.querySelector('button[aria-label="Editar conta de Ana Técnica"]') as HTMLButtonElement;
    expect(button).not.toBeNull();
    button.click();
    fixture.detectChanges();
    expect(component.editingUser?.id).toBe(2);
    expect((fixture.nativeElement.querySelector('#userNameInput') as HTMLInputElement).value).toBe('Ana Técnica');
    expect(fixture.nativeElement.textContent).toContain('Salvar alterações');
    expect(fixture.nativeElement.textContent).toContain('Cancelar edição');
  });

  it('confirms deactivation before sending and refreshes state', async () => {
    await setupComponent();
    const user = {...mockUsers[1], active: true, version: 3};
    userServiceSpy.changeState.mockReturnValue(of({...user, active:false, version:4}));
    component.requestStateChange(user);
    expect(userServiceSpy.changeState).not.toHaveBeenCalled();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]').textContent).toContain('Ana Técnica');
    component.confirmDeactivation();
    expect(userServiceSpy.changeState).toHaveBeenCalledWith(2,false,3);
    expect(component.users.find(u => u.id===2)?.active).toBe(false);
    expect(component.successMessage).toContain('histórico');
  });
  it('reactivates without changing credentials and filters accounts', async () => {
    await setupComponent();
    const user={...mockUsers[1],active:false,version:4};
    component.users=[mockUsers[0],user];
    component.stateFilter='inactive'; expect(component.filteredUsers).toEqual([user]);
    component.stateFilter='active'; expect(component.filteredUsers).toEqual([mockUsers[0]]);
    component.stateFilter=''; expect(component.filteredUsers.length).toBe(2);
    userServiceSpy.changeState.mockReturnValue(of({...user,active:true,version:5}));
    component.requestStateChange(user);
    expect(userServiceSpy.changeState).toHaveBeenCalledWith(2,true,4);
    expect(component.successMessage).toContain('reativada');
  });
  it('blocks self deactivation and duplicate submissions', async () => {
    await setupComponent(); component.requestStateChange(mockUsers[0]);
    expect(component.pendingDeactivation).toBeNull();
    const operation=new Subject<UserSummary>(); userServiceSpy.changeState.mockReturnValue(operation);
    component.changeState(mockUsers[1],false); component.changeState(mockUsers[1],false);
    expect(userServiceSpy.changeState).toHaveBeenCalledTimes(1);
    operation.complete();
  });
  it('reloads conflicts and redirects revoked sessions', async () => {
    await setupComponent();
    userServiceSpy.changeState.mockReturnValue(throwError(()=>new HttpErrorResponse({status:409,error:{message:'Conflito de versão'}})));
    component.changeState(mockUsers[1],false);
    expect(component.formErrorMessage).toBe('Conflito de versão');
    expect(userServiceSpy.getUsers).toHaveBeenCalledTimes(2);
    userServiceSpy.changeState.mockReturnValue(throwError(()=>new HttpErrorResponse({status:401})));
    component.changeState(mockUsers[1],false); expect(authServiceSpy.logout).toHaveBeenCalled();
  });
});