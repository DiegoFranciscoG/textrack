import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra un stack en ejecución (docker compose o ng serve + API). Las credenciales de demo llegan por
 * variables de entorno: E2E_VIEWER_EMAIL / E2E_VIEWER_PASSWORD (solo lectura) y E2E_STAFF_PASSWORD.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4300',
    trace: 'retain-on-failure',
    locale: 'es-EC',
    timezoneId: 'America/Guayaquil',
  },
  projects: [
    {
      name: 'desktop',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1440, height: 900 },
        // Opcional: PW_CHANNEL=msedge|chrome usa un navegador instalado en lugar del Chromium de Playwright.
        channel: process.env['PW_CHANNEL'] || undefined,
      },
    },
  ],
});
