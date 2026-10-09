import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { TicketDetail } from './ticket-detail';
import { TicketService } from '../../core/services/ticket.service';
import { TicketCommentResponse, TicketResponse } from '../../core/models/ticket.model';

describe('TicketDetail', () => {
  let component: TicketDetail;
  let fixture: ComponentFixture<TicketDetail>;
  let ticketServiceSpy: {
    getTicketById: ReturnType<typeof vi.fn>;
    getComments: ReturnType<typeof vi.fn>;
  };

  const mockTicket: TicketResponse = {
    id: 10,
    title: 'Falha no teclado',
    description: 'Teclado não responde a certas teclas',
    solution: 'Substituição do teclado realizada com sucesso',
    status: 'RESOLVIDO',
    category: 'INCIDENTE_EQUIPAMENTO',
    priority: 'MEDIA',
    createdAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T11:00:00Z',
    requesterId: 1,
    requesterName: 'Carlos Solicitante',
    requesterEmail: 'carlos@helpdesk.com',
    technicianId: 2,
    technicianName: 'Ana Tecnica',
    technicianEmail: 'ana@helpdesk.com',
    assetId: 5,
    assetCode: 'TECL-01',
    assetType: 'Periférico',
  };

  const mockComments: TicketCommentResponse[] = [
    {
      id: 1,
      content: 'Aguardando envio do periférico.',
      createdAt: '2026-09-25T10:05:00Z',
      ticketId: 10,
      authorId: 2,
      authorName: 'Ana Tecnica',
      authorRole: 'TECHNICIAN',
    },
    {
      id: 2,
      content: 'Periférico entregue na recepção de TI.',
      createdAt: '2026-09-25T10:20:00Z',
      ticketId: 10,
      authorId: 1,
      authorName: 'Carlos Solicitante',
      authorRole: 'REQUESTER',
    },
  ];

  const setupTestBed = async (routeId: string | null = '10') => {
    ticketServiceSpy = {
      getTicketById: vi.fn().mockReturnValue(of(mockTicket)),
      getComments: vi.fn().mockReturnValue(of(mockComments)),
    };

    await TestBed.configureTestingModule({
      imports: [TicketDetail],
      providers: [
        provideRouter([]),
        { provide: TicketService, useValue: ticketServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? routeId : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TicketDetail);
    component = fixture.componentInstance;
    await fixture.whenStable();
  };

  it('should create the component', async () => {
    await setupTestBed('10');
    expect(component).toBeTruthy();
  });

  it('should load ticket details and comments on init', async () => {
    await setupTestBed('10');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ticketServiceSpy.getTicketById).toHaveBeenCalledWith(10);
    expect(ticketServiceSpy.getComments).toHaveBeenCalledWith(10);

    expect(component.ticket).toEqual(mockTicket);
    expect(component.comments.length).toBe(2);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('#10');
    expect(compiled.textContent).toContain('Falha no teclado');
    expect(compiled.textContent).toContain('RESOLVIDO');
    expect(compiled.textContent).toContain('INCIDENTE_EQUIPAMENTO');
    expect(compiled.textContent).toContain('Carlos Solicitante');
    expect(compiled.textContent).toContain('Ana Tecnica');
    expect(compiled.textContent).toContain('TECL-01');
    expect(compiled.textContent).toContain('Substituição do teclado realizada com sucesso');

    // Comments check
    expect(compiled.textContent).toContain('Aguardando envio do periférico.');
    expect(compiled.textContent).toContain('Periférico entregue na recepção de TI.');
  });

  it('should display "Nenhum comentário registrado." when comments list is empty', async () => {
    await setupTestBed('10');
    ticketServiceSpy.getComments.mockReturnValue(of([]));

    component.loadComments(10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.comments.length).toBe(0);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Nenhum comentário registrado.');
  });

  it('should display friendly error for 401 Unauthorized', async () => {
    await setupTestBed('10');
    const error401 = new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' });
    ticketServiceSpy.getTicketById.mockReturnValue(throwError(() => error401));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Sua sessão expirou ou o token é inválido.');
  });

  it('should display friendly error for 403 Forbidden', async () => {
    await setupTestBed('10');
    const error403 = new HttpErrorResponse({ status: 403, statusText: 'Forbidden' });
    ticketServiceSpy.getTicketById.mockReturnValue(throwError(() => error403));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Você não possui permissão para visualizar este chamado.');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Você não possui permissão para visualizar este chamado.');
  });

  it('should display friendly error for 404 Not Found', async () => {
    await setupTestBed('10');
    const error404 = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
    ticketServiceSpy.getTicketById.mockReturnValue(throwError(() => error404));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Chamado não encontrado.');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Chamado não encontrado.');
  });

  it('should handle invalid ticket ID in route parameter', async () => {
    await setupTestBed('invalido');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Chamado não encontrado.');
    expect(ticketServiceSpy.getTicketById).not.toHaveBeenCalled();
  });

  it('should display comments error when comment fetch fails', async () => {
    await setupTestBed('10');
    const error403 = new HttpErrorResponse({ status: 403, statusText: 'Forbidden' });
    ticketServiceSpy.getComments.mockReturnValue(throwError(() => error403));

    component.loadComments(10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.commentsErrorMessage).toBe('Você não possui permissão para visualizar os comentários deste chamado.');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Você não possui permissão para visualizar os comentários deste chamado.');
  });

  it('should render fallback when technician and asset are not assigned', async () => {
    const unassignedTicket: TicketResponse = {
      ...mockTicket,
      technicianId: null,
      technicianName: null,
      technicianEmail: null,
      assetId: null,
      assetCode: null,
      assetType: null,
      solution: null,
    };

    await setupTestBed('10');
    ticketServiceSpy.getTicketById.mockReturnValue(of(unassignedTicket));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Não atribuído');
    expect(compiled.textContent).toContain('Nenhum ativo vinculado');
  });

  it('should render a back navigation link to /home', async () => {
    await setupTestBed('10');
    fixture.detectChanges();
    await fixture.whenStable();

    const backLink: HTMLAnchorElement = fixture.nativeElement.querySelector('.btn-back');
    expect(backLink).toBeTruthy();
    expect(backLink.textContent).toContain('Voltar para a listagem');
  });
});
