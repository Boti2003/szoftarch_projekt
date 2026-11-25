import { defineConfig } from 'vitest/config';
import { configDefaults } from 'vitest/config';
import react from '@vitejs/plugin-react';

// A dev szerver a /api hívásokat a Gateway-re proxyzza (lokális fejlesztés).
// Éles/compose környezetben az Nginx végzi a proxyt.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // A Playwright e2e specek külön futnak (test:e2e); a Vitest ne vegye fel őket.
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
});
