import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/format';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <div class="login">
      <form class="card" (ngSubmit)="submit()" #form="ngForm">
        <div class="brand">
          <span class="logo">T</span>
          <div>
            <h1>textrack</h1>
            <p class="muted">Seguimiento de producción para plantas de confección</p>
          </div>
        </div>
        <label>
          Correo
          <input name="email" type="email" autocomplete="username" required [(ngModel)]="email" />
        </label>
        <label>
          Contraseña
          <input name="password" type="password" autocomplete="current-password" required [(ngModel)]="password" />
        </label>
        @if (error()) {
          <div class="message error" role="alert">{{ error() }}</div>
        }
        <button class="primary" type="submit" [disabled]="loading() || form.invalid">
          {{ loading() ? 'Ingresando…' : 'Ingresar' }}
        </button>
        <p class="muted demo">
          Demo pública de solo lectura: <code>visor&#64;textrack.demo</code> · la contraseña está en el README.
        </p>
      </form>
    </div>
  `,
  styles: `
    .login {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 1rem;
    }
    form {
      width: min(400px, 100%);
      display: flex;
      flex-direction: column;
      gap: 0.9rem;
      padding: 1.5rem;
    }
    .brand {
      display: flex;
      gap: 0.8rem;
      align-items: center;
      margin-bottom: 0.5rem;
    }
    .brand h1 {
      margin: 0;
    }
    .brand p {
      margin: 0;
    }
    .logo {
      width: 44px;
      height: 44px;
      border-radius: 11px;
      background: var(--primary);
      color: #fff;
      display: grid;
      place-items: center;
      font-weight: 800;
      font-size: 1.3rem;
    }
    button {
      justify-content: center;
    }
    .demo {
      font-size: 0.8rem;
      margin: 0;
    }
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected email = '';
  protected password = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async submit(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.auth.login(this.email.trim(), this.password);
      await this.router.navigate(['/']);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
