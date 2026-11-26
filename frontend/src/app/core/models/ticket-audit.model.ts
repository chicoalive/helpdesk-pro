export interface TicketAuditResponse {
  id: number;
  ticketId: number;
  changedById: number;
  changedByName: string;
  changedByEmail: string;
  fieldName: string;
  justification?: string | null;
  oldValue: string | null;
  newValue: string | null;
  changedAt: string;
}
