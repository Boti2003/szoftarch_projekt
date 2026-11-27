import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { FriendsPage } from './FriendsPage';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter>
          <FriendsPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

test('a barátlista betölti a barátokat az Identity-statisztikákkal', async () => {
  renderPage();
  expect(await screen.findByText('Bela Kovacs')).toBeInTheDocument();
  expect(screen.getByText('Szint 4 · 500 XP')).toBeInTheDocument();
  expect(screen.getByText('Cecil Nagy')).toBeInTheDocument();
});

test('a Kérelmek fülön megjelenik a beérkező barátkérelem elfogad/elutasít gombbal', async () => {
  renderPage();
  fireEvent.click(screen.getByRole('tab', { name: 'Kérelmek' }));

  expect(await screen.findByText('Barátkérelmet küldött neked')).toBeInTheDocument();
  // The sender UUID is resolved to a display name via Identity /users/{id}/public.
  expect(await screen.findByText('Daniel Szabo')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Elfogad' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Elutasít' })).toBeInTheDocument();
});

test('a Keresés fülön a felhasználókeresés találatokat ad és küldhető kérelem', async () => {
  renderPage();
  fireEvent.click(screen.getByRole('tab', { name: 'Keresés' }));

  const input = screen.getByLabelText('Felhasználónév keresése');
  fireEvent.change(input, { target: { value: 'be' } });

  // The search result (bela) renders with a "Barátnak jelöl" action.
  expect(await screen.findByText(/Bela Kovacs \(@bela\)/)).toBeInTheDocument();
  const sendButton = screen.getByRole('button', { name: 'Barátnak jelöl' });
  fireEvent.click(sendButton);

  // Success snackbar appears after the mutation resolves.
  await waitFor(() =>
    expect(screen.getByText(/Barátkérelem elküldve/)).toBeInTheDocument(),
  );
});
