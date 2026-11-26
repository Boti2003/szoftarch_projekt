import { render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { LeaderboardPage } from './LeaderboardPage';

test('LeaderboardPage megjeleníti a toplista elemeket és a saját helyezést', async () => {
  render(
    <BrowserRouter>
      <AuthProvider>
        <LeaderboardPage />
      </AuthProvider>
    </BrowserRouter>,
  );

  expect(await screen.findByText('Globális Ranglista')).toBeInTheDocument();
  // Első helyezett Béla (🥇 1.)
  expect(await screen.findByText('@bela')).toBeInTheDocument();
  expect(await screen.findByText('500 XP')).toBeInTheDocument();

  // Második helyezett Anna (🥈 2.)
  expect(await screen.findByText('@annakovacs')).toBeInTheDocument();
  expect(await screen.findByText('150 XP')).toBeInTheDocument();

  // Saját helyezés kártya
  expect(await screen.findByText(/Saját helyezésed: 🥈 2. – 150 pont/i)).toBeInTheDocument();
});
