import { Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from './core/services/auth.service';
import { Header } from './core/components/header/header';

@Component({
  imports: [RouterOutlet, Header],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);

  protected readonly title = signal('frontend');
  readonly currentUrl = signal(this.router.url || '');

  readonly showHeader = computed(() => {
    const url = this.currentUrl();
    const isLogin = url.startsWith('/login') || url === '/' || url === '';
    return this.authService.isAuthenticated() && !isLogin;
  });

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        this.currentUrl.set(e.urlAfterRedirects || e.url);
      });
  }
}
