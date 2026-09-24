import { Component, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { NumPipe, UsdPipe } from '../../core/format';
import { Style } from '../../core/models';

/** Rutas de operaciones por estilo con SAM (minutos estándar, OIT) y tarifa a destajo vigente. */
@Component({
  selector: 'app-engineering',
  imports: [NumPipe, UsdPipe],
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Ingeniería de métodos</h1>
          <p class="muted">
            SAM = tiempo básico + suplementos (OIT, <em>Introduction to Work Study</em>). La tarifa por pieza se programa
            con vigencia y nunca se solapa (restricción de exclusión en PostgreSQL).
          </p>
        </div>
      </div>
      <div class="grid grid-2">
        @for (s of styles(); track s.id) {
          <section class="card table-wrap">
            <h2>{{ s.code }} · {{ s.name }}</h2>
            <p class="muted">{{ s.operations.length }} operaciones · SAM total {{ s.totalSam | num }} min por prenda</p>
            <table>
              <thead><tr><th>Sec.</th><th>Operación</th><th>Máquina</th><th class="num">SAM</th><th class="num">Tarifa</th>
                <th class="num">USD / min</th></tr></thead>
              <tbody>
                @for (op of s.operations; track op.id) {
                  <tr>
                    <td>{{ op.sequence }}</td>
                    <td><strong>{{ op.code }}</strong> {{ op.name }}</td>
                    <td class="muted">{{ op.machineType }}</td>
                    <td class="num">{{ op.samMinutes | num }}</td>
                    <td class="num">{{ op.currentRateUsd | usd }}</td>
                    <td class="num">{{ op.currentRateUsd != null ? (op.currentRateUsd / op.samMinutes | num) : '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        }
      </div>
    </section>
  `,
})
export class Engineering {
  protected readonly styles = signal<Style[]>([]);

  constructor() {
    inject(ApiService).styles().subscribe((styles) => this.styles.set(styles));
  }
}
