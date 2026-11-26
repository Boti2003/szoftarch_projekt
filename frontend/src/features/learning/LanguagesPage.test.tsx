import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { expect, test } from 'vitest';
import { LanguagesPage } from './LanguagesPage';
import { languagesFixture, server } from '../../test/mswServer';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LanguagesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test('kilistázza a nyelveket és jelöli az aktívat', async () => {
  renderPage();
  expect(await screen.findByText('English')).toBeInTheDocument();
  expect(await screen.findByText('Aktív')).toBeInTheDocument();
});

test('aktiválás PUT hívást indít egy nem aktív nyelvre', async () => {
  const otherId = '11111111-1111-1111-1111-111111111199';
  server.use(
    http.get('/api/v1/languages', () =>
      HttpResponse.json([
        ...languagesFixture,
        { id: otherId, code: 'de', name: 'Deutsch' },
      ]),
    ),
  );

  let activatedId: string | null = null;
  server.use(
    http.put('/api/v1/learning/enrollments/:languageId/active', ({ params }) => {
      activatedId = params.languageId as string;
      return new HttpResponse(null, { status: 204 });
    }),
  );

  renderPage();

  const activateButton = await screen.findByRole('button', { name: 'Kezdés' });
  fireEvent.click(activateButton);

  await waitFor(() => expect(activatedId).toBe(otherId));
});

test('hiba esetén hibaállapotot jelenít meg', async () => {
  server.use(
    http.get('/api/v1/languages', () => new HttpResponse(null, { status: 500 })),
    http.get(
      '/api/v1/learning/enrollments',
      () => new HttpResponse(null, { status: 500 }),
    ),
  );

  renderPage();

  expect(
    await screen.findByText(/Nem sikerült betölteni a nyelveket/),
  ).toBeInTheDocument();
});
