import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

// Böngésző-oldali MSW worker a megosztott handlerekkel. A handlers.ts NEM
// importál msw/node-ot, így a böngészőbe nem kerül Node-specifikus kód
// (korábban a mswServer.ts-ből importálva a `node:net`/setupServer miatt
// "The superclass is not a constructor" hibával elszállt a bootstrap).
// Csak VITE_USE_MSW=true esetén indul (Playwright e2e és kézi fejlesztői próba).
export const worker = setupWorker(...handlers);

export function startBrowserMocks(): Promise<unknown> {
  return worker.start({ onUnhandledRequest: 'bypass' });
}
