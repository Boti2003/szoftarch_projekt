import { setupServer } from 'msw/node';
import { handlers } from './handlers';

// Node-oldali MSW szerver a Vitest tesztekhez. A handlerek és fixtures a
// handlers.ts-ben vannak (msw/node nélkül), hogy a böngésző-worker is
// használhassa őket. A meglévő tesztek kompatibilitásáért minden fixture-t
// és a handlers-t itt re-exportáljuk.
export * from './handlers';

export const server = setupServer(...handlers);
