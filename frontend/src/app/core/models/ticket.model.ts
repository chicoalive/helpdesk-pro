export type TicketStatus =
  | 'CRIADO'
  | 'ABERTO'
  | 'EM_ATENDIMENTO'
  | 'AGUARDANDO_USUARIO'
  | 'RESOLVIDO'
  | 'FECHADO'
  | 'CANCELADO';

export type TicketCategory =
  | 'INCIDENTE_EQUIPAMENTO'
  | 'INCIDENTE_SISTEMA'
  | 'SOLICITACAO_ACESSO'
  | 'DUVIDA'
  | 'OUTRO';

export type TicketPriority = 'BAIXA' | 'MEDIA' | 'ALTA';

export type UserRole = 'REQUESTER' | 'TECHNICIAN' | 'ADMIN';

export interface TicketResponse {
  id: number;
  title: string;
  description: string;
  solution: string | null;
  status: TicketStatus;
  category: TicketCategory;
  priority: TicketPriority;
  createdAt: string;
  updatedAt: string | null;
  requesterId: number;
  requesterName: string;
  requesterEmail: string;
  technicianId: number | null;
  technicianName: string | null;
  technicianEmail: string | null;
  assetId: number | null;
  assetCode: string | null;
  assetType: string | null;
}

export interface TicketCommentResponse {
  id: number;
  content: string;
  createdAt: string;
  ticketId: number | null;
  authorId: number | null;
  authorName: string | null;
  authorRole: UserRole | null;
}

export type AssetStatus = 'INACTIVE' | 'IN_USE' | 'UNDER_MAINTENANCE' | 'DISCARDED';

export interface AssetResponse {
  id: number;
  code: string;
  type: string;
  description: string | null;
  status: AssetStatus;
}

export interface AssetRequest {
  code: string;
  type: string;
  description?: string | null;
  status: AssetStatus;
}
