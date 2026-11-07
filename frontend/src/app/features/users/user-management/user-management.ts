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

  errorMessage: string | null = null;
  formErrorMessage: string | null = null;
  successMessage: string | null = null;

  searchTerm = '';
  roleFilter = '';

  readonly userForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    role: new FormControl<'REQUESTER' | 'TECHNICIAN'>('REQUESTER', {
      nonNullable: true,
      validators: [Validators.required],
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
      return matchesSearch && matchesRole;
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

  onSubmit(): void {
    this.formErrorMessage = null;
    this.successMessage = null;

    if (this.userForm.invalid || this.isSaving) {
      this.userForm.markAllAsTouched();
      return;
    }

    const raw = this.userForm.getRawValue();
    const trimmedName = raw.name.trim();
    const trimmedEmail = raw.email.trim().toLowerCase();
    const trimmedPassword = raw.password.trim();

    if (!trimmedName || !trimmedEmail || !trimmedPassword) {
      this.formErrorMessage = 'Preencha todos os campos obrigatórios.';
      return;
    }

    this.isSaving = true;

    const payload: UserCreateRequest = {
      name: trimmedName,
      email: trimmedEmail,
      password: trimmedPassword,
      role: raw.role,
    };

    this.userService.createUser(payload).subscribe({
      next: (createdUser) => {
        this.isSaving = false;
        this.users = [...this.users, createdUser];
        this.userForm.reset({
          name: '',
          email: '',
          password: '',
          role: 'REQUESTER',
        });
        this.successMessage = `Usuário "${createdUser.name}" (${this.getRoleLabel(createdUser.role)}) cadastrado com sucesso!`;
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving = false;
        const backendMsg = error.error?.message;
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
