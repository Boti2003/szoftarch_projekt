import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';

async function bootstrap() {
  // Opt-in browser API mocking for the Playwright e2e walking skeleton.
  if (import.meta.env.VITE_USE_MSW === 'true') {
    const { startBrowserMocks } = await import('./test/browserMocks');
    await startBrowserMocks();
  }

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}

void bootstrap();
