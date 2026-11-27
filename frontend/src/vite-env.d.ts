/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Opt-in flag to start the MSW browser worker (Playwright e2e only).
  readonly VITE_USE_MSW?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
