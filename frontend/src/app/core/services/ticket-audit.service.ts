import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TicketAuditResponse } from '../models/ticket-audit.model';
import { AuthService } from './auth.service';

@Injectable({
  providedIn: 'root',
})
export class TicketAuditService {
  private readonly apiUrl = '/api/tickets';

  constructor(
    private readonly http: HttpClient,
    private readonly authService: AuthService,
  ) {}

  getTicketAudit(ticketId: number): Observable<TicketAuditResponse[]> {
    const token = this.authService.getToken();
    const headers = new HttpHeaders({
      Authorization: `Bearer ${token ?? ''}`,
    });
    return this.http.get<TicketAuditResponse[]>(`${this.apiUrl}/${ticketId}/audit`, { headers });
  }
}
