import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TicketService } from './ticket.service';
import { TicketCommentResponse, TicketResponse } from '../models/ticket.model';

describe('TicketService', () => {
  let service: TicketService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        TicketService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(TicketService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should send GET request to /api/tickets with Authorization header', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');

    const mockTickets: TicketResponse[] = [
      {
        id: 1,
        title: 'Chamado Teste',
        description: 'Descrição do teste',
        solution: null,
        status: 'ABERTO',
        category: 'INCIDENTE_SISTEMA',
        priority: 'ALTA',
        createdAt: '2026-09-25T12:00:00Z',
        updatedAt: null,
        requesterId: 2,
        requesterName: 'Carlos Solicitante',
        requesterEmail: 'carlos@helpdesk.com',
        technicianId: null,
        technicianName: null,
        technicianEmail: null,
        assetId: null,
        assetCode: null,
        assetType: null,
      },
    ];

    service.getTickets().subscribe((tickets) => {
      expect(tickets).toEqual(mockTickets);
      expect(tickets.length).toBe(1);
    });

    const req = httpMock.expectOne('/api/tickets');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');

    req.flush(mockTickets);
  });

  it('should send GET request to /api/tickets/{id} with Authorization header', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');

    const mockTicket: TicketResponse = {
      id: 5,
      title: 'Chamado Individual',
      description: 'Detalhes do chamado',
      solution: null,
      status: 'EM_ATENDIMENTO',
      category: 'INCIDENTE_EQUIPAMENTO',
      priority: 'MEDIA',
      createdAt: '2026-09-25T10:00:00Z',
      updatedAt: null,
      requesterId: 1,
      requesterName: 'Maria Solicitante',
      requesterEmail: 'maria@helpdesk.com',
      technicianId: 2,
      technicianName: 'Ana Tecnica',
      technicianEmail: 'ana@helpdesk.com',
      assetId: 10,
      assetCode: 'NOTE-100',
      assetType: 'Notebook',
    };

    service.getTicketById(5).subscribe((ticket) => {
      expect(ticket).toEqual(mockTicket);
      expect(ticket.id).toBe(5);
    });

    const req = httpMock.expectOne('/api/tickets/5');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');

    req.flush(mockTicket);
  });

  it('should send GET request to /api/tickets/{ticketId}/comments with Authorization header and preserve order', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');

    const mockComments: TicketCommentResponse[] = [
      {
        id: 1,
        content: 'Primeiro comentário cronológico',
        createdAt: '2026-09-25T10:05:00Z',
        ticketId: 5,
        authorId: 2,
        authorName: 'Ana Tecnica',
        authorRole: 'TECHNICIAN',
      },
      {
        id: 2,
        content: 'Segundo comentário cronológico',
        createdAt: '2026-09-25T10:15:00Z',
        ticketId: 5,
        authorId: 1,
        authorName: 'Maria Solicitante',
        authorRole: 'REQUESTER',
      },
    ];

    service.getComments(5).subscribe((comments) => {
      expect(comments).toEqual(mockComments);
      expect(comments.length).toBe(2);
      expect(comments[0].id).toBe(1);
      expect(comments[1].id).toBe(2);
    });

    const req = httpMock.expectOne('/api/tickets/5/comments');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');

    req.flush(mockComments);
  });
});
