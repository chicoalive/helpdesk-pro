import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TicketService } from '../../../core/services/ticket.service';
import { AssetService } from '../../../core/services/asset.service';
import { AuthService } from '../../../core/services/auth.service';
import { AssetResponse, TicketCategory, TicketRequest } from '../../../core/models/ticket.model';

@Component({
  selector: 'app-ticket-form',
  imports: [ReactiveFormsModule, RouterLink],
  styleUrl: './ticket-form.css',
  templateUrl: './ticket-form.html',
})
export class TicketForm implements OnInit {
  availableAssets: AssetResponse[] = [];
  isLoadingAssets = false;
  isSaving = false;

  selectedFile: File | null = null;
  attachmentError: string | null = null;
  errorMessage: string | null = null;
  existingTicketId: number | null = null;

  private static readonly MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;
  private static readonly ALLOWED_MIME_TYPES = ['image/png', 'image/jpeg'];

  readonly categoryOptions: {
    value: TicketCategory;
    label: string;
    estimatedTime: string;
  }[] = [
    {
      value: 'INCIDENTE_SISTEMA',
      label: 'Problema no sistema',
      estimatedTime: 'Prazo estimado de atendimento: até 4 horas',
    },
    {
      value: 'INCIDENTE_EQUIPAMENTO',
      label: 'Problema no equipamento',
      estimatedTime: 'Prazo estimado de atendimento: até 24 horas',
    },
    {
      value: 'SOLICITACAO_ACESSO',
      label: 'Solicitação de acesso',
      estimatedTime: 'Prazo estimado de atendimento: até 24 horas',
    },
    {
      value: 'DUVIDA',
      label: 'Dúvida',
      estimatedTime: 'Prazo estimado de atendimento: até 72 horas',
    },
    {
      value: 'OUTRO',
      label: 'Outro',
      estimatedTime: 'Prazo estimado de atendimento: até 24 horas',
    },
  ];

  readonly ticketForm = new FormGroup({
    title: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    category: new FormControl<TicketCategory | ''>('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    assetId: new FormControl<number | null>(null),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  constructor(
    private readonly router: Router,
    private readonly ticketService: TicketService,
    private readonly assetService: AssetService,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  get title() {
    return this.ticketForm.get('title');
  }

  get category() {
    return this.ticketForm.get('category');
  }

  get assetId() {
    return this.ticketForm.get('assetId');
  }

  get description() {
    return this.ticketForm.get('description');
  }

  get isRequester(): boolean {
    return this.authService.isRequester();
  }

  get isEquipmentIncident(): boolean {
    return this.ticketForm.controls.category.value === 'INCIDENTE_EQUIPAMENTO';
  }

  get selectedCategoryEstimate(): string | null {
    const currentCategory = this.ticketForm.controls.category.value;
    if (!currentCategory) {
      return null;
    }
    const found = this.categoryOptions.find((opt) => opt.value === currentCategory);
    return found ? found.estimatedTime : null;
  }

  ngOnInit(): void {
    if (!this.authService.isRequester()) {
      this.errorMessage =
        'Você não possui permissão para abrir chamados. Apenas solicitantes podem criar novos chamados.';
      this.ticketForm.disable();
      return;
    }

    this.ticketForm.controls.category.valueChanges.subscribe((category) => {
      this.onCategoryChange(category);
    });

    this.loadAvailableAssets();
  }

  onCategoryChange(category: TicketCategory | ''): void {
    const assetControl = this.ticketForm.controls.assetId;
    if (category === 'INCIDENTE_EQUIPAMENTO') {
      assetControl.setValidators([Validators.required]);
    } else {
      assetControl.clearValidators();
      assetControl.setValue(null);
    }
    assetControl.updateValueAndValidity();
    this.cdr.markForCheck();
  }

  loadAvailableAssets(): void {
    this.isLoadingAssets = true;

    this.assetService.getAvailableAssets().subscribe({
      next: (assets) => {
        this.availableAssets = assets;
        this.isLoadingAssets = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.availableAssets = [];
        this.isLoadingAssets = false;
        this.cdr.markForCheck();
      },
    });
  }

  formatAssetLabel(asset: AssetResponse): string {
    if (asset.description && asset.description.trim()) {
      return `${asset.code} - ${asset.type} (${asset.description.trim()})`;
    }
    return `${asset.code} - ${asset.type}`;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files && input.files.length > 0 ? input.files[0] : null;
    this.validateSelectedFile(file);
    this.cdr.markForCheck();
  }

  validateSelectedFile(file: File | null): boolean {
    if (!file) {
      this.selectedFile = null;
      this.attachmentError = null;
      return true;
    }

    if (!TicketForm.ALLOWED_MIME_TYPES.includes(file.type)) {
      this.selectedFile = null;
      this.attachmentError = 'Formato de imagem inválido. Envie apenas imagens PNG ou JPG.';
      return false;
    }

    if (file.size === 0) {
      this.selectedFile = null;
      this.attachmentError = 'O arquivo selecionado está vazio.';
      return false;
    }

    if (file.size > TicketForm.MAX_FILE_SIZE_BYTES) {
      this.selectedFile = null;
      this.attachmentError = 'A imagem deve ter no máximo 2 MB.';
      return false;
    }

    this.selectedFile = file;
    this.attachmentError = null;
    return true;
  }

  onSubmit(): void {
    const raw = this.ticketForm.getRawValue();
    if (!raw.title.trim()) {
      this.ticketForm.controls.title.setValue('');
    }
    if (!raw.description.trim()) {
      this.ticketForm.controls.description.setValue('');
    }

    if (this.ticketForm.invalid || this.attachmentError || this.isSaving) {
      this.ticketForm.markAllAsTouched();
      return;
    }

    if (!raw.category) {
      this.ticketForm.markAllAsTouched();
      return;
    }

    const isEquipment = raw.category === 'INCIDENTE_EQUIPAMENTO';
    const parsedAssetId =
      isEquipment && raw.assetId !== null && raw.assetId !== undefined
        ? Number(raw.assetId)
        : null;

    if (isEquipment && !parsedAssetId) {
      this.ticketForm.controls.assetId.markAsTouched();
      return;
    }

    this.isSaving = true;
    this.errorMessage = null;
    this.existingTicketId = null;

    const payload: TicketRequest = {
      title: raw.title.trim(),
      description: raw.description.trim(),
      category: raw.category,
      assetId: parsedAssetId,
    };

    const fileToUpload = this.selectedFile;

    this.ticketService.createTicket(payload).subscribe({
      next: (created) => {
        if (!fileToUpload) {
          this.isSaving = false;
          this.router.navigate(['/tickets', created.id]);
          return;
        }

        this.ticketService.uploadAttachment(created.id, fileToUpload).subscribe({
          next: () => {
            this.isSaving = false;
            this.router.navigate(['/tickets', created.id]);
          },
          error: () => {
            this.isSaving = false;
            this.errorMessage =
              'O chamado foi criado com sucesso, mas não foi possível anexar a imagem.';
            this.cdr.markForCheck();
          },
        });
      },
      error: (error: HttpErrorResponse) => {
        this.isSaving = false;
        this.existingTicketId =
          typeof error.error?.existingTicketId === 'number'
            ? error.error.existingTicketId
            : null;
        if (error.status === 400) {
          this.errorMessage =
            error.error?.message ||
            'Dados inválidos para abertura do chamado. Verifique os campos informados.';
        } else if (error.status === 403) {
          this.errorMessage =
            'Você não possui permissão para abrir chamados. Apenas solicitantes podem criar chamados.';
        } else if (error.status === 404) {
          this.errorMessage = 'O ativo selecionado não foi encontrado.';
        } else if (error.status === 401) {
          this.errorMessage =
            'Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.';
        } else {
          this.errorMessage =
            'Ocorreu um erro ao abrir o chamado. Verifique os dados e tente novamente.';
        }
        this.cdr.markForCheck();
      },
    });
  }
}
