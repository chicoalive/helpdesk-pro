import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { AssetList } from './asset-list';
import { AssetService } from '../../../core/services/asset.service';
import { AuthService } from '../../../core/services/auth.service';
import { AssetResponse } from '../../../core/models/ticket.model';

describe('AssetList', () => {
  let component: AssetList;
  let fixture: ComponentFixture<AssetList>;
  let assetServiceSpy: { getAssets: ReturnType<typeof vi.fn> };
  let authServiceSpy: { isAdmin: ReturnType<typeof vi.fn> };

  const mockAssets: AssetResponse[] = [
    {
      id: 1,
      code: 'NOTE-100',
      type: 'Notebook',
      description: 'Dell Latitude 3420',
      status: 'IN_USE',
    },
    {
      id: 2,
      code: 'NOTE-999',
      type: 'Notebook',
      description: 'Notebook Inativo',
      status: 'INACTIVE',
    },
  ];

  const setupComponent = async (isAdminUser = true) => {
    assetServiceSpy = {
      getAssets: vi.fn().mockReturnValue(of(mockAssets)),
    };
    authServiceSpy = {
      isAdmin: vi.fn().mockReturnValue(isAdminUser),
    };

    await TestBed.configureTestingModule({
      imports: [AssetList, FormsModule],
      providers: [
        provideRouter([]),
        { provide: AssetService, useValue: assetServiceSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AssetList);
    component = fixture.componentInstance;
    await fixture.whenStable();
  };

  it('should create', async () => {
    await setupComponent();
    expect(component).toBeTruthy();
  });

  it('should load assets on init and render table with data', async () => {
    await setupComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(assetServiceSpy.getAssets).toHaveBeenCalled();
    expect(component.assets.length).toBe(2);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('NOTE-100');
    expect(compiled.textContent).toContain('NOTE-999');
    expect(compiled.textContent).toContain('Notebook');
    expect(compiled.textContent).toContain('Em Uso');
  });

  it('should display "Novo Ativo" and "Editar" links when user is ADMIN', async () => {
    await setupComponent(true);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.btn-primary')?.textContent).toContain('Novo Ativo');

    const editLinks = compiled.querySelectorAll('.btn-edit');
    expect(editLinks.length).toBe(2);
    expect(editLinks[0].textContent).toContain('Editar');
  });

  it('should hide "Novo Ativo" and "Editar" links when user is NOT admin', async () => {
    await setupComponent(false);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.btn-primary')).toBeNull();
    expect(compiled.querySelectorAll('.btn-edit').length).toBe(0);
  });

  it('should filter assets by searchTerm matching code or description', async () => {
    await setupComponent();
    component.searchTerm = '999';
    expect(component.filteredAssets.length).toBe(1);
    expect(component.filteredAssets[0].code).toBe('NOTE-999');

    component.searchTerm = 'dell';
    expect(component.filteredAssets.length).toBe(1);
    expect(component.filteredAssets[0].code).toBe('NOTE-100');
  });

  it('should filter assets by statusFilter', async () => {
    await setupComponent();
    component.statusFilter = 'INACTIVE';
    expect(component.filteredAssets.length).toBe(1);
    expect(component.filteredAssets[0].code).toBe('NOTE-999');

    component.statusFilter = 'IN_USE';
    expect(component.filteredAssets.length).toBe(1);
    expect(component.filteredAssets[0].code).toBe('NOTE-100');
  });

  it('should show empty message when filter has no results', async () => {
    await setupComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = 'inexistente';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.filteredAssets.length).toBe(0);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Nenhum ativo encontrado');
  });

  it('should display error message when loading assets fails', async () => {
    await setupComponent();
    const error500 = new HttpErrorResponse({ status: 500, statusText: 'Internal Error' });
    assetServiceSpy.getAssets.mockReturnValue(throwError(() => error500));

    component.loadAssets();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Não foi possível carregar a lista de ativos. Tente novamente mais tarde.');
    expect(component.assets.length).toBe(0);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Não foi possível carregar a lista de ativos.');
  });

  it('should display permission denied message when loading assets returns 403', async () => {
    await setupComponent(false);
    const error403 = new HttpErrorResponse({ status: 403, statusText: 'Forbidden' });
    assetServiceSpy.getAssets.mockReturnValue(throwError(() => error403));

    component.loadAssets();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage).toBe('Você não possui permissão para visualizar os ativos.');
    expect(component.assets.length).toBe(0);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Você não possui permissão para visualizar os ativos.');
  });
});
