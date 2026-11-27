import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { expect, test } from 'vitest';
import {
  DUEL_ID,
  duelDetailFixture,
  mockUser,
  server,
} from '../../test/mswServer';
import { AuthProvider } from '../auth/AuthContext';
import { DuelDetailPage } from './DuelDetailPage';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[`/duels/${DUEL_ID}`]}>
          <Routes>
            <Route path="/duels/:id" element={<DuelDetailPage />} />
            <Route path="/duels" element={<div>Párbajok lista</div>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

test('egy folyamatban lévő párbaj mutatja az aktuális feladatot és a rejtett ellenfél-eredményt', async () => {
  renderPage();

  // First exercise is shown (answeredCount = 0 in the fixture).
  expect(await screen.findByText('Hello magyarul?')).toBeInTheDocument();
  // Opponent UUID resolves to a display name in the header.
  expect(
    await screen.findByText((_c, el) => el?.textContent === 'Ellenfél: Bela Kovacs'),
  ).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Beküldés' })).toBeInTheDocument();

  // Opponent result is gated until the viewer completes.
  expect(screen.getByText('Rejtve, amíg be nem fejezed')).toBeInTheDocument();
});

test('válasz beküldése után megjelenik a helyes visszajelzés', async () => {
  renderPage();

  const option = await screen.findByLabelText('Szia');
  fireEvent.click(option);
  fireEvent.click(screen.getByRole('button', { name: 'Beküldés' }));

  await waitFor(() =>
    expect(screen.getByText('Helyes válasz!')).toBeInTheDocument(),
  );
});

test('befejezett párbajnál a győztes és az ellenfél eredménye látszik', async () => {
  // Override the detail endpoint: completed duel, current user won, opponent visible.
  server.use(
    http.get('/api/v1/duels/:id', () =>
      HttpResponse.json({
        ...duelDetailFixture,
        status: 'COMPLETED',
        winnerId: mockUser.id,
        self: { userId: mockUser.id, status: 'COMPLETED', score: 50, answeredCount: 5 },
        opponent: {
          userId: duelDetailFixture.opponentId,
          status: 'COMPLETED',
          score: 30,
          answeredCount: 5,
        },
      }),
    ),
  );

  renderPage();

  expect(await screen.findByText('Nyertél! 🎉')).toBeInTheDocument();
  expect(screen.getByText('50 pont · 5/5')).toBeInTheDocument();
  expect(screen.getByText('30 pont · 5/5')).toBeInTheDocument();
});
