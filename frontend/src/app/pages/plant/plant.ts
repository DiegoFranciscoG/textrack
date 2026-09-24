import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { PlantTimePipe, STOP_REASONS, errorMessage } from '../../core/format';
import { Attendance, Machine, MachineStop, Operator } from '../../core/models';

/** Asistencia (minutos reloj para eficiencia y piso SBU) y paros planificados/no planificados (OEE). */
@Component({
  selector: 'app-plant',
  imports: [FormsModule, PlantTimePipe],
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Asistencia y paros de máquina</h1>
          <p class="muted">Los paros planificados reducen el tiempo ocupado planificado (PBT); los no planificados, la disponibilidad.</p>
        </div>
      </div>
      @if (error()) { <div class="message error">{{ error() }}</div> }

      <div class="grid grid-2">
        <section class="card table-wrap">
          <h2>Asistencia de hoy</h2>
          <table>
            <thead><tr><th>Operario</th><th>Entrada</th><th>Salida</th><th></th></tr></thead>
            <tbody>
              @for (o of operators(); track o.id) {
                @let a = attendanceOf(o.id);
                <tr>
                  <td><strong>{{ o.code }}</strong> {{ o.fullName }}</td>
                  <td>{{ a?.checkIn | plantTime }}</td>
                  <td>{{ a?.checkOut | plantTime }}</td>
                  <td>
                    @if (canManage()) {
                      @if (!a) { <button class="small" type="button" (click)="checkIn(o.id)">Entrada</button> }
                      @else if (!a.checkOut) { <button class="small" type="button" (click)="checkOut(a.id)">Salida</button> }
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </section>

        <section class="card stack">
          <h2>Paros de hoy</h2>
          @if (canManage()) {
            <form class="form-grid" (ngSubmit)="reportStop()">
              <label>Máquina
                <select name="machine" [(ngModel)]="machineId" required>
                  @for (m of machines(); track m.id) { <option [ngValue]="m.id">{{ m.code }} · {{ m.lineCode }}</option> }
                </select>
              </label>
              <label>Motivo
                <select name="reason" [(ngModel)]="reason">
                  @for (r of reasonKeys; track r) { <option [value]="r">{{ reasons[r] }}</option> }
                </select>
              </label>
              <label class="check"><input name="planned" type="checkbox" [(ngModel)]="planned" /> Planificado</label>
              <button class="primary" type="submit">Iniciar paro</button>
            </form>
          }
          <table>
            <thead><tr><th>Máquina</th><th>Motivo</th><th>Inicio</th><th>Fin</th><th></th></tr></thead>
            <tbody>
              @for (s of stops(); track s.id) {
                <tr>
                  <td>{{ s.machineCode }}</td>
                  <td><span class="badge" [class]="s.planned ? 'badge info' : 'badge bad'">{{ reasons[s.reason] }}</span></td>
                  <td>{{ s.startedAt | plantTime }}</td>
                  <td>{{ s.endedAt | plantTime }}</td>
                  <td>@if (!s.endedAt && canManage()) { <button class="small" type="button" (click)="closeStop(s.id)">Cerrar</button> }</td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="muted">Sin paros hoy.</td></tr>
              }
            </tbody>
          </table>
        </section>
      </div>
    </section>
  `,
  styles: `
    .check {
      flex-direction: row;
      align-items: center;
    }
  `,
})
export class Plant {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly reasons = STOP_REASONS;
  protected readonly reasonKeys = Object.keys(STOP_REASONS);
  protected readonly operators = signal<Operator[]>([]);
  protected readonly machines = signal<Machine[]>([]);
  protected readonly attendances = signal<Attendance[]>([]);
  protected readonly stops = signal<MachineStop[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly canManage = computed(() => this.auth.hasRole('ADMIN', 'SUPERVISOR'));

  protected machineId: number | null = null;
  protected reason = 'MECHANICAL';
  protected planned = false;

  constructor() {
    this.api.operators().subscribe((ops) => this.operators.set(ops));
    this.api.machines().subscribe((machines) => {
      this.machines.set(machines);
      this.machineId = machines[0]?.id ?? null;
    });
    this.reload();
  }

  protected attendanceOf(operatorId: number): Attendance | undefined {
    return this.attendances().find((a) => a.operatorId === operatorId);
  }

  protected checkIn(operatorId: number): void {
    this.api.checkIn(operatorId).subscribe({ next: () => this.reload(), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected checkOut(attendanceId: number): void {
    this.api.checkOut(attendanceId).subscribe({ next: () => this.reload(), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected reportStop(): void {
    if (this.machineId == null) {
      return;
    }
    this.api
      .reportStop({ machineId: this.machineId, reason: this.reason, planned: this.planned })
      .subscribe({ next: () => this.reload(), error: (e) => this.error.set(errorMessage(e)) });
  }

  protected closeStop(id: number): void {
    this.api.closeStop(id).subscribe({ next: () => this.reload(), error: (e) => this.error.set(errorMessage(e)) });
  }

  private reload(): void {
    this.error.set(null);
    this.api.attendances().subscribe((list) => this.attendances.set(list));
    this.api.stops().subscribe((list) => this.stops.set(list));
  }
}
