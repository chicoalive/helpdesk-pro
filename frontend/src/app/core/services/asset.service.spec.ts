import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AssetService } from './asset.service';
import { AssetRequest, AssetResponse } from '../models/ticket.model';

describe('AssetService', () => {
  let service: AssetService;
  let httpMock: HttpTestingController;

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

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        AssetService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(AssetService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should send GET request to /api/assets with Authorization header', () => {
    localStorage.setItem('helpdesk_token', 'jwt-token-teste');

    service.getAssets().subscribe((assets) => {
      expect(assets).toEqual(mockAssets);
      expect(assets.length).toBe(2);
    });

    const req = httpMock.expectOne('/api/assets');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token-teste');

    req.flush(mockAssets);
  });

  it('should send GET request to /api/assets/{id} with Authorization header', () => {
    localStorage.setItem('helpdesk_token', 'jwt-token-teste');

    service.getAssetById(1).subscribe((asset) => {
      expect(asset).toEqual(mockAssets[0]);
    });

    const req = httpMock.expectOne('/api/assets/1');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token-teste');

    req.flush(mockAssets[0]);
  });

  it('should send POST request to /api/assets with payload and Authorization header', () => {
    localStorage.setItem('helpdesk_token', 'jwt-token-teste');

    const newAsset: AssetRequest = {
      code: 'MON-01',
      type: 'Monitor',
      description: 'Monitor Dell 24',
      status: 'IN_USE',
    };

    const createdAsset: AssetResponse = {
      id: 3,
      ...newAsset,
      description: newAsset.description ?? null,
    };

    service.createAsset(newAsset).subscribe((asset) => {
      expect(asset).toEqual(createdAsset);
      expect(asset.id).toBe(3);
    });

    const req = httpMock.expectOne('/api/assets');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(newAsset);
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token-teste');

    req.flush(createdAsset);
  });

  it('should send PUT request to /api/assets/{id} with payload and Authorization header', () => {
    localStorage.setItem('helpdesk_token', 'jwt-token-teste');

    const updateData: AssetRequest = {
      code: 'NOTE-100-EDIT',
      type: 'Notebook',
      description: 'Atualizado',
      status: 'UNDER_MAINTENANCE',
    };

    const updatedAsset: AssetResponse = {
      id: 1,
      ...updateData,
      description: updateData.description ?? null,
    };

    service.updateAsset(1, updateData).subscribe((asset) => {
      expect(asset).toEqual(updatedAsset);
    });

    const req = httpMock.expectOne('/api/assets/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(updateData);
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token-teste');

    req.flush(updatedAsset);
  });
});
