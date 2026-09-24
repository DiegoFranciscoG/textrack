import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Role } from '../core/models';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  roles?: Role[];
}

const NAV: NavItem[] = [
  { path: '/', label: 'Tablero en vivo', icon: '◉' },
  { path: '/orders', label: 'Órdenes y cortes', icon: '▤' },
  { path: '/rolls', label: 'Rollos · 4 puntos', icon: '◎' },
  { path: '/payroll', label: 'Destajo', icon: '$' },
  { path: '/quality', label: 'Calidad AQL', icon: '✓' },
  { path: '/traceability', label: 'Trazabilidad', icon: '⇄' },
  { path: '/engineering', label: 'Ingeniería (SAM)', icon: '⚙' },
  { path: '/plant', label: 'Asistencia y paros', icon: '⏱' },
  { path: '/scan', label: 'Registrar lectura', icon: '⌗', roles: ['ADMIN', 'SUPERVISOR'] },
];

const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administración',
  PLANNER: 'Planificación',
  SUPERVISOR: 'Supervisión',
  QUALITY: 'Calidad',
  SCANNER: 'Tablet de planta',
  VIEWER: 'Solo lectura',
};

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell" [class.open]="menuOpen()">
      <aside class="sidebar">
        <a class="brand" routerLink="/" (click)="menuOpen.set(false)">
          <span class="logo">T</span>
          <span><strong>textrack</strong><small>MES de confección</small></span>
        </a>
        <nav>
          @for (item of nav(); track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: item.path === '/' }"
              (click)="menuOpen.set(false)"
            >
              <span class="icon" aria-hidden="true">{{ item.icon }}</span>{{ item.label }}
            </a>
          }
        </nav>
        @if (auth.user(); as user) {
          <div class="user">
            <div>
              <strong>{{ user.fullName }}</strong>
              <small>{{ roleLabel(user.role) }}</small>
            </div>
            <button class="small" type="button" (click)="auth.logout()">Salir</button>
          </div>
        }
      </aside>
      <div class="content">
        <header class="topbar">
          <button class="small menu" type="button" aria-label="Abrir menú" (click)="menuOpen.update((v) => !v)">☰</button>
          <span class="muted">Planta demo · datos ficticios</span>
        </header>
        <main>
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    .shell {
      display: grid;
      grid-template-columns: 240px 1fr;
      min-height: 100vh;
    }
    .sidebar {
      background: var(--surface);
      border-right: 1px solid var(--border);
      display: flex;
      flex-direction: column;
      padding: 1rem 0.75rem;
      gap: 1rem;
      position: sticky;
      top: 0;
      height: 100vh;
    }
    .brand {
      display: flex;
      gap: 0.6rem;
      align-items: center;
      text-decoration: none;
      color: var(--text);
      padding: 0 0.4rem;
    }
    .brand small {
      display: block;
      color: var(--muted);
      font-size: 0.72rem;
    }
    .logo {
      width: 34px;
      height: 34px;
      border-radius: 9px;
      background: var(--primary);
      color: #fff;
      display: grid;
      place-items: center;
      font-weight: 800;
    }
    nav {
      display: flex;
      flex-direction: column;
      gap: 2px;
      flex: 1;
    }
    nav a {
      display: flex;
      gap: 0.6rem;
      align-items: center;
      padding: 0.5rem 0.6rem;
      border-radius: 8px;
      color: var(--text);
      text-decoration: none;
    }
    nav a:hover {
      background: var(--surface-2);
    }
    nav a.active {
      background: var(--primary-soft);
      color: var(--primary);
      font-weight: 600;
    }
    .icon {
      width: 1.2rem;
      text-align: center;
    }
    .user {
      border-top: 1px solid var(--border);
      padding-top: 0.75rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 0.5rem;
    }
    .user small {
      display: block;
      color: var(--muted);
    }
    .topbar {
      display: none;
      align-items: center;
      gap: 0.75rem;
      padding: 0.6rem 0.75rem;
      border-bottom: 1px solid var(--border);
      background: var(--surface);
    }
    @media (max-width: 900px) {
      .shell {
        grid-template-columns: 1fr;
      }
      .sidebar {
        position: fixed;
        z-index: 10;
        width: 260px;
        transform: translateX(-100%);
        transition: transform 0.2s;
      }
      .shell.open .sidebar {
        transform: none;
        box-shadow: 0 0 0 100vmax rgb(0 0 0 / 35%);
      }
      .topbar {
        display: flex;
      }
    }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly menuOpen = signal(false);
  protected readonly nav = computed(() =>
    NAV.filter((item) => !item.roles || this.auth.hasRole(...item.roles)),
  );

  protected roleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }
}
