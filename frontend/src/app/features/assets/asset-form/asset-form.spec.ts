import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { AssetForm } from './asset-form';
import { AssetService } from '../../../core/services/asset.service';
import { AuthService } from '../../../core/services/auth.service';
import { AssetResponse } from '../../../core/models/ticket.model';

describe('AssetForm', () => {
  let component: AssetForm;
  let fixture: ComponentFixture<AssetForm>;
  let router: Router;
  let assetServiceSpy: {
    getAssetById: ReturnType<typeof vi.fn>;
    createAsset: ReturnType<typeof vi.fn>;
    updateAsset: ReturnType<typeof vi.fn>;
    getAudit: ReturnType<typeof vi.fn>;
  };
  let authServiceSpy: { isAdmin: ReturnType<typeof vi.fn> };

  const mockAsset: AssetResponse = {
    id: 1, version:0,
    code: 'NOTE-100',
    type: 'Notebook',
    description: 'Dell Latitude 3420',
    status: 'IN_USE',
  };

  const setupComponent = async (idParam: string | null = null, isAdminUser = true) => {
    assetServiceSpy = {
      getAudit: vi.fn().mockReturnValue(of([])),
      getAssetById: vi.fn().mockReturnValue(of(mockAsset)),
      createAsset: vi.fn().mockReturnValue(of(mockAsset)),
      updateAsset: vi.fn().mockReturnValue(of(mockAsset)),
    };
    authServiceSpy = {
      isAdmin: vi.fn().mockReturnValue(isAdminUser),
    };

    await TestBed.configureTestingModule({
      imports: [AssetForm, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        { provide: AssetService, useValue: assetServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? idParam : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockImplementation(() => Promise.resolve(true));

    fixture = TestBed.createComponent(AssetForm);
    component = fixture.componentInstance;
    await fixture.whenStable();
  };

  it('should create in create mode when id is null', async () => {
    await setupComponent(null);
    expect(component).toBeTruthy();
    expect(component.isEditMode).toBe(false);
  });

  it('should invalidate form when required fields are empty', async () => {
    await setupComponent(null);
    component.assetForm.controls.code.setValue('');
    component.assetForm.controls.type.setValue('');

    expect(component.assetForm.invalid).toBe(true);
    expect(component.assetForm.controls.code.valid).toBe(false);
    expect(component.assetForm.controls.type.valid).toBe(false);

    component.assetForm.controls.code.setValue('SERV-01');
    component.assetForm.controls.type.setValue('Servidor');
    expect(component.assetForm.valid).toBe(true);
  });

  it('should create asset successfully and navigate to /assets', async () => {
    await setupComponent(null);
    component.assetForm.setValue({
      code: 'MON-05',
      type: 'Monitor',
      description: 'Monitor LG 29 Ultrawide',
      status: 'IN_USE',
    });

    component.onSubmit();

    expect(assetServiceSpy.createAsset).toHaveBeenCalledWith({
      code: 'MON-05',
      type: 'Monitor',
      description: 'Monitor LG 29 Ultrawide',
      status: 'IN_USE',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/assets']);
  });

  it('should load asset in edit mode and populate form', async () => {
    await setupComponent('1');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.isEditMode).toBe(true);
    expect(assetServiceSpy.getAssetById).toHaveBeenCalledWith(1);
    expect(component.assetForm.controls.code.value).toBe('NOTE-100');
    expect(component.assetForm.controls.type.value).toBe('Notebook');
    expect(component.assetForm.controls.description.value).toBe('Dell Latitude 3420');
    expect(component.assetForm.controls.status.value).toBe('IN_USE');
  });

  it('should update asset in edit mode successfully and navigate to /assets', async () => {
    await setupComponent('1');
    component.assetForm.setValue({
      code: 'NOTE-100-MOD',
      type: 'Notebook',
      description: 'Memoria RAM expandida',
      status: 'UNDER_MAINTENANCE',
    });
    component.statusConfirmed = true;
    component.onSubmit();

    expect(assetServiceSpy.updateAsset).toHaveBeenCalledWith(1, {
      expectedVersion: 0,
      code: 'NOTE-100-MOD',
      type: 'Notebook',
      description: 'Memoria RAM expandida',
      status: 'UNDER_MAINTENANCE',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/assets']);
  });

  it('should handle 409 Conflict with duplicate code message', async () => {
    await setupComponent(null);
    const error409 = new HttpErrorResponse({ status: 409, statusText: 'Conflict' });
    assetServiceSpy.createAsset.mockReturnValue(throwError(() => error409));

    component.assetForm.setValue({
      code: 'NOTE-100',
      type: 'Notebook',
      description: '',
      status: 'IN_USE',
    });

    component.onSubmit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Já existe um ativo cadastrado com este código.');
  });

  it('should handle 403 Forbidden with permission error message', async () => {
    await setupComponent(null);
    const error403 = new HttpErrorResponse({ status: 403, statusText: 'Forbidden' });
    assetServiceSpy.createAsset.mockReturnValue(throwError(() => error403));

    component.assetForm.setValue({
      code: 'NOTE-100',
      type: 'Notebook',
      description: '',
      status: 'IN_USE',
    });

    component.onSubmit();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toContain('Apenas administradores podem gerenciar ativos');
  });

  it('should handle 404 Not Found when asset is missing in edit mode', async () => {
    const error404 = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
    await setupComponent('999');
    assetServiceSpy.getAssetById.mockReturnValue(throwError(() => error404));

    component.loadAsset(999);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Ativo não encontrado.');
  });

  it('should block non-admin users with error message and disabled form', async () => {
    await setupComponent(null, false);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toContain('Apenas administradores podem cadastrar ou editar ativos.');
    expect(component.assetForm.disabled).toBe(true);
  });
  it('requires confirmation before an equipment status change',async()=>{
    await setupComponent('1');component.assetForm.controls.status.setValue('DISCARDED');component.onSubmit();
    expect(assetServiceSpy.updateAsset).not.toHaveBeenCalled();expect(component.errorMessage).toContain('Confirme');
    component.statusConfirmed=true;component.onSubmit();expect(assetServiceSpy.updateAsset).toHaveBeenCalledWith(1,expect.objectContaining({status:'DISCARDED',expectedVersion:0}));
  });
  it('preserves draft on conflict and reloads only after explicit action',async()=>{
    await setupComponent('1');component.assetForm.controls.description.setValue('Rascunho mantido');
    assetServiceSpy.updateAsset.mockReturnValue(throwError(()=>new HttpErrorResponse({status:409,error:{message:'Este ativo foi alterado por outra operação. Recarregue os dados e tente novamente.'}})));
    component.onSubmit();expect(component.hasConflict).toBe(true);expect(component.assetForm.controls.description.value).toBe('Rascunho mantido');
    component.onSubmit();expect(assetServiceSpy.updateAsset).toHaveBeenCalledTimes(1);
    assetServiceSpy.getAssetById.mockReturnValue(of({...mockAsset,version:1,description:'Salvo por outro administrador'}));component.loadAsset(1);
    expect(component.hasConflict).toBe(false);expect(component.assetVersion).toBe(1);expect(component.assetForm.controls.description.value).toBe('Salvo por outro administrador');
  });
  it('normalizes code and rejects whitespace and oversized fields',async()=>{
    await setupComponent();component.assetForm.setValue({code:'  nb-01  ',type:'Notebook',description:'',status:'IN_USE'});component.onSubmit();expect(assetServiceSpy.createAsset).toHaveBeenCalledWith(expect.objectContaining({code:'NB-01'}));
    component.assetForm.controls.code.setValue('   ');expect(component.assetForm.invalid).toBe(true);component.assetForm.controls.code.setValue('x'.repeat(256));expect(component.assetForm.invalid).toBe(true);
  });
  it('does not submit twice while processing',async()=>{
    await setupComponent();assetServiceSpy.createAsset.mockReturnValue(new (await import('rxjs')).Observable(()=>{}));component.assetForm.setValue({code:'NB-02',type:'Notebook',description:'',status:'IN_USE'});component.onSubmit();component.onSubmit();expect(assetServiceSpy.createAsset).toHaveBeenCalledTimes(1);
  });
  it('shows patrimonial audit with readable status and no credentials',async()=>{
    await setupComponent('1');assetServiceSpy.getAudit.mockReturnValue(of([{id:1,assetId:1,actorId:5,actorName:'Administrador',operation:'ASSET_UPDATED',occurredAt:'2026-01-01T12:00:00Z',changes:{status:{oldValue:'IN_USE',newValue:'DISCARDED'}}}]));component.loadAudit(1);fixture.detectChanges();await fixture.whenStable();expect(fixture.nativeElement.textContent).toContain('Auditoria patrimonial');expect(fixture.nativeElement.textContent).toContain('Descartado');expect(fixture.nativeElement.textContent).toContain('09:00:00');
  });
  it('refuses edit submission when server version is unavailable',async()=>{
    await setupComponent('1');component.assetVersion=null;component.onSubmit();expect(assetServiceSpy.updateAsset).not.toHaveBeenCalled();expect(component.errorMessage).toContain('versão atual');
  });

});
