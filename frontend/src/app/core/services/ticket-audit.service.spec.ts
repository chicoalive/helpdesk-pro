import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { TicketAuditService } from './ticket-audit.service';
import { TicketAuditResponse } from '../models/ticket-audit.model';

describe('TicketAuditService', () => {
  let service: TicketAuditService;
  let httpMock: HttpTestingController;
  const authServiceSpy = {
    getToken: () => 'audit-test-token',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TicketAuditService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });
    service = TestBed.inject(TicketAuditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests a ticket audit with the authenticated token', () => {
    const response: TicketAuditResponse[] = [{
      id: 8,
      ticketId: 10,
      changedById: 2,
      changedByName: 'Ana Técnica',
      changedByEmail: 'ana@example.com',
      fieldName: 'status',
      oldValue: 'ABERTO',
      newValue: 'EM_ATENDIMENTO',
      changedAt: '2026-10-13T12:00:00Z',
    }];

    service.getTicketAudit(10).subscribe((entries) => {
      expect(entries).toEqual(response);
    });

    const request = httpMock.expectOne('/api/tickets/10/audit');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer audit-test-token');
    request.flush(response);
  });
});
