import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AssetService } from '../../../core/services/asset.service';
import { AuthService } from '../../../core/services/auth.service';
import { AssetResponse, AssetStatus } from '../../../core/models/ticket.model';

@Component({
  selector: 'app-asset-list',
  imports: [RouterLink, FormsModule],
  styleUrl: './asset-list.css',
  templateUrl: './asset-list.html',
})
export class AssetList implements OnInit {
  assets: AssetResponse[] = [];
  isLoading = false;
  errorMessage: string | null = null;
  searchTerm = '';
  statusFilter = '';

  constructor(
    private readonly assetService: AssetService,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadAssets();
  }

  get isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  get filteredAssets(): AssetResponse[] {
    const term = this.searchTerm.trim().toLowerCase();
    return this.assets.filter((asset) => {
      const matchesSearch =
        !term ||
        asset.code.toLowerCase().includes(term) ||
        asset.type.toLowerCase().includes(term) ||
        (asset.description && asset.description.toLowerCase().includes(term));

      const matchesStatus =
        !this.statusFilter || asset.status === this.statusFilter;

      return matchesSearch && matchesStatus;
    });
  }

  loadAssets(): void {
    this.isLoading = true;
    this.errorMessage = null;

    this.assetService.getAssets().subscribe({
      next: (data) => {
        this.assets = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.assets = [];
        this.isLoading = false;
        if (error.status === 401) {
          this.errorMessage = 'Sua sessão expirou ou o token é inválido. Por favor, faça login novamente.';
        } else if (error.status === 403) {
          this.errorMessage = 'Você não possui permissão para visualizar os ativos.';
        } else {
          this.errorMessage = 'Não foi possível carregar a lista de ativos. Tente novamente mais tarde.';
        }
        this.cdr.markForCheck();
      },
    });
  }

  getStatusLabel(status: AssetStatus): string {
    switch (status) {
      case 'IN_USE':
        return 'Em Uso';
      case 'INACTIVE':
        return 'Inativo';
      case 'UNDER_MAINTENANCE':
        return 'Em Manutenção';
      case 'DISCARDED':
        return 'Descartado';
      default:
        return status;
    }
  }
}
