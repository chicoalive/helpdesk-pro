import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
export interface RecoveryRequest { id:number;userId:number;name:string;email:string;role:string;active:boolean;status:string;requestedAt:string;decidedAt:string|null;expiresAt:string|null;approvedById:number|null;version:number; }
export interface RecoveryAuthorization {requestId:number;authorization:string;expiresAt:string;}
@Injectable({providedIn:'root'})
export class PasswordRecoveryService {
 constructor(private readonly http:HttpClient){}
 private headers(){return new HttpHeaders({Authorization:`Bearer ${localStorage.getItem('helpdesk_token') ?? ''}`});}
 request(email:string){return this.http.post<{message:string}>('/api/auth/password-recovery',{email});}
 reset(authorization:string,password:string,confirmation:string){return this.http.post<{message:string}>('/api/auth/password-reset',{authorization,password,confirmation});}
 list(){return this.http.get<RecoveryRequest[]>('/api/admin/password-recoveries',{headers:this.headers()});}
 approve(row:RecoveryRequest){return this.http.post<RecoveryAuthorization>(`/api/admin/password-recoveries/${row.id}/approve`,{expectedVersion:row.version,identityVerified:true},{headers:this.headers()});}
 decide(row:RecoveryRequest,action:'reject'|'revoke'){return this.http.post<RecoveryRequest>(`/api/admin/password-recoveries/${row.id}/${action}`,{expectedVersion:row.version},{headers:this.headers()});}
}
