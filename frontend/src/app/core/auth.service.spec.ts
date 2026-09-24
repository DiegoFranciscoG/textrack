import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';
import { TokenResponse } from './models';
import { RUNTIME_CONFIG } from './runtime-config';

const response: TokenResponse = {
  accessToken: 'access',
  tokenType: 'Bearer',
  expiresIn: 900,
  refreshToken: 'refresh-1',
  user: { id: 1, email: 'visor@textrack.demo', fullName: 'Visitante', role: 'VIEWER' },
};

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIG, useValue: { apiUrl: '' } },
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('guarda el JWT en memoria y el refresh token en sessionStorage', async () => {
    const login = auth.login('visor@textrack.demo', 'secreta');
    http.expectOne('/api/v1/auth/login').flush(response);
    await login;

    expect(auth.token()).toBe('access');
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.hasRole('VIEWER')).toBe(true);
    expect(auth.hasRole('ADMIN')).toBe(false);
    expect(sessionStorage.getItem('textrack.refresh')).toBe('refresh-1');
    expect(JSON.stringify(sessionStorage)).not.toContain('access');
  });

  it('comparte una sola renovación entre llamadas concurrentes y limpia la sesión si falla', async () => {
    sessionStorage.setItem('textrack.refresh', 'viejo');
    const first = auth.refresh();
    const second = auth.refresh();
    http.expectOne('/api/v1/auth/refresh').flush({ title: 'No autenticado' }, { status: 401, statusText: 'Unauthorized' });

    expect(await first).toBe(false);
    expect(await second).toBe(false);
    expect(sessionStorage.getItem('textrack.refresh')).toBeNull();
    expect(auth.isAuthenticated()).toBe(false);
  });
});
