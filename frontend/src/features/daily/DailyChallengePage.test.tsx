import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { expect, test } from 'vitest';
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
