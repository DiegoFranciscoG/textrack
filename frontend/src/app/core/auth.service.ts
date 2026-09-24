import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Role, TokenResponse, UserSummary } from './models';
import { RUNTIME_CONFIG } from './runtime-config';

const REFRESH_KEY = 'textrack.refresh';
const USER_KEY = 'textrack.user';

/**
 * Sesión del usuario. El JWT de acceso vive solo en memoria (no se persiste) y el refresh token en
 * sessionStorage, que se borra al cerrar la pestaña. Al recargar la página se renueva el acceso con el refresh.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly router = inject(Router);
  // HttpClient sin interceptores para no entrar en bucle al renovar el token.
  private readonly http = new HttpClient(inject(HttpBackend));

  private readonly accessToken = signal<string | null>(null);
  private readonly currentUser = signal<UserSummary | null>(readStoredUser());
  private refreshing: Promise<boolean> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  token(): string | null {
    return this.accessToken();
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.currentUser();
    return !!user && roles.includes(user.role);
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      this.http.post<TokenResponse>(`${this.config.apiUrl}/api/v1/auth/login`, { email, password }),
    );
    this.store(response);
  }

  /** Renueva el acceso con el refresh token (una sola petición aunque varias llamadas fallen a la vez). */
  refresh(): Promise<boolean> {
    if (this.refreshing) {
      return this.refreshing;
    }
    const refreshToken = sessionStorage.getItem(REFRESH_KEY);
    if (!refreshToken) {
      return Promise.resolve(false);
    }
    this.refreshing = firstValueFrom(
      this.http.post<TokenResponse>(`${this.config.apiUrl}/api/v1/auth/refresh`, { refreshToken }),
    )
      .then((response) => {
        this.store(response);
        return true;
      })
      .catch(() => {
        this.clear();
        return false;
      })
      .finally(() => (this.refreshing = null));
    return this.refreshing;
  }

  async ensureAccessToken(): Promise<boolean> {
    return this.accessToken() !== null || this.refresh();
  }

  async logout(): Promise<void> {
    const refreshToken = sessionStorage.getItem(REFRESH_KEY);
    this.clear();
    if (refreshToken) {
      await firstValueFrom(
        this.http.post(`${this.config.apiUrl}/api/v1/auth/logout`, { refreshToken }),
      ).catch(() => undefined);
    }
    await this.router.navigate(['/login']);
  }

  private store(response: TokenResponse): void {
    this.accessToken.set(response.accessToken);
    this.currentUser.set(response.user);
    sessionStorage.setItem(REFRESH_KEY, response.refreshToken);
    sessionStorage.setItem(USER_KEY, JSON.stringify(response.user));
  }

  private clear(): void {
    this.accessToken.set(null);
    this.currentUser.set(null);
    sessionStorage.removeItem(REFRESH_KEY);
    sessionStorage.removeItem(USER_KEY);
  }
}

function readStoredUser(): UserSummary | null {
  try {
    const raw = sessionStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as UserSummary) : null;
  } catch {
    return null;
  }
}
