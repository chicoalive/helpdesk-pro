import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserCreateRequest, UserSummary } from '../models/ticket.model';

@Injectable({
  providedIn: 'root',
})
export class UserService {
  private readonly apiUrl = '/api/users';

  constructor(private readonly http: HttpClient) {}

  getUsers(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(this.apiUrl);
  }

  createUser(payload: UserCreateRequest): Observable<UserSummary> {
    return this.http.post<UserSummary>(this.apiUrl, payload);
  }
}
