import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DashboardService } from './dashboard.service';
import { DashboardResponse } from '../models/dashboard.model';

describe('DashboardService', () => {
  let service: DashboardService;
  let httpMock: HttpTestingController;

  const mockSummary: DashboardResponse = {
    ticketsByStatus: {
      CRIADO: 0,
      ABERTO: 1,
      EM_ATENDIMENTO: 0,
      AGUARDANDO_USUARIO: 0,
      RESOLVIDO: 0,
      FECHADO: 0,
      CANCELADO: 0,
    },
    ticketsByPriority: { BAIXA: 0, MEDIA: 1, ALTA: 0 },
    ticketsByTechnician: [],
    unassignedTickets: 1,
    assetsUnderMaintenance: 0,
  };

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [DashboardService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(DashboardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should send GET to /api/dashboard/summary with Authorization header', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');
    let result: DashboardResponse | undefined;

    service.getSummary().subscribe((data) => (result = data));

    const req = httpMock.expectOne('/api/dashboard/summary');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');
    req.flush(mockSummary);

    expect(result).toEqual(mockSummary);
  });
});
