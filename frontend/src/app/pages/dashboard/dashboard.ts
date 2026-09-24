import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api.service';
import { DashboardSocketService } from '../../core/dashboard-socket.service';
import { NumPipe, PctPipe, PlantTimePipe, STOP_REASONS, UsdPipe, errorMessage } from '../../core/format';
import { DashboardSnapshot, OperatorKpi } from '../../core/models';
import { Gauge } from '../../shared/gauge';

@Component({
  selector: 'app-dashboard',
  imports: [Gauge, PctPipe, UsdPipe, NumPipe, PlantTimePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(ApiService);
  protected readonly socket = inject(DashboardSocketService);

  private readonly initial = signal<DashboardSnapshot | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly data = computed(() => this.socket.snapshot() ?? this.initial());
  protected readonly flash = signal(false);
  protected readonly reasons = STOP_REASONS;

  /** Operarios bajo el piso primero, luego por eficiencia descendente. */
  protected readonly operators = computed<OperatorKpi[]>(() =>
    [...(this.data()?.operators ?? [])].sort(
      (a, b) => Number(b.belowFloor) - Number(a.belowFloor) || (b.efficiency ?? 0) - (a.efficiency ?? 0),
    ),
  );

  constructor() {
    this.api.dashboard().subscribe({
      next: (snapshot) => this.initial.set(snapshot),
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.socket.connect(inject(DestroyRef));
    effect(() => {
      if (this.socket.snapshot()) {
        this.flash.set(true);
        setTimeout(() => this.flash.set(false), 900);
      }
    });
  }

  protected tone(value: number | undefined, good = 0.85, fair = 0.65): string {
    const v = value ?? 0;
    return v >= good ? 'ok' : v >= fair ? 'warn' : 'bad';
  }

  protected width(value: number | undefined): string {
    return `${Math.max(0, Math.min(100, (value ?? 0) * 100))}%`;
  }
}
