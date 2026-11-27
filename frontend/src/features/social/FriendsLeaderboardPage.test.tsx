import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { FriendsLeaderboardPage } from './FriendsLeaderboardPage';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter>
          <FriendsLeaderboardPage />
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

test('a baráti ranglista pont szerint rangsorolva jelenik meg, a saját sor kiemelve', async () => {
  renderPage();

  // Top friend first, then the current user shown as "Te", then the lower friend.
  expect(await screen.findByText('Bela Kovacs')).toBeInTheDocument();
  expect(screen.getByText('Te')).toBeInTheDocument();
  expect(screen.getByText('🥇 1.')).toBeInTheDocument();
  expect(screen.getByText('🥈 2.')).toBeInTheDocument();
  expect(screen.getByText('🥉 3.')).toBeInTheDocument();
});
