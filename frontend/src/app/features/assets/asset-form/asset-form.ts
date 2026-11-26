import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AssetService } from '../../../core/services/asset.service';
import { AuthService } from '../../../core/services/auth.service';
import { AssetRequest, AssetStatus, AssetAuditResponse } from '../../../core/models/ticket.model';

@Component({
  selector: 'app-asset-form',
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './asset-form.css',
  templateUrl: './asset-form.html',
})
export class AssetForm implements OnInit {
  assetVersion: number | null = null;
  loadedStatus: AssetStatus | null = null;
  statusConfirmed = false;
  hasConflict = false;
  auditEntries: AssetAuditResponse[] = [];
  auditError: string | null = null;
  get requiresStatusConfirmation(): boolean { return this.isEditMode && this.loadedStatus !== null && this.assetForm.controls.status.value !== this.loadedStatus && this.assetForm.controls.status.value !== 'IN_USE'; }
  auditFields(entry:AssetAuditResponse){return Object.entries(entry.changes).map(([field,values])=>({field,...values}));}
  fieldLabel(field:string){return ({code:'Código',type:'Tipo',description:'Descrição',status:'Situação'} as Record<string,string>)[field]??field;}
  auditValue(field:string,value:string|null){return value===null?'Não informado':field==='status'?this.statusLabel(value):value;}
  statusLabel(value:string){return ({IN_USE:'Em uso',INACTIVE:'Inativo',UNDER_MAINTENANCE:'Em manutenção',DISCARDED:'Descartado'} as Record<string,string>)[value]??value;}
  auditDate(value:string){return new Date(value).toLocaleString('pt-BR',{timeZone:'America/Sao_Paulo'});}
  loadAudit(id:number):void { this.assetService.getAudit(id).subscribe({next:entries=>{this.auditEntries=entries;this.auditError=null;this.cdr.markForCheck();},error:()=>{this.auditError='Não foi possível carregar a auditoria do ativo.';this.cdr.markForCheck();}}); }
  isEditMode = false;
  assetId: number | null = null;

  isLoading = false;
  isSaving = false;

  errorMessage: string | null = null;
  successMessage: string | null = null;

  readonly assetForm = new FormGroup({
    code: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)],
    }),
    type: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)],
    }),
    description: new FormControl('', {
      nonNullable: true, validators:[Validators.maxLength(255)],
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
    this.assetForm.controls.status.valueChanges.subscribe(()=>this.statusConfirmed=false);
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
    this.hasConflict = false;
    this.isLoading = true;
    this.errorMessage = null;

    this.assetService.getAssetById(id).subscribe({
      next: (asset) => {
        this.assetVersion = asset.version ?? null; this.loadedStatus = asset.status; this.statusConfirmed=false;
        this.loadAudit(id);
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

    if(this.hasConflict)return;
    if(this.isEditMode && this.assetVersion === null){this.errorMessage='Recarregue o ativo para obter sua versão atual.';return;}
    if(this.requiresStatusConfirmation && !this.statusConfirmed){this.errorMessage='Confirme a alteração de situação do equipamento.';return;}
    this.isSaving = true;
    this.errorMessage = null;

    const raw = this.assetForm.getRawValue();
    const payload: AssetRequest = {
      code: raw.code.trim().toUpperCase(),
      type: raw.type.trim(),
      description: raw.description.trim() ? raw.description.trim() : null,
      status: raw.status,
    };

    if(this.isEditMode)payload.expectedVersion=this.assetVersion!;
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
          this.errorMessage = error.error?.message || 'Já existe um ativo cadastrado com este código.';
          this.hasConflict = this.errorMessage!.includes('alterado por outra operação');
        } else if (error.status === 403) {
          this.errorMessage =
            'Você não possui permissão para executar esta operação. Apenas administradores podem gerenciar ativos.';
        } else if (error.status === 404) {
          this.errorMessage = 'Ativo não encontrado.';
        } else if (error.status === 401) {
          this.errorMessage = 'Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.';
        } else {
          this.errorMessage =
            error.error?.message || 'Ocorreu um erro ao salvar o ativo. Verifique os dados e tente novamente.';
        }
        this.cdr.markForCheck();
      },
    });
  }
}
