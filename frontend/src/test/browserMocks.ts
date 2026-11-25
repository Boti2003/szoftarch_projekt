import { setupWorker } from 'msw/browser';
import { handlers } from './mswServer';

// Browser-side MSW worker reusing the shared handlers. Only started when the
// app is launched with VITE_USE_MSW=true (the Playwright e2e dev server), so
// production and normal `npm run dev` are unaffected.
export const worker = setupWorker(...handlers);

export function startBrowserMocks(): Promise<unknown> {
  return worker.start({ onUnhandledRequest: 'bypass' });
}
