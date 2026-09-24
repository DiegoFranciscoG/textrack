import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

/** Exige sesión y, si la ruta declara `data.roles`, uno de esos roles. */
export const authGuard: CanActivateFn = async (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isAuthenticated() || !(await auth.ensureAccessToken())) {
    return router.createUrlTree(['/login']);
  }
  const roles = route.data['roles'] as Role[] | undefined;
  if (roles && !auth.hasRole(...roles)) {
    return router.createUrlTree(['/']);
  }
  return true;
};
