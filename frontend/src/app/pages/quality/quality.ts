import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { PlantTimePipe, errorMessage } from '../../core/format';
import { AqlInspection, AqlPlan, DefectType, InspectionLevel, OrderSummary } from '../../core/models';

const AQLS = [0.1, 0.15, 0.25, 0.4, 0.65, 1.0, 1.5, 2.5, 4.0, 6.5];
const LEVELS: InspectionLevel[] = ['S1', 'S2', 'S3', 'S4', 'I', 'II', 'III'];

interface DraftDefect {
  defectTypeCode: string;
  quantity: number;
  bundleCode: string;
  operationCode: string;
}

/** Plan de muestreo AQL (ISO 2859-1) e inspección de lotes con defectos críticos, mayores y menores. */
@Component({
  selector: 'app-quality',
  imports: [FormsModule, PlantTimePipe],
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Calidad · muestreo AQL</h1>
          <p class="muted">Plan simple de inspección normal (ISO 2859-1, Tablas 1 y 2-A). El servidor decide el resultado.</p>
        </div>
      </div>
      @if (error()) { <div class="message error">{{ error() }}</div> }

      <div class="grid grid-2">
        <section class="card stack">
          <h2>Calculadora de plan</h2>
          <div class="form-grid">
            <label>Tamaño del lote <input type="number" min="2" [(ngModel)]="lotSize" (change)="refreshPlans()" /></label>
            <label>Nivel
              <select [(ngModel)]="level" (change)="refreshPlans()">
                @for (l of levels; track l) { <option [value]="l">{{ l }}</option> }
              </select>
            </label>
            <label>AQL mayores
              <select [(ngModel)]="aqlMajor" (change)="refreshPlans()">
                @for (a of aqls; track a) { <option [ngValue]="a">{{ a }}</option> }
              </select>
            </label>
            <label>AQL menores
              <select [(ngModel)]="aqlMinor" (change)="refreshPlans()">
                @for (a of aqls; track a) { <option [ngValue]="a">{{ a }}</option> }
              </select>
            </label>
          </div>
          @if (majorPlan(); as mp) {
            <table>
              <thead><tr><th>Clase</th><th>Letra</th><th class="num">Muestra</th><th class="num">Ac</th><th class="num">Re</th></tr></thead>
              <tbody>
                <tr><td>Mayores (AQL {{ mp.aql }})</td><td>{{ mp.initialLetter }} → {{ mp.planLetter }}</td>
                  <td class="num">{{ mp.sampleSize }}</td><td class="num">{{ mp.acceptNumber }}</td><td class="num">{{ mp.rejectNumber }}</td></tr>
                @if (minorPlan(); as np) {
                  <tr><td>Menores (AQL {{ np.aql }})</td><td>{{ np.initialLetter }} → {{ np.planLetter }}</td>
                    <td class="num">{{ np.sampleSize }}</td><td class="num">{{ np.acceptNumber }}</td><td class="num">{{ np.rejectNumber }}</td></tr>
                }
                <tr><td>Críticos</td><td colspan="4">Cualquier defecto crítico rechaza el lote</td></tr>
              </tbody>
            </table>
            <p class="muted">Inspeccionar {{ sampleSize() }} prendas. {{ mp.standardEdition }}.</p>
          }
        </section>

        @if (canInspect()) {
          <form class="card stack" (ngSubmit)="submit()">
            <h2>Registrar inspección</h2>
            <div class="form-grid">
              <label>Orden
                <select name="order" [(ngModel)]="orderId" required>
                  @for (o of orders(); track o.id) { <option [ngValue]="o.id">{{ o.code }} · {{ o.styleCode }}</option> }
                </select>
              </label>
              <label>Línea
                <select name="line" [(ngModel)]="lineId">
                  <option [ngValue]="null">—</option>
                  @for (l of lines(); track l.id) { <option [ngValue]="l.id">{{ l.name }}</option> }
                </select>
              </label>
              <label>Unidades defectuosas <input name="defective" type="number" min="0" [(ngModel)]="defectiveUnits" /></label>
            </div>
            @for (d of defects(); track $index) {
              <div class="form-grid">
                <label>Defecto
                  <select [name]="'t' + $index" [(ngModel)]="d.defectTypeCode">
                    @for (t of defectTypes(); track t.code) { <option [value]="t.code">{{ t.name }} ({{ t.defaultSeverity }})</option> }
                  </select>
                </label>
                <label>Cantidad <input [name]="'q' + $index" type="number" min="1" [(ngModel)]="d.quantity" /></label>
                <label>Bulto <input [name]="'b' + $index" placeholder="CT-00001-001" [(ngModel)]="d.bundleCode" /></label>
                <label>Operación <input [name]="'o' + $index" placeholder="OP50" [(ngModel)]="d.operationCode" /></label>
              </div>
            }
            <div class="row">
              <button type="button" (click)="addDefect()">+ Defecto</button>
              <button class="primary" type="submit">Evaluar lote</button>
            </div>
            @if (lastResult(); as r) {
              <div class="message" [class]="r.result === 'ACCEPTED' ? 'message success' : 'message error'">
                Lote {{ r.result === 'ACCEPTED' ? 'ACEPTADO' : 'RECHAZADO' }}: n={{ r.sampleSize }}, mayores {{ r.majorFound }}
                (Ac {{ r.majorAccept }}), menores {{ r.minorFound }} (Ac {{ r.minorAccept }}), críticos {{ r.criticalFound }}.
              </div>
            }
          </form>
        }
      </div>

      <section class="card table-wrap" style="margin-top: 1rem">
        <h2>Inspecciones recientes</h2>
        <table>
          <thead>
            <tr><th>Fecha</th><th>Orden</th><th>Línea</th><th class="num">Lote</th><th>Nivel/AQL</th><th class="num">n</th>
              <th class="num">Críticos</th><th class="num">Mayores</th><th class="num">Menores</th><th>Resultado</th></tr>
          </thead>
          <tbody>
            @for (i of inspections(); track i.id) {
              <tr>
                <td>{{ i.inspectedAt | plantTime: true }}</td><td>{{ i.orderCode }}</td><td>{{ i.lineCode }}</td>
                <td class="num">{{ i.lotSize }}</td><td>{{ i.level }} · {{ i.aqlMajor }}/{{ i.aqlMinor }}</td>
                <td class="num">{{ i.sampleSize }}</td><td class="num">{{ i.criticalFound }}</td>
                <td class="num">{{ i.majorFound }} / {{ i.majorAccept }}</td><td class="num">{{ i.minorFound }} / {{ i.minorAccept }}</td>
                <td><span class="badge" [class]="i.result === 'ACCEPTED' ? 'badge ok' : 'badge bad'">
                  {{ i.result === 'ACCEPTED' ? 'Aceptado' : 'Rechazado' }}</span></td>
              </tr>
            } @empty {
              <tr><td colspan="10" class="empty">Sin inspecciones.</td></tr>
            }
          </tbody>
        </table>
      </section>
    </section>
  `,
})
export class Quality {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly aqls = AQLS;
  protected readonly levels = LEVELS;
  protected readonly majorPlan = signal<AqlPlan | null>(null);
  protected readonly minorPlan = signal<AqlPlan | null>(null);
  protected readonly sampleSize = computed(() =>
    Math.max(this.majorPlan()?.sampleSize ?? 0, this.minorPlan()?.sampleSize ?? 0),
  );
  protected readonly orders = signal<OrderSummary[]>([]);
  protected readonly lines = signal<{ id: number; code: string; name: string }[]>([]);
  protected readonly defectTypes = signal<DefectType[]>([]);
  protected readonly inspections = signal<AqlInspection[]>([]);
  protected readonly defects = signal<DraftDefect[]>([]);
  protected readonly lastResult = signal<AqlInspection | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly canInspect = computed(() => this.auth.hasRole('ADMIN', 'QUALITY'));

  protected lotSize = 500;
  protected level: InspectionLevel = 'II';
  protected aqlMajor = 2.5;
  protected aqlMinor = 4.0;
  protected orderId: number | null = null;
  protected lineId: number | null = null;
  protected defectiveUnits = 1;

  constructor() {
    this.refreshPlans();
    this.loadInspections();
    this.api.orders().subscribe((orders) => {
      this.orders.set(orders);
      this.orderId = orders.find((o) => o.status === 'IN_PROGRESS')?.id ?? orders[0]?.id ?? null;
    });
    this.api.lines().subscribe((lines) => this.lines.set(lines));
    this.api.defectTypes().subscribe((types) => {
      this.defectTypes.set(types);
      this.defects.set([{ defectTypeCode: 'OPEN_SEAM', quantity: 1, bundleCode: '', operationCode: '' }]);
    });
  }

  protected refreshPlans(): void {
    this.error.set(null);
    const lot = Number(this.lotSize);
    this.api.aqlPlan(lot, this.level, this.aqlMajor).subscribe({
      next: (plan) => this.majorPlan.set(plan),
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.api.aqlPlan(lot, this.level, this.aqlMinor).subscribe((plan) => this.minorPlan.set(plan));
  }

  protected addDefect(): void {
    this.defects.update((list) => [...list, { defectTypeCode: 'LOOSE_THREAD', quantity: 1, bundleCode: '', operationCode: '' }]);
  }

  protected submit(): void {
    if (this.orderId == null) {
      return;
    }
    this.error.set(null);
    this.api
      .createAqlInspection({
        productionOrderId: this.orderId,
        lineId: this.lineId ?? undefined,
        level: this.level,
        lotSize: Number(this.lotSize),
        aqlMajor: this.aqlMajor,
        aqlMinor: this.aqlMinor,
        defectiveUnits: Number(this.defectiveUnits),
        defects: this.defects().map((d) => ({
          defectTypeCode: d.defectTypeCode,
          quantity: Number(d.quantity),
          bundleCode: d.bundleCode.trim() || undefined,
          operationCode: d.operationCode.trim() || undefined,
        })),
      })
      .subscribe({
        next: (result) => {
          this.lastResult.set(result);
          this.loadInspections();
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
  }

  private loadInspections(): void {
    this.api.aqlInspections().subscribe((list) => this.inspections.set(list));
  }
}
