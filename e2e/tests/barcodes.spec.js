import { expect, test } from '@playwright/test';
import { api, registerAndLogIn } from './support/account.js';

// Link-on-first-scan through the real ui's nginx, api and MongoDB: an unknown
// code is a 404 the scanner turns into "which product is this?", linking it
// makes the next scan find the product, and a code is never silently moved to
// a second product. There is no barcode ui yet (E2), so this drives the api
// from inside a logged-in page.

test.describe.configure({ mode: 'serial' });

// A GS1 Bulgaria (380) EAN-13, and the same pack as a UPC-A would report it
const EAN = '3800178226137';
const UPC_A = '036000291452';

let page;
let milk;
let bread;

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage();
  await registerAndLogIn(page, 'barcodes');
  // Product names carry the run's time: products are a shared catalog
  const stamp = Date.now();
  milk = (await api(page, 'POST', `/api/products?name=${encodeURIComponent(`E2E Мляко ${stamp}`)}`)).body;
  bread = (await api(page, 'POST', `/api/products?name=${encodeURIComponent(`E2E Хляб ${stamp}`)}`)).body;
});

test.afterAll(async () => {
  await page.close();
});

test('an unknown barcode is not linked yet', async () => {
  const lookup = await api(page, 'GET', `/api/products/by-barcode/${EAN}`);

  expect(lookup.status).toBe(404);
  expect(lookup.body.error).toBe('BARCODE_NOT_LINKED');
});

test('the first scan links it, the next scan finds the product', async () => {
  const link = await api(page, 'PUT', `/api/products/${milk.id}/barcodes/${EAN}`);
  expect(link.status).toBe(200);
  expect(link.body.barcodes).toEqual([EAN]);

  const lookup = await api(page, 'GET', `/api/products/by-barcode/${EAN}`);
  expect(lookup.status).toBe(200);
  expect(lookup.body).toMatchObject({ id: milk.id, name: milk.name, barcodes: [EAN] });
});

test('replaying the same link is harmless', async () => {
  const again = await api(page, 'PUT', `/api/products/${milk.id}/barcodes/${EAN}`);

  expect(again.status).toBe(200);
  expect(again.body.barcodes).toEqual([EAN]);
});

test('a code already on another product is a conflict naming it', async () => {
  const conflict = await api(page, 'PUT', `/api/products/${bread.id}/barcodes/${EAN}`);

  expect(conflict.status).toBe(409);
  expect(conflict.body).toMatchObject({ error: 'BARCODE_ALREADY_LINKED', productId: milk.id });
});

test('UPC-A and EAN-13 forms of one code find the same product', async () => {
  await api(page, 'PUT', `/api/products/${bread.id}/barcodes/${UPC_A}`);

  const lookup = await api(page, 'GET', `/api/products/by-barcode/0${UPC_A}`);
  expect(lookup.body.id).toBe(bread.id);
});

test('a misread code is refused', async () => {
  const misread = await api(page, 'PUT', `/api/products/${milk.id}/barcodes/3800178226138`);

  expect(misread.status).toBe(400);
  expect(misread.body.error).toBe('INVALID_BARCODE');
});

test('unlinking frees the code for another product', async () => {
  const unlink = await api(page, 'DELETE', `/api/products/${milk.id}/barcodes/${EAN}`);
  expect(unlink.status).toBe(200);
  expect(unlink.body.barcodes).toEqual([]);

  const relink = await api(page, 'PUT', `/api/products/${bread.id}/barcodes/${EAN}`);
  expect(relink.status).toBe(200);
  expect(relink.body.barcodes).toEqual(expect.arrayContaining([EAN, `0${UPC_A}`]));
});
