import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { TicketDetail } from './ticket-detail';
import { TicketService } from '../../core/services/ticket.service';
import { TicketCommentResponse, TicketResponse } from '../../core/models/ticket.model';
import { TicketAuditService } from '../../core/services/ticket-audit.service';
import { TicketAuditResponse } from '../../core/models/ticket-audit.model';
import { AuthService } from '../../core/services/auth.service';

describe('TicketDetail', () => {
  let component: TicketDetail;
  let fixture: ComponentFixture<TicketDetail>;
  let ticketServiceSpy: {
    getTicketById: ReturnType<typeof vi.fn>;
    getComments: ReturnType<typeof vi.fn>;
    getAttachment: ReturnType<typeof vi.fn>;
    assignTechnician: ReturnType<typeof vi.fn>;
    updateTicketStatus: ReturnType<typeof vi.fn>;
    getTechnicians: ReturnType<typeof vi.fn>;
  };
  let ticketAuditServiceSpy: { getTicketAudit: ReturnType<typeof vi.fn> };
  let authServiceSpy: {
    canViewDashboard: ReturnType<typeof vi.fn>;
    isRequester: ReturnType<typeof vi.fn>;
  };

  const mockAuditEntries: TicketAuditResponse[] = [
    {
      id: 1,
      ticketId: 10,
      changedById: 3,
      changedByName: 'Admin Helpdesk',
      changedByEmail: 'admin@helpdesk.com',
      fieldName: 'technician',
      oldValue: null,
      newValue: '2',
      changedAt: '2026-09-25T10:30:00Z',
    },
    {
      id: 2,
      ticketId: 10,
      changedById: 2,
      changedByName: 'Ana Tecnica',
      changedByEmail: 'ana@helpdesk.com',
      fieldName: 'status',
      oldValue: 'ABERTO',
      newValue: 'EM_ATENDIMENTO',
      changedAt: '2026-09-25T10:40:00Z',
    },
    {
      id: 3,
      ticketId: 10,
      changedById: 2,
      changedByName: 'Ana Tecnica',
      changedByEmail: 'ana@helpdesk.com',
      fieldName: 'solution',
      oldValue: 'Diagnóstico inicial',
      newValue: 'Substituição concluída',
      changedAt: '2026-09-25T11:00:00Z',
    },
  ];

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
    slaDeadline: '2026-09-26T10:00:00Z',
    slaBreached: false,
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

  const setupTestBed = async (routeId: string | null = '10', canViewAudit = true) => {
    ticketServiceSpy = {
      getTicketById: vi.fn().mockReturnValue(of(mockTicket)),
      getComments: vi.fn().mockReturnValue(of(mockComments)),
      getAttachment: vi
        .fn()
        .mockReturnValue(of(new Blob([new Uint8Array([1, 2, 3])], { type: 'image/png' }))),
      assignTechnician: vi.fn().mockReturnValue(of(mockTicket)),
      updateTicketStatus: vi.fn().mockReturnValue(of(mockTicket)),
      getTechnicians: vi.fn().mockReturnValue(
        of([
          {
            id: 2,
            name: 'Ana Tecnica',
            email: 'ana@helpdesk.com',
            role: 'TECHNICIAN',
          },
        ]),
      ),
    };
    ticketAuditServiceSpy = {
      getTicketAudit: vi.fn().mockReturnValue(of(mockAuditEntries)),
    };
    authServiceSpy = {
      canViewDashboard: vi.fn().mockReturnValue(canViewAudit),
      isRequester: vi.fn().mockReturnValue(!canViewAudit),
    };

    await TestBed.configureTestingModule({
      imports: [TicketDetail],
      providers: [
        provideRouter([]),
        { provide: TicketService, useValue: ticketServiceSpy },
        { provide: TicketAuditService, useValue: ticketAuditServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
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
    expect(ticketAuditServiceSpy.getTicketAudit).toHaveBeenCalledWith(10);

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
    expect(compiled.textContent).toContain('Prazo do SLA');
    expect(compiled.textContent).toContain(component.formatDate(mockTicket.slaDeadline));
    expect(compiled.textContent).toContain('Dentro do SLA');

    // Comments check
    expect(compiled.textContent).toContain('Aguardando envio do periférico.');
    expect(compiled.textContent).toContain('Periférico entregue na recepção de TI.');
  });

  it('should render the audit history in the received order with readable field values', async () => {
    await setupTestBed('10');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const auditSection = compiled.querySelector('.audit-card') as HTMLElement;
    expect(auditSection.textContent).toContain('Auditoria de alterações');
    expect(auditSection.textContent).toContain('Não atribuído');
    expect(auditSection.textContent).toContain('Técnico #2');
    expect(auditSection.textContent).toContain('ABERTO');
    expect(auditSection.textContent).toContain('EM_ATENDIMENTO');
    expect(auditSection.textContent).toContain('Diagnóstico inicial');
    expect(auditSection.textContent).toContain('Substituição concluída');

    const fields = [...auditSection.querySelectorAll('.audit-item .comment-body strong')].map((field) => field.textContent);
    expect(fields).toEqual(['Técnico', 'Status', 'Solução']);
    const auditText = auditSection.textContent ?? '';
    expect(auditText.indexOf('Admin Helpdesk')).toBeLessThan(
      auditText.indexOf('Ana Tecnica'),
    );
  });

  it('should load and display audit for technicians', async () => {
    await setupTestBed('10', true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(authServiceSpy.canViewDashboard).toHaveBeenCalled();
    expect(ticketAuditServiceSpy.getTicketAudit).toHaveBeenCalledWith(10);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Auditoria de alterações');
  });

  it('should not render or request audit for requesters', async () => {
    await setupTestBed('10', false);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.audit-card')).toBeNull();
    expect(ticketAuditServiceSpy.getTicketAudit).not.toHaveBeenCalled();
  });

  it('should display an empty state when the audit list is empty', async () => {
    await setupTestBed('10');
    ticketAuditServiceSpy.getTicketAudit.mockReturnValue(of([]));
    component.loadAudit(10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhuma alteração auditada.');
  });

  it('should show a defensive forbidden audit message without breaking ticket details and comments', async () => {
    await setupTestBed('10');
    ticketAuditServiceSpy.getTicketAudit.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden' })),
    );
    component.loadAudit(10);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Você não possui permissão para visualizar a auditoria deste chamado.');
    expect(compiled.textContent).toContain('Falha no teclado');
    expect(compiled.textContent).toContain('Histórico de Comentários');
    expect(compiled.textContent).toContain('Aguardando envio do periférico.');
  });

  it('should show a generic audit error without breaking the detail screen', async () => {
    await setupTestBed('10');
    ticketAuditServiceSpy.getTicketAudit.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );
    component.loadAudit(10);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Não foi possível carregar a auditoria deste chamado.');
    expect(compiled.textContent).toContain('Falha no teclado');
  });

  it('should show the breached SLA situation', async () => {
    await setupTestBed('10');
    const breachedTicket: TicketResponse = {
      ...mockTicket,
      slaBreached: true,
    };
    ticketServiceSpy.getTicketById.mockReturnValue(of(breachedTicket));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('SLA atrasado');
    expect(compiled.textContent).not.toContain('Dentro do SLA');
  });

  it('should safely omit SLA information when there is no deadline', async () => {
    await setupTestBed('10');
    const ticketWithoutDeadline: TicketResponse = {
      ...mockTicket,
      slaDeadline: null,
      slaBreached: false,
    };
    ticketServiceSpy.getTicketById.mockReturnValue(of(ticketWithoutDeadline));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Prazo do SLA');
    expect(compiled.textContent).not.toContain('Situação do SLA');
    expect(compiled.textContent).not.toContain('undefined');
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

  it('should display friendly category and deadline labels for REQUESTER without showing the word "SLA"', async () => {
    await setupTestBed('10', false);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Problema no equipamento');
    expect(compiled.textContent).not.toContain('INCIDENTE_EQUIPAMENTO');
    expect(compiled.textContent).toContain('Equipamento relacionado');
    expect(compiled.textContent).not.toContain('Ativo Vinculado');
    expect(compiled.textContent).toContain('TECL-01');
    expect(compiled.textContent).toContain('(Periférico)');
    expect(compiled.textContent).toContain('Prazo estimado de atendimento');
    expect(compiled.textContent).toContain('Situação do prazo');
    expect(compiled.textContent).toContain('Dentro do prazo');
    expect(compiled.textContent).not.toContain('SLA');
  });

  it('should hide equipment block for REQUESTER when ticket.assetId is null', async () => {
    await setupTestBed('10', false);
    const ticketWithoutAsset: TicketResponse = {
      ...mockTicket,
      category: 'INCIDENTE_SISTEMA',
      assetId: null,
      assetCode: null,
      assetType: null,
    };
    ticketServiceSpy.getTicketById.mockReturnValue(of(ticketWithoutAsset));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Equipamento relacionado');
    expect(compiled.textContent).not.toContain('Ativo Vinculado');
    expect(compiled.textContent).not.toContain('Nenhum ativo vinculado');
  });

  it('should display friendly breached deadline message for REQUESTER without showing "SLA"', async () => {
    await setupTestBed('10', false);
    const breachedTicket: TicketResponse = {
      ...mockTicket,
      category: 'INCIDENTE_SISTEMA',
      slaBreached: true,
    };
    ticketServiceSpy.getTicketById.mockReturnValue(of(breachedTicket));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Problema no sistema');
    expect(compiled.textContent).not.toContain('INCIDENTE_SISTEMA');
    expect(compiled.textContent).toContain('Prazo de atendimento ultrapassado');
    expect(compiled.textContent).not.toContain('SLA');
  });

  it('should keep technical SLA and category presentation for ADMIN and TECHNICIAN', async () => {
    // ADMIN / TECHNICIAN (canViewAudit = true, isRequester = false)
    await setupTestBed('10', true);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('INCIDENTE_EQUIPAMENTO');
    expect(compiled.textContent).toContain('Ativo Vinculado');
    expect(compiled.textContent).toContain('Prazo do SLA');
    expect(compiled.textContent).toContain('Situação do SLA');
    expect(compiled.textContent).toContain('Dentro do SLA');
    expect(compiled.querySelector('.btn-print')).toBeNull();
  });

  it('should show "Imprimir comprovante" button for REQUESTER and call window.print without mutating ticket data', async () => {
    await setupTestBed('10', false);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const printBtn = compiled.querySelector('.btn-print') as HTMLButtonElement;
    expect(printBtn).toBeTruthy();
    expect(printBtn.textContent).toContain('Imprimir comprovante');
    expect(compiled.querySelector('.print-brand')?.textContent).toContain('HelpDeskPro');

    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => {});
    const originalTicketSnapshot = { ...component.ticket };

    printBtn.click();

    expect(printSpy).toHaveBeenCalledTimes(1);
    expect(component.ticket).toEqual(originalTicketSnapshot);
    printSpy.mockRestore();
  });

  it('should not call getAttachment or display attachment section when hasAttachment is false', async () => {
    await setupTestBed('10', false);
    const ticketWithoutAttachment: TicketResponse = {
      ...mockTicket,
      hasAttachment: false,
      attachmentName: null,
      attachmentContentType: null,
    };
    ticketServiceSpy.getTicketById.mockReturnValue(of(ticketWithoutAttachment));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ticketServiceSpy.getAttachment).not.toHaveBeenCalled();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.attachment-block')).toBeNull();
    expect(compiled.textContent).not.toContain('Imagem anexada');
  });

  it('should fetch Blob via getAttachment, create object URL, display image when hasAttachment is true, and revoke URL on ngOnDestroy', async () => {
    await setupTestBed('10', false);
    const createObjectUrlSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:http://localhost/fake-attachment-url');
    const revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});

    const ticketWithAttachment: TicketResponse = {
      ...mockTicket,
      hasAttachment: true,
      attachmentName: 'erro-tela.png',
      attachmentContentType: 'image/png',
    };
    ticketServiceSpy.getTicketById.mockReturnValue(of(ticketWithAttachment));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ticketServiceSpy.getAttachment).toHaveBeenCalledWith(10);
    expect(createObjectUrlSpy).toHaveBeenCalledTimes(1);
    expect(component.attachmentUrl).toBe('blob:http://localhost/fake-attachment-url');

    const compiled = fixture.nativeElement as HTMLElement;
    const attachmentSection = compiled.querySelector('.attachment-block') as HTMLElement;
    expect(attachmentSection).toBeTruthy();
    expect(attachmentSection.classList.contains('no-print')).toBe(true);
    expect(attachmentSection.textContent).toContain('Imagem anexada');

    const imgEl = attachmentSection.querySelector('img.attachment-image') as HTMLImageElement;
    expect(imgEl).toBeTruthy();
    expect(imgEl.getAttribute('src')).toBe('blob:http://localhost/fake-attachment-url');
    expect(imgEl.getAttribute('alt')).toBe('erro-tela.png');

    component.ngOnDestroy();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:http://localhost/fake-attachment-url');
    expect(component.attachmentUrl).toBeNull();

    createObjectUrlSpy.mockRestore();
    revokeObjectUrlSpy.mockRestore();
  });

  it('should render management controls for ADMIN/TECHNICIAN', async () => {
    await setupTestBed('10', true);
    fixture.detectChanges();
    await fixture.whenStable();

    const adminCompiled = fixture.nativeElement as HTMLElement;
    expect(adminCompiled.querySelector('.management-card')).toBeTruthy();
    expect(adminCompiled.textContent).toContain('Atendimento do Chamado');
  });

  it('should hide management controls for REQUESTER', async () => {
    await setupTestBed('10', false);
    fixture.detectChanges();
    await fixture.whenStable();

    const reqCompiled = fixture.nativeElement as HTMLElement;
    expect(reqCompiled.querySelector('.management-card')).toBeNull();
    expect(reqCompiled.textContent).not.toContain('Atendimento do Chamado');
  });

  it('should call assignTechnician, update ticket data (including automatic CRIADO -> ABERTO), and reload audit', async () => {
    await setupTestBed('10', true);
    const createdTicket: TicketResponse = {
      ...mockTicket,
      status: 'CRIADO',
      technicianId: null,
      technicianName: null,
      technicianEmail: null,
      solution: null,
    };
    const assignedTicket: TicketResponse = {
      ...createdTicket,
      status: 'ABERTO',
      technicianId: 2,
      technicianName: 'Ana Tecnica',
      technicianEmail: 'ana@helpdesk.com',
    };

    ticketServiceSpy.getTicketById.mockReturnValue(of(createdTicket));
    ticketServiceSpy.assignTechnician.mockReturnValue(of(assignedTicket));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    ticketAuditServiceSpy.getTicketAudit.mockClear();
    component.selectedTechnicianId = 2;
    component.onAssignTechnician();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ticketServiceSpy.assignTechnician).toHaveBeenCalledWith(10, 2);
    expect(component.ticket?.technicianId).toBe(2);
    expect(component.ticket?.status).toBe('ABERTO');
    expect(component.operationSuccessMessage).toBe('Técnico atribuído com sucesso.');
    expect(ticketAuditServiceSpy.getTicketAudit).toHaveBeenCalledWith(10);
  });

  it('should call updateTicketStatus with solution, update ticket data, and reload audit', async () => {
    await setupTestBed('10', true);
    const inProgressTicket: TicketResponse = {
      ...mockTicket,
      status: 'EM_ATENDIMENTO',
      solution: null,
    };
    const resolvedTicket: TicketResponse = {
      ...inProgressTicket,
      status: 'RESOLVIDO',
      solution: 'Teclado substituído por novo equipamento',
    };

    ticketServiceSpy.getTicketById.mockReturnValue(of(inProgressTicket));
    ticketServiceSpy.updateTicketStatus.mockReturnValue(of(resolvedTicket));

    component.loadTicket(10);
    fixture.detectChanges();
    await fixture.whenStable();

    ticketAuditServiceSpy.getTicketAudit.mockClear();
    component.selectedStatus = 'RESOLVIDO';
    component.solutionText = '  Teclado substituído por novo equipamento  ';
    expect(component.shouldShowSolutionField).toBe(true);

    component.onUpdateStatus();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ticketServiceSpy.updateTicketStatus).toHaveBeenCalledWith(
      10,
      'RESOLVIDO',
      'Teclado substituído por novo equipamento',
    );
    expect(component.ticket?.status).toBe('RESOLVIDO');
    expect(component.ticket?.solution).toBe('Teclado substituído por novo equipamento');
    expect(component.operationSuccessMessage).toBe('Chamado atualizado com sucesso.');
    expect(ticketAuditServiceSpy.getTicketAudit).toHaveBeenCalledWith(10);
  });

  it('should display API error message without breaking the screen when status update or technician assignment fails', async () => {
    await setupTestBed('10', true);
    const error400 = new HttpErrorResponse({
      status: 400,
      statusText: 'Bad Request',
      error: {
        message: 'Para transitar o status para RESOLVIDO, o campo solução é obrigatório.',
      },
    });
    ticketServiceSpy.updateTicketStatus.mockReturnValue(throwError(() => error400));

    component.selectedStatus = 'RESOLVIDO';
    component.solutionText = '';
    component.onUpdateStatus();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.isUpdatingStatus).toBe(false);
    expect(component.operationErrorMessage).toBe(
      'Para transitar o status para RESOLVIDO, o campo solução é obrigatório.',
    );

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain(
      'Para transitar o status para RESOLVIDO, o campo solução é obrigatório.',
    );
    expect(compiled.textContent).toContain('Falha no teclado');
  });
});
