import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { STATUS_LABELS, errorMessage, plantToday } from '../../core/format';
import { OrderSummary, Style } from '../../core/models';

interface DraftLine {
  sizeCode: string;
  color: string;
  quantity: number;
}

@Component({
  selector: 'app-orders',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Órdenes de producción</h1>
          <p class="muted">Matriz tallas × colores, cortes y avance de confección.</p>
        </div>
        @if (canCreate()) {
          <button class="primary" type="button" (click)="showForm.set(!showForm())">
            {{ showForm() ? 'Cerrar' : 'Nueva orden' }}
          </button>
        }
      </div>

      @if (showForm()) {
        <form class="card stack create" (ngSubmit)="create()">
          <div class="form-grid">
            <label>Estilo
              <select name="style" required [(ngModel)]="styleId">
                @for (s of styles(); track s.id) {
                  <option [ngValue]="s.id">{{ s.code }} · {{ s.name }}</option>
                }
              </select>
            </label>
            <label>Cliente <input name="customer" required maxlength="120" [(ngModel)]="customer" /></label>
            <label>Fecha de entrega <input name="due" type="date" required [min]="today" [(ngModel)]="dueDate" /></label>
          </div>
          <h3>Tallas y colores</h3>
          @for (line of lines(); track $index) {
            <div class="form-grid">
              <label>Talla
                <select [name]="'size' + $index" [(ngModel)]="line.sizeCode">
                  @for (size of sizes(); track size) { <option [value]="size">{{ size }}</option> }
                </select>
              </label>
              <label>Color <input [name]="'color' + $index" required maxlength="40" [(ngModel)]="line.color" /></label>
              <label>Cantidad <input [name]="'qty' + $index" type="number" min="1" required [(ngModel)]="line.quantity" /></label>
              <button type="button" class="small" (click)="removeLine($index)" [disabled]="lines().length === 1">Quitar</button>
            </div>
          }
          <div class="row">
            <button type="button" (click)="addLine()">+ Talla/color</button>
            <button class="primary" type="submit" [disabled]="saving()">Crear orden</button>
          </div>
          @if (error()) { <div class="message error">{{ error() }}</div> }
        </form>
      }

      <div class="card table-wrap">
        <table>
          <thead>
            <tr><th>Orden</th><th>Estilo</th><th>Cliente</th><th>Entrega</th><th>Estado</th></tr>
          </thead>
          <tbody>
            @for (o of orders(); track o.id) {
              <tr class="clickable" (click)="open(o.id)">
                <td><a [routerLink]="['/orders', o.id]"><strong>{{ o.code }}</strong></a></td>
                <td>{{ o.styleCode }} <span class="muted">{{ o.styleName }}</span></td>
                <td>{{ o.customer }}</td>
                <td>{{ o.dueDate }}</td>
                <td><span class="badge" [class]="'badge ' + tone(o.status)">{{ labels[o.status] }}</span></td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="empty">No hay órdenes.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .create {
      margin-bottom: 1rem;
    }
    .create h3 {
      margin: 0.5rem 0 0;
    }
  `,
})
export class Orders {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly labels = STATUS_LABELS;
  protected readonly today = plantToday();
  protected readonly orders = signal<OrderSummary[]>([]);
  protected readonly styles = signal<Style[]>([]);
  protected readonly sizes = signal<string[]>([]);
  protected readonly showForm = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly lines = signal<DraftLine[]>([{ sizeCode: 'M', color: '', quantity: 100 }]);
  protected readonly canCreate = computed(() => this.auth.hasRole('ADMIN', 'PLANNER'));

  protected styleId: number | null = null;
  protected customer = '';
  protected dueDate = '';

  constructor() {
    this.load();
    this.api.styles().subscribe((styles) => {
      this.styles.set(styles);
      this.styleId = styles[0]?.id ?? null;
    });
    this.api.sizes().subscribe((sizes) => this.sizes.set(sizes));
  }

  protected tone(status: string): string {
    return status === 'IN_PROGRESS' ? 'info' : status === 'COMPLETED' ? 'ok' : status === 'CANCELLED' ? 'bad' : 'warn';
  }

  protected addLine(): void {
    const last = this.lines().at(-1);
    this.lines.update((lines) => [...lines, { sizeCode: 'L', color: last?.color ?? '', quantity: 100 }]);
  }

  protected removeLine(index: number): void {
    this.lines.update((lines) => lines.filter((_, i) => i !== index));
  }

  protected open(id: number): void {
    void this.router.navigate(['/orders', id]);
  }

  protected create(): void {
    if (this.styleId == null) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api
      .createOrder({
        styleId: this.styleId,
        customer: this.customer,
        dueDate: this.dueDate,
        lines: this.lines().map((l) => ({ ...l, color: l.color.trim(), quantity: Number(l.quantity) })),
      })
      .subscribe({
        next: (order) => {
          this.saving.set(false);
          void this.router.navigate(['/orders', order.id]);
        },
        error: (e) => {
          this.saving.set(false);
          this.error.set(errorMessage(e));
        },
      });
  }

  private load(): void {
    this.api.orders().subscribe((orders) => this.orders.set(orders));
  }
}
