import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardResponse } from '../models/dashboard.model';

@Injectable({
  providedIn: 'root',
})
export class DashboardService {
  private readonly apiUrl = '/api/dashboard/summary';

  constructor(private readonly http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('helpdesk_token');
    return new HttpHeaders({
      Authorization: `Bearer ${token ?? ''}`,
    });
  }

  getSummary(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(this.apiUrl, {
      headers: this.getAuthHeaders(),
    });
  }
}
