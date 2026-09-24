import { Pipe, PipeTransform } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from './models';

const PLANT_ZONE = 'America/Guayaquil';

const money = new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD', minimumFractionDigits: 2 });
const percent = new Intl.NumberFormat('es-EC', { style: 'percent', maximumFractionDigits: 1 });
const decimal = new Intl.NumberFormat('es-EC', { maximumFractionDigits: 2 });
const time = new Intl.DateTimeFormat('es-EC', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: PLANT_ZONE,
});
const dateTime = new Intl.DateTimeFormat('es-EC', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: PLANT_ZONE,
});

@Pipe({ name: 'usd' })
export class UsdPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return value == null ? '—' : money.format(value);
  }
}

@Pipe({ name: 'pct' })
export class PctPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return value == null ? '—' : percent.format(value);
  }
}

@Pipe({ name: 'num' })
export class NumPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return value == null ? '—' : decimal.format(value);
  }
}

/** Hora de planta (America/Guayaquil) sin depender de la zona del navegador. */
@Pipe({ name: 'plantTime' })
export class PlantTimePipe implements PipeTransform {
  transform(value: string | null | undefined, withDate = false): string {
    if (!value) {
      return '—';
    }
    return (withDate ? dateTime : time).format(new Date(value));
  }
}

/** Fecha de hoy en la planta, formato ISO (yyyy-mm-dd). */
export function plantToday(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: PLANT_ZONE }).format(now);
}

/** Mensaje legible a partir de un error HTTP con Problem Details (RFC 9457). */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const problem = error.error as ProblemDetail | null;
    if (problem?.errors) {
      return Object.entries(problem.errors)
        .map(([field, message]) => `${field}: ${message}`)
        .join(' · ');
    }
    if (problem?.detail) {
      return problem.detail;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor';
    }
    return `Error ${error.status}`;
  }
  return 'Error inesperado';
}

export const STATUS_LABELS: Record<string, string> = {
  PLANNED: 'Planificada',
  CUTTING: 'En corte',
  IN_PROGRESS: 'En confección',
  COMPLETED: 'Terminada',
  CANCELLED: 'Cancelada',
  RECEIVED: 'Por inspeccionar',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
  EXHAUSTED: 'Agotado',
  ACCEPTED: 'Aceptado',
};

export const STOP_REASONS: Record<string, string> = {
  MECHANICAL: 'Falla mecánica',
  ELECTRICAL: 'Falla eléctrica',
  NO_MATERIAL: 'Falta de material',
  NO_OPERATOR: 'Sin operario',
  CHANGEOVER: 'Cambio de estilo',
  PLANNED_MAINTENANCE: 'Mantenimiento planificado',
  MEETING: 'Reunión',
  OTHER: 'Otro',
};
