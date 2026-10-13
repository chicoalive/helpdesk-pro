import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { NEVER, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { DashboardSummary } from './dashboard-summary';
import { DashboardService } from '../../../core/services/dashboard.service';
import { DashboardResponse } from '../../../core/models/dashboard.model';

describe('DashboardSummary', () => {
  let fixture: ComponentFixture<DashboardSummary>;
  let component: DashboardSummary;
  let serviceSpy: { getSummary: ReturnType<typeof vi.fn> };

  const summary: DashboardResponse = {
    ticketsByStatus: {
      CRIADO: 1,
      ABERTO: 0,
      EM_ATENDIMENTO: 5,
      AGUARDANDO_USUARIO: 0,
      RESOLVIDO: 2,
      FECHADO: 7,
      CANCELADO: 0,
    },
    ticketsByPriority: { BAIXA: 3, MEDIA: 0, ALTA: 4 },
    ticketsByTechnician: [
      { technicianId: 2, technicianName: 'Ana Técnica', total: 6 },
      { technicianId: 3, technicianName: 'Bruno', total: 1 },
    ],
    unassignedTickets: 9,
    assetsUnderMaintenance: 4,
  };

  const zeroed: DashboardResponse = {
    ticketsByStatus: {
      CRIADO: 0,
      ABERTO: 0,
      EM_ATENDIMENTO: 0,
      AGUARDANDO_USUARIO: 0,
      RESOLVIDO: 0,
      FECHADO: 0,
      CANCELADO: 0,
    },
    ticketsByPriority: { BAIXA: 0, MEDIA: 0, ALTA: 0 },
    ticketsByTechnician: [],
    unassignedTickets: 0,
    assetsUnderMaintenance: 0,
  };

  async function setup(response$: unknown) {
    serviceSpy = { getSummary: vi.fn().mockReturnValue(response$) };

    await TestBed.configureTestingModule({
      imports: [DashboardSummary],
      providers: [{ provide: DashboardService, useValue: serviceSpy }],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardSummary);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('should render all counts from the API, including zero values', async () => {
    await setup(of(summary));

    expect(serviceSpy.getSummary).toHaveBeenCalledTimes(1);
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('.card-status .metric-list li').length).toBe(7);
    expect(el.querySelectorAll('.card-priority .metric-list li').length).toBe(3);

    const statusText = el.querySelector('.card-status')!.textContent!;
    expect(statusText).toContain('EM_ATENDIMENTO');
    expect(statusText).toContain('CANCELADO');
    expect(statusText).toContain('FECHADO');

    const priorityRows = Array.from(el.querySelectorAll('.card-priority .metric-list li')).map((li) =>
      li.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(priorityRows).toEqual(['BAIXA 3', 'MEDIA 0', 'ALTA 4']);

    const techRows = Array.from(el.querySelectorAll('.card-technician .metric-list li')).map((li) =>
      li.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(techRows).toEqual(['Ana Técnica 6', 'Bruno 1']);

    expect(el.querySelector('.card-unassigned .big-number')!.textContent!.trim()).toBe('9');
    expect(el.querySelector('.card-maintenance .big-number')!.textContent!.trim()).toBe('4');
    expect(component.errorMessage).toBeNull();
  });

  it('should show loading state while waiting for the API', async () => {
    await setup(NEVER);

    expect(component.isLoading).toBe(true);
    expect(text()).toContain('Carregando indicadores...');
    expect(text()).not.toContain('Chamados por status');
  });

  it('should render zeroed data and empty technician state without treating it as error', async () => {
    await setup(of(zeroed));

    const el = fixture.nativeElement as HTMLElement;
    expect(component.errorMessage).toBeNull();
    expect(el.querySelector('.alert-error')).toBeNull();
    expect(el.querySelectorAll('.card-status .metric-list li').length).toBe(7);
    expect(el.querySelectorAll('.card-priority .metric-list li').length).toBe(3);
    expect(text()).toContain('Nenhum chamado atribuído a técnicos.');
    expect(el.querySelector('.card-unassigned .big-number')!.textContent!.trim()).toBe('0');
    expect(el.querySelector('.card-maintenance .big-number')!.textContent!.trim()).toBe('0');
  });

  it('should show friendly error message for generic failures and hide data', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));

    expect(text()).toContain('Não foi possível carregar os indicadores. Tente novamente mais tarde.');
    expect((fixture.nativeElement as HTMLElement).querySelector('.cards-grid')).toBeNull();
  });

  it('should show permission message and no data on 403', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 403 })));

    expect(text()).toContain('Você não possui permissão para visualizar os indicadores.');
    expect((fixture.nativeElement as HTMLElement).querySelector('.cards-grid')).toBeNull();
  });

  it('should show session message and no data on 401 without altering session', async () => {
    localStorage.setItem('helpdesk_token', 'token-existente');
    await setup(throwError(() => new HttpErrorResponse({ status: 401 })));

    expect(text()).toContain('Sua sessão expirou ou é inválida.');
    expect((fixture.nativeElement as HTMLElement).querySelector('.cards-grid')).toBeNull();
    expect(localStorage.getItem('helpdesk_token')).toBe('token-existente');
    localStorage.clear();
  });
});
