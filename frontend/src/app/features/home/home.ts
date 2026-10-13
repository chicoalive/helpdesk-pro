import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TicketService } from '../../core/services/ticket.service';
import { AuthService } from '../../core/services/auth.service';
import { TicketResponse } from '../../core/models/ticket.model';
import { DashboardSummary } from './dashboard-summary/dashboard-summary';

@Component({
  selector: 'app-home',
  imports: [FormsModule, RouterLink, DashboardSummary],
  styleUrl: './home.css',
  templateUrl: './home.html',
})
export class Home implements OnInit {
  tickets: TicketResponse[] = [];
  errorMessage: string | null = null;
  isLoading = false;

  searchTerm = '';
  statusFilter = '';
  priorityFilter = '';

  constructor(
    private readonly ticketService: TicketService,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  get canViewAssets(): boolean {
    return this.authService.canViewAssets();
  }

  get canViewDashboard(): boolean {
    return this.authService.canViewDashboard();
  }

  ngOnInit(): void {
    this.loadTickets();
  }

  get filteredTickets(): TicketResponse[] {
    const term = this.searchTerm.trim().toLowerCase();

    return this.tickets.filter((ticket) => {
      const matchesSearch =
        !term ||
        ticket.id.toString().includes(term) ||
        ticket.title.toLowerCase().includes(term);

      const matchesStatus =
        !this.statusFilter || ticket.status === this.statusFilter;

      const matchesPriority =
        !this.priorityFilter || ticket.priority === this.priorityFilter;

      return matchesSearch && matchesStatus && matchesPriority;
    });
  }

  loadTickets(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.ticketService.getTickets().subscribe({
      next: (data) => {
        this.tickets = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.errorMessage = 'Não foi possível carregar os chamados. Tente novamente mais tarde.';
        this.tickets = [];
        this.isLoading = false;
        this.cdr.markForCheck();
      },
    });
  }
}
