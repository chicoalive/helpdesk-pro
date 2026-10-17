import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { TicketForm } from './ticket-form';
import { TicketService } from '../../../core/services/ticket.service';
import { AssetService } from '../../../core/services/asset.service';
import { AuthService } from '../../../core/services/auth.service';
import { AssetResponse, TicketResponse } from '../../../core/models/ticket.model';

describe('TicketForm', () => {
  let component: TicketForm;
  let fixture: ComponentFixture<TicketForm>;
  let router: Router;
  let ticketServiceSpy: {
    createTicket: ReturnType<typeof vi.fn>;
    uploadAttachment: ReturnType<typeof vi.fn>;
  };
  let assetServiceSpy: {
    getAvailableAssets: ReturnType<typeof vi.fn>;
  };
  let authServiceSpy: {
    isRequester: ReturnType<typeof vi.fn>;
  };

  const mockAvailableAssets: AssetResponse[] = [
    {
      id: 1,
      code: 'NOTE-100',
      type: 'Notebook',
      description: 'Dell Latitude 3420',
      status: 'IN_USE',
    },
    {
      id: 2,
      code: 'MON-200',
      type: 'Monitor',
      description: null,
      status: 'IN_USE',
    },
  ];

  const mockCreatedTicket: TicketResponse = {
    id: 42,
    title: 'Sistema ERP fora do ar',
    description: 'Erro ao emitir nota fiscal',
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
  };

  const setupComponent = async (
    isRequesterUser = true,
    assets: AssetResponse[] = mockAvailableAssets,
  ) => {
    ticketServiceSpy = {
      createTicket: vi.fn().mockReturnValue(of(mockCreatedTicket)),
      uploadAttachment: vi.fn().mockReturnValue(
        of({
          ...mockCreatedTicket,
          hasAttachment: true,
          attachmentName: 'erro.png',
          attachmentContentType: 'image/png',
        }),
      ),
    };
    assetServiceSpy = {
      getAvailableAssets: vi.fn().mockReturnValue(of(assets)),
    };
    authServiceSpy = {
      isRequester: vi.fn().mockReturnValue(isRequesterUser),
    };

    await TestBed.configureTestingModule({
      imports: [TicketForm, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        { provide: TicketService, useValue: ticketServiceSpy },
        { provide: AssetService, useValue: assetServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));

    fixture = TestBed.createComponent(TicketForm);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  };

  it('should create and load available assets on init for REQUESTER', async () => {
    await setupComponent(true);
    expect(component).toBeTruthy();
    expect(assetServiceSpy.getAvailableAssets).toHaveBeenCalledTimes(1);
    expect(component.availableAssets.length).toBe(2);
  });

  it('should block non-requester users with error message and disabled form', async () => {
    await setupComponent(false);
    expect(component.errorMessage).toContain('Apenas solicitantes podem criar novos chamados.');
    expect(component.ticketForm.disabled).toBe(true);
    expect(assetServiceSpy.getAvailableAssets).not.toHaveBeenCalled();
  });

  it('should invalidate form when required fields are empty', async () => {
    await setupComponent(true);
    expect(component.ticketForm.invalid).toBe(true);
    expect(component.ticketForm.controls.title.valid).toBe(false);
    expect(component.ticketForm.controls.category.valid).toBe(false);
    expect(component.ticketForm.controls.description.valid).toBe(false);

    component.onSubmit();
    expect(ticketServiceSpy.createTicket).not.toHaveBeenCalled();
  });

  it('should display friendly placeholders, category labels, and contextual estimated time without technical jargon', async () => {
    await setupComponent(true);
    const compiled = fixture.nativeElement as HTMLElement;

    const titleInput = compiled.querySelector('#title') as HTMLInputElement;
    const descTextarea = compiled.querySelector('#description') as HTMLTextAreaElement;
    const categorySelect = compiled.querySelector('#category') as HTMLSelectElement;

    expect(titleInput.placeholder).toBe('Ex: Não consigo acessar o sistema');
    expect(descTextarea.placeholder).toBe(
      'Explique o que aconteceu, quando começou e o que aparece na tela.',
    );

    expect(categorySelect.textContent).toContain('Problema no sistema');
    expect(categorySelect.textContent).toContain('Problema no equipamento');
    expect(categorySelect.textContent).toContain('Solicitação de acesso');
    expect(categorySelect.textContent).toContain('Dúvida');
    expect(categorySelect.textContent).toContain('Outro');

    expect(compiled.textContent).not.toContain('SLA');
    expect(compiled.textContent).not.toContain('INCIDENTE_SISTEMA');
    expect(compiled.textContent).not.toContain('INCIDENTE_EQUIPAMENTO');
    expect(compiled.textContent).not.toContain('SOLICITACAO_ACESSO');
    expect(compiled.querySelector('.estimate-info')).toBeNull();

    component.ticketForm.controls.category.setValue('INCIDENTE_SISTEMA');
    fixture.detectChanges();
    expect(component.selectedCategoryEstimate).toBe('Prazo estimado de atendimento: até 4 horas');
    expect(compiled.querySelector('.estimate-info')?.textContent).toContain(
      'Prazo estimado de atendimento: até 4 horas',
    );

    component.ticketForm.controls.category.setValue('INCIDENTE_EQUIPAMENTO');
    fixture.detectChanges();
    expect(component.selectedCategoryEstimate).toBe('Prazo estimado de atendimento: até 24 horas');
    expect(compiled.textContent).toContain('Qual equipamento está com problema? *');

    component.ticketForm.controls.category.setValue('DUVIDA');
    fixture.detectChanges();
    expect(component.selectedCategoryEstimate).toBe('Prazo estimado de atendimento: até 72 horas');
  });

  it('should NOT require or show asset select for non-equipment categories', async () => {
    await setupComponent(true);
    component.ticketForm.controls.title.setValue('Problema no sistema');
    component.ticketForm.controls.category.setValue('INCIDENTE_SISTEMA');
    component.ticketForm.controls.description.setValue('Não abre a tela');
    fixture.detectChanges();

    expect(component.isEquipmentIncident).toBe(false);
    expect(component.ticketForm.controls.assetId.valid).toBe(true);
    expect(component.ticketForm.valid).toBe(true);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#assetId')).toBeNull();
  });

  it('should show and require asset select when category is INCIDENTE_EQUIPAMENTO, and clear requirement when switching back', async () => {
    await setupComponent(true);
    component.ticketForm.controls.title.setValue('Notebook não liga');
    component.ticketForm.controls.description.setValue('Sem sinal de energia');
    component.ticketForm.controls.category.setValue('INCIDENTE_EQUIPAMENTO');
    fixture.detectChanges();

    expect(component.isEquipmentIncident).toBe(true);
    expect(component.ticketForm.controls.assetId.valid).toBe(false);
    expect(component.ticketForm.invalid).toBe(true);

    const compiled = fixture.nativeElement as HTMLElement;
    const assetSelect = compiled.querySelector('#assetId') as HTMLSelectElement;
    expect(assetSelect).toBeTruthy();
    expect(assetSelect.textContent).toContain('NOTE-100 - Notebook (Dell Latitude 3420)');
    expect(assetSelect.textContent).toContain('MON-200 - Monitor');

    component.ticketForm.controls.assetId.setValue(1);
    fixture.detectChanges();
    expect(component.ticketForm.valid).toBe(true);

    // Switch to DUVIDA -> should clear assetId and validator
    component.ticketForm.controls.category.setValue('DUVIDA');
    fixture.detectChanges();
    expect(component.isEquipmentIncident).toBe(false);
    expect(component.ticketForm.controls.assetId.value).toBeNull();
    expect(component.ticketForm.valid).toBe(true);
  });

  it('should display a clear message when INCIDENTE_EQUIPAMENTO is selected and no available assets exist', async () => {
    await setupComponent(true, []);
    component.ticketForm.controls.category.setValue('INCIDENTE_EQUIPAMENTO');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('#assetId')).toBeNull();
    expect(compiled.querySelector('.empty-assets-msg')?.textContent).toContain(
      'Não há ativos disponíveis (em uso) para vincular a este chamado',
    );
  });

  it('should send correct payload without priority on createTicket and redirect to /tickets/{id}', async () => {
    await setupComponent(true);
    component.ticketForm.setValue({
      title: '  Sistema ERP fora do ar  ',
      category: 'INCIDENTE_SISTEMA',
      assetId: null,
      description: '  Erro ao emitir nota fiscal  ',
    });

    component.onSubmit();

    expect(ticketServiceSpy.createTicket).toHaveBeenCalledTimes(1);
    const sentPayload = ticketServiceSpy.createTicket.mock.calls[0][0];
    expect(sentPayload).toEqual({
      title: 'Sistema ERP fora do ar',
      description: 'Erro ao emitir nota fiscal',
      category: 'INCIDENTE_SISTEMA',
      assetId: null,
    });
    expect('priority' in sentPayload).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/tickets', 42]);
  });

  it('should send selected assetId when category is INCIDENTE_EQUIPAMENTO', async () => {
    await setupComponent(true);
    component.ticketForm.controls.title.setValue('Teclado falhando');
    component.ticketForm.controls.category.setValue('INCIDENTE_EQUIPAMENTO');
    component.ticketForm.controls.assetId.setValue(1);
    component.ticketForm.controls.description.setValue('Teclas travando');

    component.onSubmit();

    expect(ticketServiceSpy.createTicket).toHaveBeenCalledWith({
      title: 'Teclado falhando',
      description: 'Teclas travando',
      category: 'INCIDENTE_EQUIPAMENTO',
      assetId: 1,
    });
    expect(router.navigate).toHaveBeenCalledWith(['/tickets', 42]);
  });

  it('should display friendly error message when createTicket fails without breaking the screen', async () => {
    await setupComponent(true);
    const error500 = new HttpErrorResponse({ status: 500, statusText: 'Server Error' });
    ticketServiceSpy.createTicket.mockReturnValue(throwError(() => error500));

    component.ticketForm.setValue({
      title: 'Falha no acesso',
      category: 'SOLICITACAO_ACESSO',
      assetId: null,
      description: 'Preciso de permissão na pasta de rede',
    });

    component.onSubmit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.isSaving).toBe(false);
    expect(component.errorMessage).toContain('Ocorreu um erro ao abrir o chamado.');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.alert-error')?.textContent).toContain(
      'Ocorreu um erro ao abrir o chamado.',
    );
  });

  it('should call createTicket and then uploadAttachment when a valid PNG or JPEG is selected', async () => {
    await setupComponent(true);
    const validPng = new File([new Uint8Array([137, 80, 78, 71])], 'print.png', {
      type: 'image/png',
    });

    const isValid = component.validateSelectedFile(validPng);
    expect(isValid).toBe(true);
    expect(component.attachmentError).toBeNull();
    expect(component.selectedFile).toBe(validPng);

    component.ticketForm.setValue({
      title: 'Erro na tela inicial',
      category: 'INCIDENTE_SISTEMA',
      assetId: null,
      description: 'Segue print anexado',
    });

    component.onSubmit();

    expect(ticketServiceSpy.createTicket).toHaveBeenCalledTimes(1);
    expect(ticketServiceSpy.uploadAttachment).toHaveBeenCalledTimes(1);
    expect(ticketServiceSpy.uploadAttachment).toHaveBeenCalledWith(42, validPng);
    expect(router.navigate).toHaveBeenCalledWith(['/tickets', 42]);
  });

  it('should block invalid file type or file larger than 2MB in frontend before submitting', async () => {
    await setupComponent(true);
    component.ticketForm.setValue({
      title: 'Erro na tela',
      category: 'DUVIDA',
      assetId: null,
      description: 'Teste de validação de anexo',
    });

    const invalidTypeFile = new File([new Uint8Array([1, 2, 3])], 'doc.pdf', {
      type: 'application/pdf',
    });
    expect(component.validateSelectedFile(invalidTypeFile)).toBe(false);
    expect(component.attachmentError).toContain('Formato de imagem inválido');
    component.onSubmit();
    expect(ticketServiceSpy.createTicket).not.toHaveBeenCalled();

    const oversizedFile = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'grande.jpg', {
      type: 'image/jpeg',
    });
    expect(component.validateSelectedFile(oversizedFile)).toBe(false);
    expect(component.attachmentError).toContain('no máximo 2 MB');
    component.onSubmit();
    expect(ticketServiceSpy.createTicket).not.toHaveBeenCalled();
  });

  it('should not delete the created ticket and should display a clear message if attachment upload fails', async () => {
    await setupComponent(true);
    const uploadError = new HttpErrorResponse({ status: 500, statusText: 'Upload Failed' });
    ticketServiceSpy.uploadAttachment.mockReturnValue(throwError(() => uploadError));

    const validJpg = new File([new Uint8Array([255, 216, 255])], 'foto.jpg', {
      type: 'image/jpeg',
    });
    component.validateSelectedFile(validJpg);

    component.ticketForm.setValue({
      title: 'Falha ao imprimir',
      category: 'OUTRO',
      assetId: null,
      description: 'Impressora apresenta código de erro',
    });

    component.onSubmit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(ticketServiceSpy.createTicket).toHaveBeenCalledTimes(1);
    expect(ticketServiceSpy.uploadAttachment).toHaveBeenCalledWith(42, validJpg);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.isSaving).toBe(false);
    expect(component.errorMessage).toBe(
      'O chamado foi criado com sucesso, mas não foi possível anexar a imagem.',
    );
  });

  it('should display backend duplicate ticket message and "Ver chamado existente" link to /tickets/{id} when existingTicketId is returned', async () => {
    await setupComponent(true);
    const duplicateError = new HttpErrorResponse({
      status: 400,
      statusText: 'Bad Request',
      error: {
        status: 400,
        message:
          'Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #15.',
        existingTicketId: 15,
      },
    });
    ticketServiceSpy.createTicket.mockReturnValue(throwError(() => duplicateError));

    component.ticketForm.setValue({
      title: 'Sistema travou novamente',
      category: 'INCIDENTE_SISTEMA',
      assetId: null,
      description: 'Não consigo entrar no sistema',
    });

    component.onSubmit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.isSaving).toBe(false);
    expect(component.existingTicketId).toBe(15);
    expect(component.errorMessage).toBe(
      'Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #15.',
    );

    const compiled = fixture.nativeElement as HTMLElement;
    const alertEl = compiled.querySelector('.alert-error') as HTMLElement;
    expect(alertEl.textContent).toContain(
      'Você já possui um chamado em andamento para este tipo de problema. Acompanhe o chamado #15.',
    );

    const existingLink = alertEl.querySelector('.btn-existing-ticket') as HTMLAnchorElement;
    expect(existingLink).toBeTruthy();
    expect(existingLink.textContent).toContain('Ver chamado existente');
    expect(existingLink.getAttribute('href')).toBe('/tickets/15');
  });
});
