import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TicketService } from '../../core/services/ticket.service';
import { TicketAuditService } from '../../core/services/ticket-audit.service';
import { TicketAuditResponse } from '../../core/models/ticket-audit.model';
import { AuthService } from '../../core/services/auth.service';
import {
  TicketCategory,
  TicketCommentResponse,
  TicketResponse,
  TicketStatus,
  UserRole,
  UserSummary,
} from '../../core/models/ticket.model';

@Component({
  selector: 'app-ticket-detail',
  imports: [RouterLink, FormsModule],
  styleUrl: './ticket-detail.css',
  templateUrl: './ticket-detail.html',
})
export class TicketDetail implements OnInit, OnDestroy {
  ticket: TicketResponse | null = null;
  comments: TicketCommentResponse[] = [];
  auditEntries: TicketAuditResponse[] = [];
  technicians: UserSummary[] = [];
  attachmentUrl: string | null = null;

  selectedTechnicianId: number | null = null;
  selectedStatus: TicketStatus = 'CRIADO';
  solutionText = '';

  readonly statusOptions: TicketStatus[] = [
    'CRIADO',
    'ABERTO',
    'EM_ATENDIMENTO',
    'AGUARDANDO_USUARIO',
    'RESOLVIDO',
    'FECHADO',
    'CANCELADO',
  ];

  isLoadingTicket = false;
  isLoadingComments = false;
  isLoadingAudit = false;
  isLoadingAttachment = false;
  isAssigningTechnician = false;
  isUpdatingStatus = false;

  errorMessage: string | null = null;
  commentsErrorMessage: string | null = null;
  auditErrorMessage: string | null = null;
  attachmentErrorMessage: string | null = null;
  operationErrorMessage: string | null = null;
  operationSuccessMessage: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly ticketService: TicketService,
    private readonly ticketAuditService: TicketAuditService,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const ticketId = Number(idParam);

    if (!idParam || isNaN(ticketId) || ticketId <= 0) {
      this.errorMessage = 'Chamado não encontrado.';
      return;
    }

    this.loadTicket(ticketId);
    this.loadComments(ticketId);
    if (this.canViewAudit) {
      this.loadTechnicians();
      this.loadAudit(ticketId);
    }
  }

  ngOnDestroy(): void {
    this.revokeAttachmentUrl();
  }

  private revokeAttachmentUrl(): void {
    if (this.attachmentUrl) {
      URL.revokeObjectURL(this.attachmentUrl);
      this.attachmentUrl = null;
    }
  }

  get canViewAudit(): boolean {
    return this.authService.canViewDashboard();
  }

  get canManageTicket(): boolean {
    return this.canViewAudit;
  }

  get isRequester(): boolean {
    if (typeof this.authService.isRequester === 'function') {
      return this.authService.isRequester();
    }
    return !this.authService.canViewDashboard();
  }

  get shouldShowSolutionField(): boolean {
    if (!this.ticket) {
      return false;
    }
    return (
      this.selectedStatus === 'RESOLVIDO' ||
      this.selectedStatus === 'FECHADO' ||
      this.ticket.status === 'EM_ATENDIMENTO' ||
      this.ticket.status === 'RESOLVIDO' ||
      !!this.ticket.solution
    );
  }

  getCategoryLabel(category: TicketCategory | string): string {
    switch (category) {
      case 'INCIDENTE_SISTEMA':
        return 'Problema no sistema';
      case 'INCIDENTE_EQUIPAMENTO':
        return 'Problema no equipamento';
      case 'SOLICITACAO_ACESSO':
        return 'Solicitação de acesso';
      case 'DUVIDA':
        return 'Dúvida';
      case 'OUTRO':
        return 'Outro';
      default:
        return category;
    }
  }

  printReceipt(): void {
    window.print();
  }

  loadTechnicians(): void {
    if (!this.canManageTicket || typeof this.ticketService.getTechnicians !== 'function') {
      return;
    }

    this.ticketService.getTechnicians().subscribe({
      next: (techs) => {
        this.technicians = techs;
        this.cdr.markForCheck();
      },
      error: () => {
        this.technicians = [];
        this.cdr.markForCheck();
      },
    });
  }

  private syncFormFromTicket(ticket: TicketResponse): void {
    this.selectedStatus = ticket.status;
    this.solutionText = ticket.solution ?? '';
    if (ticket.technicianId !== null) {
      this.selectedTechnicianId = ticket.technicianId;
    }
  }

  onAssignTechnician(): void {
    if (!this.ticket || !this.selectedTechnicianId || this.isAssigningTechnician) {
      return;
    }

    this.isAssigningTechnician = true;
    this.operationErrorMessage = null;
    this.operationSuccessMessage = null;

    const techId = Number(this.selectedTechnicianId);

    this.ticketService.assignTechnician(this.ticket.id, techId).subscribe({
      next: (updatedTicket) => {
        this.ticket = updatedTicket;
        this.syncFormFromTicket(updatedTicket);
        this.isAssigningTechnician = false;
        this.operationSuccessMessage = 'Técnico atribuído com sucesso.';
        this.loadAudit(updatedTicket.id);
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.isAssigningTechnician = false;
        this.operationErrorMessage =
          error.error?.message || 'Não foi possível atribuir o técnico ao chamado.';
        this.cdr.markForCheck();
      },
    });
  }

  onUpdateStatus(): void {
    if (!this.ticket || !this.selectedStatus || this.isUpdatingStatus) {
      return;
    }

    this.isUpdatingStatus = true;
    this.operationErrorMessage = null;
    this.operationSuccessMessage = null;

    const solutionPayload =
      this.shouldShowSolutionField && this.solutionText.trim().length > 0
        ? this.solutionText.trim()
        : null;

    this.ticketService
      .updateTicketStatus(this.ticket.id, this.selectedStatus, solutionPayload)
      .subscribe({
        next: (updatedTicket) => {
          this.ticket = updatedTicket;
          this.syncFormFromTicket(updatedTicket);
          this.isUpdatingStatus = false;
          this.operationSuccessMessage = 'Chamado atualizado com sucesso.';
          this.loadAudit(updatedTicket.id);
          this.cdr.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          this.isUpdatingStatus = false;
          this.operationErrorMessage =
            error.error?.message || 'Não foi possível atualizar o status do chamado.';
          this.cdr.markForCheck();
        },
      });
  }

  loadTicket(ticketId: number): void {
    this.isLoadingTicket = true;
    this.errorMessage = null;

    this.ticketService.getTicketById(ticketId).subscribe({
      next: (ticket) => {
        this.ticket = ticket;
        this.syncFormFromTicket(ticket);
        this.isLoadingTicket = false;
        this.revokeAttachmentUrl();
        this.attachmentErrorMessage = null;
        if (ticket.hasAttachment) {
          this.loadAttachment(ticket.id);
        }
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.ticket = null;
        this.isLoadingTicket = false;
        this.revokeAttachmentUrl();
        this.errorMessage = this.resolveErrorMessage(error);
        this.cdr.markForCheck();
      },
    });
  }

  loadAttachment(ticketId: number): void {
    this.isLoadingAttachment = true;
    this.attachmentErrorMessage = null;
    this.revokeAttachmentUrl();

    this.ticketService.getAttachment(ticketId).subscribe({
      next: (blob) => {
        this.attachmentUrl = URL.createObjectURL(blob);
        this.isLoadingAttachment = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingAttachment = false;
        this.attachmentErrorMessage = 'Não foi possível carregar a imagem anexada.';
        this.cdr.markForCheck();
      },
    });
  }

  loadComments(ticketId: number): void {
    this.isLoadingComments = true;
    this.commentsErrorMessage = null;

    this.ticketService.getComments(ticketId).subscribe({
      next: (comments) => {
        this.comments = comments;
        this.isLoadingComments = false;
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.comments = [];
        this.isLoadingComments = false;
        this.commentsErrorMessage = this.resolveErrorMessage(error, true);
        this.cdr.markForCheck();
      },
    });
  }

  loadAudit(ticketId: number): void {
    if (!this.canViewAudit) {
      return;
    }

    this.isLoadingAudit = true;
    this.auditErrorMessage = null;

    this.ticketAuditService.getTicketAudit(ticketId).subscribe({
      next: (entries) => {
        this.auditEntries = entries;
        this.isLoadingAudit = false;
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.auditEntries = [];
        this.isLoadingAudit = false;
        this.auditErrorMessage = this.resolveAuditErrorMessage(error);
        this.cdr.markForCheck();
      },
    });
  }

  getAuditFieldLabel(fieldName: string): string {
    switch (fieldName) {
      case 'status':
        return 'Status';
      case 'technician':
        return 'Técnico';
      case 'solution':
        return 'Solução';
      default:
        return fieldName;
    }
  }

  formatAuditValue(fieldName: string, value: string | null): string {
    if (fieldName === 'technician') {
      return value === null ? 'Não atribuído' : `Técnico #${value}`;
    }
    return value ?? '—';
  }

  private resolveAuditErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 403) {
      return 'Você não possui permissão para visualizar a auditoria deste chamado.';
    }
    return 'Não foi possível carregar a auditoria deste chamado.';
  }

  private resolveErrorMessage(error: HttpErrorResponse, isComments = false): string {
    if (error.status === 401) {
      return 'Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.';
    }
    if (error.status === 403) {
      return isComments
        ? 'Você não possui permissão para visualizar os comentários deste chamado.'
        : 'Você não possui permissão para visualizar este chamado.';
    }
    if (error.status === 404) {
      return 'Chamado não encontrado.';
    }
    return isComments
      ? 'Não foi possível carregar os comentários deste chamado.'
      : 'Ocorreu um erro ao carregar os detalhes do chamado. Tente novamente mais tarde.';
  }

  formatDate(dateStr: string | null | undefined): string {
    if (!dateStr) {
      return '-';
    }
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? dateStr : date.toLocaleString('pt-BR');
  }

  getRoleLabel(role: UserRole | null | undefined): string {
    switch (role) {
      case 'ADMIN':
        return 'Administrador';
      case 'TECHNICIAN':
        return 'Técnico';
      case 'REQUESTER':
        return 'Solicitante';
      default:
        return 'Usuário';
    }
  }
}
