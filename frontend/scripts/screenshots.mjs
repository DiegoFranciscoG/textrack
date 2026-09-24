// Capturas para el README: npm run screenshots (requiere el stack en marcha y E2E_VIEWER_PASSWORD).
// Usa el usuario de solo lectura de la demo; guarda PNG en ../docs/img.
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';

const base = process.env.E2E_BASE_URL ?? 'http://localhost:8088';
const password = process.env.E2E_VIEWER_PASSWORD;
if (!password) {
  console.error('Define E2E_VIEWER_PASSWORD');
  process.exit(1);
}
const out = new URL('../../docs/img/', import.meta.url);
mkdirSync(out, { recursive: true });
const file = (name) => new URL(name, out).pathname.replace(/^\/([A-Za-z]:)/, '$1');

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 1440, height: 960 },
  locale: 'es-EC',
  timezoneId: 'America/Guayaquil',
  colorScheme: 'light',
});
const page = await context.newPage();

await page.goto(`${base}/login`);
await page.screenshot({ path: file('login.png') });
await page.getByLabel('Correo').fill('visor@textrack.demo');
await page.getByLabel('Contraseña').fill(password);
await page.getByRole('button', { name: 'Ingresar' }).click();
await page.getByText('En vivo', { exact: true }).waitFor({ timeout: 20000 });
await page.waitForTimeout(800);
await page.screenshot({ path: file('dashboard.png') });

await page.getByRole('link', { name: 'Órdenes y cortes' }).click();
await page.getByRole('heading', { name: 'Órdenes de producción' }).waitFor();
await page.screenshot({ path: file('orders.png') });
await page.getByRole('link', { name: 'ORD-2026-0003' }).click();
await page.getByRole('heading', { name: 'Cuellos de botella por operación' }).waitFor();
await page.getByRole('link', { name: /CT-/ }).first().click();
await page.getByRole('heading', { name: /Bultos del corte/ }).waitFor();
await page.getByRole('heading', { name: 'Cuellos de botella por operación' }).scrollIntoViewIfNeeded();
await page.screenshot({ path: file('bottlenecks.png') });
const bundle = (await page.locator('tbody code').first().textContent())?.trim();

await page.getByRole('link', { name: 'Rollos · 4 puntos' }).click();
await page.getByRole('heading', { name: /Rollos de tela/ }).waitFor();
await page.screenshot({ path: file('rolls.png') });

await page.getByRole('link', { name: 'Destajo' }).click();
await page.getByRole('heading', { name: 'Pago a destajo' }).waitFor();
await page.locator('tr.alert').first().click();
await page.getByRole('heading', { name: /lecturas del día/ }).waitFor();
await page.waitForTimeout(500);
await page.screenshot({ path: file('payroll.png'), fullPage: true });

await page.getByRole('link', { name: 'Calidad AQL' }).click();
await page.getByRole('heading', { name: 'Calidad · muestreo AQL' }).waitFor();
await page.waitForTimeout(800);
await page.screenshot({ path: file('quality.png') });

await page.getByRole('link', { name: 'Trazabilidad' }).click();
await page.getByPlaceholder('Serie de prenda, bulto o rollo').fill(`${bundle}-07`);
await page.getByRole('button', { name: 'Buscar' }).click();
await page.getByRole('heading', { name: 'Quién cosió cada operación' }).waitFor();
await page.screenshot({ path: file('traceability.png') });

await page.getByRole('link', { name: 'Ingeniería (SAM)' }).click();
await page.getByRole('heading', { name: 'Ingeniería de métodos' }).waitFor();
await page.waitForTimeout(500);
await page.screenshot({ path: file('engineering.png') });

// Vista móvil del tablero
await page.setViewportSize({ width: 390, height: 844 });
await page.goto(`${base}/`);
await page.getByRole('heading', { name: 'Tablero en vivo' }).waitFor({ timeout: 20000 }).catch(() => undefined);
await page.waitForTimeout(1500);
await page.screenshot({ path: file('dashboard-mobile.png') });

// Documentación OpenAPI de la API
await page.setViewportSize({ width: 1440, height: 960 });
await page.goto(`${process.env.E2E_API_URL ?? 'http://localhost:8081'}/swagger-ui.html`);
await page.getByText('textrack API').first().waitFor({ timeout: 20000 });
await page.waitForTimeout(1000);
await page.screenshot({ path: file('swagger.png') });

await page.goto('about:blank');
await browser.close();
console.log('Capturas guardadas en docs/img');
