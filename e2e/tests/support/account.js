import { expect } from '@playwright/test';

// A fresh, verified, logged-in account for a spec that needs one, through the
// real ui like smoke.spec.js does it, so the session cookie is set exactly as
// a browser gets it.
//
// Registration is rate limited to 3 per hour per address and every spec file
// shares one address in a run, so a spec should call this once (in beforeAll),
// not per test.

const MAILHOG = `http://localhost:${process.env.E2E_MAILHOG_PORT ?? 7835}`;
const PASSWORD = 'E2e-Account-2026!';

/** The link from the verification mail, as a path on the ui. */
async function verificationPath(email) {
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

export async function registerAndLogIn(page, name) {
  const email = `e2e-${name}-${Date.now()}@example.test`;

  await page.goto('/register');
  await page.getByLabel('Имейл').fill(email);
  await page.getByLabel('Парола', { exact: true }).fill(PASSWORD);
  await page.getByLabel('Потвърдете паролата').fill(PASSWORD);
  await page.getByRole('button', { name: 'Регистриране' }).click();
  await expect(page.getByText('Регистрацията е успешна!')).toBeVisible();

  await page.goto(await verificationPath(email));
  await expect(page.getByText('Потвърждението е успешно!')).toBeVisible();

  await page.goto('/login');
  await page.getByLabel('Имейл').fill(email);
  await page.getByLabel('Парола', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Влизане' }).click();
  await expect(page).toHaveURL(/\/purchases$/);

  return email;
}

/**
 * Calls the api from inside the page, same-origin through the ui's nginx, so
 * the request carries the session cookie the way the ui's own calls do.
 * Resolves to { status, body } with the body parsed as JSON when there is one.
 */
export function api(page, method, path) {
  return page.evaluate(
    async ([m, p]) => {
      const response = await fetch(p, { method: m, credentials: 'same-origin' });
      const text = await response.text();
      return { status: response.status, body: text ? JSON.parse(text) : null };
    },
    [method, path]
  );
}
