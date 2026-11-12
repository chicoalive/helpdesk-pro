import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UserService } from './user.service';
import { UserCreateRequest, UserSummary } from '../models/ticket.model';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch users via GET /api/users', () => {
    // Prepare a fake token
    localStorage.setItem('helpdesk_token', 'test-token');
    const mockUsers: UserSummary[] = [
      { id: 1, name: 'Admin TI', email: 'admin@helpdesk.com', role: 'ADMIN' },
      { id: 2, name: 'Ana Técnica', email: 'ana@helpdesk.com', role: 'TECHNICIAN' },
    ];

    service.getUsers().subscribe((users) => {
      expect(users).toEqual(mockUsers);
    });

    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('GET');
    // Verify Authorization header is sent
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush(mockUsers);
  });

  it('should create a user via POST /api/users', () => {
    const payload: UserCreateRequest = {
      name: 'Novo Solicitante',
      email: 'novo@helpdesk.com',
      password: '123',
      role: 'REQUESTER',
    };

    const createdUser: UserSummary = {
      id: 10,
      name: 'Novo Solicitante',
      email: 'novo@helpdesk.com',
      role: 'REQUESTER',
    };

    service.createUser(payload).subscribe((result) => {
      expect(result).toEqual(createdUser);
    });

    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    // Verify Authorization header is sent
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush(createdUser);
  });
});
