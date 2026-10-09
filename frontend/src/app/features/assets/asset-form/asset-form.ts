import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AssetService } from '../../../core/services/asset.service';
import { AuthService } from '../../../core/services/auth.service';
import { AssetRequest, AssetStatus } from '../../../core/models/ticket.model';

@Component({
  selector: 'app-asset-form',
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './asset-form.css',
  templateUrl: './asset-form.html',
})
export class AssetForm implements OnInit {
  isEditMode = false;
  assetId: number | null = null;

  isLoading = false;
  isSaving = false;

  errorMessage: string | null = null;
  successMessage: string | null = null;

  readonly assetForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    type: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    description: new FormControl('', {
      nonNullable: true,
    }),
    status: new FormControl<AssetStatus>('IN_USE', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly assetService: AssetService,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  get code() {
    return this.assetForm.get('code');
  }

  get type() {
    return this.assetForm.get('type');
  }

  get status() {
    return this.assetForm.get('status');
  }

  get isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  ngOnInit(): void {
    if (!this.authService.isAdmin()) {
      this.errorMessage =
        'Você não possui permissão para gerenciar ativos. Apenas administradores podem cadastrar ou editar ativos.';
      this.assetForm.disable();
      return;
    }

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEditMode = true;
      const parsedId = Number(idParam);
      if (isNaN(parsedId) || parsedId <= 0) {
        this.errorMessage = 'Ativo não encontrado.';
        return;
      }
      this.assetId = parsedId;
      this.loadAsset(this.assetId);
    }
  }

  loadAsset(id: number): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.assetService.getAssetById(id).subscribe({
      next: (asset) => {
        this.assetForm.patchValue({
          code: asset.code,
          type: asset.type,
          description: asset.description ?? '',
          status: asset.status,
        });
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        if (error.status === 404) {
          this.errorMessage = 'Ativo não encontrado.';
        } else if (error.status === 403) {
          this.errorMessage = 'Você não possui permissão para visualizar este ativo.';
        } else if (error.status === 401) {
          this.errorMessage = 'Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.';
        } else {
          this.errorMessage = 'Não foi possível carregar os dados do ativo.';
        }
        this.cdr.markForCheck();
      },
    });
  }

  onSubmit(): void {
    if (this.assetForm.invalid || this.isSaving) {
      this.assetForm.markAllAsTouched();
      return;
    }

    this.isSaving = true;
    this.errorMessage = null;

    const raw = this.assetForm.getRawValue();
    const payload: AssetRequest = {
      code: raw.code.trim(),
      type: raw.type.trim(),
      description: raw.description.trim() ? raw.description.trim() : null,
      status: raw.status,
    };

    const request$ = this.isEditMode && this.assetId
      ? this.assetService.updateAsset(this.assetId, payload)
      : this.assetService.createAsset(payload);

    request$.subscribe({
      next: () => {
        this.isSaving = false;
        this.router.navigate(['/assets']);
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving = false;
        if (error.status === 409) {
          this.errorMessage = 'Já existe um ativo cadastrado com este código.';
        } else if (error.status === 403) {
          this.errorMessage =
            'Você não possui permissão para executar esta operação. Apenas administradores podem gerenciar ativos.';
        } else if (error.status === 404) {
          this.errorMessage = 'Ativo não encontrado.';
        } else if (error.status === 401) {
          this.errorMessage = 'Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.';
        } else {
          this.errorMessage =
            'Ocorreu um erro ao salvar o ativo. Verifique os dados e tente novamente.';
        }
        this.cdr.markForCheck();
      },
    });
  }
}
