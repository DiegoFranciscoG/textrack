import { InjectionToken } from '@angular/core';

/**
 * Configuración leída en tiempo de ejecución desde /config.json. Permite usar el mismo build en Docker
 * (API en el mismo origen, detrás de nginx) y en Vercel (API en Render), sin recompilar.
 */
export interface RuntimeConfig {
  /** URL base de la API sin barra final; vacío = mismo origen. */
  apiUrl: string;
}

export const RUNTIME_CONFIG = new InjectionToken<RuntimeConfig>('RUNTIME_CONFIG');

export async function loadRuntimeConfig(): Promise<RuntimeConfig> {
  try {
    const response = await fetch('/config.json', { cache: 'no-store' });
    if (response.ok) {
      const config = (await response.json()) as Partial<RuntimeConfig>;
      return { apiUrl: (config.apiUrl ?? '').replace(/\/+$/, '') };
    }
  } catch {
    // sin config.json se usa el mismo origen
  }
  return { apiUrl: '' };
}

/** ws(s)://host/ws a partir de la URL de la API o del origen actual. */
export function webSocketUrl(apiUrl: string, location: Location): string {
  const base = apiUrl || `${location.protocol}//${location.host}`;
  return base.replace(/^http/, 'ws') + '/ws';
}
