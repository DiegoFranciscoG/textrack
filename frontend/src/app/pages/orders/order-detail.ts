import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { NumPipe, PlantTimePipe, STATUS_LABELS, errorMessage } from '../../core/format';
import { BottleneckReport, CutDetail, OrderDetail, Roll } from '../../core/models';

@Component({
  selector: 'app-order-detail',
  imports: [FormsModule, RouterLink, NumPipe, PlantTimePipe],
  templateUrl: './order-detail.html',
  styles: `
    .progress {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 1rem;
    }
    .bottleneck td {
      background: var(--warn-soft);
      font-weight: 600;
    }
    .load {
      display: grid;
      grid-template-columns: 120px 60px;
      gap: 0.5rem;
      align-items: center;
    }
    .ratio {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
    .ratio label {
      width: 80px;
    }
  `,
})
export class OrderDetailPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly labels = STATUS_LABELS;
  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly report = signal<BottleneckReport | null>(null);
  protected readonly rolls = signal<Roll[]>([]);
  protected readonly selectedCut = signal<CutDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly canCut = computed(() => this.auth.hasRole('ADMIN', 'PLANNER'));
  protected readonly colors = computed(() => [...new Set(this.order()?.lines.map((l) => l.color) ?? [])]);
  protected readonly maxRemaining = computed(() =>
    Math.max(1, ...(this.report()?.operations.map((o) => o.remainingMinutes ?? 0) ?? [1])),
  );

  // formulario de corte
  protected readonly cutColor = signal('');
  protected bundleSize = 20;
  protected ratios: Record<string, number> = {};
  protected rollId: number | null = null;
  protected plies = 50;
  protected meters = 55;

  protected readonly availableRolls = computed(() =>
    this.rolls().filter((r) => r.status === 'APPROVED' && r.color.toLowerCase() === this.cutColor().toLowerCase()),
  );

  ngOnInit(): void {
    this.load();
  }

  protected sizesFor(color: string): string[] {
    return this.order()?.lines.filter((l) => l.color === color).map((l) => l.sizeCode) ?? [];
  }

  protected selectColor(color: string): void {
    this.cutColor.set(color);
    this.ratios = Object.fromEntries(this.sizesFor(color).map((s) => [s, 1]));
    this.rollId = this.availableRolls()[0]?.id ?? null;
  }

  protected piecesPreview(): number {
    return Object.values(this.ratios).reduce((sum, r) => sum + Number(r || 0), 0) * Number(this.plies || 0);
  }

  protected showCut(cutId: number): void {
    this.api.cut(cutId).subscribe((cut) => this.selectedCut.set(cut));
  }

  protected createCut(): void {
    if (this.rollId == null) {
      return;
    }
    this.error.set(null);
    const sizeRatios = Object.entries(this.ratios)
      .filter(([, value]) => Number(value) > 0)
      .map(([sizeCode, piecesPerPly]) => ({ sizeCode, piecesPerPly: Number(piecesPerPly) }));
    this.api
      .createCut(Number(this.id()), {
        color: this.cutColor(),
        maxBundleSize: Number(this.bundleSize),
        sizeRatios,
        rolls: [{ rollId: this.rollId, plies: Number(this.plies), metersUsed: Number(this.meters) }],
      })
      .subscribe({
        next: (cut) => {
          this.message.set(`Corte ${cut.code}: ${cut.bundles.length} bultos y ${cut.tickets} tickets firmados.`);
          this.selectedCut.set(cut);
          this.load();
        },
        error: (e) => this.error.set(errorMessage(e)),
      });
  }

  protected downloadTickets(cutId: number, code: string): void {
    this.api.ticketsPdf(cutId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `tickets-${code}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
  }

  protected loadWidth(minutes: number | undefined): string {
    return `${Math.min(100, ((minutes ?? 0) / this.maxRemaining()) * 100)}%`;
  }

  private load(): void {
    const id = Number(this.id());
    this.api.order(id).subscribe({
      next: (order) => {
        this.order.set(order);
        if (!this.cutColor() && order.lines.length) {
          this.api.rolls().subscribe((rolls) => {
            this.rolls.set(rolls);
            this.selectColor(order.lines[0].color);
          });
        }
      },
      error: (e) => this.error.set(errorMessage(e)),
    });
    this.api.bottlenecks(id).subscribe((report) => this.report.set(report));
  }
}
