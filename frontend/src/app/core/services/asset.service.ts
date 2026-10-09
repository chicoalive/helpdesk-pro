import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AssetRequest, AssetResponse } from '../models/ticket.model';

@Injectable({
  providedIn: 'root',
})
export class AssetService {
  private readonly apiUrl = '/api/assets';

  constructor(private readonly http: HttpClient) {}

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('helpdesk_token');
    return new HttpHeaders({
      Authorization: `Bearer ${token ?? ''}`,
    });
  }

  getAssets(): Observable<AssetResponse[]> {
    return this.http.get<AssetResponse[]>(this.apiUrl, {
      headers: this.getAuthHeaders(),
    });
  }

  getAssetById(id: number): Observable<AssetResponse> {
    return this.http.get<AssetResponse>(`${this.apiUrl}/${id}`, {
      headers: this.getAuthHeaders(),
    });
  }

  createAsset(asset: AssetRequest): Observable<AssetResponse> {
    return this.http.post<AssetResponse>(this.apiUrl, asset, {
      headers: this.getAuthHeaders(),
    });
  }

  updateAsset(id: number, asset: AssetRequest): Observable<AssetResponse> {
    return this.http.put<AssetResponse>(`${this.apiUrl}/${id}`, asset, {
      headers: this.getAuthHeaders(),
    });
  }
}
