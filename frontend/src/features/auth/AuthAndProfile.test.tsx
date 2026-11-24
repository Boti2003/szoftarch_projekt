import { render, screen } from '@testing-library/react';
import { BrowserRouter, MemoryRouter, Route, Routes } from 'react-router-dom';
import { expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../../test/mswServer';
import { apiFetch, setOnUnauthorized } from '../../api/client';
import { AuthProvider } from './AuthContext';
import { LoginPage } from './LoginPage';
import { RegisterPage } from './RegisterPage';
import { ProtectedRoute } from './ProtectedRoute';
import { ProfilePage } from '../profile/ProfilePage';

test('LoginPage megjeleníti az űrlapmezőket', () => {
  render(
    <BrowserRouter>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </BrowserRouter>,
  );

  expect(screen.getByRole('heading', { name: 'Bejelentkezés' })).toBeInTheDocument();
  expect(screen.getByLabelText(/Felhasználónév vagy Email/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/Jelszó/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Bejelentkezés' })).toBeInTheDocument();
});

test('RegisterPage megjeleníti a validációs segédszövegeket', () => {
  render(
    <BrowserRouter>
      <AuthProvider>
        <RegisterPage />
      </AuthProvider>
    </BrowserRouter>,
  );

  expect(screen.getByRole('heading', { name: 'Regisztráció' })).toBeInTheDocument();
  expect(screen.getByLabelText(/Email cím/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/Felhasználónév/i)).toBeInTheDocument();
  expect(screen.getByText(/3-30 karakter/i)).toBeInTheDocument();
  expect(screen.getByText(/legalább 1 szám és 1 speciális karakter/i)).toBeInTheDocument();
});

test('ProfilePage megjeleníti a pontszámot, szintet és a mentés gombot', async () => {
  render(
    <BrowserRouter>
      <AuthProvider>
        <ProfilePage />
      </AuthProvider>
    </BrowserRouter>,
  );

  expect(await screen.findByText('Felhasználói Profil')).toBeInTheDocument();
  expect(await screen.findByText('Szint 2')).toBeInTheDocument();
  expect(await screen.findByText(/150 \/ 200 XP/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Mentés' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Jelszó módosítása' })).toBeInTheDocument();
});

test('apiFetch meghívja az onUnauthorized visszahívást lejárt refresh token esetén', async () => {
  server.use(
    http.get('/api/v1/test-protected', () => new HttpResponse(null, { status: 401 })),
    http.post('/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),
  );

  let unauthorizedCalled = false;
  setOnUnauthorized(() => {
    unauthorizedCalled = true;
  });

  await expect(apiFetch('/test-protected')).rejects.toThrow();
  expect(unauthorizedCalled).toBe(true);
  setOnUnauthorized(null);
});

test('ProtectedRoute átirányít /login-ra, ha nincs bejelentkezett felhasználó', async () => {
  server.use(
    http.post('/api/v1/auth/refresh', () => new HttpResponse(null, { status: 401 })),
  );

  render(
    <MemoryRouter initialEntries={['/leaderboard']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<div>Bejelentkező oldal</div>} />
          <Route element={<ProtectedRoute />}>
            <Route path="/leaderboard" element={<div>Védett Ranglista</div>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );

  expect(await screen.findByText('Bejelentkező oldal')).toBeInTheDocument();
});

test('párhuzamos 401 kérések esetén a refresh dedupikálva van (csak egyszer hívja a refresh-t)', async () => {
  let refreshCount = 0;
  server.use(
    http.get('/api/v1/parallel-1', () => new HttpResponse(null, { status: 401 })),
    http.get('/api/v1/parallel-2', () => new HttpResponse(null, { status: 401 })),
    http.post('/api/v1/auth/refresh', () => {
      refreshCount++;
      return HttpResponse.json({
        accessToken: 'new-token',
        tokenType: 'Bearer',
        expiresInSeconds: 300,
        user: { id: '1', email: 'a@b.c', username: 'u', displayName: 'd' },
      });
    }),
  );

  // Egyszerre indítunk két kérést
  await Promise.allSettled([
    apiFetch('/parallel-1'),
    apiFetch('/parallel-2'),
  ]);

  expect(refreshCount).toBe(1);
});
