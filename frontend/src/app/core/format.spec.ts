import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { PctPipe, PlantTimePipe, UsdPipe, errorMessage, plantToday } from './format';
import { webSocketUrl } from './runtime-config';
import { mondayOf } from '../pages/payroll/payroll';

describe('format', () => {
  it('usa la fecha de la planta (America/Guayaquil), no la del navegador', () => {
    // 02:00 UTC del 24 de septiembre = 21:00 del 23 en Guayaquil
    expect(plantToday(new Date('2026-09-24T02:00:00Z'))).toBe('2026-09-23');
  });

  it('formatea moneda y porcentaje, y usa guion para valores ausentes', () => {
    expect(new UsdPipe().transform(16.07)).toContain('16,07');
    expect(new PctPipe().transform(0.7231)).toContain('72,3');
    expect(new UsdPipe().transform(null)).toBe('—');
    expect(new UsdPipe().transform(0.0275, 4)).toContain('0,0275');
  });

  it('muestra la hora de planta', () => {
    expect(new PlantTimePipe().transform('2026-09-23T15:30:00Z')).toBe('10:30');
  });

  it('traduce Problem Details a un mensaje legible', () => {
    const invalid = new HttpErrorResponse({ status: 400, error: { errors: { code: 'no válido' } } });
    const rule = new HttpErrorResponse({ status: 422, error: { detail: 'El rollo no está aprobado' } });
    const offline = new HttpErrorResponse({ status: 0 });

    expect(errorMessage(invalid)).toBe('code: no válido');
    expect(errorMessage(rule)).toBe('El rollo no está aprobado');
    expect(errorMessage(offline)).toBe('No hay conexión con el servidor');
    expect(errorMessage(new Error('x'))).toBe('Error inesperado');
  });

  it('construye la URL del WebSocket desde la API o el origen', () => {
    const location = { protocol: 'https:', host: 'textrack.example' } as Location;
    expect(webSocketUrl('', location)).toBe('wss://textrack.example/ws');
    expect(webSocketUrl('http://localhost:8081', location)).toBe('ws://localhost:8081/ws');
  });

  it('calcula el lunes de la semana para el pago del descanso semanal', () => {
    expect(mondayOf('2026-09-23')).toBe('2026-09-21');
    expect(mondayOf('2026-09-21')).toBe('2026-09-21');
    expect(mondayOf('2026-09-27')).toBe('2026-09-21');
  });
});
