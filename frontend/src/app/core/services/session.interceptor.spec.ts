import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { AuthService } from './auth.service';
import { sessionInterceptor } from './session.interceptor';

describe('Revoked session handling',()=>{
  const auth={getToken:vi.fn(),logout:vi.fn()};
  let http:HttpClient; let controller:HttpTestingController;
  beforeEach(()=>{
    vi.resetAllMocks(); auth.getToken.mockReturnValue('current');
    TestBed.configureTestingModule({providers:[provideHttpClient(withInterceptors([sessionInterceptor])),provideHttpClientTesting(),{provide:AuthService,useValue:auth}]});
    http=TestBed.inject(HttpClient); controller=TestBed.inject(HttpTestingController);
  });
  afterEach(()=>controller.verify());
  it('closes a revoked authenticated session',()=>{
    http.get('/api/tickets',{headers:{Authorization:'Bearer current'}}).subscribe({error:()=>{}});
    controller.expectOne('/api/tickets').flush({}, {status:401,statusText:'Unauthorized'});
    expect(auth.logout).toHaveBeenCalledOnce();
  });
  it('does not close a new session for an old response',()=>{
    http.get('/api/tickets',{headers:{Authorization:'Bearer old'}}).subscribe({error:()=>{}});
    controller.expectOne('/api/tickets').flush({}, {status:401,statusText:'Unauthorized'});
    expect(auth.logout).not.toHaveBeenCalled();
  });
  it('preserves session after permission denial or failed login',()=>{
    http.get('/api/users',{headers:{Authorization:'Bearer current'}}).subscribe({error:()=>{}});
    controller.expectOne('/api/users').flush({}, {status:403,statusText:'Forbidden'});
    http.post('/api/auth/login',{}).subscribe({error:()=>{}});
    controller.expectOne('/api/auth/login').flush({}, {status:401,statusText:'Unauthorized'});
    expect(auth.logout).not.toHaveBeenCalled();
  });
});
