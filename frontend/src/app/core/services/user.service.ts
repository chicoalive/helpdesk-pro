import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserCreateRequest, UserUpdateRequest, UserSummary } from '../models/ticket.model';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private readonly apiUrl = '/api/users';

  constructor(private readonly http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('helpdesk_token');
    return new HttpHeaders({
      Authorization: `Bearer ${token ?? ''}`
    });
  }

  getUsers(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(this.apiUrl, { headers: this.getAuthHeaders() });
  }

  changeState(id: number, active: boolean, expectedVersion: number): Observable<UserSummary> {
    return this.http.patch<UserSummary>(`${this.apiUrl}/${id}/state`, { active, expectedVersion },
      { headers: this.getAuthHeaders() });
  }

  updateUser(id: number, payload: UserUpdateRequest): Observable<UserSummary> {
    return this.http.put<UserSummary>(this.apiUrl + '/' + id, payload, { headers: this.getAuthHeaders() });
  }

  createUser(payload: UserCreateRequest): Observable<UserSummary> {
    return this.http.post<UserSummary>(this.apiUrl, payload, { headers: this.getAuthHeaders() });
  }
}
