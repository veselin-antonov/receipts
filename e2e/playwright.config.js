import { defineConfig, devices } from '@playwright/test';

// The stack is started by e2e/run.sh, not by Playwright, so a failing run can
// keep it up (E2E_KEEP=1) for a look.
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  // One retry at most: registration is rate limited to 3 per hour per address.
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]]
    : 'list',
  use: {
    baseURL: `http://localhost:${process.env.E2E_UI_PORT ?? 8080}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
