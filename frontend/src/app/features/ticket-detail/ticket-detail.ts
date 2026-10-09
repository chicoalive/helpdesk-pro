import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TicketService } from '../../core/services/ticket.service';
import { TicketCommentResponse, TicketResponse, UserRole } from '../../core/models/ticket.model';

@Component({
  selector: 'app-ticket-detail',
  imports: [RouterLink],
  styleUrl: './ticket-detail.css',
  templateUrl: './ticket-detail.html',
})
export class TicketDetail implements OnInit {
  ticket: TicketResponse | null = null;
  comments: TicketCommentResponse[] = [];

  isLoadingTicket = false;
  isLoadingComments = false;

  errorMessage: string | null = null;
  commentsErrorMessage: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly ticketService: TicketService,
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
  }

  loadTicket(ticketId: number): void {
    this.isLoadingTicket = true;
    this.errorMessage = null;

    this.ticketService.getTicketById(ticketId).subscribe({
      next: (ticket) => {
        this.ticket = ticket;
        this.isLoadingTicket = false;
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.ticket = null;
        this.isLoadingTicket = false;
        this.errorMessage = this.resolveErrorMessage(error);
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
