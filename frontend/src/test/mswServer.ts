import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

// Alapértelmezett handlerek a skeletonhoz. A gazdák feature-önként bővítik.
export const handlers = [
  http.get('/api/v1/system/health', () =>
    HttpResponse.json({
      status: 'UP',
      gateway: 'UP',
      identity: 'UP',
      learning: 'UP',
      social: 'UP',
      ai: 'UP',
    }),
  ),
];

export const server = setupServer(...handlers);
