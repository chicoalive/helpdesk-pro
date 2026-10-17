import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  TicketAssignTechnicianRequest,
  TicketCommentResponse,
  TicketRequest,
  TicketResponse,
  TicketStatus,
  TicketStatusUpdateRequest,
  UserSummary,
} from '../models/ticket.model';

@Injectable({
  providedIn: 'root',
})
export class TicketService {
  private readonly apiUrl = '/api/tickets';

  constructor(private readonly http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('helpdesk_token');
    return new HttpHeaders({
      Authorization: `Bearer ${token ?? ''}`,
    });
  }

  getTickets(): Observable<TicketResponse[]> {
    return this.http.get<TicketResponse[]>(this.apiUrl, {
      headers: this.getAuthHeaders(),
    });
  }

  getTicketById(id: number): Observable<TicketResponse> {
    return this.http.get<TicketResponse>(`${this.apiUrl}/${id}`, {
      headers: this.getAuthHeaders(),
    });
  }

  createTicket(ticket: TicketRequest): Observable<TicketResponse> {
    return this.http.post<TicketResponse>(this.apiUrl, ticket, {
      headers: this.getAuthHeaders(),
    });
  }

  assignTechnician(ticketId: number, technicianId: number): Observable<TicketResponse> {
    const payload: TicketAssignTechnicianRequest = { technicianId };
    return this.http.patch<TicketResponse>(`${this.apiUrl}/${ticketId}/technician`, payload, {
      headers: this.getAuthHeaders(),
    });
  }

  updateTicketStatus(
    ticketId: number,
    status: TicketStatus,
    solution?: string | null,
  ): Observable<TicketResponse> {
    const payload: TicketStatusUpdateRequest = {
      status,
      solution: solution && solution.trim() ? solution.trim() : null,
    };
    return this.http.patch<TicketResponse>(`${this.apiUrl}/${ticketId}/status`, payload, {
      headers: this.getAuthHeaders(),
    });
  }

  getTechnicians(): Observable<UserSummary[]> {
    return this.http
      .get<UserSummary[]>('/api/users', {
        headers: this.getAuthHeaders(),
      })
      .pipe(map((users) => users.filter((user) => user.role === 'TECHNICIAN')));
  }

  getComments(ticketId: number): Observable<TicketCommentResponse[]> {
    return this.http.get<TicketCommentResponse[]>(`${this.apiUrl}/${ticketId}/comments`, {
      headers: this.getAuthHeaders(),
    });
  }

  uploadAttachment(ticketId: number, file: File): Observable<TicketResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<TicketResponse>(`${this.apiUrl}/${ticketId}/attachment`, formData, {
      headers: this.getAuthHeaders(),
    });
  }

  getAttachment(ticketId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${ticketId}/attachment`, {
      headers: this.getAuthHeaders(),
      responseType: 'blob',
    });
  }
}
