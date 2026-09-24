import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { NumPipe, PctPipe, PlantTimePipe, UsdPipe, errorMessage, plantToday } from '../../core/format';
import { DailyPayroll, OperatorDayDetail, WeeklyPay } from '../../core/models';

const PREMIUM_LABELS: Record<string, string> = {
  NONE: 'Ordinaria',
  NIGHT: 'Nocturna (+25 %)',
  SUPPLEMENTARY: 'Suplementaria (+50 %)',
  SUPPLEMENTARY_NIGHT: 'Suplementaria 00h–06h (+100 %)',
  WEEKEND: 'Fin de semana (+100 %)',
};

/** Destajo diario con piso SBU prorrateado, detalle por lectura y semana con descanso del Art. 53. */
@Component({
  selector: 'app-payroll',
  imports: [FormsModule, UsdPipe, PctPipe, NumPipe, PlantTimePipe],
  template: `
    <section class="page">
      <div class="page-header">
        <div>
          <h1>Pago a destajo</h1>
          @if (payroll(); as p) {
            <p class="muted">
              SBU {{ p.sbu | usd }} ({{ p.legalReference }}) · piso por hora ordinaria {{ p.hourlyFloor | usd }}
              (SBU/240) · recargos del Código del Trabajo, Arts. 49 y 55.
            </p>
          }
        </div>
        <label>Jornada <input type="date" [(ngModel)]="date" (change)="load()" [max]="today" /></label>
      </div>
      @if (error()) { <div class="message error">{{ error() }}</div> }

      @if (payroll(); as p) {
        <div class="grid grid-4" style="margin-bottom: 1rem">
          <div class="card kpi"><span class="label">Total del día</span><span class="value">{{ p.totalPay | usd }}</span></div>
          <div class="card kpi"><span class="label">Complemento al SBU</span><span class="value">{{ p.totalTopUp | usd }}</span>
            <span class="hint">lo que paga la empresa para cumplir el piso</span></div>
          <div class="card kpi" [class.alert]="p.operatorsBelowFloor > 0"><span class="label">Bajo el piso</span>
            <span class="value">{{ p.operatorsBelowFloor }} / {{ p.operators.length }}</span></div>
        </div>

        <div class="card table-wrap">
          <table>
            <thead>
              <tr>
                <th>Operario</th><th class="num">Piezas</th><th class="num">Min. estándar</th><th class="num">Asistencia</th>
                <th class="num">Eficiencia</th><th class="num">Ordinario</th><th class="num">Extra + recargos</th>
                <th class="num">Piso</th><th class="num">Complemento</th><th class="num">Total</th><th></th>
              </tr>
            </thead>
            <tbody>
              @for (o of p.operators; track o.operatorId) {
                <tr [class.alert]="o.belowFloor" class="clickable" (click)="showDetail(o.operatorId)">
                  <td><strong>{{ o.operatorCode }}</strong> <span class="muted">{{ o.operatorName }}</span></td>
                  <td class="num">{{ o.pieces | num }}</td>
                  <td class="num">{{ o.earnedMinutes | num }}</td>
                  <td class="num">{{ o.attendedMinutes | num }} min</td>
                  <td class="num">{{ o.efficiency | pct }}</td>
                  <td class="num">{{ o.ordinaryPay | usd }}</td>
                  <td class="num">{{ o.extraPay + o.premiumPay | usd }}</td>
                  <td class="num">{{ o.floor | usd }}</td>
                  <td class="num">{{ o.topUp | usd }}</td>
                  <td class="num"><strong>{{ o.totalPay | usd }}</strong></td>
                  <td>@if (o.belowFloor) { <span class="badge bad">Bajo SBU</span> } @if (o.weekend) { <span class="badge info">Fin de semana</span> }</td>
                </tr>
              } @empty {
                <tr><td colspan="11" class="empty">Sin actividad en esta jornada.</td></tr>
              }
            </tbody>
          </table>
        </div>
      }

      @if (detail(); as d) {
        <div class="grid grid-2" style="margin-top: 1rem">
          <section class="card">
            <h2>{{ d.summary.operatorCode }} · lecturas del día</h2>
            <div class="table-wrap">
              <table>
                <thead><tr><th>Hora</th><th>Operación</th><th class="num">Piezas</th><th class="num">Tarifa</th>
                  <th class="num">Base</th><th>Recargo</th><th class="num">Monto recargo</th></tr></thead>
                <tbody>
                  @for (l of d.lines; track $index) {
                    <tr>
                      <td>{{ l.scannedAt | plantTime }}</td><td>{{ l.operationCode }}</td><td class="num">{{ l.quantity }}</td>
                      <td class="num">{{ l.rateUsd | usd: 4 }}</td><td class="num">{{ l.baseAmount | usd }}</td>
                      <td>{{ premiums[l.premium] }}</td><td class="num">{{ l.premiumAmount | usd }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </section>
          @if (week(); as w) {
            <section class="card">
              <h2>Semana desde {{ w.weekStart }}</h2>
              <table>
                <thead><tr><th>Día</th><th class="num">Piezas</th><th class="num">Total</th><th></th></tr></thead>
                <tbody>
                  @for (day of w.days; track day.workDate) {
                    <tr [class.alert]="day.belowFloor"><td>{{ day.workDate }}</td><td class="num">{{ day.pieces }}</td>
                      <td class="num">{{ day.totalPay | usd }}</td><td>@if (day.weekend) { <span class="badge info">descanso</span> }</td></tr>
                  }
                  <tr><td colspan="2">Descanso semanal (Art. 53): 2 × {{ w.weeklyRest.dailyRate | usd }}</td>
                    <td class="num">{{ w.weeklyRest.amount | usd }}</td><td></td></tr>
                  <tr><td colspan="2"><strong>Total semana</strong></td><td class="num"><strong>{{ w.weekTotal | usd }}</strong></td><td></td></tr>
                </tbody>
              </table>
            </section>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .kpi.alert {
      border-color: var(--bad);
      background: var(--bad-soft);
    }
  `,
})
export class Payroll {
  private readonly api = inject(ApiService);

  protected readonly today = plantToday();
  protected readonly premiums = PREMIUM_LABELS;
  protected readonly payroll = signal<DailyPayroll | null>(null);
  protected readonly detail = signal<OperatorDayDetail | null>(null);
  protected readonly week = signal<WeeklyPay | null>(null);
  protected readonly error = signal<string | null>(null);
  protected date = this.today;

  constructor() {
    this.load();
  }

  protected load(): void {
    this.detail.set(null);
    this.week.set(null);
    this.api.payroll(this.date).subscribe({
      next: (payroll) => this.payroll.set(payroll),
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected showDetail(operatorId: number): void {
    this.api.payrollDetail(operatorId, this.date).subscribe((detail) => this.detail.set(detail));
    this.api.weeklyPay(operatorId, mondayOf(this.date)).subscribe((week) => this.week.set(week));
  }
}

/** Lunes de la semana de una fecha ISO. */
export function mondayOf(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  const offset = (date.getUTCDay() + 6) % 7;
  date.setUTCDate(date.getUTCDate() - offset);
  return date.toISOString().slice(0, 10);
}
