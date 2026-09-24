import { Page, expect, test } from '@playwright/test';

const viewerEmail = process.env['E2E_VIEWER_EMAIL'] ?? 'visor@textrack.demo';
const viewerPassword = process.env['E2E_VIEWER_PASSWORD'] ?? '';

/**
 * Una sola sesión para todo el flujo: el login tiene rate limiting (5 intentos por minuto por IP y usuario), así
 * que cada test reutiliza la misma página en lugar de volver a autenticarse.
 */
test.describe.configure({ mode: 'serial' });

test.describe('Flujo crítico con usuario de solo lectura', () => {
  test.skip(!viewerPassword, 'Define E2E_VIEWER_PASSWORD para ejecutar los e2e');

  let page: Page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage();
  });

  test.afterAll(async () => {
    await page.goto('about:blank');
    await page.close();
  });

  test('credenciales incorrectas muestran un error genérico', async () => {
    await page.goto('/login');
    await page.getByLabel('Correo').fill(viewerEmail);
    await page.getByLabel('Contraseña').fill('incorrecta');
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(page.getByRole('alert')).toContainText('Credenciales inválidas');
  });

  test('el tablero muestra OEE por línea y se conecta en vivo por WebSocket', async () => {
    await page.getByLabel('Contraseña').fill(viewerPassword);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(page.getByRole('heading', { name: 'Tablero en vivo' })).toBeVisible();
    await expect(page.getByText('OEE planta')).toBeVisible();
    await expect(page.locator('app-gauge').first()).toBeVisible();
    await expect(page.getByText('En vivo', { exact: true })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('heading', { name: /Operarios/ })).toBeVisible();
  });

  test('trazabilidad de una prenda hasta el rollo y el lote de teñido', async () => {
    await page.getByRole('link', { name: 'Órdenes y cortes' }).click();
    await page.getByRole('link', { name: /ORD-/ }).last().click();
    await page.getByRole('link', { name: /CT-/ }).first().click();
    const bundle = (await page.locator('tbody code').first().textContent())?.trim() ?? '';
    expect(bundle).toMatch(/^CT-\d{5}-\d{3}$/);

    await page.getByRole('link', { name: 'Trazabilidad' }).click();
    await page.getByPlaceholder('Serie de prenda, bulto o rollo').fill(`${bundle}-01`);
    await page.getByRole('button', { name: 'Buscar' }).click();
    await expect(page.getByText('Lote de teñido')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Quién cosió cada operación' })).toBeVisible();
  });

  test('el visor no ve acciones de escritura', async () => {
    await page.getByRole('link', { name: 'Órdenes y cortes' }).click();
    await expect(page.getByRole('heading', { name: 'Órdenes de producción' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Nueva orden' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Registrar lectura' })).toHaveCount(0);
  });
});
