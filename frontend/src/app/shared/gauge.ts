import { Component, computed, input } from '@angular/core';

/** Indicador circular en SVG (sin librerías de gráficos). Colorea según umbrales típicos de OEE. */
@Component({
  selector: 'app-gauge',
  template: `
    <svg viewBox="0 0 120 120" [attr.aria-label]="label() + ' ' + text()" role="img">
      <circle cx="60" cy="60" r="50" class="track" />
      <circle
        cx="60"
        cy="60"
        r="50"
        class="value"
        [class]="tone()"
        [attr.stroke-dasharray]="circumference"
        [attr.stroke-dashoffset]="offset()"
      />
      <text x="60" y="60" class="number">{{ text() }}</text>
      <text x="60" y="80" class="caption">{{ label() }}</text>
    </svg>
  `,
  styles: `
    :host {
      display: block;
      width: 120px;
    }
    circle {
      fill: none;
      stroke-width: 11;
    }
    .track {
      stroke: var(--surface-2);
    }
    .value {
      stroke: var(--primary);
      stroke-linecap: round;
      transform: rotate(-90deg);
      transform-origin: 60px 60px;
      transition: stroke-dashoffset 0.6s ease;
    }
    .value.ok {
      stroke: var(--ok);
    }
    .value.warn {
      stroke: var(--warn);
    }
    .value.bad {
      stroke: var(--bad);
    }
    .number {
      fill: var(--text);
      font-size: 22px;
      font-weight: 700;
      text-anchor: middle;
      dominant-baseline: middle;
    }
    .caption {
      fill: var(--muted);
      font-size: 10px;
      text-anchor: middle;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
  `,
})
export class Gauge {
  readonly value = input.required<number>();
  readonly label = input('OEE');
  /** Umbrales: ≥ good → verde; ≥ fair → ámbar; si no, rojo. */
  readonly good = input(0.65);
  readonly fair = input(0.45);

  protected readonly circumference = 2 * Math.PI * 50;
  protected readonly offset = computed(
    () => this.circumference * (1 - Math.max(0, Math.min(1, this.value() ?? 0))),
  );
  protected readonly text = computed(() => `${Math.round((this.value() ?? 0) * 100)}%`);
  protected readonly tone = computed(() => {
    const v = this.value() ?? 0;
    return v >= this.good() ? 'value ok' : v >= this.fair() ? 'value warn' : 'value bad';
  });
}
