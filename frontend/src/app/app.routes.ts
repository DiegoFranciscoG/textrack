import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.Login), title: 'Ingresar · textrack' },
  {
    path: '',
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
        title: 'Tablero · textrack',
      },
      {
        path: 'orders',
        loadComponent: () => import('./pages/orders/orders').then((m) => m.Orders),
        title: 'Órdenes · textrack',
      },
      {
        path: 'orders/:id',
        loadComponent: () => import('./pages/orders/order-detail').then((m) => m.OrderDetailPage),
        title: 'Orden · textrack',
      },
      {
        path: 'rolls',
        loadComponent: () => import('./pages/rolls/rolls').then((m) => m.Rolls),
        title: 'Rollos · textrack',
      },
      {
        path: 'payroll',
        loadComponent: () => import('./pages/payroll/payroll').then((m) => m.Payroll),
        title: 'Destajo · textrack',
      },
      {
        path: 'quality',
        loadComponent: () => import('./pages/quality/quality').then((m) => m.Quality),
        title: 'Calidad · textrack',
      },
      {
        path: 'traceability',
        loadComponent: () => import('./pages/traceability/traceability').then((m) => m.Traceability),
        title: 'Trazabilidad · textrack',
      },
      {
        path: 'engineering',
        loadComponent: () => import('./pages/engineering/engineering').then((m) => m.Engineering),
        title: 'Ingeniería · textrack',
      },
      {
        path: 'plant',
        loadComponent: () => import('./pages/plant/plant').then((m) => m.Plant),
        title: 'Planta · textrack',
      },
      {
        path: 'scan',
        loadComponent: () => import('./pages/scan/scan').then((m) => m.Scan),
        data: { roles: ['ADMIN', 'SUPERVISOR'] },
        title: 'Registrar lectura · textrack',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
