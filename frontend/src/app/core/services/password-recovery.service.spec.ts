import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController,provideHttpClientTesting} from '@angular/common/http/testing';
import {PasswordRecoveryService,RecoveryRequest} from './password-recovery.service';
describe('PasswordRecoveryService',()=>{
 let service:PasswordRecoveryService;let http:HttpTestingController;
 beforeEach(()=>{TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});service=TestBed.inject(PasswordRecoveryService);http=TestBed.inject(HttpTestingController);localStorage.setItem('helpdesk_token','test-admin');});
 afterEach(()=>{http.verify();localStorage.clear();});
 it('requests recovery without authentication',()=>{service.request('user@empresa.test').subscribe();const r=http.expectOne('/api/auth/password-recovery');expect(r.request.headers.has('Authorization')).toBe(false);expect(r.request.body).toEqual({email:'user@empresa.test'});r.flush({message:'generic'});});
 it('sends reset credentials only in the POST body',()=>{service.reset('A'.repeat(43),'StrongPassword123','StrongPassword123').subscribe();const r=http.expectOne('/api/auth/password-reset');expect(r.request.method).toBe('POST');expect(r.request.body.authorization).toHaveLength(43);expect(r.request.headers.has('Authorization')).toBe(false);r.flush({message:'ok'});});
 it('requires JWT and version for administrative authorization',()=>{service.approve({id:7,version:2} as RecoveryRequest).subscribe();const r=http.expectOne('/api/admin/password-recoveries/7/approve');expect(r.request.headers.get('Authorization')).toBe('Bearer test-admin');expect(r.request.body).toEqual({expectedVersion:2,identityVerified:true});r.flush({authorization:'A'.repeat(43)});});
 it('lists without expecting authorization values',()=>{service.list().subscribe(r=>expect(r).toEqual([]));const r=http.expectOne('/api/admin/password-recoveries');expect(r.request.headers.get('Authorization')).toBe('Bearer test-admin');r.flush([]);});
 it('revokes with the expected version',()=>{service.decide({id:7,version:3} as RecoveryRequest,'revoke').subscribe();const r=http.expectOne('/api/admin/password-recoveries/7/revoke');expect(r.request.body).toEqual({expectedVersion:3});r.flush({});});
});
