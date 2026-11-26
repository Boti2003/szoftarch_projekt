import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';

async function bootstrap() {
  // Opt-in browser API mocking (Playwright e2e és kézi fejlesztői kipróbálás).
  // A mock indításának hibája NE akassza meg a renderelést (ne legyen fehér képernyő):
  // hiba esetén logolunk és mock nélkül töltünk be.
  if (import.meta.env.VITE_USE_MSW === 'true') {
    try {
      const { startBrowserMocks } = await import('./test/browserMocks');
      await startBrowserMocks();
    } catch (err) {
      console.error('[MSW] A böngésző-mock indítása sikertelen, mock nélkül töltök:', err);
    }
  }

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}

void bootstrap();
