import { TicketPriority, TicketStatus } from './ticket.model';

export interface TechnicianTicketCount {
  technicianId: number;
  technicianName: string;
  total: number;
}

export interface DashboardResponse {
  ticketsBySla?: Partial<Record<import('./ticket-sla').TicketSlaStatus, number>>;
  ticketsByStatus: Record<TicketStatus, number>;
  ticketsByPriority: Record<TicketPriority, number>;
  ticketsByTechnician: TechnicianTicketCount[];
  unassignedTickets: number;
  assetsUnderMaintenance: number;
}
