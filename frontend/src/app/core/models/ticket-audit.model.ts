export interface TicketAuditResponse {
  id: number;
  ticketId: number;
  changedById: number;
  changedByName: string;
  changedByEmail: string;
  fieldName: string;
  oldValue: string | null;
  newValue: string | null;
  changedAt: string;
}
