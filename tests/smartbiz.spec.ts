import { test, expect, type Page } from '@playwright/test';

async function login(page: Page, email = 'admin@smartbiz.local') {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill('DemoSmartBiz2026!');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Inventario', exact: true })).toBeVisible();
}

test('CP-P0: alta, filtros, carrito editable, cobro, recibo e inventario persistido', async ({ page }) => {
  await login(page);
  const suffix = Date.now().toString();
  const category = `Prueba ${suffix}`;
  const name = `Producto ${suffix}`;
  const code = `E2E-${suffix}`;
  await page.getByRole('button', { name: 'Categoría', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Nombre', { exact: true }).fill(category);
  await page.getByRole('button', { name: 'Guardar', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('button', { name: 'Nuevo producto', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Nombre', { exact: true }).fill(name);
  await dialog.getByLabel('Código', { exact: true }).fill(code);
  await dialog.getByRole('combobox', { name: 'Categoría', exact: true }).selectOption({ label: category });
  await dialog.getByLabel('Precio (COP)', { exact: true }).fill('1500');
  await dialog.getByLabel('Existencias iniciales').fill('10');
  await dialog.getByRole('button', { name: 'Guardar', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  await page.getByLabel('Buscar por nombre o código').fill(code);
  await expect(page.getByRole('row').filter({ hasText: name })).toContainText('10 unidades');
  await page.getByLabel('Filtrar por categoría').selectOption({ label: category });
  await expect(page.getByRole('row').filter({ hasText: name })).toBeVisible();
  await page.getByRole('button', { name: 'Caja', exact: true }).click();
  await page.getByLabel('Buscar por nombre o código').fill(code);
  await page.getByRole('button', { name: `Agregar ${name}`, exact: true }).click();
  await page.getByLabel(`Cantidad de ${name}`).fill('3');
  await page.getByLabel(`Cantidad de ${name}`).fill('2');
  await page.getByLabel('Efectivo recibido (COP)').fill('5000');
  await page.getByRole('button', { name: 'Revisar y cobrar' }).click();
  await page.getByRole('button', { name: 'Confirmar pago en efectivo' }).click();
  await expect(page.getByRole('heading', { name: '¡Venta realizada!' })).toBeVisible();
  await expect(page.getByRole('dialog')).toContainText(name);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar TXT' }).click();
  expect((await download).suggestedFilename()).toMatch(/^smartbiz-.*\.txt$/);
  await page.getByRole('button', { name: 'Cerrar ventana' }).click();
  await page.getByRole('button', { name: 'Inventario', exact: true }).click();
  await page.getByLabel('Buscar por nombre o código').fill(code);
  await expect(page.getByRole('row').filter({ hasText: name })).toContainText('8 unidades');
  await page.reload();
  await page.getByLabel('Buscar por nombre o código').fill(code);
  await expect(page.getByRole('row').filter({ hasText: name })).toContainText('8 unidades');
  await page.getByRole('button', { name: 'Historial de ventas', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Ver recibo' }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Ver recibo' }).first().click();
  await expect(page.getByRole('dialog')).toContainText(name);
  await page.getByRole('button', { name: 'Cerrar ventana' }).click();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Inicia sesión' })).toBeVisible();
});

test('CP-P0: rol bodega y consulta responsive sin opciones de caja', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, 'bodega@smartbiz.local');
  await expect(page.getByRole('button', { name: 'Nuevo producto', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Caja', exact: true })).toHaveCount(0);
  await page.getByLabel('Buscar por nombre o código').fill('NO-EXISTE-' + Date.now());
  await expect(page.getByRole('heading', { name: 'No hay productos para mostrar' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});

test('CP-P0: credenciales invalidas y permisos del cajero', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('caja@smartbiz.local');
  await page.getByLabel('Contraseña', { exact: true }).fill('incorrecta');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByLabel('Contraseña', { exact: true }).fill('DemoSmartBiz2026!');
  await page.getByRole('button', { name: 'Iniciar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Inventario', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Nuevo producto', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Caja', exact: true })).toBeVisible();
});
