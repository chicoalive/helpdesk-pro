import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TicketService } from './ticket.service';
import { TicketCommentResponse, TicketRequest, TicketResponse } from '../models/ticket.model';

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
        slaDeadline: '2026-09-25T16:00:00Z',
        slaBreached: false,
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
      expect(tickets[0].slaDeadline).toBe('2026-09-25T16:00:00Z');
      expect(tickets[0].slaBreached).toBe(false);
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
      slaDeadline: '2026-09-25T14:00:00Z',
      slaBreached: true,
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
      expect(ticket.slaDeadline).toBe('2026-09-25T14:00:00Z');
      expect(ticket.slaBreached).toBe(true);
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

  it('should send POST request to /api/tickets with payload and Authorization header', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');

    const payload: TicketRequest = {
      title: 'Sistema indisponível',
      description: 'Erro 500 ao acessar portal',
      category: 'INCIDENTE_SISTEMA',
      assetId: null,
    };

    const createdTicket: TicketResponse = {
      id: 20,
      title: payload.title,
      description: payload.description,
      solution: null,
      status: 'CRIADO',
      category: payload.category,
      priority: 'ALTA',
      createdAt: '2026-10-16T18:00:00Z',
      updatedAt: null,
      slaDeadline: '2026-10-16T22:00:00Z',
      slaBreached: false,
      requesterId: 2,
      requesterName: 'Carlos Solicitante',
      requesterEmail: 'carlos@helpdesk.com',
      technicianId: null,
      technicianName: null,
      technicianEmail: null,
      assetId: null,
      assetCode: null,
      assetType: null,
    };

    service.createTicket(payload).subscribe((ticket) => {
      expect(ticket).toEqual(createdTicket);
      expect(ticket.id).toBe(20);
      expect(ticket.priority).toBe('ALTA');
    });

    const req = httpMock.expectOne('/api/tickets');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    expect(req.request.body.priority).toBeUndefined();
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');

    req.flush(createdTicket);
  });

  it('should send POST request with FormData to /api/tickets/{id}/attachment on uploadAttachment', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');

    const file = new File([new Uint8Array([137, 80, 78, 71])], 'print.png', { type: 'image/png' });
    const updatedTicket: TicketResponse = {
      id: 20,
      title: 'Sistema indisponível',
      description: 'Erro 500 ao acessar portal',
      solution: null,
      status: 'CRIADO',
      category: 'INCIDENTE_SISTEMA',
      priority: 'ALTA',
      createdAt: '2026-10-16T18:00:00Z',
      updatedAt: null,
      slaDeadline: '2026-10-16T22:00:00Z',
      slaBreached: false,
      requesterId: 2,
      requesterName: 'Carlos Solicitante',
      requesterEmail: 'carlos@helpdesk.com',
      technicianId: null,
      technicianName: null,
      technicianEmail: null,
      assetId: null,
      assetCode: null,
      assetType: null,
      hasAttachment: true,
      attachmentName: 'print.png',
      attachmentContentType: 'image/png',
    };

    service.uploadAttachment(20, file).subscribe((res) => {
      expect(res).toEqual(updatedTicket);
      expect(res.hasAttachment).toBe(true);
      expect(res.attachmentName).toBe('print.png');
      expect(res.attachmentContentType).toBe('image/png');
    });

    const req = httpMock.expectOne('/api/tickets/20/attachment');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');
    expect(req.request.body instanceof FormData).toBe(true);
    expect((req.request.body as FormData).get('file')).toBe(file);

    req.flush(updatedTicket);
  });

  it('should send GET request to /api/tickets/{id}/attachment with responseType blob on getAttachment', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');

    const mockBlob = new Blob([new Uint8Array([1, 2, 3])], { type: 'image/png' });

    service.getAttachment(20).subscribe((blob) => {
      expect(blob).toEqual(mockBlob);
      expect(blob.type).toBe('image/png');
    });

    const req = httpMock.expectOne('/api/tickets/20/attachment');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');

    req.flush(mockBlob);
  });

  it('should send PATCH request to /api/tickets/{id}/technician on assignTechnician', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');

    const updatedTicket: TicketResponse = {
      id: 10,
      title: 'Chamado atribuído',
      description: 'Descrição',
      solution: null,
      status: 'ABERTO',
      category: 'INCIDENTE_SISTEMA',
      priority: 'ALTA',
      createdAt: '2026-10-16T18:00:00Z',
      updatedAt: '2026-10-16T18:05:00Z',
      slaDeadline: '2026-10-16T22:00:00Z',
      slaBreached: false,
      requesterId: 1,
      requesterName: 'Carlos Solicitante',
      requesterEmail: 'carlos@helpdesk.com',
      technicianId: 2,
      technicianName: 'Ana Tecnica',
      technicianEmail: 'ana@helpdesk.com',
      assetId: null,
      assetCode: null,
      assetType: null,
    };

    service.assignTechnician(10, 2).subscribe((res) => {
      expect(res).toEqual(updatedTicket);
      expect(res.technicianId).toBe(2);
      expect(res.status).toBe('ABERTO');
    });

    const req = httpMock.expectOne('/api/tickets/10/technician');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');
    expect(req.request.body).toEqual({ technicianId: 2 });

    req.flush(updatedTicket);
  });

  it('should send PATCH request to /api/tickets/{id}/status with status and solution on updateTicketStatus', () => {
    localStorage.setItem('helpdesk_token', 'meu-jwt-de-teste');

    const updatedTicket: TicketResponse = {
      id: 10,
      title: 'Chamado resolvido',
      description: 'Descrição',
      solution: 'Reiniciado o serviço de autenticação',
      status: 'RESOLVIDO',
      category: 'INCIDENTE_SISTEMA',
      priority: 'ALTA',
      createdAt: '2026-10-16T18:00:00Z',
      updatedAt: '2026-10-16T18:30:00Z',
      slaDeadline: '2026-10-16T22:00:00Z',
      slaBreached: false,
      requesterId: 1,
      requesterName: 'Carlos Solicitante',
      requesterEmail: 'carlos@helpdesk.com',
      technicianId: 2,
      technicianName: 'Ana Tecnica',
      technicianEmail: 'ana@helpdesk.com',
      assetId: null,
      assetCode: null,
      assetType: null,
    };

    service
      .updateTicketStatus(10, 'RESOLVIDO', '  Reiniciado o serviço de autenticação  ')
      .subscribe((res) => {
        expect(res).toEqual(updatedTicket);
        expect(res.status).toBe('RESOLVIDO');
        expect(res.solution).toBe('Reiniciado o serviço de autenticação');
      });

    const req = httpMock.expectOne('/api/tickets/10/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.headers.get('Authorization')).toBe('Bearer meu-jwt-de-teste');
    expect(req.request.body).toEqual({
      status: 'RESOLVIDO',
      solution: 'Reiniciado o serviço de autenticação',
    });

    req.flush(updatedTicket);
  });
});
