import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { UserService } from '../../../core/services/user.service';
import { AuthService } from '../../../core/services/auth.service';
import { UserCreateRequest, UserRole, UserSummary } from '../../../core/models/ticket.model';

@Component({
  selector: 'app-user-management',
  imports: [RouterLink, FormsModule, ReactiveFormsModule],
  templateUrl: './user-management.html',
  styleUrl: './user-management.css',
})
export class UserManagement implements OnInit {
  users: UserSummary[] = [];
  isLoading = false;
  isSaving = false;
  editingUser: UserSummary | null = null;

  errorMessage: string | null = null;
  formErrorMessage: string | null = null;
  successMessage: string | null = null;

  searchTerm = '';
  roleFilter = '';
  stateFilter = '';
  pendingDeactivation: UserSummary | null = null;

  readonly userForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2), Validators.maxLength(255)],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(255)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(12),
        (control) => new TextEncoder().encode(control.value ?? '').length <= 72
          ? null : { passwordBytes: true },
      ],
    }),
    role: new FormControl<UserRole>('REQUESTER', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^(ADMIN|TECHNICIAN|REQUESTER)$/)],
    }),
  });

  constructor(
    private readonly userService: UserService,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  get name() {
    return this.userForm.get('name');
  }

  get email() {
    return this.userForm.get('email');
  }

  get password() {
    return this.userForm.get('password');
  }

  get role() {
    return this.userForm.get('role');
  }

  get isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  get filteredUsers(): UserSummary[] {
    const term = this.searchTerm.trim().toLowerCase();
    return this.users.filter((user) => {
      const matchesSearch =
        !term ||
        user.name.toLowerCase().includes(term) ||
        user.email.toLowerCase().includes(term);

      const matchesRole = !this.roleFilter || user.role === this.roleFilter;
      return matchesSearch && matchesRole && (!this.stateFilter || (user.active !== false) === (this.stateFilter === 'active'));
    });
  }

  ngOnInit(): void {
    if (!this.authService.isAdmin()) {
      this.errorMessage =
        'Você não possui permissão para gerenciar usuários. Acesso restrito a administradores.';
      this.userForm.disable();
      return;
    }

    this.loadUsers();
  }

  loadUsers(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.userService.getUsers().subscribe({
      next: (data) => {
        this.users = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.users = [];
        this.isLoading = false;
        if (error.status === 401) {
          this.authService.logout();
          this.errorMessage =
            'Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.';
        } else if (error.status === 403) {
          this.errorMessage = 'Você não possui permissão para visualizar a lista de usuários.';
        } else {
          this.errorMessage =
            'Não foi possível carregar a lista de usuários. Tente novamente mais tarde.';
        }
        this.cdr.markForCheck();
      },
    });
  }

  canDeactivate(user: UserSummary): boolean {
    return user.id !== this.authService.getUserId();
  }

  requestStateChange(user: UserSummary): void {
    if (!this.isAdmin || this.isSaving) return;
    if (user.active !== false) {
      if (this.canDeactivate(user)) this.pendingDeactivation = { ...user };
    } else this.changeState(user, true);
  }

  confirmDeactivation(): void {
    if (this.pendingDeactivation) this.changeState(this.pendingDeactivation, false);
  }

  changeState(user: UserSummary, active: boolean): void {
    if (!this.isAdmin || this.isSaving || (!active && !this.canDeactivate(user))) return;
    this.isSaving = true;
    this.formErrorMessage = null;
    this.successMessage = null;
    this.userService.changeState(user.id, active, user.version ?? 0).subscribe({
      next: (updated) => {
        this.isSaving = false;
        this.pendingDeactivation = null;
        this.users = this.users.map((item) => item.id === updated.id ? updated : item);
        if (this.editingUser?.id === updated.id) this.cancelEdit();
        this.successMessage = active ? 'Conta reativada com sucesso.' : 'Conta desativada. As sessões foram encerradas e o histórico foi preservado.';
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving = false;
        this.pendingDeactivation = null;
        if (error.status === 401) { this.authService.logout(); return; }
        if (error.status === 409) this.loadUsers();
        this.formErrorMessage = error.error?.message || 'Não foi possível alterar o estado da conta.';
        this.cdr.markForCheck();
      },
    });
  }

  startEdit(user: UserSummary): void {
    if (!this.isAdmin || this.isSaving) return;
    this.editingUser = { ...user };
    this.userForm.reset({ name: user.name, email: user.email, password: '', role: user.role });
    this.userForm.controls.password.disable();
    this.formErrorMessage = null;
    this.successMessage = null;
  }

  cancelEdit(): void {
    if (this.isSaving) return;
    this.editingUser = null;
    this.userForm.controls.password.enable();
    this.userForm.reset({ name: '', email: '', password: '', role: 'REQUESTER' });
    this.formErrorMessage = null;
    this.successMessage = null;
  }

  onSubmit(): void {
    if (this.isSaving) return;
    this.formErrorMessage = null;
    this.successMessage = null;
    if (!this.isAdmin) {
      this.formErrorMessage = 'Você não possui permissão para cadastrar usuários.';
      return;
    }
    const values = this.userForm.getRawValue();
    this.userForm.patchValue({
      name: values.name.trim(),
      email: values.email.trim().toLowerCase(),
    });

    if (this.userForm.invalid || this.isSaving) {
      this.userForm.markAllAsTouched();
      return;
    }

    const raw = this.userForm.getRawValue();
    const trimmedName = raw.name.trim();
    const trimmedEmail = raw.email.trim().toLowerCase();
    const password = raw.password;

    if (!trimmedName || !trimmedEmail || (!this.editingUser && !password.trim())) {
      this.formErrorMessage = 'Preencha todos os campos obrigatórios.';
      return;
    }

    this.isSaving = true;

    const payload: UserCreateRequest = {
      name: trimmedName,
      email: trimmedEmail,
      password,
      role: raw.role,
    };

    const editing = this.editingUser;
    const sensitiveSelfChange = editing?.id === this.authService.getUserId()
      && (editing.email !== trimmedEmail || editing.role !== raw.role);
    const operation = editing
      ? this.userService.updateUser(editing.id, {
          name: trimmedName, email: trimmedEmail, role: raw.role, expectedVersion: editing.version ?? 0,
        })
      : this.userService.createUser(payload);

    operation.subscribe({
      next: (createdUser) => {
        this.isSaving = false;
        this.users = editing
          ? this.users.map((user) => user.id === createdUser.id ? createdUser : user)
          : [...this.users, createdUser];
        this.editingUser = null;
        this.userForm.controls.password.enable();
        this.userForm.reset({
          name: '',
          email: '',
          password: '',
          role: 'REQUESTER',
        });
        this.successMessage = editing
          ? 'Conta atualizada com sucesso.'
          : `Usuário "${createdUser.name}" (${this.getRoleLabel(createdUser.role)}) cadastrado com sucesso!`;
        if (sensitiveSelfChange) this.authService.logout();
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving = false;
        if (error.status === 401) {
          this.authService.logout();
          return;
        }
        if (editing && error.status === 409) this.loadUsers();
        const validationErrors = error.error?.validationErrors;
        const details = validationErrors && typeof validationErrors === 'object'
          ? Object.values(validationErrors).filter((value) => typeof value === 'string').join(' ')
          : '';
        const backendMsg = details || error.error?.message;
        if (typeof backendMsg === 'string' && backendMsg.trim().length > 0) {
          this.formErrorMessage = backendMsg;
        } else if (error.status === 400 || error.status === 409) {
          this.formErrorMessage =
            'Já existe um usuário cadastrado com este e-mail ou os dados informados são inválidos.';
        } else if (error.status === 403) {
          this.formErrorMessage = 'Você não possui permissão para cadastrar usuários.';
        } else if (error.status === 401) {
          this.formErrorMessage =
            'Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.';
        } else {
          this.formErrorMessage =
            'Não foi possível cadastrar o usuário. Tente novamente mais tarde.';
        }
        this.cdr.markForCheck();
      },
    });
  }

  getRoleLabel(role: UserRole | string): string {
    switch (role) {
      case 'ADMIN':
        return 'Administrador';
      case 'TECHNICIAN':
        return 'Técnico';
      case 'REQUESTER':
        return 'Solicitante';
      default:
        return role;
    }
  }
}
