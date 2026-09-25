import { expect, test } from '@playwright/test';

// One user's first session, through the real ui, nginx, api, MongoDB and
// MailHog. OpenAI is a stub (e2e/openai-stub) that answers every scan with the
// same two-item receipt.
//
// Each step is a contract between the ui and the api that has broken before
// without any unit test noticing: registration and verification posted to
// routes the api no longer served; Jackson 3 would have rejected the manual
// form's payload; Mongo credentials were silently dropped by Boot 4.

const MAILHOG = `http://localhost:${process.env.E2E_MAILHOG_PORT ?? 8025}`;
const email = `e2e-${Date.now()}@example.test`;
const password = 'E2e-Smoke-2026!';

test.describe.configure({ mode: 'serial' });

let page;

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage();
});

test.afterAll(async () => {
  await page.close();
});

/** The link from the verification mail, as a path on the ui. */
async function verificationPath() {
  for (let attempt = 0; attempt < 40; attempt++) {
    const search = await fetch(
      `${MAILHOG}/api/v2/search?kind=to&query=${encodeURIComponent(email)}`
    ).then((r) => r.json());
    const body = search.items?.[0]?.Content?.Body
      // quoted-printable soft line breaks and escaped '='
      ?.replace(/=\r?\n/g, '')
      .replace(/=3D/g, '=');
    const link = body?.match(/https?:\/\/\S+\/verify\?\S+/)?.[0];
    if (link) {
      const url = new URL(link);
      return url.pathname + url.search;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`no verification mail for ${email}`);
}

test('registers a new account', async () => {
  await page.goto('/register');
  await page.getByLabel('Имейл').fill(email);
  await page.getByLabel('Парола', { exact: true }).fill(password);
  await page.getByLabel('Потвърдете паролата').fill(password);
  await page.getByRole('button', { name: 'Регистриране' }).click();

  await expect(page.getByText('Регистрацията е успешна!')).toBeVisible();
});

test('verifies it from the mail', async () => {
  await page.goto(await verificationPath());

  await expect(page.getByText('Потвърждението е успешно!')).toBeVisible();
});

test('logs in and lands on an empty purchase list', async () => {
  await page.goto('/login');
  await page.getByLabel('Имейл').fill(email);
  await page.getByLabel('Парола', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Влизане' }).click();

  await expect(page).toHaveURL(/\/purchases$/);
  await expect(page.getByText('Няма намерени покупки.')).toBeVisible();
});

test('scans a receipt, reviews it and saves both rows', async () => {
  await page.locator('#receipt-file').setInputFiles('fixtures/receipt.pdf');
  await page.getByRole('button', { name: 'Сканирай' }).click();

  const review = page.locator('section', {
    has: page.getByText('Преглед на разпознатите покупки'),
  });
  await expect(review.locator('input[value="E2E Прясно мляко"]')).toBeVisible();
  await expect(review.locator('input[value="E2E Банани"]')).toBeVisible();

  await page.getByRole('button', { name: 'Запази покупките' }).click();

  await expect(page.getByRole('cell', { name: 'E2E Прясно мляко' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'E2E Банани' })).toBeVisible();
});

test('adds a purchase by hand', async () => {
  await page.getByRole('button', { name: 'Нова Покупка' }).click();
  const dialog = page.getByRole('dialog', { name: 'Нова покупка' });

  await dialog.locator('input[name="product"]').fill('E2E Хляб');
  await dialog.locator('input[name="price"]').fill('1.89');

  await dialog.getByRole('combobox').filter({ hasText: 'Изберете магазин' }).click();
  await page.getByRole('button', { name: 'Добави' }).click();
  // The new-store input takes focus on a 100 ms timer. Opening the date picker
  // before that fires lets the late focus close it, so wait for the focus.
  const newStore = dialog.locator('input[name="store"]');
  await expect(newStore).toBeFocused();
  await newStore.fill('E2E Магазин');

  await dialog.getByRole('button', { name: 'Изберете дата' }).click();
  await page.locator('[data-today] button').click();

  await dialog.getByRole('button', { name: 'Submit' }).click();

  await expect(dialog).toBeHidden();
  await expect(page.getByRole('cell', { name: 'E2E Хляб' })).toBeVisible();
});

test('everything saved is still there after a reload', async () => {
  await page.reload();

  for (const product of ['E2E Прясно мляко', 'E2E Банани', 'E2E Хляб']) {
    await expect(page.getByRole('cell', { name: product })).toBeVisible();
  }
});
