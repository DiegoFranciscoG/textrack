import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { NumPipe, PlantTimePipe, errorMessage } from '../../core/format';
import { BundleTrace, RollTrace } from '../../core/models';

/** Trazabilidad rollo → bulto → prenda (serie = código de bulto + número de pieza). */
@Component({
  selector: 'app-traceability',
  imports: [FormsModule, PlantTimePipe, NumPipe],
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Trazabilidad</h1>
          <p class="muted">Busca una prenda (<code>CT-00001-001-07</code>), un bulto (<code>CT-00001-001</code>) o un rollo (<code>R-24091</code>).</p>
        </div>
      </div>
      <form class="card row" (ngSubmit)="search()">
        <input name="q" [(ngModel)]="query" placeholder="Serie de prenda, bulto o rollo" required style="flex: 1" />
        <button class="primary" type="submit">Buscar</button>
      </form>
      @if (error()) { <div class="message error" style="margin-top: 1rem">{{ error() }}</div> }

      @if (bundle(); as b) {
        <div class="chain">
          <div class="card step">
            <span class="label">Rollo</span>
            <strong>{{ b.roll.code }}</strong>
            <span>Lote de teñido <strong>{{ b.roll.dyeLot }}</strong></span>
            <span class="muted">{{ b.roll.supplier }} · {{ b.roll.pointsPer100SqYd | num }} pts/100 yd²
              @if (b.roll.accepted) { · aprobado }</span>
          </div>
          <div class="arrow">→</div>
          <div class="card step">
            <span class="label">Corte y bulto</span>
            <strong>{{ b.bundleCode }}</strong>
            <span>{{ b.cutCode }} · talla {{ b.sizeCode }} · {{ b.color }} · {{ b.quantity }} piezas</span>
            <span class="muted">Cortado {{ b.cutAt | plantTime: true }}</span>
          </div>
          <div class="arrow">→</div>
          <div class="card step">
            <span class="label">{{ b.garmentSerial ? 'Prenda' : 'Orden' }}</span>
            <strong>{{ b.garmentSerial ?? b.orderCode }}</strong>
            <span>{{ b.styleCode }} · {{ b.styleName }}</span>
            <span class="muted">{{ b.orderCode }} · {{ b.customer }}</span>
          </div>
        </div>

        <div class="grid grid-2">
          <section class="card table-wrap">
            <h2>Quién cosió cada operación</h2>
            <table>
              <thead><tr><th>Op.</th><th>Operación</th><th>Operario</th><th>Hora</th></tr></thead>
              <tbody>
                @for (op of b.operations; track op.ticketId) {
                  <tr>
                    <td>{{ op.operationCode }}</td><td>{{ op.operationName }}</td>
                    <td>@if (op.operatorCode) { <strong>{{ op.operatorCode }}</strong> {{ op.operatorName }} } @else { <span class="muted">pendiente</span> }</td>
                    <td>{{ op.scannedAt | plantTime: true }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
          <section class="card">
            <h2>Defectos de calidad asociados</h2>
            @for (d of b.defects; track $index) {
              <p><span class="badge" [class]="d.severity === 'MINOR' ? 'badge warn' : 'badge bad'">{{ d.severity }}</span>
                {{ d.defectName }} × {{ d.quantity }} @if (d.operationCode) { en {{ d.operationCode }} }
                <span class="muted">{{ d.inspectedAt | plantTime: true }}</span></p>
            } @empty {
              <p class="muted">Sin defectos registrados para este bulto.</p>
            }
          </section>
        </div>
      }

      @if (roll(); as r) {
        <section class="card" style="margin-top: 1rem">
          <h2>Rollo {{ r.roll.code }} · lote {{ r.roll.dyeLot }}</h2>
          <p class="muted">{{ r.color }} · {{ r.remainingM | num }} de {{ r.lengthM | num }} m disponibles · {{ r.bundles }} bultos,
            {{ r.pieces }} piezas cortadas de este rollo.</p>
          <div class="table-wrap">
            <table>
              <thead><tr><th>Bulto</th><th>Corte</th><th>Talla</th><th class="num">Piezas</th></tr></thead>
              <tbody>
                @for (b of r.bundleList; track b.bundleCode) {
                  <tr class="clickable" (click)="query = b.bundleCode; search()">
                    <td><code>{{ b.bundleCode }}</code></td><td>{{ b.cutCode }}</td><td>{{ b.sizeCode }}</td>
                    <td class="num">{{ b.quantity }}</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    </section>
  `,
  styles: `
    .chain {
      display: grid;
      grid-template-columns: 1fr auto 1fr auto 1fr;
      gap: 0.75rem;
      align-items: center;
      margin: 1rem 0;
    }
    .step {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .label {
      font-size: 0.72rem;
      text-transform: uppercase;
      color: var(--muted);
      font-weight: 600;
    }
    .arrow {
      font-size: 1.5rem;
      color: var(--muted);
    }
    @media (max-width: 900px) {
      .chain {
        grid-template-columns: 1fr;
      }
      .arrow {
        transform: rotate(90deg);
        justify-self: center;
      }
    }
  `,
})
export class Traceability {
  private readonly api = inject(ApiService);

  protected query = '';
  protected readonly bundle = signal<BundleTrace | null>(null);
  protected readonly roll = signal<RollTrace | null>(null);
  protected readonly error = signal<string | null>(null);

  protected search(): void {
    const q = this.query.trim().toUpperCase();
    this.error.set(null);
    this.bundle.set(null);
    this.roll.set(null);
    const onError = (e: unknown) => this.error.set(errorMessage(e));
    if (/^CT-\d{5}-\d{3}-\d{1,3}$/.test(q)) {
      this.api.traceGarment(q).subscribe({ next: (b) => this.bundle.set(b), error: onError });
    } else if (/^CT-\d{5}-\d{3}$/.test(q)) {
      this.api.traceBundle(q).subscribe({ next: (b) => this.bundle.set(b), error: onError });
    } else {
      this.api.traceRoll(q).subscribe({ next: (r) => this.roll.set(r), error: onError });
    }
  }
}
