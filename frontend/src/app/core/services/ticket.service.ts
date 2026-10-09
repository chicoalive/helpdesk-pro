import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TicketCommentResponse, TicketResponse } from '../models/ticket.model';

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

  getComments(ticketId: number): Observable<TicketCommentResponse[]> {
    return this.http.get<TicketCommentResponse[]>(`${this.apiUrl}/${ticketId}/comments`, {
      headers: this.getAuthHeaders(),
    });
  }
}
