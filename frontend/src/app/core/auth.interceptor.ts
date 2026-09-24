import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { RUNTIME_CONFIG } from './runtime-config';

/** Agrega el JWT solo a llamadas de nuestra API y reintenta una vez tras renovar el token si recibe 401. */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const config = inject(RUNTIME_CONFIG);
  const isApi = request.url.startsWith(`${config.apiUrl}/api/`);
  if (!isApi || request.url.includes('/api/v1/auth/')) {
    return next(request);
  }
  const withToken = (req: HttpRequest<unknown>) => {
    const token = auth.token();
    return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  };
  return from(auth.ensureAccessToken()).pipe(
    switchMap(() => next(withToken(request))),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        return from(auth.refresh()).pipe(
          switchMap((renewed) => {
            if (!renewed) {
              void auth.logout();
              return throwError(() => error);
            }
            return next(withToken(request));
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
