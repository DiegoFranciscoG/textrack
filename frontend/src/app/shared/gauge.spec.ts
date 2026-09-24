import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Gauge } from './gauge';

describe('Gauge', () => {
  function render(value: number) {
    const fixture = TestBed.createComponent(Gauge);
    fixture.componentRef.setInput('value', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra el porcentaje y colorea según el umbral de OEE', () => {
    expect(render(0.72).querySelector('.number')?.textContent).toBe('72%');
    expect(render(0.72).querySelector('circle.value')?.getAttribute('class')).toContain('ok');
    expect(render(0.5).querySelector('circle.value')?.getAttribute('class')).toContain('warn');
    expect(render(0.2).querySelector('circle.value')?.getAttribute('class')).toContain('bad');
  });

  it('acota valores fuera de rango', () => {
    const svg = render(1.4);
    expect(svg.querySelector('circle.value')?.getAttribute('stroke-dashoffset')).toBe('0');
  });
});
