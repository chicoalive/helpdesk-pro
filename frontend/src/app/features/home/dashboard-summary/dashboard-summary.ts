import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DashboardService } from '../../../core/services/dashboard.service';
import { DashboardResponse, TechnicianTicketCount } from '../../../core/models/dashboard.model';

export interface DashboardEntry {
  key: string;
  value: number;
}

@Component({
  selector: 'app-dashboard-summary',
  standalone: true,
  styleUrl: './dashboard-summary.css',
  templateUrl: './dashboard-summary.html',
})
export class DashboardSummary implements OnInit {
  isLoading = false;
  errorMessage: string | null = null;

  statusEntries: DashboardEntry[] = [];
  priorityEntries: DashboardEntry[] = [];
  technicians: TechnicianTicketCount[] = [];
  unassignedTickets = 0;
  assetsUnderMaintenance = 0;
  hasData = false;

  constructor(
    private readonly dashboardService: DashboardService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadSummary();
  }

  loadSummary(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.dashboardService.getSummary().subscribe({
      next: (data: DashboardResponse) => {
        this.statusEntries = Object.entries(data.ticketsByStatus ?? {}).map(([key, value]) => ({ key, value }));
        this.priorityEntries = Object.entries(data.ticketsByPriority ?? {}).map(([key, value]) => ({ key, value }));
        this.technicians = data.ticketsByTechnician ?? [];
        this.unassignedTickets = data.unassignedTickets ?? 0;
        this.assetsUnderMaintenance = data.assetsUnderMaintenance ?? 0;
        this.hasData = true;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.hasData = false;
        this.statusEntries = [];
        this.priorityEntries = [];
        this.technicians = [];
        this.errorMessage = this.resolveErrorMessage(error);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
    });
  }

  private resolveErrorMessage(error: HttpErrorResponse): string {
    if (error?.status === 403) {
      return 'Você não possui permissão para visualizar os indicadores.';
    }
    if (error?.status === 401) {
      return 'Sua sessão expirou ou é inválida. Faça login novamente para ver os indicadores.';
    }
    return 'Não foi possível carregar os indicadores. Tente novamente mais tarde.';
  }
}
