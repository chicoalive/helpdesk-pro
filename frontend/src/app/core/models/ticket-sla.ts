import { TicketResponse } from './ticket.model';

export type TicketSlaStatus = 'IN_PROGRESS_ON_TIME' | 'IN_PROGRESS_OVERDUE' | 'RESOLVED_ON_TIME' | 'RESOLVED_OVERDUE' | 'CANCELLED' | 'UNKNOWN';
export function slaStatus(ticket: TicketResponse): TicketSlaStatus {
  if (ticket.status === 'CANCELADO') return 'CANCELLED';
  if (ticket.slaStatus) return ticket.slaStatus;
  if (ticket.status === 'RESOLVIDO' || ticket.status === 'FECHADO') return 'UNKNOWN';
  if (!ticket.slaDeadline) return 'UNKNOWN';
  return ticket.slaBreached ? 'IN_PROGRESS_OVERDUE' : 'IN_PROGRESS_ON_TIME';
}
export function slaLabel(ticket: TicketResponse, requester = false, detail = false): string {
  switch (slaStatus(ticket)) {
    case 'IN_PROGRESS_ON_TIME': return requester ? 'Dentro do prazo' : 'Dentro do SLA';
    case 'IN_PROGRESS_OVERDUE': return requester ? (detail ? 'Prazo de atendimento ultrapassado' : 'Prazo ultrapassado') : 'SLA atrasado';
    case 'RESOLVED_ON_TIME': return 'Resolvido dentro do prazo';
    case 'RESOLVED_OVERDUE': return 'Resolvido fora do prazo';
    case 'CANCELLED': return 'Cancelado — não se aplica';
    default: return ticket.status === 'RESOLVIDO' || ticket.status === 'FECHADO' ? 'Sem medição histórica' : 'Prazo indisponível';
  }
}
export function slaOverdue(ticket: TicketResponse): boolean { return ['IN_PROGRESS_OVERDUE','RESOLVED_OVERDUE'].includes(slaStatus(ticket)); }
export function slaWithin(ticket: TicketResponse): boolean { return ['IN_PROGRESS_ON_TIME','RESOLVED_ON_TIME'].includes(slaStatus(ticket)); }
export function slaDelay(ticket: TicketResponse): string {
  if (!slaOverdue(ticket) || ticket.slaDelaySeconds == null) return '';
  const seconds = Math.max(1, Math.ceil(ticket.slaDelaySeconds));
  const hours = Math.floor(seconds / 3600), minutes = Math.floor(seconds % 3600 / 60), rest = seconds % 60;
  return [hours ? hours+' h' : '', minutes ? minutes+' min' : '', rest ? rest+' s' : ''].filter(Boolean).join(' ');
}
export function brazilDate(value: string | null | undefined): string {
  if (!value) return '-'; const date = new Date(value);
  return isNaN(date.getTime()) ? '-' : date.toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' });
}
