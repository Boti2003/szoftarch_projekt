import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
import { dailyChallengeFixture, server } from '../../test/mswServer';
import { DailyChallengePage } from './DailyChallengePage';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DailyChallengePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

test('betölti a mai kihívást és megjeleníti a státuszt', async () => {
  renderPage();

  // ownStatus from the daily fixture.
  expect(await screen.findByText('Folyamatban')).toBeInTheDocument();
  expect(screen.getByText('Helyes válaszok: 0')).toBeInTheDocument();
  // Both fixture exercises render (prompts shown as exercise headings).
  expect(
    screen.getByRole('heading', { name: 'Melyik a helyes?' }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole('heading', { name: 'Igaz vagy hamis?' }),
  ).toBeInTheDocument();
});

test('egy feladat beküldése után megjelenik a visszajelzés', async () => {
  renderPage();

  const option = await screen.findByLabelText('Hello');
  fireEvent.click(option);
  // Submit the first exercise's answer.
  fireEvent.click(screen.getAllByRole('button', { name: 'Beküldés' })[0]);

  expect(await screen.findByText('Szép munka!')).toBeInTheDocument();
  expect(screen.getByText('Eredmény: 100%')).toBeInTheDocument();
});

test('beküldés után újratölti a mai státuszt és frissíti a bónusz-chipet', async () => {
  // Első GET: folyamatban, 0 helyes, nincs bónusz. Beküldés után a daily-today
  // query invalidálódik, a második GET a frissített, teljesített állapotot adja.
  let todayCalls = 0;
  server.use(
    http.get('/api/v1/daily-challenges/today', () => {
      todayCalls += 1;
      if (todayCalls === 1) {
        return HttpResponse.json(dailyChallengeFixture);
      }
      return HttpResponse.json({
        ...dailyChallengeFixture,
        ownStatus: { status: 'COMPLETED', correctCount: 1, bonusAwarded: true },
      });
    }),
  );

  renderPage();

  expect(await screen.findByText('Folyamatban')).toBeInTheDocument();
  expect(screen.getByText('Helyes válaszok: 0')).toBeInTheDocument();

  const option = await screen.findByLabelText('Hello');
  fireEvent.click(option);
  fireEvent.click(screen.getAllByRole('button', { name: 'Beküldés' })[0]);

  // A beküldés utáni invalidáció/refetch az aggregált státuszt frissíti.
  expect(await screen.findByText('Teljesítve')).toBeInTheDocument();
  expect(screen.getByText('Helyes válaszok: 1')).toBeInTheDocument();
  expect(screen.getByText('Bónusz megszerezve')).toBeInTheDocument();
});
