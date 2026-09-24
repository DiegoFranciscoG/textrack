import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, CreateRoll } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NumPipe, PlantTimePipe, STATUS_LABELS, errorMessage } from '../../core/format';
import { FabricInspectionResult, Roll } from '../../core/models';

interface DraftDefect {
  positionM: number;
  lengthMm: number;
  hole: boolean;
}

/** Recepción de rollos e inspección con el sistema de 4 puntos (ASTM D5430). */
@Component({
  selector: 'app-rolls',
  imports: [FormsModule, NumPipe, PlantTimePipe],
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Rollos de tela · sistema de 4 puntos</h1>
          <p class="muted">
            Puntos por defecto: ≤ 75 mm → 1 · ≤ 150 mm → 2 · ≤ 230 mm → 3 · mayor o agujero → 4 (máx. 4 por yarda).
            Un rollo rechazado no se puede usar en un corte.
          </p>
        </div>
        @if (canReceive()) {
          <button class="primary" type="button" (click)="showReceive.set(!showReceive())">Recibir rollo</button>
        }
      </div>

      @if (message()) { <div class="message success">{{ message() }}</div> }
      @if (error()) { <div class="message error">{{ error() }}</div> }

      @if (showReceive()) {
        <form class="card form-grid" style="margin-bottom: 1rem" (ngSubmit)="receive()">
          <label>Código <input name="code" required pattern="[A-Z0-9][A-Z0-9-]{1,29}" [(ngModel)]="draft.code" /></label>
          <label>Proveedor <input name="supplier" required [(ngModel)]="draft.supplier" /></label>
          <label>Lote de teñido <input name="dyeLot" required [(ngModel)]="draft.dyeLot" /></label>
          <label>Color <input name="color" required [(ngModel)]="draft.color" /></label>
          <label>Largo (m) <input name="length" type="number" min="1" [(ngModel)]="draft.lengthM" /></label>
          <label>Ancho (cm) <input name="width" type="number" min="30" [(ngModel)]="draft.widthCm" /></label>
          <button class="primary" type="submit">Guardar</button>
        </form>
      }

      <div class="grid" [class.split]="inspecting()">
        <div class="card table-wrap">
          <table>
            <thead>
              <tr><th>Rollo</th><th>Lote</th><th>Color</th><th class="num">Metros</th><th class="num">Pts/100 yd²</th>
                <th>Estado</th><th></th></tr>
            </thead>
            <tbody>
              @for (r of rolls(); track r.id) {
                <tr>
                  <td><strong>{{ r.code }}</strong> <span class="muted">{{ r.supplier }}</span></td>
                  <td>{{ r.dyeLot }}</td>
                  <td>{{ r.color }}</td>
                  <td class="num">{{ r.remainingM | num }} / {{ r.lengthM | num }}</td>
                  <td class="num">{{ r.pointsPer100SqYd | num }}</td>
                  <td><span class="badge" [class]="'badge ' + tone(r.status)">{{ labels[r.status] }}</span></td>
                  <td>
                    @if (r.status === 'RECEIVED' && canInspect()) {
                      <button class="small" type="button" (click)="startInspection(r)">Inspeccionar</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>

        @if (inspecting(); as roll) {
          <form class="card stack" (ngSubmit)="inspect(roll)">
            <h2>Inspección de {{ roll.code }}</h2>
            <div class="form-grid">
              <label>Metros inspeccionados <input name="len" type="number" min="1" [max]="roll.lengthM" [(ngModel)]="inspectedLength" /></label>
              <label>Ancho útil (cm) <input name="w" type="number" min="30" [(ngModel)]="width" /></label>
            </div>
            <h3>Defectos</h3>
            @for (d of defects(); track $index) {
              <div class="form-grid">
                <label>Posición (m) <input [name]="'p' + $index" type="number" min="0" step="0.1" [(ngModel)]="d.positionM" /></label>
                <label>Largo (mm) <input [name]="'l' + $index" type="number" min="1" [(ngModel)]="d.lengthMm" /></label>
                <label class="check"><input [name]="'h' + $index" type="checkbox" [(ngModel)]="d.hole" /> Agujero</label>
                <button class="small" type="button" (click)="removeDefect($index)">Quitar</button>
              </div>
            }
            <div class="row">
              <button type="button" (click)="addDefect()">+ Defecto</button>
              <button class="primary" type="submit">Calificar rollo</button>
              <button type="button" (click)="inspecting.set(null)">Cancelar</button>
            </div>
            @if (result(); as res) {
              <div class="message" [class]="res.accepted ? 'message success' : 'message error'">
                {{ res.totalPoints }} puntos · {{ res.pointsPer100SqYd | num }} pts/100 yd² (máximo {{ res.maxPointsAllowed }}):
                rollo {{ res.accepted ? 'APROBADO' : 'RECHAZADO' }}.
              </div>
            }
          </form>
        }
      </div>
      <p class="muted">Recibidos: {{ rolls().length }} · última recepción {{ rolls()[0]?.receivedAt | plantTime: true }}</p>
    </section>
  `,
  styles: `
    .split {
      grid-template-columns: minmax(0, 3fr) minmax(300px, 2fr);
      align-items: start;
    }
    .check {
      flex-direction: row;
      align-items: center;
    }
    @media (max-width: 1000px) {
      .split {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class Rolls {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly labels = STATUS_LABELS;
  protected readonly rolls = signal<Roll[]>([]);
  protected readonly showReceive = signal(false);
  protected readonly inspecting = signal<Roll | null>(null);
  protected readonly defects = signal<DraftDefect[]>([]);
  protected readonly result = signal<FabricInspectionResult | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly canReceive = computed(() => this.auth.hasRole('ADMIN', 'PLANNER'));
  protected readonly canInspect = computed(() => this.auth.hasRole('ADMIN', 'QUALITY'));

  protected draft: CreateRoll = { code: '', supplier: '', dyeLot: '', color: '', lengthM: 100, widthCm: 160 };
  protected inspectedLength = 0;
  protected width = 0;

  constructor() {
    this.load();
  }

  protected tone(status: string): string {
    return status === 'APPROVED' ? 'ok' : status === 'REJECTED' ? 'bad' : status === 'RECEIVED' ? 'warn' : '';
  }

  protected receive(): void {
    this.error.set(null);
    this.api.receiveRoll({ ...this.draft, code: this.draft.code.toUpperCase() }).subscribe({
      next: (roll) => {
        this.message.set(`Rollo ${roll.code} recibido. Pendiente de inspección.`);
        this.showReceive.set(false);
        this.load();
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected startInspection(roll: Roll): void {
    this.inspecting.set(roll);
    this.inspectedLength = roll.lengthM;
    this.width = roll.widthCm;
    this.defects.set([{ positionM: 10, lengthMm: 60, hole: false }]);
    this.result.set(null);
  }

  protected addDefect(): void {
    this.defects.update((list) => [...list, { positionM: 0, lengthMm: 50, hole: false }]);
  }

  protected removeDefect(index: number): void {
    this.defects.update((list) => list.filter((_, i) => i !== index));
  }

  protected inspect(roll: Roll): void {
    this.error.set(null);
    this.api
      .inspectRoll(roll.id, {
        inspectedLengthM: Number(this.inspectedLength),
        widthCm: Number(this.width),
        defects: this.defects().map((d) => ({
          positionM: Number(d.positionM),
          lengthMm: Number(d.lengthMm),
          hole: d.hole,
        })),
      })
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.load();
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
  }

  private load(): void {
    this.api.rolls().subscribe((rolls) => this.rolls.set(rolls));
  }
}
