import { defineConfig } from '@playwright/test';
import fs from 'node:fs';
const baseURL = process.env.E2E_BASE_URL || 'http://127.0.0.1:8135';
const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
export default defineConfig({
  testDir: './tests/e2e', timeout: 30000, fullyParallel: true, retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL, locale: 'en-US', trace: 'retain-on-failure', screenshot: 'only-on-failure', launchOptions: fs.existsSync(chrome) ? { executablePath: chrome } : {} },
  webServer: process.env.E2E_BASE_URL ? undefined : { command: 'node ../tools/local_http_proxy.cjs', env: { PORT: '8135', HOST: '127.0.0.1' }, url: baseURL, reuseExistingServer: !process.env.CI }
});
