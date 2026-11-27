import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { DuelsPage } from './DuelsPage';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter>
          <DuelsPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

test('a párbajlista betölti a saját párbajokat státusz-címkével és szereppel', async () => {
  renderPage();

  // The in-progress duel the user started (challenger) and the pending one
  // the user was challenged in both render with their status chips.
  expect(await screen.findByText('Te hívtad ki')).toBeInTheDocument();
  expect(screen.getByText('Téged hívott ki')).toBeInTheDocument();
  expect(screen.getByText('Folyamatban')).toBeInTheDocument();
  expect(screen.getByText('Függőben')).toBeInTheDocument();
});

test('az Új párbaj gomb megnyitja a kihívás párbeszédablakot', async () => {
  renderPage();
  await screen.findByText('Te hívtad ki');

  screen.getByRole('button', { name: 'Új párbaj' }).click();

  expect(await screen.findByRole('heading', { name: 'Új párbaj' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Kihívás' })).toBeInTheDocument();
});
