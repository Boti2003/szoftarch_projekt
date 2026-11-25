import { defineConfig, devices } from '@playwright/test';

// Minimal Playwright config for the walking-skeleton e2e. Runs the Vite dev
// server with VITE_USE_MSW=true so the browser app is fully API-mocked; no
// backend is required. Gated behind the `test:e2e` script so `npm test`
// (Vitest) stays browser-free and green.
const PORT = 5173;

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: true,
  reporter: 'list',
  use: {
    baseURL: `http://localhost:${PORT}`,
    trace: 'on-first-retry',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
  webServer: {
    command: 'npm run dev',
    url: `http://localhost:${PORT}`,
    reuseExistingServer: !process.env.CI,
    env: { VITE_USE_MSW: 'true' },
    timeout: 120_000,
  },
});
