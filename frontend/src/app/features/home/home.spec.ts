import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Home } from './home';
import { TicketService } from '../../core/services/ticket.service';
import { AuthService } from '../../core/services/auth.service';
import { TicketResponse } from '../../core/models/ticket.model';

describe('Home', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;
  let ticketServiceSpy: { getTickets: ReturnType<typeof vi.fn> };
  let authServiceSpy: { canViewAssets: ReturnType<typeof vi.fn> };

  const mockTickets: TicketResponse[] = [
    {
      id: 10,
      title: 'Problema na impressora',
      description: 'Impressora não puxa folha',
      solution: null,
      status: 'ABERTO',
      category: 'INCIDENTE_EQUIPAMENTO',
      priority: 'MEDIA',
      createdAt: '2026-09-25T10:00:00Z',
      updatedAt: null,
      requesterId: 1,
      requesterName: 'Maria Solicitante',
      requesterEmail: 'maria@helpdesk.com',
      technicianId: null,
      technicianName: null,
      technicianEmail: null,
      assetId: null,
      assetCode: null,
      assetType: null,
    },
    {
      id: 11,
      title: 'Erro de login no sistema',
      description: 'Usuário não consegue acessar portal',
      solution: null,
      status: 'EM_ATENDIMENTO',
      category: 'INCIDENTE_SISTEMA',
      priority: 'ALTA',
      createdAt: '2026-09-25T11:00:00Z',
      updatedAt: null,
      requesterId: 2,
      requesterName: 'Carlos Solicitante',
      requesterEmail: 'carlos@helpdesk.com',
      technicianId: 3,
      technicianName: 'Ana Técnica',
      technicianEmail: 'ana@helpdesk.com',
      assetId: null,
      assetCode: null,
      assetType: null,
    },
    {
      id: 12,
      title: 'Solicitação de acesso VPN',
      description: 'Acesso para trabalho remoto',
      solution: 'Acesso liberado',
      status: 'RESOLVIDO',
      category: 'SOLICITACAO_ACESSO',
      priority: 'BAIXA',
      createdAt: '2026-09-25T12:00:00Z',
      updatedAt: '2026-09-25T13:00:00Z',
      requesterId: 1,
      requesterName: 'Maria Solicitante',
      requesterEmail: 'maria@helpdesk.com',
      technicianId: 3,
      technicianName: 'Ana Técnica',
      technicianEmail: 'ana@helpdesk.com',
      assetId: null,
      assetCode: null,
      assetType: null,
    },
  ];

  beforeEach(async () => {
    ticketServiceSpy = {
      getTickets: vi.fn().mockReturnValue(of(mockTickets)),
    };
    authServiceSpy = {
      canViewAssets: vi.fn().mockReturnValue(true),
    };

    await TestBed.configureTestingModule({
      imports: [Home, FormsModule],
      providers: [
        provideRouter([]),
        { provide: TicketService, useValue: ticketServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load tickets on init and render table with columns and actions', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ticketServiceSpy.getTickets).toHaveBeenCalled();
    expect(component.tickets.length).toBe(3);
    expect(component.filteredTickets.length).toBe(3);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('#10');
    expect(compiled.textContent).toContain('Problema na impressora');
    expect(compiled.textContent).toContain('INCIDENTE_EQUIPAMENTO');
    expect(compiled.textContent).toContain('MEDIA');
    expect(compiled.textContent).toContain('ABERTO');

    const actionButtons = compiled.querySelectorAll('.btn-details');
    expect(actionButtons.length).toBe(3);
    expect(actionButtons[0].textContent).toContain('Detalhes');
  });

  it('should filter tickets by searchTerm matching title (case-insensitive)', () => {
    component.searchTerm = 'vpn';
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(12);

    component.searchTerm = 'IMPRESSORA';
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(10);
  });

  it('should filter tickets by searchTerm matching id', () => {
    component.searchTerm = '11';
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].title).toBe('Erro de login no sistema');
  });

  it('should filter tickets by statusFilter', () => {
    component.statusFilter = 'RESOLVIDO';
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(12);

    component.statusFilter = 'ABERTO';
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(10);
  });

  it('should filter tickets by priorityFilter', () => {
    component.priorityFilter = 'ALTA';
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(11);

    component.priorityFilter = 'BAIXA';
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(12);
  });

  it('should filter tickets by combined filters', () => {
    component.searchTerm = 'sistema';
    component.statusFilter = 'EM_ATENDIMENTO';
    component.priorityFilter = 'ALTA';
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(11);

    component.priorityFilter = 'BAIXA';
    expect(component.filteredTickets.length).toBe(0);
  });

  it('should filter table when status select is changed in DOM', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const selects = fixture.nativeElement.querySelectorAll('select');
    const statusSelect = selects[0] as HTMLSelectElement;
    statusSelect.value = 'RESOLVIDO';
    statusSelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.statusFilter).toBe('RESOLVIDO');
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(12);
  });

  it('should filter table when priority select is changed in DOM', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const selects = fixture.nativeElement.querySelectorAll('select');
    const prioritySelect = selects[1] as HTMLSelectElement;
    prioritySelect.value = 'ALTA';
    prioritySelect.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.priorityFilter).toBe('ALTA');
    expect(component.filteredTickets.length).toBe(1);
    expect(component.filteredTickets[0].id).toBe(11);
  });

  it('should show "Nenhum chamado encontrado" when filter yields no results', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = 'nao-existe';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.filteredTickets.length).toBe(0);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Nenhum chamado encontrado');
  });

  it('should display error message when loading fails', async () => {
    ticketServiceSpy.getTickets.mockReturnValue(throwError(() => new Error('Falha de rede')));

    component.loadTickets();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Não foi possível carregar os chamados. Tente novamente mais tarde.');
    expect(component.tickets.length).toBe(0);
    expect(component.filteredTickets.length).toBe(0);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Não foi possível carregar os chamados.');
  });

  it('should display "Ativos de TI" link when user can view assets (ADMIN / TECHNICIAN)', async () => {
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.btn-assets')).toBeTruthy();
    expect(compiled.querySelector('.btn-assets')?.textContent).toContain('Ativos de TI');
  });

  it('should hide "Ativos de TI" link when user is REQUESTER (cannot view assets)', async () => {
    authServiceSpy.canViewAssets.mockReturnValue(false);
    const reqFixture = TestBed.createComponent(Home);
    reqFixture.detectChanges();
    await reqFixture.whenStable();

    const compiled = reqFixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.btn-assets')).toBeNull();
  });
});
